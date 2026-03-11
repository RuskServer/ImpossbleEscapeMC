package com.lunar_prototype.impossbleEscapeMC.ai;

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

    // --- Decision Persistence ---
    private int[] currentActions; // [Movement, Shooting]
    private int decisionTimer = 0;
    
    // --- State Tracking for Interrupts ---
    private float lastSuppression = 0.0f;
    private double lastHealthPercent = 1.0;
    private boolean lastCanSee = false;

    public ScavBrain(Mob entity) {
        this.entity = entity;
        this.random = new Random();
        this.currentActions = new int[] { 0, 1 }; // Default: Push, Don't Shoot

        // Initial state
        this.aggression = 0.2f + random.nextFloat() * 0.3f;
        this.fear = 0.1f;
        this.tactical = 0.3f + random.nextFloat() * 0.2f;
    }

    public int[] decide(LivingEntity target, GunStats stats, float suppression, float tacticalAdvice) {
        boolean canSee = target != null;
        double healthPercent = entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        
        // --- 1. Update Internal States ---
        updateInternalStates(target, stats, suppression, tacticalAdvice, canSee, healthPercent);

        // --- 2. Check for Interrupts ---
        boolean interrupted = shouldInterrupt(canSee, suppression, healthPercent, stats);

        // --- 3. Decision Logic ---
        if (decisionTimer <= 0 || interrupted) {
            // Determine new actions
            int moveAction = determineMoveAction(suppression, canSee, tacticalAdvice);
            int shootAction = determineShootAction(canSee, suppression);
            
            this.currentActions = new int[] { moveAction, shootAction };
            
            // Set duration based on action type (Current AI tick is 5 ticks = 0.25s)
            // 2 to 6 ticks = 0.5s to 1.5s
            this.decisionTimer = 2 + random.nextInt(4);
            
            // Critical actions (Retreat/Evasion) should have shorter reconsideration times
            if (moveAction == 2 || moveAction == 3 || moveAction == 4) {
                this.decisionTimer = 2 + random.nextInt(2);
            }
        } else {
            decisionTimer--;
        }

        // Update tracking states for next tick
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

    private void updateInternalStates(LivingEntity target, GunStats stats, float suppression, float tacticalAdvice,
            boolean canSee, double healthPercent) {
        
        ItemStack gun = entity.getEquipment().getItemInMainHand();
        int currentAmmo = 0;
        int magSize = 30;
        if (gun != null && gun.hasItemMeta()
                && gun.getItemMeta().getPersistentDataContainer().getKeys().contains(PDCKeys.AMMO)) {
            currentAmmo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
            magSize = stats != null ? stats.magSize : 30;
        }
        double ammoPercent = (double) currentAmmo / magSize;

        // Update Aggression
        if (canSee) {
            double dist = entity.getLocation().distance(target.getLocation());
            if (dist < 15.0) {
                aggression += 0.05f;
            }
        }
        if (healthPercent > 0.6 && ammoPercent > 0.5) {
            aggression += 0.02f;
        }

        // Update Fear
        if (healthPercent < 0.3) {
            fear += 0.1f;
        }
        if (ammoPercent < 0.2) {
            fear += 0.05f;
        }
        if (suppression > 0.4f) {
            fear += suppression * 0.1f;
        }

        // Update Tactical
        if (tacticalAdvice > 0.5f) {
            tactical += 0.1f; // High recommendation for peeking
        }
        if (!canSee) {
            tactical += 0.02f; // Get more tactical when lost sight
        }

        // Decay
        aggression = clamp(aggression * 0.95f - 0.01f);
        fear = clamp(fear * 0.95f - 0.01f);
        tactical = clamp(tactical * 0.98f);
    }

    private int determineMoveAction(float suppression, boolean canSee, float tacticalAdvice) {
        // Retreat has highest priority if fear is overwhelming
        if (fear > 0.7f && random.nextFloat() < fear) {
            return 2; // Retreat
        }

        // Evasion (Strafe) under heavy fire
        if (suppression > 0.6f) {
            return random.nextBoolean() ? 3 : 4; // Strafe Left / Right
        }

        // Jump Peek or Peek & Hide recommended by tactical advice
        if (tacticalAdvice > 0.5f && tactical > 0.5f && !canSee) {
            if (random.nextFloat() < tactical) {
                return (aggression > 0.4f && random.nextBoolean()) ? 7 : 6; // Peek & Hide or Jump Peek
            }
        }

        if (canSee) {
            // Aggressive push
            if (aggression > 0.6f && random.nextFloat() < aggression) {
                return random.nextBoolean() ? 0 : 1; // Move towards target
            }

            // Default active combat movement (strafe or maintain distance)
            float r = random.nextFloat();
            if (r < 0.4)
                return 3; // Strafe L
            if (r < 0.8)
                return 4; // Strafe R
            return 5; // Jump occasionally
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
