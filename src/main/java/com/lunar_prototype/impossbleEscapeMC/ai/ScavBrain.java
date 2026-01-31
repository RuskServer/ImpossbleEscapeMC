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

    // アクションのバリエーションを増やす (0-5)
    private static final int ACTION_SIZE = 6;
    private static final int STATE_SIZE = 512;

    public ScavBrain(Mob entity) {
        this.entity = entity;
        // v1.1.0のコンストラクタ：handle = create_singularity(state_size, action_size, category_count)
        this.singularity = new Singularity(STATE_SIZE, ACTION_SIZE, 2);
    }

    /**
     * 環境情報を収集して次の行動を決定する
     */
    public int[] decide(LivingEntity target, GunStats stats) {
        float[] inputs = new float[6];

        // 1. 距離の正規化 (0m-50m -> 0.0-1.0)
        double dist = target != null ? entity.getLocation().distance(target.getLocation()) : 50.0;
        inputs[0] = (float) Math.min(1.0, dist / 50.0);

        // 2. 体力割合 (0.0-1.0)
        double maxHp = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        inputs[1] = (float) (entity.getHealth() / maxHp);

        // 3. 残弾数割合 (GunListenerのPDCから取得)
        ItemStack gun = entity.getEquipment().getItemInMainHand();
        int currentAmmo = gun.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        inputs[2] = (float) currentAmmo / (stats != null ? stats.magSize : 30);

        // 4. 射線の有無 (1.0 or 0.0)
        inputs[3] = (target != null && entity.hasLineOfSight(target)) ? 1.0f : 0.0f;

        // 5. Singularity内部状態 (アドレナリンとフラストレーション)
        inputs[4] = singularity.getAdrenaline();
        inputs[5] = singularity.getFrustration();

        // アクション選択
        this.lastActions = singularity.selectActions(inputs);

        // フラストレーション（行き詰まり）に基づく探索
        float frustration = singularity.getFrustration();
        if (frustration > 0.65f && Math.random() < frustration * 0.4) {
            // フラストレーションが高い場合、確率で行動をランダム化（探索）
            for (int i = 0; i < lastActions.length; i++) {
                lastActions[i] = (int) (Math.random() * ACTION_SIZE);
            }
        }

        return lastActions;
    }

    public void reward(float amount) {
        if (lastActions != null) {
            singularity.learn(amount); // 直前のアクションに対して報酬を与える
        }
    }

    public void terminate() {
        singularity.close(); // JNIハンドルの解放
    }
}