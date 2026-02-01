package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.dark_singularity_api.Singularity;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ScavBrain {
    private final Singularity singularity;
    private final Mob entity;
    private int[] lastActions;
    private float fitness = 0.0f; 

    private static final int ACTION_SIZE = 7;
    private static final int STATE_SIZE = 512;

    public ScavBrain(Mob entity) {
        this.entity = entity;
        this.singularity = new Singularity(STATE_SIZE, ACTION_SIZE, 2);
        
        BrainManager.loadMasterModel(this.singularity);
        bootstrapKnowledge(); // ドメイン知識の注入
    }

    public int[] decide(LivingEntity target, GunStats stats, float suppression, float tacticalAdvice) {
        float[] inputs = new float[8];
        boolean canSee = target != null;
        
        this.fitness += 0.001f;

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

        float frustration = singularity.getFrustration();
        float beta = 0.1f + (frustration * 0.4f); 
        singularity.setExplorationBeta(beta);

        this.lastActions = singularity.selectActions(inputs);
        return lastActions;
    }

    /**
     * v1.2.0: ドメイン知識（バイアス）の注入
     */
    public void bootstrapKnowledge() {
        List<Integer> stateIndices = new ArrayList<>();
        List<Integer> actionIndices = new ArrayList<>();
        List<Float> biases = new ArrayList<>();

        for (int i = 0; i < STATE_SIZE; i++) {
            // Bit 7: Tactical Advice (1.0 = Jump Peek推奨)
            if ((i & 128) != 0) {
                stateIndices.add(i);
                actionIndices.add(6);
                biases.add(3.5f); 
            }

            // Bit 6: Suppression (制圧されている時)
            if ((i & 64) != 0) {
                // 回避移動 (3, 4)
                stateIndices.add(i); actionIndices.add(3); biases.add(1.5f);
                stateIndices.add(i); actionIndices.add(4); biases.add(1.5f);
                // 伏せたりジャンプしたり (5)
                stateIndices.add(i); actionIndices.add(5); biases.add(1.0f);
            }

            // Bit 3: Sight (視界がある時)
            if ((i & 8) != 0) {
                // 攻撃性(Action 0 of Category 1)はここでは一括インデックスとして扱う
                // カテゴリを跨ぐ場合はAPI仕様に注意
            }
        }

        int[] sArr = stateIndices.stream().mapToInt(Integer::intValue).toArray();
        int[] aArr = actionIndices.stream().mapToInt(Integer::intValue).toArray();
        float[] bArr = new float[biases.size()];
        for (int k = 0; k < biases.size(); k++) bArr[k] = biases.get(k);

        if (sArr.length > 0) {
            singularity.bootstrap(sArr, aArr, bArr);
            // Bukkit.getLogger().info("[SCAV-AI] Knowledge Bootstrapped.");
        }
    }

    public float[] getNeuronStates() { return singularity.getNeuronStates(); }
    public float getAdrenaline() { return singularity.getAdrenaline(); }
    public float getFrustration() { return singularity.getFrustration(); }
    public int[] getLastActions() { return lastActions; }

    public void reward(float amount) {
        if (lastActions != null) {
            singularity.learn(amount);
            this.fitness += amount;
        }
    }

    public void onDeath() {
        BrainManager.reportResult(this.singularity, this.fitness);
    }

    public void terminate() {
        singularity.close();
    }
}
