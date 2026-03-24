package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ScavController {
    private final ImpossbleEscapeMC plugin;
    private final Mob scav;
    private final ScavBrain brain;
    private final GunListener gunListener;
    private final ScavBrain.BrainLevel brainLevel;

    // Components
    private final ScavVision vision;
    private final ScavSquad squad;
    private final ScavTactics tactics;

    private Chunk currentChunk = null;
    private Location lastKnownLocation = null;
    private int searchTicks = 0;
    private float suppression = 0.0f;
    private boolean isAlerted = false;
    private int cornerCheckTicks = 0;
    private boolean isHoldingAngle = false;
    private int lastSquadUpdate = 0;

    public boolean isSprinting() {
        return isSprinting;
    }

    private boolean isSprinting = false;
    private boolean isPreAiming = false;

    private Vector currentAimVector = null;
    private double aimErrorYaw = 0;
    private double aimErrorPitch = 0;
    private long lastMobShotTime = 0;

    private int voiceLineCooldown = 0;
    private static final int VOICE_LINE_COOLDOWN_TICKS = 100;
    private static final double LOW_EFFECTIVE_DAMAGE_THRESHOLD = 4.0;
    private static final int TARGET_MEMORY_EXPIRE_TICKS = 20 * 60;
    private final Map<UUID, TargetCombatMemory> targetMemories = new HashMap<>();

    private static class TargetCombatMemory {
        int hits;
        int lowEffectiveHits;
        long lastUpdateTick;
        int recognizedHeadArmorClass = -1;
        int recognizedChestArmorClass = -1;
    }

    public ScavController(ImpossbleEscapeMC plugin, Mob scav, GunListener listener) {
        this(plugin, scav, listener, ScavBrain.BrainLevel.MID);
    }

    public ScavController(ImpossbleEscapeMC plugin, Mob scav, GunListener listener, ScavBrain.BrainLevel brainLevel) {
        this.plugin = plugin;
        this.scav = scav;
        this.brainLevel = brainLevel;
        this.brain = new ScavBrain(scav, brainLevel);
        this.gunListener = listener;
        this.vision = new ScavVision(scav);
        this.squad = new ScavSquad(scav);
        this.tactics = new ScavTactics(scav, listener, brain);
        this.currentAimVector = scav.getEyeLocation().getDirection();
        updateChunkTicket();
    }

    public Mob getScav() { return scav; }
    public ScavSquad getSquad() { return squad; }
    public ScavBrain getBrain() { return brain; }
    public ScavBrain.BrainLevel getBrainLevel() { return brainLevel; }
    public void setLastKnownLocation(Location loc) { this.lastKnownLocation = loc; }
    public void setAlerted(boolean alerted) { this.isAlerted = alerted; }
    public int getSearchTicks() { return searchTicks; }
    public void setSearchTicks(int ticks) { this.searchTicks = ticks; }

    public void onTick() {
        updateChunkTicket();
        cleanupTargetMemories();
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        // ヒートマップ記録
        if (tactics.getTacticalCoverLoc() != null && suppression < 0.2f && scav.getLocation().distance(tactics.getTacticalCoverLoc()) < 1.5) {
            CombatHeatmapManager.record(scav.getLocation(), CombatHeatmapManager.TraceType.SAFE, 0.1f);
        }

        // タイマー更新
        if (suppression > 0) suppression = Math.max(0, suppression - 0.02f);
        if (voiceLineCooldown > 0) voiceLineCooldown--;
        tactics.updateTimers();

        // 1. 分隊更新
        lastSquadUpdate++;
        if (lastSquadUpdate >= 10) {
            squad.updateNearbyAllies();
            squad.handleSquadRoles();
            lastSquadUpdate = 0;
        }

        // 2. 索敵 & 情報共有
        if (target == null) {
            target = vision.scanForTargets();
            if (target != null) {
                scav.setTarget(target);
                playScavVoice("minecraft:scav1", 1.0f, 1.0f);
                squad.shareTargetWithAllies(target.getLocation());
            }
        }

        if (target != null) {
            canSeeTarget = vision.checkVision(target);
            if (canSeeTarget) {
                lastKnownLocation = target.getLocation();
                searchTicks = 0;
                isAlerted = true;
                isPreAiming = false;
                updateHumanAim(target);

                if (Bukkit.getCurrentTick() % 10 == 0) {
                    if (Math.random() < 0.2) playScavVoice("minecraft:scav2", 1.0f, 1.0f);
                    squad.shareTargetWithAllies(lastKnownLocation);
                }
            } else {
                handleSearching();
                if (lastKnownLocation != null && !isSprinting) {
                    isPreAiming = true;
                    updatePreAim(lastKnownLocation);
                } else {
                    isPreAiming = false;
                }
            }
        } else {
            isPreAiming = false;
            handleSearching();
        }

        // 装備チェック
        ItemStack item = scav.getEquipment().getItemInMainHand();
        String itemId = (item != null && item.hasItemMeta()) ? 
            item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null || def.gunStats == null) return;

        int currentAmmo = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean needsReload = currentAmmo <= 0;
        double healthPercent = scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        // 3. スイッチング
        if (squad.getMyRole() == ScavSquad.SquadRole.POINTMAN && (suppression > 0.8f || needsReload || healthPercent < 0.4)) {
            squad.requestRoleSwitch();
        }

        // 4. カバー検索
        if (target != null && tactics.getCoverSearchCooldown() <= 0) {
            if (suppression > 0.6f || healthPercent < 0.4 || needsReload) {
                Location cover = tactics.findCover(target);
                tactics.setTacticalCoverLoc(cover);
                tactics.setCoverSearchCooldown(60);
                if (cover != null) tactics.setCoverStayTicks(100);
            }
        }

        this.isSprinting = brain.getAdrenaline() > 0.6f || brain.getFrustration() > 0.7f || tactics.getPeekPhase() > 0 || tactics.getTacticalCoverLoc() != null;

        // 5. タクティカルアドバイス
        float tacticalAdvice = 0.0f;
        boolean hasLos = target != null && scav.hasLineOfSight(target);
        if (!hasLos && lastKnownLocation != null) tacticalAdvice = 0.8f;
        else if (suppression > 0.5f) tacticalAdvice = 0.5f;

        if (target != null) {
            float lowEffectRatio = getLowEffectiveRatio(target.getUniqueId());
            if (brainLevel == ScavBrain.BrainLevel.MID && lowEffectRatio >= 0.55f) {
                tacticalAdvice = Math.max(tacticalAdvice, 0.85f);
                // 通りにくい相手を見続けないよう、MIDだけ周期的にターゲット再評価
                if (Bukkit.getCurrentTick() % 20 == 0) {
                    LivingEntity alt = vision.scanForTargets();
                    if (alt != null && !alt.getUniqueId().equals(target.getUniqueId())) {
                        scav.setTarget(alt);
                        target = alt;
                        canSeeTarget = vision.checkVision(target);
                        lastKnownLocation = target.getLocation();
                    }
                }
            }
        }

        // 6. Peek Maneuver
        if (tactics.getPeekPhase() > 0) {
            tactics.handlePeekManeuver(target, def.gunStats, suppression, isSprinting, lastMobShotTime, t -> lastMobShotTime = t);
            checkAndInteractWithDoors();
            brain.decide(canSeeTarget ? target : null, lastKnownLocation, def.gunStats, suppression, tacticalAdvice, isSprinting);
            return;
        }

        // 7. AI思考 & 移動
        brain.updateConditions(healthPercent < 0.3, needsReload, suppression > 0.5f, tacticalAdvice > 0.5f);
        int[] actions = brain.decide(canSeeTarget ? target : null, lastKnownLocation, def.gunStats, suppression, tacticalAdvice, isSprinting);
        if (actions.length < 2) return;

        int moveAction = actions[0];
        // 特殊移動判定
        float[] neurons = brain.getNeuronStates();
        if (neurons[1] > 0.7f) moveAction = (Math.random() > 0.5) ? 3 : 4; // Retreat
        if (neurons[2] > 0.7f && !hasLos && lastKnownLocation != null) {
            if (neurons[0] > 0.6f && Math.random() > 0.6) moveAction = 7; // Peek
            else if (Math.random() > 0.8) moveAction = 6; // Search
        }

        if (moveAction == 8) {
            isHoldingAngle = true;
            if (lastKnownLocation != null) updatePreAim(lastKnownLocation);
            scav.getPathfinder().stopPathfinding();
        } else if (tactics.getTacticalCoverLoc() != null && tactics.getCoverStayTicks() > 0) {
            isHoldingAngle = false;
            double dist = scav.getLocation().distance(tactics.getTacticalCoverLoc());
            if (dist > 1.0) scav.getPathfinder().moveTo(tactics.getTacticalCoverLoc(), isSprinting ? 1.5 : 1.0);
            else {
                if (!needsReload && canSeeTarget) applyAimToEntity();
            }
        } else if (canSeeTarget) {
            isHoldingAngle = false;
            boolean isAuto = "AUTO".equalsIgnoreCase(def.gunStats.fireMode);
            tactics.handleCombatMovement(moveAction, target, isAuto, neurons[0], suppression, isSprinting, squad.getNearbyAllies());
        } else if (lastKnownLocation != null) {
            isHoldingAngle = false;
            if (moveAction == 7) tactics.startPeek(lastKnownLocation, isSprinting);
            else handleSearching();
        }

        checkAndInteractWithDoors();

        // 射撃
        if (actions[1] == 0) {
            long now = System.currentTimeMillis();
            long interval = (long) (60000.0 / def.gunStats.rpm);
            if (canSeeTarget) {
                applyAimToEntity();
                if (now - lastMobShotTime >= interval) {
                    // セミオートやポンプアクションの場合は人間らしい「タップ遅延」や「次弾装填待ち」を追加
                    if ("SEMI".equalsIgnoreCase(def.gunStats.fireMode) || "PUMP_ACTION".equalsIgnoreCase(def.gunStats.boltType)) {
                        long extraDelay = 50 + (long)(Math.random() * 150);
                        if ("PUMP_ACTION".equalsIgnoreCase(def.gunStats.boltType)) {
                            extraDelay += 300 + (long)(Math.random() * 400); // ポンプアクションはコッキング時間を考慮して大幅に遅延
                        }
                        if (now - lastMobShotTime < interval + extraDelay) return;
                    }

                    double inacc = 0.04 + (suppression * 0.1) + (scav.getVelocity().length() > 0.1 ? 0.04 : 0);
                    if ("PUMP_ACTION".equalsIgnoreCase(def.gunStats.boltType)) {
                        inacc += 0.08; // ポンプアクションは反動が大きく、次弾の精密射撃が難しいことを表現
                    }
                    
                    gunListener.executeMobShoot(scav, def.gunStats, 1, inacc);
                    lastMobShotTime = now;
                }
            } else if (isPreAiming && Math.random() < 0.05) {
                applyAimToEntity();
                if (now - lastMobShotTime >= interval) {
                    gunListener.executeMobShoot(scav, def.gunStats, 1, 0.3);
                    lastMobShotTime = now;
                }
            } else if (target != null) {
                tactics.handleJumpShot(target);
            }
        }
    }

    private void handleSearching() {
        if (lastKnownLocation == null) {
            if (isAlerted && Math.random() < 0.05) {
                scav.setRotation(scav.getLocation().getYaw() + (float)(Math.random()-0.5)*90f, (float)(Math.random()-0.5)*40f);
            }
            return;
        }
        tactics.handleSearching(lastKnownLocation, searchTicks, isSprinting, this::updatePreAim);
        double dist = scav.getLocation().distance(lastKnownLocation);
        if (dist <= 2.5) {
            cornerCheckTicks++;
            if (cornerCheckTicks > 60) {
                lastKnownLocation = null;
                tactics.resetSlicing();
                cornerCheckTicks = 0;
                isAlerted = false;
            }
        }
        searchTicks++;
        if (searchTicks > 600) { lastKnownLocation = null; isAlerted = false; }
    }

    private void updateHumanAim(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        if (currentAimVector == null) currentAimVector = idealDir.clone();
        double lerp = 0.7 - (suppression * 0.2);
        currentAimVector = currentAimVector.clone().add(idealDir.clone().subtract(currentAimVector).multiply(lerp)).normalize();
        aimErrorYaw = (aimErrorYaw + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
        aimErrorPitch = (aimErrorPitch + (Math.random()-0.5)*0.04 + (suppression > 0.3 ? (Math.random()-0.5)*suppression*0.15 : 0)) * 0.4;
    }

    private void updatePreAim(Location loc) {
        Vector ideal = loc.clone().add(0, 1.5, 0).toVector().subtract(scav.getEyeLocation().toVector()).normalize();
        if (currentAimVector == null) currentAimVector = ideal.clone();
        currentAimVector = currentAimVector.clone().add(ideal.clone().subtract(currentAimVector).multiply(0.3)).normalize();
        applyAimToEntity();
    }

    private void applyAimToEntity() {
        if (currentAimVector == null) return;
        Location l = scav.getLocation();
        scav.setRotation(l.setDirection(currentAimVector).getYaw() + (float)aimErrorYaw, l.setDirection(currentAimVector).getPitch() + (float)aimErrorPitch);
    }

    public void onSoundHeard(Location source) {
        if (scav.getTarget() == null || !scav.hasLineOfSight(scav.getTarget())) {
            isAlerted = true;
            lastKnownLocation = source.clone();
            scav.setRotation(scav.getLocation().setDirection(source.toVector().subtract(scav.getEyeLocation().toVector()).normalize()).getYaw(), 0);
            playScavVoice("minecraft:scav1", 1.0f, 1.0f); // 索敵ボイスに変更
        }
    }

    public void playScavVoice(String sound, float volume, float pitch) {
        if (voiceLineCooldown > 0) return;
        
        // 周囲の味方が最近喋ったかチェック
        for (ScavController ally : squad.getNearbyAllies()) {
            if (ally.voiceLineCooldown > VOICE_LINE_COOLDOWN_TICKS - 40) return; // 誰かが2秒以内に喋り出していたらキャンセル
        }

        scav.getWorld().playSound(scav.getLocation(), sound, volume, pitch);
        voiceLineCooldown = VOICE_LINE_COOLDOWN_TICKS + (int)(Math.random() * 40); // 5〜7秒のクールダウン
    }

    public void onKill(LivingEntity victim) {
        playScavVoice("minecraft:scav4", 1.0f, 1.0f);
    }

    public void onDamage(Entity attacker) {
        suppression = Math.min(1.0f, suppression + 0.3f);
        CombatHeatmapManager.record(scav.getLocation(), CombatHeatmapManager.TraceType.DANGER, 1.0f);
        if (scav.getHealth() / scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() < 0.5) {
            playScavVoice("minecraft:scav3", 1.0f, 1.0f);
        }
        if (attacker instanceof LivingEntity living) {
            scav.teleport(scav.getLocation().setDirection(living.getLocation().toVector().subtract(scav.getLocation().toVector()).normalize()));
            if (scav.getTarget() == null) {
                scav.setTarget(living);
                lastKnownLocation = living.getLocation();
                squad.shareTargetWithAllies(lastKnownLocation);
            }
        }
    }

    public void addSuppression(float amount) { this.suppression = Math.min(1.0f, this.suppression + amount); }
    public void onDeath() { brain.onDeath(); releaseChunkTicket(); }
    public void terminate() { brain.terminate(); releaseChunkTicket(); }

    private void updateChunkTicket() {
        Chunk newChunk = scav.getLocation().getChunk();
        if (currentChunk == null || !currentChunk.equals(newChunk)) {
            if (currentChunk != null) currentChunk.removePluginChunkTicket(plugin);
            currentChunk = newChunk;
            currentChunk.addPluginChunkTicket(plugin);
        }
    }

    private void releaseChunkTicket() {
        if (currentChunk != null) { currentChunk.removePluginChunkTicket(plugin); currentChunk = null; }
    }

    private void checkAndInteractWithDoors() {
        Location loc = scav.getLocation();
        Vector dir = loc.getDirection().setY(0).normalize();
        tryOpenDoor(loc.getBlock());
        tryOpenDoor(loc.clone().add(0, 1, 0).getBlock());
        for (double d : new double[]{1.0, 1.5}) {
            if (tryOpenDoor(loc.clone().add(dir.clone().multiply(d)).getBlock()) || 
                tryOpenDoor(loc.clone().add(0, 1, 0).add(dir.clone().multiply(d)).getBlock())) break;
        }
    }

    private boolean tryOpenDoor(Block block) {
        if (block.getType().toString().contains("TRAPDOOR")) return false;
        if (block.getBlockData() instanceof Openable openable && !openable.isOpen()) {
            openable.setOpen(true);
            block.setBlockData(openable);
            Sound s = block.getType().toString().contains("IRON") ? Sound.BLOCK_IRON_DOOR_OPEN : 
                     (block.getType().toString().contains("FENCE_GATE") ? Sound.BLOCK_FENCE_GATE_OPEN : Sound.BLOCK_WOODEN_DOOR_OPEN);
            block.getWorld().playSound(block.getLocation(), s, 1.0f, 1.0f);
            return true;
        }
        return false;
    }

    public void onBulletHitDealt(LivingEntity victim, double finalDamage, boolean penetrated, String hitLocation) {
        if (victim == null) return;
        TargetCombatMemory memory = targetMemories.computeIfAbsent(victim.getUniqueId(), id -> new TargetCombatMemory());
        memory.hits++;
        memory.lastUpdateTick = Bukkit.getCurrentTick();

        boolean lowEffective = !penetrated || finalDamage < LOW_EFFECTIVE_DAMAGE_THRESHOLD;
        if (lowEffective) {
            memory.lowEffectiveHits++;
        } else if (memory.lowEffectiveHits > 0 && Math.random() < 0.35) {
            // 有効打が続く時は過去の低有効打印象を少しずつ減衰
            memory.lowEffectiveHits--;
        }

        if (memory.hits > 16) {
            memory.hits = 8;
            memory.lowEffectiveHits = Math.max(0, memory.lowEffectiveHits / 2);
        }

        if (brainLevel == ScavBrain.BrainLevel.HIGH) {
            if ("head".equalsIgnoreCase(hitLocation)) {
                memory.recognizedHeadArmorClass = getArmorClassFromSlot(victim, EquipmentSlot.HEAD);
            } else {
                memory.recognizedChestArmorClass = getArmorClassFromSlot(victim, EquipmentSlot.CHEST);
            }
        }
    }

    private int getArmorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    private float getLowEffectiveRatio(UUID targetId) {
        TargetCombatMemory memory = targetMemories.get(targetId);
        if (memory == null || memory.hits <= 0) return 0.0f;
        return (float) memory.lowEffectiveHits / (float) memory.hits;
    }

    private void cleanupTargetMemories() {
        long now = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, TargetCombatMemory>> it = targetMemories.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TargetCombatMemory> e = it.next();
            if (now - e.getValue().lastUpdateTick > TARGET_MEMORY_EXPIRE_TICKS) {
                it.remove();
            }
        }
    }
}
