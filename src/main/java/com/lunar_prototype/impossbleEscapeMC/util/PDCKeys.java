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
    public static final PersistentDataType<byte[], byte[]> BYTE_ARRAY = PersistentDataType.BYTE_ARRAY;

    public static final NamespacedKey ITEM_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "item_id");
    public static final NamespacedKey DURABILITY = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "durability");
    public static final NamespacedKey AMMO = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "ammo");
    public static final NamespacedKey CURRENT_AMMO_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "current_ammo_id");
    public static final NamespacedKey CHAMBER_LOADED = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "chamber_loaded");
    public static final NamespacedKey CHAMBER_AMMO_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "chamber_ammo_id");
    public static final NamespacedKey JAMMED = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "jammed");
    public static final NamespacedKey ATTACHMENTS = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "attachments");
    public static final NamespacedKey ARMOR_CLASS = new NamespacedKey(ImpossbleEscapeMC.getInstance(),
            "armor_class_int");
    public static final NamespacedKey LOOT_TABLE_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "loot_table_id");
    public static final NamespacedKey CORPSE_INVENTORY = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "corpse_inventory");
    public static final NamespacedKey GUI_TRIGGER = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "gui_trigger");
    public static final NamespacedKey ITEM_WEIGHT = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "item_weight");
    public static final NamespacedKey BACKPACK_DATA = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "backpack_data");
    public static final NamespacedKey BACKPACK_UID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "backpack_uid");
    public static final NamespacedKey MAP_ZOOM = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "map_zoom");
    public static final NamespacedKey LOCKED_SLOT_PLACEHOLDER = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "locked_slot_placeholder");
    public static final NamespacedKey FIND_IN_RAID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "find_in_raid");
    public static final NamespacedKey RAID_BROUGHT_IN = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "raid_brought_in");
    public static final NamespacedKey QUEST_ID = new NamespacedKey(ImpossbleEscapeMC.getInstance(), "quest_id");

    public static NamespacedKey affix(String stat) {
        return new NamespacedKey(ImpossbleEscapeMC.getInstance(), "affix_" + stat);
    }
}
