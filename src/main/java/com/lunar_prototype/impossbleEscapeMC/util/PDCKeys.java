package com.lunar_prototype.impossbleEscapeMC.util;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class PDCKeys {
    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Integer, Integer> INTEGER = PersistentDataType.INTEGER;
    public static final PersistentDataType<Double, Double> DOUBLE = PersistentDataType.DOUBLE;
    public static final PersistentDataType<Byte, Byte> BOOLEAN = PersistentDataType.BYTE; // Boolean代わりのByte

    public static final NamespacedKey ITEM_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "item_id");
    public static final NamespacedKey DURABILITY = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "durability");
    public static final NamespacedKey AMMO = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "ammo");
    public static final NamespacedKey CURRENT_AMMO_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "current_ammo_id");
    public static final NamespacedKey CHAMBER_LOADED = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "chamber_loaded");
    public static final NamespacedKey CHAMBER_AMMO_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "chamber_ammo_id");
    public static final NamespacedKey ATTACHMENTS = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "attachments");
    public static final NamespacedKey ARMOR_CLASS = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "armor_class_int");
    public static final NamespacedKey LOOT_TABLE_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "loot_table_id");
    public static final NamespacedKey CORPSE_INVENTORY = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "corpse_inventory");

    public static NamespacedKey affix(String stat) {
        return new NamespacedKey(ImpossbleEscapeMC.getInstance(), "affix_" + stat);
    }
}