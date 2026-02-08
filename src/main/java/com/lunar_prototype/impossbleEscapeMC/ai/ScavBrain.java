package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

public class ScavBrain {
    private final Singularity singularity;
    private final Mob entity;
    private int[] lastActions;

    private static final int STATE_SIZE = 512;

    public ScavBrain(Mob entity) {
        this.entity = entity;
        // Category 0: Movement (7 actions), Category 1: Shooting (2 actions)
        this.singularity = new Singularity(STATE_SIZE, new int[]{7, 2});
        
        // BrainManager.loadMasterModel(this.singularity); // Removed generation stacking
        bootstrapKnowledge(); // Inject domain knowledge via Hamiltonian rules
    }

    public int[] decide(LivingEntity target, GunStats stats, float suppression, float tacticalAdvice) {
        float[] inputs = new float[8];
        boolean canSee = target != null;
        
        // this.fitness += 0.001f; // Removed fitness tracking

        double dist = canSee ? entity.getLocation().distance(target.getLocation()) : 50.0;
        inputs[0] = (float) Math.min(1.0, dist / 50.0);
        inputs[1] = (float) (entity.getHealth() / entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        
        ItemStack gun = entity.getEquipment().getItemInMainHand();
        int currentAmmo = gun.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        inputs[2] = (float) currentAmmo / (stats != null ? stats.magSize : 30);
        
        inputs[3] = canSee ? 1.0f : 0.0f;
        inputs[4] = singularity.getAdrenaline();
        inputs[5] = singularity.getFrustration();
        inputs[6] = suppression;
        inputs[7] = tacticalAdvice;

        // Dynamic exploration beta based on frustration
        float frustration = singularity.getFrustration();
        float beta = 0.1f + (frustration * 0.4f); 
        singularity.setExplorationBeta(beta);

        this.lastActions = singularity.selectActions(inputs);
        return lastActions;
    }

    /**
     * v2.0.0: Optimized Initial Knowledge Injection using Hamiltonian Rules
     */
    public void bootstrapKnowledge() {
        // Condition IDs mapping
        // 0: Low Health (< 30%)
        // 1: Low Ammo (< 20%)
        // 6: Suppression (High)
        // 7: Tactical Advice (Jump Peek)

        // Rule 1: Tactical Advice (Input 7) -> Jump Peek (Action 6)
        // Rule 2: Suppression (Input 6) -> Evade Left/Right (Actions 3, 4)
        // Rule 3: Suppression (Input 6) -> Jump (Action 5)
        // Rule 4: Low Health (Input 0) -> Retreat (Action 2)
        // Rule 5: Low Ammo (Input 1) -> Retreat (Action 2)

        int[] conditionIds = {
            7,       // Tactical -> Jump Peek
            6, 6, 6, // Suppression -> Strafe/Jump
            0,       // Low Health -> Retreat
            1        // Low Ammo -> Retreat
        };
        
        int[] actionIndices = {
            6,       // Jump Peek
            3, 4, 5, // Strafe L, Strafe R, Jump
            2,       // Retreat
            2        // Retreat
        };
        
        float[] resonanceStrengths = {
            3.5f,          // Tactical: Strong bias
            1.5f, 1.5f, 1.0f, // Suppression: Moderate bias
            4.0f,          // Low Health: Very Strong bias (Survival instinct)
            2.5f           // Low Ammo: Strong bias
        };

        singularity.registerHamiltonianRules(conditionIds, actionIndices, resonanceStrengths);
        // Bukkit.getLogger().info("[SCAV-AI] Hamiltonian Rules Registered.");
    }

    public void updateConditions(boolean lowHealth, boolean lowAmmo, boolean suppressed, boolean tactical) {
        // Collect active condition IDs
        int count = 0;
        if (lowHealth) count++;
        if (lowAmmo) count++;
        if (suppressed) count++;
        if (tactical) count++;

        if (count == 0) {
            singularity.setActiveConditions();
            return;
        }

        int[] active = new int[count];
        int idx = 0;
        if (lowHealth) active[idx++] = 0;
        if (lowAmmo) active[idx++] = 1;
        if (suppressed) active[idx++] = 6;
        if (tactical) active[idx++] = 7;

        singularity.setActiveConditions(active);
    }

    public float[] getNeuronStates() { return singularity.getNeuronStates(); }
    public float getAdrenaline() { return singularity.getAdrenaline(); }
    public float getFrustration() { return singularity.getFrustration(); }
    public int[] getLastActions() { return lastActions; }

    public void reward(float amount) {
        if (lastActions != null) {
            singularity.learn(amount);
            // this.fitness += amount; // Removed fitness tracking
        }
    }

    public void onDeath() {
        // BrainManager.reportResult(this.singularity, this.fitness); // Removed generation stacking
    }

    public void terminate() {
        singularity.close();
    }
}
