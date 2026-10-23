package com.lunar_prototype.impossbleEscapeMC.item.stats;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

public class ArmorStats {
    public enum ProtectedZone {
        HEAD("頭部"), FACE("顔面"), BODY("胴体"), ARMS("腕");

        public final String label;

        ProtectedZone(String label) {
            this.label = label;
        }

        public static ProtectedZone fromHitLocation(String location) {
            if (location == null) return null;
            try {
                return valueOf(location.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public int defense; // 防御力 (Attributeなどで使う場合用)
    public int armorClass; // PDCに保存するクラス参照 (1-6 etc)
    public int customModelData; // CustomModelData
    /** この防具クラスが弾を受け止める部位 */
    public List<ProtectedZone> coverage = List.of();

    // EquippableComponent settings
    public String slot; // EquipmentSlot name (HEAD, CHEST, etc.)
    public String equipSound; // Sound key string
    public String model; // NamespacedKey string for model
    public String cameraOverlay; // NamespacedKey string for camera overlay
    public boolean dispensable; // default true usually, but configurable
    public boolean swappable;
    public boolean damageOnHurt;

    // ヘルメットのバイザー ({@link HelmetVisors})
    public List<String> visors; // 付けられるバイザーのアイテムID (無ければ付けられない)
    public double ricochetChance; // 元からバイザーと一体のヘルメットの、正面の頭部被弾を跳弾させる確率 (0..1)
    public int visorCustomModelData; // バイザーを付けた時の CustomModelData

    public ArmorStats() {
        this.dispensable = true;
        this.swappable = true;
        this.damageOnHurt = true;
    }
}
