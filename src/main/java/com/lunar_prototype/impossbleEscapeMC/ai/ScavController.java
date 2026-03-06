package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class ScavController {
    private final Mob scav;
    private final ScavBrain brain;
    private final GunListener gunListener;

    private Location lastKnownLocation = null;
    private int searchTicks = 0;
    private float suppression = 0.0f; // 制圧レベル (0.0 - 1.0)
    private int jumpCooldown = 0;

    // --- Aiming States (Human-like) ---
    private Vector currentAimVector = null;
    private double aimErrorYaw = 0;
    private double aimErrorPitch = 0;

    // --- Peek & Hide Maneuver States ---
    private int peekPhase = 0; // 0: None, 1: Moving out, 2: Moving back
    private Location coverLocation = null;
    private Location peekLocation = null;
    private int peekTicks = 0;

    private static final double MAX_VISION_DISTANCE = 96.0; // 6 Chunks
    private static final double FOV_ANGLE = 120.0;

    public ScavController(Mob scav, GunListener listener) {
        this.scav = scav;
        this.brain = new ScavBrain(scav);
        this.gunListener = listener;
        this.currentAimVector = scav.getEyeLocation().getDirection();
    }

    public void onTick() {
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        // クールダウンと制圧レベルの減衰
        if (suppression > 0)
            suppression = Math.max(0, suppression - 0.02f);
        if (jumpCooldown > 0)
            jumpCooldown--;

        if (target != null) {
            canSeeTarget = checkVision(target);
            if (canSeeTarget) {
                lastKnownLocation = target.getLocation();
                searchTicks = 0;
                updateHumanAim(target);
            } else {
                if (lastKnownLocation != null) {
                    searchTicks++;
                    if (searchTicks > 200) {
                        lastKnownLocation = null;
                        scav.setTarget(null);
                    }
                }
            }
        }

        // 装備中の銃のステータス取得
        ItemStack item = scav.getEquipment().getItemInMainHand();
        String itemId = (item != null && item.hasItemMeta()) ? 
            item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) {
            return;
        }

        // --- タクティカル・アドバイスの計算 (教示学習) ---
        float tacticalAdvice = 0.0f;
        boolean hasLos = target != null && scav.hasLineOfSight(target);

        if (!hasLos && lastKnownLocation != null) {
            tacticalAdvice = 0.8f; // Peek&Hide推奨度を下げる
        } else if (suppression > 0.5f) {
            tacticalAdvice = 0.5f; // 回避推奨
        }

        // --- Peek and Hide Maneuver (Priority Override) ---
        if (peekPhase > 0) {
            peekTicks++;
            if (peekPhase == 1) { // 飛び出しフェーズ
                scav.getPathfinder().moveTo(peekLocation);

                // 視認したか、最大時間（5 ticks）経過で決め撃ち
                boolean currentLos = target != null && scav.hasLineOfSight(target);
                if (currentLos || peekTicks >= 5) {
                    if (currentLos) updateHumanAim(target);
                    
                    applyAimToEntity();
                    gunListener.executeMobShoot(scav, def.gunStats, 1, 0.25 + (suppression * 0.2));
                    
                    peekPhase = 2; // 戻りフェーズへ
                    peekTicks = 0;
                }
            } else if (peekPhase == 2) { // 戻りフェーズ
                scav.getPathfinder().moveTo(coverLocation);
                if (scav.getLocation().distance(coverLocation) < 1.0 || peekTicks >= 5) {
                    peekPhase = 0; // マニューバ終了
                }
            }
            brain.decide(canSeeTarget ? target : null, def.gunStats, suppression, tacticalAdvice);
            return;
        }

        // --- 状況の注入 (Condition Injection) ---
        double maxHealth = scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        boolean isLowHealth = scav.getHealth() < (maxHealth * 0.3);
        int currentAmmo = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int magSize = def.gunStats.magSize;
        boolean isLowAmmo = currentAmmo < (magSize * 0.2);

        brain.updateConditions(isLowHealth, isLowAmmo, suppression > 0.5f, tacticalAdvice > 0.5f);

        // AIに思考させる
        int[] actions = brain.decide(canSeeTarget ? target : null, def.gunStats, suppression, tacticalAdvice);
        if (actions.length < 2) return;

        int moveAction = actions[0];
        float[] neurons = brain.getNeuronStates();
        float agg = neurons[0], fear = neurons[1], tac = neurons[2];

        // 感情レイヤーによる行動補正
        if (fear > 0.7f || (suppression > 0.8f && agg < 0.4f)) {
            moveAction = (Math.random() > 0.5) ? 3 : 4;
        }
        if (tac > 0.7f && !hasLos && lastKnownLocation != null) {
            if (agg > 0.6f && Math.random() > 0.6) {
                moveAction = 7; // アグレッシブなら Peek & Hide
            } else if (Math.random() > 0.8) {
                moveAction = 6; // 低確率で Jump Peek
            }
        }

        // --- 移動の実行 ---
        if (canSeeTarget) {
            handleCombatMovement(moveAction, target);
        } else if (lastKnownLocation != null) {
            if (moveAction == 7) {
                coverLocation = scav.getLocation().clone();
                Vector toTarget = lastKnownLocation.toVector().subtract(coverLocation.toVector()).normalize();
                Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
                if (Math.random() > 0.5) tangent.multiply(-1);
                peekLocation = coverLocation.clone().add(tangent.multiply(2.0));
                peekPhase = 1;
                peekTicks = 0;
                scav.getPathfinder().moveTo(peekLocation);
            } else {
                scav.getPathfinder().moveTo(lastKnownLocation);
                if (scav.getLocation().distance(lastKnownLocation) < 2) {
                    lastKnownLocation = null;
                }
            }
        }

        // --- Action 1: 射撃 ---
        if (actions[1] == 0) {
            if (canSeeTarget) {
                applyAimToEntity();
                double dynamicInaccuracy = 0.15 + (suppression * 0.25);
                if (scav.getVelocity().length() > 0.1) dynamicInaccuracy += 0.1;
                
                gunListener.executeMobShoot(scav, def.gunStats, 1, dynamicInaccuracy);
            } else if (target != null && scav.isOnGround() && jumpCooldown <= 0 && checkJumpShotVision(target)) {
                if (Math.random() < 0.15) { // ジャンプショット確率を大幅に低下
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.45, 0)));
                    jumpCooldown = 60; // 3秒クールダウン
                }
            }
        }
    }

    private void updateHumanAim(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Vector idealDir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
        
        if (currentAimVector == null) currentAimVector = idealDir.clone();

        // 1. 反応遅延とスムージング (Lerp)
        // 遠いほど、また制圧されているほどエイムが追いつかない
        double lerpFactor = 0.2 - (suppression * 0.1); 
        currentAimVector = currentAimVector.clone().add(idealDir.clone().subtract(currentAimVector).multiply(lerpFactor)).normalize();

        // 2. ランダムな揺らぎ (Sway)
        // 呼吸や緊張によるわずかな震えをPitch/Yawの誤差として蓄積
        aimErrorYaw += (Math.random() - 0.5) * 0.1;
        aimErrorPitch += (Math.random() - 0.5) * 0.1;
        
        // 制圧時は揺らぎが激しくなる
        if (suppression > 0.3) {
            aimErrorYaw += (Math.random() - 0.5) * suppression * 0.5;
            aimErrorPitch += (Math.random() - 0.5) * suppression * 0.5;
        }
        
        // 誤差の減衰 (常に中心に戻ろうとする力)
        aimErrorYaw *= 0.8;
        aimErrorPitch *= 0.8;
    }

    private void applyAimToEntity() {
        if (currentAimVector == null) return;
        
        Location loc = scav.getLocation();
        Vector finalDir = currentAimVector.clone();
        
        // 誤差を適用
        float yaw = loc.setDirection(finalDir).getYaw() + (float)aimErrorYaw;
        float pitch = loc.setDirection(finalDir).getPitch() + (float)aimErrorPitch;
        
        scav.setRotation(yaw, pitch);
    }

    private boolean checkJumpShotVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location jumpEye = eye.clone().add(0, 1.2, 0); // リアルなジャンプ高に合わせる
        Vector direction = target.getEyeLocation().toVector().subtract(jumpEye.toVector());
        var result = jumpEye.getWorld().rayTraceBlocks(jumpEye, direction.normalize(), direction.length(),
                org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null;
    }

    private boolean checkVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        double dist = eye.distance(targetLoc);
        if (dist > MAX_VISION_DISTANCE)
            return false;

        Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
        Vector direction = eye.getDirection();
        double angle = direction.angle(toTarget) * 180 / Math.PI;
        if (angle > FOV_ANGLE / 2.0)
            return false;

        return scav.hasLineOfSight(target);
    }

    private void handleCombatMovement(int action, LivingEntity target) {
        Location sLoc = scav.getLocation();
        Location tLoc = target.getLocation();
        Vector toTarget = tLoc.toVector().subtract(sLoc.toVector()).normalize();

        switch (action) {
            case 0:
            case 1:
                scav.getPathfinder().moveTo(target);
                break;
            case 2:
                scav.getPathfinder().moveTo(sLoc.add(toTarget.clone().multiply(-5)));
                break;
            case 3:
            case 4:
                double orbitDist = 15.0;
                double currentDist = sLoc.distance(tLoc);
                Vector tangent = new Vector(-toTarget.getZ(), 0, toTarget.getX());
                if (action == 4)
                    tangent.multiply(-1);
                Vector finalMove = tangent.multiply(4).add(toTarget.clone().multiply(currentDist - orbitDist));
                scav.getPathfinder().moveTo(sLoc.add(finalMove));
                break;
            case 5:
                if (scav.isOnGround() && jumpCooldown <= 0) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.42, 0)));
                    jumpCooldown = 100; // 移動ジャンプは5秒に1回
                }
                break;
            case 6:
                if (scav.isOnGround() && jumpCooldown <= 0) {
                    Vector tan = new Vector(-toTarget.getZ(), 0, toTarget.getX());
                    if (Math.random() > 0.5)
                        tan.multiply(-1);
                    scav.setVelocity(scav.getVelocity().add(tan.multiply(0.5)).add(new Vector(0, 0.42, 0)));
                    jumpCooldown = 80;
                }
                break;
        }
    }

    public void onDamage(Entity attacker) {
        suppression = Math.min(1.0f, suppression + 0.3f);
        if (attacker instanceof LivingEntity living) {
            Location loc = scav.getLocation();
            loc.setDirection(living.getLocation().toVector().subtract(loc.toVector()).normalize());
            scav.teleport(loc);
            if (scav.getTarget() == null) {
                scav.setTarget(living);
                lastKnownLocation = living.getLocation();
            }
        }
    }

    public void addSuppression(float amount) {
        this.suppression = Math.min(1.0f, this.suppression + amount);
    }

    public void onDeath() {
        brain.onDeath();
    }

    public ScavBrain getBrain() {
        return brain;
    }

    public Mob getScav() {
        return this.scav;
    }
}
