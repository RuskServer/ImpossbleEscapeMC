package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 銃弾の部位判定と防具の貫通判定。プラグイン銃 ({@link BulletTask}) とデータパック銃 ({@link DatapackBulletHitBridge}) で共通。
 */
public final class BulletDamageModel {

    public static final String HEAD = "head";
    public static final String BODY = "body";
    public static final String ARMS = "arms";
    public static final String LEGS = "legs";

    /** 目の高さからこの範囲は頭 */
    private static final double HEAD_HEIGHT = 0.25;
    /** 足元から (目の高さまでの) この割合までは足 */
    private static final double LEGS_RATIO = 0.45;
    /** 胴の高さで、体の中心から左右にこの距離 (当たり判定の幅に対する割合) より外は腕 */
    private static final double ARMS_LATERAL_RATIO = 0.3;

    private BulletDamageModel() {
    }

    /** 着弾点から部位 (head / body / arms / legs) を決める */
    public static String hitLocation(LivingEntity victim, Vector hit) {
        double footY = victim.getLocation().getY();
        double headY = victim.getEyeLocation().getY();
        double height = headY - footY;
        if (hit.getY() >= headY - HEAD_HEIGHT) return HEAD;
        if (hit.getY() <= footY + height * LEGS_RATIO) return LEGS;
        return isArm(victim, hit) ? ARMS : BODY;
    }

    /** 胴の高さの着弾点が、体の向きに対して中心から左右に離れていれば腕 */
    public static boolean isArm(LivingEntity victim, Vector hit) {
        double yaw = Math.toRadians(victim.getBodyYaw());
        // 体の左右方向 (正面 (-sin, 0, cos) に直交する水平方向)
        double lateral = (hit.getX() - victim.getLocation().getX()) * Math.cos(yaw)
                + (hit.getZ() - victim.getLocation().getZ()) * Math.sin(yaw);
        return Math.abs(lateral) > victim.getWidth() * ARMS_LATERAL_RATIO;
    }

    /** 部位を守る防具の防具クラス。頭はヘルメット、胴はボディアーマー、腕・足は無し */
    public static int armorClass(LivingEntity victim, String hitLocation) {
        return switch (hitLocation) {
            case HEAD -> armorClassFromSlot(victim, EquipmentSlot.HEAD);
            case BODY -> armorClassFromSlot(victim, EquipmentSlot.CHEST);
            default -> 0;
        };
    }

    public static int armorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        if (entity.getEquipment() == null) return 0;
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    /** 弾薬クラスと防具クラスから、防具を貫通したかを抽選する */
    public static boolean penetrates(int ammoClass, int armorClass) {
        if (armorClass <= 0) return true;
        double chance = (ammoClass > armorClass) ? 0.95 : (ammoClass == armorClass ? 0.70 : 0.15);
        return Math.random() < chance;
    }
}
