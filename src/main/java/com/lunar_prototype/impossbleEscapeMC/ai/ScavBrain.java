package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.util.CombatEvaluator;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import java.util.Random;

public class ScavBrain {
    private final Mob entity;
    private final Random random;

    // Internal States for AI simulation (0.0 to 1.0)
    private float aggression;
    private float fear;
    private float tactical;

    // --- Action Types ---
    // 0: Approach, 1: Maintain, 2: Retreat, 3: Strafe L, 4: Strafe R, 5: Jump, 6: Peek, 7: Jump Peek, 8: HOLD
    private int[] currentActions; // [Movement, Shooting]
    private int decisionTimer = 0;
    
    // --- State Tracking for Interrupts ---
    private float lastSuppression = 0.0f;
    private double lastHealthPercent = 1.0;
    private boolean lastCanSee = false;

    public ScavBrain(Mob entity) {
        this.entity = entity;
        this.random = new Random();
        this.currentActions = new int[] { 8, 1 }; // Default: HOLD, Don't Shoot

        // Initial state
        this.aggression = 0.2f + random.nextFloat() * 0.3f;
        this.fear = 0.1f;
        this.tactical = 0.3f + random.nextFloat() * 0.2f;
    }

    public int[] decide(LivingEntity target, GunStats stats, float suppression, float tacticalAdvice) {
        boolean canSee = target != null;
        double healthPercent = entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        double dist = target != null ? entity.getLocation().distance(target.getLocation()) : 100.0;
        
        // --- 1. Update Internal States ---
        updateInternalStates(dist, stats, suppression, tacticalAdvice, canSee, healthPercent);

        // --- 2. Check for Interrupts ---
        boolean interrupted = shouldInterrupt(canSee, suppression, healthPercent, stats);

        // --- 3. Decision Logic ---
        if (decisionTimer <= 0 || interrupted) {
            // Determine new actions
            int moveAction = determineMoveAction(suppression, canSee, tacticalAdvice);
            int shootAction = determineShootAction(canSee, suppression);
            
            this.currentActions = new int[] { moveAction, shootAction };
            
            // Set duration based on action type
            this.decisionTimer = 4 + random.nextInt(6); // デフォルト 1.0s - 2.5s
            
            // 特別なアクションのタイマー調整
            if (moveAction == 8) { // HOLD (視界喪失時の待機)
                this.decisionTimer = 12 + random.nextInt(8); // 3s - 5s 待機
            } else if (moveAction == 2 || moveAction == 3 || moveAction == 4) {
                this.decisionTimer = 4 + random.nextInt(4); // 回避行動は短めに再検討
            }
        } else {
            decisionTimer--;
        }

        this.lastCanSee = canSee;
        this.lastSuppression = suppression;
        this.lastHealthPercent = healthPercent;

        return currentActions;
    }

    private boolean shouldInterrupt(boolean canSee, float suppression, double healthPercent, GunStats stats) {
        // A. Sudden sight change (Found target or lost target)
        if (canSee != lastCanSee) return true;

        // B. Heavy damage or sudden suppression spike
        if (healthPercent < lastHealthPercent - 0.1) return true; // Took significant damage
        if (suppression > lastSuppression + 0.3f) return true;    // Sudden heavy fire

        // C. Panic state (Fear spikes)
        if (fear > 0.8f && lastSuppression < 0.5f) return true;

        // D. Out of ammo while trying to shoot
        if (currentActions[1] == 0) { // If currently wanting to shoot
            ItemStack gun = entity.getEquipment().getItemInMainHand();
            if (gun != null && gun.hasItemMeta()) {
                int ammo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
                if (ammo <= 0) return true; // Need to change action to cover/reload
            }
        }

        return false;
    }

    private void updateInternalStates(double distance, GunStats stats, float suppression, float tacticalAdvice,
            boolean canSee, double healthPercent) {
        
        ItemStack gun = entity.getEquipment().getItemInMainHand();
        double ammoPercent = 1.0;
        if (gun != null && gun.hasItemMeta() && stats != null) {
            int currentAmmo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            ammoPercent = (double) currentAmmo / stats.magSize;
        }

        // Update Aggression/Fear using Evaluator
        aggression += CombatEvaluator.calculateAggressionSpike(distance, suppression, healthPercent, canSee);
        fear += CombatEvaluator.calculateFearSpike(suppression, healthPercent, ammoPercent);
        
        if (tacticalAdvice > 0.5f) {
            tactical += 0.1f;
        }
        if (!canSee) {
            tactical += 0.02f;
        }

        // Decay
        aggression = clamp(aggression * 0.96f - 0.005f);
        fear = clamp(fear * 0.94f - 0.01f);
        tactical = clamp(tactical * 0.98f);
    }

    private int determineMoveAction(float suppression, boolean canSee, float tacticalAdvice) {
        // --- 視認中の行動 ---
        if (canSee) {
            if (fear > 0.7f && random.nextFloat() < fear) {
                return 2; // Retreat
            }
            if (suppression > 0.6f) {
                return random.nextBoolean() ? 3 : 4; // Strafe L / R
            }
            if (aggression > 0.6f && random.nextFloat() < aggression) {
                return random.nextBoolean() ? 0 : 1; // Aggressive Push / Approach
            }
            // 戦闘中のデフォルト移動
            float r = random.nextFloat();
            if (r < 0.4) return 3;
            if (r < 0.8) return 4;
            return 5; // Jump
        }

        // --- 視界喪失後の行動 ---
        if (lastCanSee) {
            // 見失った直後は 100% HOLD (角を置く)
            return 8; 
        }

        // 待機中
        if (currentActions[0] == 8) {
            // 攻撃性が高まれば最後に見失った場所へ向かう
            if (aggression > 0.5f && random.nextFloat() < aggression) {
                return 0; // Search Approach
            }
            return 8; // Still Holding
        }

        if (tacticalAdvice > 0.5f && tactical > 0.5f) {
            return (aggression > 0.4f && random.nextBoolean()) ? 7 : 6; // Peek / Jump Peek
        }

        return 0; // Default approach
    }

    private int determineShootAction(boolean canSee, float suppression) {
        if (canSee) {
            // Try to shoot if seeing target
            if (fear > 0.8f && suppression > 0.8f) {
                // Too suppressed/scared to shoot back efficiently, might panic fire
                return random.nextFloat() < 0.3f ? 0 : 1;
            }
            return 0; // Standard fire
        }
        // Jump shot logic is handled partially in controller. Brain decides 'yes'
        // occasionally if tactical is high
        if (tactical > 0.6f && random.nextFloat() < 0.2f) {
            return 0; // Attempt predictive/jump fire
        }

        return 1; // Don't shoot
    }

    private float clamp(float val) {
        return Math.max(0.0f, Math.min(1.0f, val));
    }

    // Compatibility methods so Controller doesn't break entirely yet, though some
    // need adjustment
    public void updateConditions(boolean lowHealth, boolean lowAmmo, boolean suppressed, boolean tacticalFlag) {
        // Conditions are already handled in updateInternalStates based on parameters,
        // but can add immediate spikes here if needed.
        if (lowHealth)
            fear = clamp(fear + 0.2f);
        if (lowAmmo)
            fear = clamp(fear + 0.1f);
        if (suppressed)
            fear = clamp(fear + 0.1f);
        if (tacticalFlag)
            tactical = clamp(tactical + 0.2f);
    }

    public float[] getNeuronStates() {
        return new float[] { aggression, fear, tactical, 0.0f };
    }

    public float getAdrenaline() {
        return aggression;
    }

    public float getFrustration() {
        return fear;
    }

    public int[] getLastActions() {
        return currentActions != null ? currentActions : new int[] { 0, 1 };
    }

    // Removed ML feedback loops
    public void reward(float amount) {
    }

    public void onDeath() {
    }

    public void terminate() {
    }
}
