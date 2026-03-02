package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

public class ArmorStats {
    public int defense; // 防御力 (Attributeなどで使う場合用)
    public int armorClass; // PDCに保存するクラス参照 (1-6 etc)
    public int customModelData; // CustomModelData

    // EquippableComponent settings
    public String slot; // EquipmentSlot name (HEAD, CHEST, etc.)
    public String equipSound; // Sound key string
    public String model; // NamespacedKey string for model
    public String cameraOverlay; // NamespacedKey string for camera overlay
    public boolean dispensable; // default true usually, but configurable
    public boolean swappable;
    public boolean damageOnHurt;

    public ArmorStats() {
        this.dispensable = true;
        this.swappable = true;
        this.damageOnHurt = true;
    }
}
