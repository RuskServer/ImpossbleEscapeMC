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
    
    private double lastDist = -1;
    private double lastHealth = -1;
    private boolean lastWasLos = false;

    private static final double MAX_VISION_DISTANCE = 96.0; // 6 Chunks
    private static final double FOV_ANGLE = 120.0;

    public ScavController(Mob scav, GunListener listener) {
        this.scav = scav;
        this.brain = new ScavBrain(scav);
        this.gunListener = listener;
    }

    public void onTick() {
        LivingEntity target = scav.getTarget();
        boolean canSeeTarget = false;

        // 制圧レベルの自然減衰
        if (suppression > 0) suppression = Math.max(0, suppression - 0.05f);

        if (target != null) {
            canSeeTarget = checkVision(target);
            if (canSeeTarget) {
                lastKnownLocation = target.getLocation();
                searchTicks = 0;
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
        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(itemId);

        if (def == null || def.gunStats == null) {
            return;
        }

        // --- タクティカル・アドバイスの計算 (教示学習) ---
        float tacticalAdvice = 0.0f;
        boolean hasLos = target != null && scav.hasLineOfSight(target);
        
        if (!hasLos && lastKnownLocation != null) {
            tacticalAdvice = 1.0f; // ジャンプピーク推奨
        } else if (suppression > 0.5f) {
            tacticalAdvice = 0.5f; // 回避推奨
        }

        // --- 状況の注入 (Condition Injection) ---
        // 1. Low Health Check (< 30%)
        double maxHealth = scav.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        boolean isLowHealth = scav.getHealth() < (maxHealth * 0.3);

        // 2. Low Ammo Check (< 20%)
        int currentAmmo = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int magSize = def.gunStats != null ? def.gunStats.magSize : 30;
        boolean isLowAmmo = currentAmmo < (magSize * 0.2);

        // 3. Update Brain Conditions (Hamiltonian Fields)
        brain.updateConditions(isLowHealth, isLowAmmo, suppression > 0.5f, tacticalAdvice > 0.5f);

        // AIに思考させる
        int[] actions = brain.decide(canSeeTarget ? target : null, def.gunStats, suppression, tacticalAdvice);

        if (actions.length < 2) return;

        // --- 擬似AIによる強制介入 (感情レイヤーの重ね合わせ) ---
        int moveAction = actions[0];
        float[] neurons = brain.getNeuronStates(); // [0:Aggression, 1:Fear, 2:Tactical, 3:Reflex]
        
        float agg = 0, fear = 0, tac = 0;
        if (neurons != null && neurons.length >= 3) {
            agg = neurons[0];
            fear = neurons[1];
            tac = neurons[2];

            if (fear > 0.7f || (suppression > 0.6f && agg < 0.5f)) {
                moveAction = (Math.random() > 0.5) ? 3 : 4; 
            }
            if (tac > 0.6f && !hasLos && lastKnownLocation != null) {
                moveAction = 6;
            }
            if (agg > 0.9f) {
                moveAction = 1; 
            }
        }

        // --- 詳細デバッグログ ---
        if (Math.random() < 0.1) { // ログ過多防止のため10%の確率で出力
            Bukkit.getLogger().info(String.format(
                "[SCAV-DEBUG] %s | LOS:%b Sup:%.2f Adv:%.1f | Neuron[Agg:%.2f Fear:%.2f Tac:%.2f] | Action[Raw:%d -> Final:%d]",
                scav.getName(), hasLos, suppression, tacticalAdvice, agg, fear, tac, actions[0], moveAction
            ));
        }

        // --- 移動の実行 ---
        if (canSeeTarget) {
            handleCombatMovement(moveAction, target);
        } else if (lastKnownLocation != null) {
            scav.getPathfinder().moveTo(lastKnownLocation);
            if (scav.getLocation().distance(lastKnownLocation) < 2) {
                lastKnownLocation = null;
            }
        }

        // --- Action 1: 射撃 (ジャンプショット含む) ---
        if (actions[1] == 0) {
            if (canSeeTarget) {
                gunListener.executeMobShoot(scav, def.gunStats, 1, 0.2);
            } else if (target != null && scav.isOnGround() && checkJumpShotVision(target)) {
                // 常にジャンプすると読まれるため、40%の確率で実行
                if (Math.random() < 0.4) {
                    scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.5, 0)));
                    Bukkit.getLogger().info("[SCAV] " + scav.getName() + " attempts a JUMP SHOT!");
                    brain.reward(0.1f);
                }
            }
        }

        // --- 戦術的評価（報酬系） ---
        if (target != null) {
            float positioningReward = 0.0f;
            double currentDist = scav.getLocation().distance(target.getLocation());
            double currentHealth = scav.getHealth();

            if (hasLos) {
                // 1. ピーキング評価
                if (!lastWasLos && moveAction == 6) {
                    brain.reward(0.8f);
                    Bukkit.getLogger().info("[SCAV-AI] " + scav.getName() + " PERFECT JUMP PEEK!");
                }
                // 2. 射線維持報酬
                positioningReward += 0.005f;
                // 3. 適正距離報酬
                if (currentDist >= 10 && currentDist <= 25) {
                    positioningReward += 0.01f;
                }
                // 4. 回避移動報酬
                if (moveAction >= 3) {
                    positioningReward += 0.02f;
                }
            } else {
                if (currentDist < 5) positioningReward -= 0.01f;
            }

            // 無謀な突撃ペナルティ
            if (lastDist != -1 && lastHealth != -1) {
                if (currentHealth < lastHealth && currentDist < lastDist) {
                    brain.reward(-0.4f);
                }
            }

            if (positioningReward != 0) brain.reward(positioningReward);

            // 状態更新
            lastDist = currentDist;
            lastHealth = currentHealth;
            lastWasLos = hasLos;
        }
    }

    private boolean checkJumpShotVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location jumpEye = eye.clone().add(0, 1.5, 0);
        Vector direction = target.getEyeLocation().toVector().subtract(jumpEye.toVector());
        var result = jumpEye.getWorld().rayTraceBlocks(jumpEye, direction.normalize(), direction.length(), org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null || result.getHitBlock() == null;
    }

    private boolean checkVision(LivingEntity target) {
        Location eye = scav.getEyeLocation();
        Location targetLoc = target.getEyeLocation();
        double dist = eye.distance(targetLoc);
        if (dist > MAX_VISION_DISTANCE) return false;

        Vector toTarget = targetLoc.toVector().subtract(eye.toVector()).normalize();
        Vector direction = eye.getDirection();
        double angle = direction.angle(toTarget) * 180 / Math.PI;
        if (angle > FOV_ANGLE / 2.0) return false;

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
                if (action == 4) tangent.multiply(-1);
                Vector finalMove = tangent.multiply(4).add(toTarget.clone().multiply(currentDist - orbitDist));
                scav.getPathfinder().moveTo(sLoc.add(finalMove));
                break;
            case 5:
                if (scav.isOnGround()) scav.setVelocity(scav.getVelocity().add(new Vector(0, 0.42, 0)));
                break;
            case 6:
                if (scav.isOnGround()) {
                    Vector tan = new Vector(-toTarget.getZ(), 0, toTarget.getX());
                    if (Math.random() > 0.5) tan.multiply(-1);
                    scav.setVelocity(scav.getVelocity().add(tan.multiply(0.5)).add(new Vector(0, 0.42, 0)));
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

    public void addSuppression(float amount) { this.suppression = Math.min(1.0f, this.suppression + amount); }
    public void onDeath() { brain.onDeath(); }
    public ScavBrain getBrain() { return brain; }
    public Mob getScav() { return this.scav; }
}
