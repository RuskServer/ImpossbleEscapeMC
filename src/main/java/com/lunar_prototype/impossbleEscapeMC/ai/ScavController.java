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

import java.util.*;

public class ScavController {
    public static class SoundContact {
        public enum Kind {
            FOOTSTEP,
            GUNSHOT,
            UNKNOWN
        }

        public final Kind kind;
        public final Location sourceLocation;
        public final Vector movementDirection;
        public final double movementSpeed;
        public final boolean sprinting;
        public final boolean sneaking;
        public final int continuousNoiseTicks;
        public final double walkedDistance;

        public SoundContact(Kind kind, Location sourceLocation, Vector movementDirection, double movementSpeed,
                            boolean sprinting, boolean sneaking, int continuousNoiseTicks, double walkedDistance) {
            this.kind = kind != null ? kind : Kind.UNKNOWN;
            this.sourceLocation = sourceLocation != null ? sourceLocation.clone() : null;
            this.movementDirection = movementDirection != null ? movementDirection.clone() : null;
            this.movementSpeed = movementSpeed;
            this.sprinting = sprinting;
            this.sneaking = sneaking;
            this.continuousNoiseTicks = continuousNoiseTicks;
            this.walkedDistance = walkedDistance;
        }

        public static SoundContact footstep(Location sourceLocation, Vector movementDirection, double movementSpeed,
                                            boolean sprinting, boolean sneaking, int continuousNoiseTicks, double walkedDistance) {
            return new SoundContact(Kind.FOOTSTEP, sourceLocation, movementDirection, movementSpeed, sprinting, sneaking, continuousNoiseTicks, walkedDistance);
        }

        public static SoundContact gunshot(Location sourceLocation) {
            return new SoundContact(Kind.GUNSHOT, sourceLocation, null, 0.0, false, false, 0, 0.0);
        }
    }

    private enum BehaviorState {
        RELAXED,
        SUSPICIOUS,
        COMBAT_READY
    }

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
    private int lostTargetSteps = 0;

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
    private static final float ALERTNESS_RELAXED_THRESHOLD = 0.30f;
    private static final float ALERTNESS_COMBAT_THRESHOLD = 0.65f;
    private final Map<UUID, TargetCombatMemory> targetMemories = new HashMap<>();

    private static class TargetCombatMemory {
        int hits;
        int lowEffectiveHits;
        long lastUpdateTick;
        int recognizedHeadArmorClass = -1;
        int recognizedChestArmorClass = -1;
    }
    private UUID lastLoggedTargetId = null;
    private boolean initializedTargetState = false;
    private final Location homeLocation;
    private Location lastHeardSoundLocation = null;
    private int investigateTicks = 0;
    private BehaviorState behaviorState = BehaviorState.RELAXED;
    private float alertness;
    private boolean returningHome = false;
    private UUID intelOriginScavId;
    private int intelRelayDepth = 0;
    private boolean intelFromShared = false;

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
        this.squad = new ScavSquad(this);
        this.tactics = new ScavTactics(scav, listener, brain);
        this.currentAimVector = scav.getEyeLocation().getDirection();
        this.homeLocation = scav.getLocation().clone();
        this.alertness = (brainLevel == ScavBrain.BrainLevel.LOW) ? 0.15f : 0.25f;
        this.intelOriginScavId = scav.getUniqueId();
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
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        decayAlertness();
        vision.setAlertness(alertness);

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
                if (!intelFromShared) {
                    markDirectIntelSource();
                }
                playScavVoice("minecraft:scav1", 1.0f, 1.0f);
                squad.shareTargetWithAllies(target.getLocation());
            }
        }

        if (target != null) {
            canSeeTarget = vision.checkVision(target);
            if (canSeeTarget) {
                lostTargetSteps = 0;
                addAlertness(0.08f, "VISUAL_CONTACT", raidSessionId);
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
                lostTargetSteps++;
                if (lostTargetSteps >= 4 && lastKnownLocation != null) {
                    scav.setTarget(null);
                    target = null;
                    canSeeTarget = false;
                    lostTargetSteps = 0;
                    isPreAiming = false;
                    handleSearching();
                } else {
                    handleSearching();
                    if (lastKnownLocation != null && !isSprinting) {
                        isPreAiming = true;
                        updatePreAim(lastKnownLocation);
                    } else {
                        isPreAiming = false;
                    }
                }
            }
        } else {
            lostTargetSteps = 0;
            isPreAiming = false;
            handleSearching();
        }

        updateBehaviorState();
        if (target == null && lastKnownLocation == null) {
            handleIdleOrReturnHome(raidSessionId);
        }

        logTargetTransitionIfNeeded(raidSessionId, target);

        // 装備チェック
        ItemStack item = scav.getEquipment().getItemInMainHand();
        String itemId = (item != null && item.hasItemMeta()) ? 
            item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        ItemDefinition def = ItemRegistry.get(itemId);
        if (def == null || def.gunStats == null) {
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, 0.0f, new int[] {8, 1});
            return;
        }

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
            int[] peekActions = brain.decide(canSeeTarget ? target : null, lastKnownLocation, def.gunStats, suppression, tacticalAdvice, isSprinting, alertness);
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, peekActions);
            return;
        }

        // 7. AI思考 & 移動
        brain.updateConditions(healthPercent < 0.3, needsReload, suppression > 0.5f, tacticalAdvice > 0.5f);
        int[] actions = brain.decide(canSeeTarget ? target : null, lastKnownLocation, def.gunStats, suppression, tacticalAdvice, isSprinting, alertness);
        if (actions.length < 2) {
            logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, actions);
            return;
        }

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

        logSnapshotIfNeeded(raidSessionId, target, canSeeTarget, tacticalAdvice, actions);
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
                clearSharedIntel();
            }
        }
        searchTicks++;
        if (searchTicks > 600) {
            lastKnownLocation = null;
            isAlerted = false;
            clearSharedIntel();
        }
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
        onSoundHeard(SoundContact.gunshot(source));
    }

    public void onSoundHeard(SoundContact sound) {
        if (sound == null || sound.sourceLocation == null) return;
        if (scav.getTarget() != null && scav.hasLineOfSight(scav.getTarget()) && sound.kind != SoundContact.Kind.GUNSHOT) {
            return;
        }

        Location source = sound.sourceLocation.clone();
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        double dist = scav.getLocation().distance(source);

        float hearingBoost = (float) Math.max(0.08, 0.28 - (dist / 180.0));
        double movementSpeed = Math.max(0.0, sound.movementSpeed);
        if (sound.kind == SoundContact.Kind.GUNSHOT) {
            hearingBoost = (float) Math.max(0.20, 0.45 - (dist / 140.0));
        } else {
            if (sound.sprinting) hearingBoost += 0.08f;
            if (sound.continuousNoiseTicks >= 40) hearingBoost += 0.08f;
            if (sound.continuousNoiseTicks >= 80) hearingBoost += 0.05f;
            if (!sound.sneaking && movementSpeed > 0.12) hearingBoost += 0.03f;
            if (sound.walkedDistance > 1.5) hearingBoost += 0.03f;
        }
        addAlertness(hearingBoost, "SOUND_HEARD", raidSessionId);

        isAlerted = true;
        lastHeardSoundLocation = source.clone();

        SoundArc arc = classifySoundArc(source);
        int baseInvestigate = (behaviorState == BehaviorState.RELAXED) ? 40 : 100;
        if (sound.kind == SoundContact.Kind.GUNSHOT) {
            baseInvestigate += 20;
        } else {
            if (sound.sprinting) baseInvestigate += 20;
            if (sound.continuousNoiseTicks >= 40) baseInvestigate += 20;
            if (arc != SoundArc.FRONT) baseInvestigate += 20;
            if (arc == SoundArc.BACK) baseInvestigate += 20;
        }
        investigateTicks = baseInvestigate;

        if (sound.kind == SoundContact.Kind.GUNSHOT
                || arc != SoundArc.FRONT
                || sound.sprinting
                || sound.continuousNoiseTicks >= 40
                || behaviorState != BehaviorState.RELAXED) {
            Location inferred = source.clone();
            if (sound.kind == SoundContact.Kind.FOOTSTEP && sound.movementDirection != null && sound.movementSpeed > 0.05) {
                double lead = Math.min(5.0, 1.0 + (sound.movementSpeed * 0.35) + (arc == SoundArc.BACK ? 1.25 : 0.0));
                inferred.add(sound.movementDirection.clone().normalize().multiply(lead));
            }
            lastKnownLocation = inferred;
        }

        Vector toSource = source.toVector().subtract(scav.getEyeLocation().toVector());
        if (toSource.lengthSquared() > 0) {
            scav.setRotation(scav.getLocation().setDirection(toSource.normalize()).getYaw(), 0);
        }
        playScavVoice("minecraft:scav1", 1.0f, 1.0f); // 索敵ボイスに変更

        if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("x", source.getX());
            payload.put("y", source.getY());
            payload.put("z", source.getZ());
            payload.put("state", behaviorState.name());
            payload.put("kind", sound.kind.name());
            payload.put("arc", arc.name());
            payload.put("speed", movementSpeed);
            payload.put("continuousTicks", sound.continuousNoiseTicks);
            payload.put("walkedDistance", sound.walkedDistance);
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "SOUND_INVESTIGATE_START", payload);
        }
    }

    private enum SoundArc {
        FRONT,
        SIDE,
        BACK
    }

    private SoundArc classifySoundArc(Location source) {
        Vector toSource = source.toVector().subtract(scav.getLocation().toVector());
        if (toSource.lengthSquared() == 0) return SoundArc.FRONT;

        Vector forward = scav.getLocation().getDirection().clone();
        forward.setY(0);
        if (forward.lengthSquared() == 0) return SoundArc.FRONT;
        forward.normalize();

        Vector flatToSource = toSource.clone();
        flatToSource.setY(0);
        if (flatToSource.lengthSquared() == 0) return SoundArc.FRONT;
        flatToSource.normalize();

        Vector lateral = new Vector(-forward.getZ(), 0, forward.getX());
        double frontDot = flatToSource.dot(forward);
        double sideDot = Math.abs(flatToSource.dot(lateral.normalize()));

        if (frontDot < -0.35) return SoundArc.BACK;
        if (sideDot > 0.65) return SoundArc.SIDE;
        return SoundArc.FRONT;
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
        String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
        addAlertness(0.35f, "TOOK_DAMAGE", raidSessionId);
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
                markDirectIntelSource();
                squad.shareTargetWithAllies(lastKnownLocation);
            }
        }
    }

    public UUID getIntelOriginScavId() {
        return intelOriginScavId != null ? intelOriginScavId : scav.getUniqueId();
    }

    public int getIntelRelayDepth() {
        return intelRelayDepth;
    }

    public void receiveSharedTarget(Location loc, UUID originScavId, int relayDepth) {
        if (loc == null) return;

        if (intelFromShared && Objects.equals(this.intelOriginScavId, originScavId) && this.intelRelayDepth <= relayDepth) {
            if (lastKnownLocation == null) {
                lastKnownLocation = loc.clone();
            }
            isAlerted = true;
            return;
        }

        lastKnownLocation = loc.clone();
        isAlerted = true;
        intelFromShared = true;
        intelOriginScavId = (originScavId != null) ? originScavId : scav.getUniqueId();
        intelRelayDepth = Math.max(0, relayDepth);
    }

    private void markDirectIntelSource() {
        intelOriginScavId = scav.getUniqueId();
        intelRelayDepth = 0;
        intelFromShared = false;
    }

    private void clearSharedIntel() {
        intelOriginScavId = scav.getUniqueId();
        intelRelayDepth = 0;
        intelFromShared = false;
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

    private void logTargetTransitionIfNeeded(String raidSessionId, LivingEntity target) {
        if (raidSessionId == null || plugin.getAiRaidLogger() == null || !plugin.getAiRaidLogger().isEnabled()) return;
        UUID currentTargetId = target != null ? target.getUniqueId() : null;

        if (!initializedTargetState) {
            initializedTargetState = true;
            lastLoggedTargetId = currentTargetId;
            if (currentTargetId != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("targetId", currentTargetId.toString());
                payload.put("reason", "INITIAL");
                plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_ACQUIRED", payload);
            }
            return;
        }

        if (Objects.equals(lastLoggedTargetId, currentTargetId)) return;
        if (lastLoggedTargetId == null && currentTargetId != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("targetId", currentTargetId.toString());
            payload.put("reason", "ACQUIRE");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_ACQUIRED", payload);
        } else if (lastLoggedTargetId != null && currentTargetId == null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("previousTargetId", lastLoggedTargetId.toString());
            payload.put("reason", "LOST");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_LOST", payload);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("fromTargetId", lastLoggedTargetId.toString());
            payload.put("toTargetId", currentTargetId.toString());
            payload.put("reason", "SWITCH");
            plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TARGET_SWITCH", payload);
        }
        lastLoggedTargetId = currentTargetId;
    }

    private void logSnapshotIfNeeded(String raidSessionId, LivingEntity target, boolean canSeeTarget, float tacticalAdvice, int[] actions) {
        if (raidSessionId == null || plugin.getAiRaidLogger() == null || !plugin.getAiRaidLogger().isEnabled()) return;
        int interval = plugin.getAiRaidLogger().getSampleIntervalTicks();
        if (interval <= 0 || Bukkit.getCurrentTick() % interval != 0) return;

        int move = actions != null && actions.length > 0 ? actions[0] : 8;
        int shoot = actions != null && actions.length > 1 ? actions[1] : 1;
        float[] neurons = brain.getNeuronStates();
        float aggression = neurons.length > 0 ? neurons[0] : 0.0f;
        float fear = neurons.length > 1 ? neurons[1] : 0.0f;
        float tactical = neurons.length > 2 ? neurons[2] : 0.0f;

        plugin.getAiRaidLogger().logSnapshot(
                raidSessionId,
                scav.getUniqueId(),
                brainLevel.name(),
                scav.getLocation(),
                target != null ? target.getUniqueId() : null,
                canSeeTarget,
                lastKnownLocation,
                suppression,
                searchTicks,
                isSprinting,
                isHoldingAngle,
                alertness,
                1.0f - alertness,
                behaviorState.name(),
                scav.getLocation().distance(homeLocation),
                brain.getCurrentModeName(),
                move,
                shoot,
                aggression,
                fear,
                tactical,
                tacticalAdvice,
                plugin.getAiRaidLogger().isCaptureRaycastEnabled() ? buildRaycastSurfaceSample(target) : null
        );
    }

    private void updateBehaviorState() {
        if (alertness >= ALERTNESS_COMBAT_THRESHOLD) {
            behaviorState = BehaviorState.COMBAT_READY;
        } else if (alertness >= ALERTNESS_RELAXED_THRESHOLD) {
            behaviorState = BehaviorState.SUSPICIOUS;
        } else {
            behaviorState = BehaviorState.RELAXED;
        }
    }

    private void handleIdleOrReturnHome(String raidSessionId) {
        boolean moved = false;

        if (investigateTicks > 0 && lastHeardSoundLocation != null) {
            investigateTicks--;
            double distToSound = scav.getLocation().distance(lastHeardSoundLocation);
            if (distToSound > 1.5) {
                scav.getPathfinder().moveTo(lastHeardSoundLocation, 0.9);
                moved = true;
            }
            if (distToSound < 2.0 || investigateTicks <= 0) {
                lastHeardSoundLocation = null;
                investigateTicks = 0;
            }
        }

        if (!moved && behaviorState == BehaviorState.RELAXED) {
            double homeDist = scav.getLocation().distance(homeLocation);
            if (homeDist > 3.0) {
                scav.getPathfinder().moveTo(homeLocation, 0.75);
                if (!returningHome && raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("homeDistance", homeDist);
                    plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "RETURN_HOME_START", payload);
                }
                returningHome = true;
            } else if (homeDist <= 1.5) {
                scav.getPathfinder().stopPathfinding();
                if (returningHome && raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("homeDistance", homeDist);
                    plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "RETURN_HOME_DONE", payload);
                }
                returningHome = false;
            }
        } else {
            returningHome = false;
        }
    }

    private void decayAlertness() {
        float decay;
        if (brainLevel == ScavBrain.BrainLevel.LOW) {
            decay = 0.0025f;
        } else {
            decay = 0.0015f;
        }
        if (lastKnownLocation != null) {
            decay *= 0.5f;
        }
        alertness = clamp01(alertness - decay);
    }

    private void addAlertness(float amount, String reason, String raidSessionId) {
        float before = alertness;
        alertness = clamp01(alertness + amount);
        if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
            if (Math.abs(alertness - before) > 0.0001f) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("reason", reason);
                payload.put("before", before);
                payload.put("after", alertness);
                payload.put("delta", alertness - before);
                plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "ALERTNESS_CHANGE", payload);
            }
        }
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private List<Map<String, Object>> buildRaycastSurfaceSample(LivingEntity target) {
        ScavVision.LosSnapshot los = vision.getLastLosSnapshot();
        if (los == null || los.rays == null || los.rays.isEmpty()) return null;

        List<Map<String, Object>> out = new ArrayList<>();

        Map<String, Object> primary = new HashMap<>();
        primary.put("kind", "los_primary");
        primary.put("targetId", los.targetId != null ? los.targetId.toString() : null);
        primary.put("visible", los.visible);
        if (los.primaryRay != null) {
            primary.put("band", los.primaryRay.band);
            primary.put("lateral", los.primaryRay.lateral);
            primary.put("distance", los.primaryRay.distance);
            primary.put("blockType", los.primaryRay.blockType);
            primary.put("face", los.primaryRay.face);
            primary.put("x", los.primaryRay.hitX);
            primary.put("y", los.primaryRay.hitY);
            primary.put("z", los.primaryRay.hitZ);
        }
        out.add(primary);

        // 面圧縮: バンド(upper/middle/lower)ごとに可視率/距離を集約
        Map<String, BandAgg> bands = new HashMap<>();
        Map<String, SurfaceAgg> surfaces = new HashMap<>();

        for (ScavVision.LosRay ray : los.rays) {
            String bandKey = ray.band != null ? ray.band : "unknown";
            BandAgg b = bands.computeIfAbsent(bandKey, k -> new BandAgg());
            b.count++;
            if (ray.clear) b.clearCount++;
            b.minDistance = Math.min(b.minDistance, ray.distance);
            b.distanceSum += ray.distance;

            if (!ray.clear && ray.blockType != null) {
                String face = ray.face != null ? ray.face : "UNKNOWN";
                String surfaceKey = bandKey + "|" + ray.blockType + "|" + face;
                SurfaceAgg s = surfaces.computeIfAbsent(surfaceKey, k -> new SurfaceAgg(bandKey, ray.blockType, face));
                s.count++;
                s.minDistance = Math.min(s.minDistance, ray.distance);
                s.distanceSum += ray.distance;
            }
        }

        List<Map<String, Object>> bandList = new ArrayList<>();
        for (Map.Entry<String, BandAgg> e : bands.entrySet()) {
            BandAgg b = e.getValue();
            Map<String, Object> rec = new HashMap<>();
            rec.put("band", e.getKey());
            rec.put("count", b.count);
            rec.put("clearCount", b.clearCount);
            rec.put("visibilityRatio", b.count > 0 ? (double) b.clearCount / (double) b.count : 0.0);
            rec.put("minDistance", b.minDistance == Double.POSITIVE_INFINITY ? null : b.minDistance);
            rec.put("avgDistance", b.count > 0 ? b.distanceSum / (double) b.count : null);
            bandList.add(rec);
        }

        List<Map<String, Object>> surfaceList = new ArrayList<>();
        for (SurfaceAgg s : surfaces.values()) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("band", s.band);
            rec.put("blockType", s.blockType);
            rec.put("face", s.face);
            rec.put("count", s.count);
            rec.put("minDistance", s.minDistance == Double.POSITIVE_INFINITY ? null : s.minDistance);
            rec.put("avgDistance", s.count > 0 ? s.distanceSum / (double) s.count : null);
            surfaceList.add(rec);
        }

        Map<String, Object> surfaceSummary = new HashMap<>();
        surfaceSummary.put("kind", "depth_bins");
        surfaceSummary.put("bands", bandList);
        surfaceSummary.put("surfaces", surfaceList);
        surfaceSummary.put("rayCount", los.rays.size());
        out.add(surfaceSummary);

        return out;
    }

    private static class BandAgg {
        int count = 0;
        int clearCount = 0;
        double minDistance = Double.POSITIVE_INFINITY;
        double distanceSum = 0.0;
    }

    private static class SurfaceAgg {
        final String band;
        final String blockType;
        final String face;
        int count = 0;
        double minDistance = Double.POSITIVE_INFINITY;
        double distanceSum = 0.0;

        SurfaceAgg(String band, String blockType, String face) {
            this.band = band;
            this.blockType = blockType;
            this.face = face;
        }
    }
}
