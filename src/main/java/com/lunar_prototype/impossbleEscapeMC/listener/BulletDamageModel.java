package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.item.ArmorDurability;
import com.lunar_prototype.impossbleEscapeMC.item.factory.HelmetVisors;
import com.lunar_prototype.impossbleEscapeMC.item.definition.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.stats.ArmorStats;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * 銃弾の部位判定と防具の貫通判定。データパック銃 ({@link DatapackBulletHitBridge}) のダメージ計算で使う。
 */
public final class BulletDamageModel {

    public static final String HEAD = "head";
    public static final String FACE = "face";
    public static final String BODY = "body";
    public static final String ARMS = "arms";
    public static final String LEGS = "legs";

    /** 目の高さからこの範囲は頭 */
    private static final double HEAD_HEIGHT = 0.25;
    /** 防具を抜かれた時に、防具クラス 1 つにつき減らす割合 */
    private static final double PENETRATED_REDUCTION_PER_CLASS = 0.10;
    private static final int MAX_ARMOR_CLASS = 6;
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
        if (hit.getY() >= headY - HEAD_HEIGHT) return isFaceHit(victim, hit, headY) ? FACE : HEAD;
        if (hit.getY() <= footY + height * LEGS_RATIO) return LEGS;
        return isArm(victim, hit) ? ARMS : BODY;
    }

    /** 頭部の前面に入った弾を顔面として分ける。頭頂・側面・後頭部は頭部扱い。 */
    private static boolean isFaceHit(LivingEntity victim, Vector hit, double eyeY) {
        if (hit.getY() > eyeY + 0.08) return false;
        Vector forward = victim.getEyeLocation().getDirection().setY(0);
        if (forward.lengthSquared() < 1e-6) return false;
        forward.normalize();
        Vector fromEye = hit.clone().subtract(victim.getEyeLocation().toVector()).setY(0);
        return fromEye.dot(forward) > victim.getWidth() * 0.15;
    }

    /** 胴の高さの着弾点が、体の向きに対して中心から左右に離れていれば腕 */
    public static boolean isArm(LivingEntity victim, Vector hit) {
        double yaw = Math.toRadians(victim.getBodyYaw());
        // 体の左右方向 (正面 (-sin, 0, cos) に直交する水平方向)
        double lateral = (hit.getX() - victim.getLocation().getX()) * Math.cos(yaw)
                + (hit.getZ() - victim.getLocation().getZ()) * Math.sin(yaw);
        return Math.abs(lateral) > victim.getWidth() * ARMS_LATERAL_RATIO;
    }

    /** 着弾部位を防護している防具のうち、有効クラスが一番高い物 */
    public record ArmorPiece(EquipmentSlot slot, ItemStack item, int armorClass) {
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** 着弾部位を防護する防具の有効クラス。重なった防具は高い方を使う。耐久が減った防具はクラスが下がる。 */
    public static int armorClass(LivingEntity victim, String hitLocation) {
        ArmorPiece piece = armorPiece(victim, hitLocation);
        return piece != null ? piece.armorClass() : 0;
    }

    /** 着弾部位を防護する防具 (装着スロットと本体)。無ければ null。 */
    public static ArmorPiece armorPiece(LivingEntity victim, String hitLocation) {
        ArmorStats.ProtectedZone zone = ArmorStats.ProtectedZone.fromHitLocation(hitLocation);
        if (zone == null || victim.getEquipment() == null) return null;
        ArmorPiece best = null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ArmorPiece piece = armorPieceFromSlot(victim, slot, zone);
            if (piece != null && (best == null || piece.armorClass() > best.armorClass())) best = piece;
        }
        return best;
    }

    private static ArmorPiece armorPieceFromSlot(LivingEntity entity, EquipmentSlot slot, ArmorStats.ProtectedZone zone) {
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        int armorClass = pdc.getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
        String id = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(id);
        if (armorClass <= 0 || def == null || def.armorStats == null
                || def.armorStats.coverage == null || !def.armorStats.coverage.contains(zone)) return null;
        int effective = effectiveArmorClass(armorClass, ArmorDurability.current(item, def), def.maxDurability);
        return effective > 0 ? new ArmorPiece(slot, item, effective) : null;
    }

    /**
     * 耐久値から有効な防具クラスを決める。満タンなら本来のクラス、減るほど下がり、0 なら防護なし。
     * 半分で本来の 3/4 (クラス4なら3)、SCAV の防具 (30〜50%) は 1 クラスほど下がる。
     */
    static int effectiveArmorClass(int armorClass, int durability, int maxDurability) {
        if (armorClass <= 0) return 0;
        if (maxDurability <= 0) return armorClass;
        if (durability <= 0) return 0;
        double ratio = Math.max(0.0, Math.min(1.0, (double) durability / maxDurability));
        return (int) Math.round(armorClass * (0.5 + 0.5 * ratio));
    }

    public static int armorClassFromSlot(LivingEntity entity, EquipmentSlot slot) {
        if (entity.getEquipment() == null) return 0;
        ItemStack item = entity.getEquipment().getItem(slot);
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    private static int armorClassFromArmoredRig(LivingEntity entity) {
        if (entity.getEquipment() == null) return 0;
        ItemStack leggings = entity.getEquipment().getLeggings();
        if (leggings == null || leggings.getType().isAir() || !leggings.hasItemMeta()) return 0;
        String id = leggings.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = ItemRegistry.get(id);
        if (def == null || def.rigStats == null || def.armorStats == null) return 0;
        return armorClassFromSlot(entity, EquipmentSlot.LEGS);
    }

    /** 全銃共通の1発 (散弾は1粒) の威力。弾が不明な旧装備は固定5ダメージ。 */
    public static double baseDamage(AmmoDefinition ammo) {
        return ammo != null ? ammo.damage : 5.0;
    }

    /**
     * 銃・データパックの威力補正は使わず、部位と防具だけで補正する。
     * 防具に止められた時はダメージなし (打撲なし)。抜かれた時も防具が勢いを殺すので、有効な防具クラス 1 つにつき
     * {@link #PENETRATED_REDUCTION_PER_CLASS} 減らす (クラス 3 なら 0.7 倍、最大のクラス 6 で 0.4 倍)
     */
    public static double damageForHit(double baseDamage, String hitLocation, boolean penetrated, int armorClass) {
        double damage = baseDamage;
        if (HEAD.equals(hitLocation) || FACE.equals(hitLocation)) damage *= 1.2;
        else if (LEGS.equals(hitLocation)) damage *= 0.6;
        if (!penetrated) return 0.0;
        int clamped = Math.max(0, Math.min(MAX_ARMOR_CLASS, armorClass));
        return damage * (1.0 - PENETRATED_REDUCTION_PER_CLASS * clamped);
    }

    /**
     * バイザーの正面防護。付けたバイザー (FAST-MT のバイザーなど) があればその跳弾率、
     * 無ければ元からバイザーと一体のヘルメット (Altyn など) の跳弾率で判定する。入射方向が不明なら判定しない。
     */
    public static boolean visorRicochets(LivingEntity victim, String hitLocation, Vector incomingDirection) {
        if ((!HEAD.equals(hitLocation) && !FACE.equals(hitLocation)) || incomingDirection == null || victim.getEquipment() == null) return false;
        double chance = faceShieldRicochetChance(victim.getEquipment().getHelmet());
        return rollVisorRicochet(hitLocation, chance, victim.getEyeLocation().getDirection(), incomingDirection, Math.random());
    }

    /** かぶっているヘルメットの、正面頭部の跳弾率 (付けたバイザー → ヘルメット自体の順)。無ければ0 */
    public static double faceShieldRicochetChance(ItemStack helmet) {
        if (helmet == null || helmet.getType().isAir() || !helmet.hasItemMeta()) return 0;
        String visorId = HelmetVisors.visorOf(helmet);
        if (visorId != null && HelmetVisors.fits(HelmetVisors.helmetDef(helmet), visorId)) {
            ItemDefinition visor = ItemRegistry.get(visorId);
            if (visor != null && visor.visorStats != null) return visor.visorStats.ricochetChance;
        }
        ItemDefinition def = ItemRegistry.get(helmet.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING));
        return def != null && def.armorStats != null ? def.armorStats.ricochetChance : 0;
    }

    /** 頭の向きに対して左右60度以内の正面入射だけを対象にする。ベクトルは変更しない。 */
    static boolean rollVisorRicochet(String hitLocation, double chance, Vector facing, Vector incoming, double roll) {
        if (!HEAD.equals(hitLocation) || !Double.isFinite(chance) || chance <= 0
                || facing == null || incoming == null) return false;
        double facingLength = Math.hypot(facing.getX(), facing.getZ());
        double incomingLength = Math.hypot(incoming.getX(), incoming.getZ());
        if (facingLength < 1e-6 || incomingLength < 1e-6) return false;
        double dot = (facing.getX() * incoming.getX() + facing.getZ() * incoming.getZ())
                / (facingLength * incomingLength);
        return dot <= -0.5 && roll >= 0 && roll < Math.min(1.0, chance);
    }

    /**
     * 弾薬クラスと防具クラスから、防具を貫通したかを抽選する。
     * 下位クラスの弾は {@link PenetrationRng} の PRD を使い、フルオートで撃ち込んでも
     * 連続で抜けたり長く抜けなかったりしないようにする (平均は {@link PenetrationRng#LOW_CLASS_CHANCE})。
     */
    public static boolean penetrates(int ammoClass, int armorClass, UUID shooter, UUID victim, String hitLocation) {
        boolean head = HEAD.equals(hitLocation) || FACE.equals(hitLocation);
        return PenetrationRng.penetrates(ammoClass, armorClass, shooter, victim, head);
    }
}
