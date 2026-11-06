package com.lunar_prototype.impossbleEscapeMC.util;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class PDCKeys {
    /**
     * キーの名前空間。保存済みのアイテムの PDC がこの文字列で読み書きされているので変えない (plugin.yml の name の小文字)。
     * プラグインのインスタンスに依存させない: ImpossbleEscapeMC.getInstance() は onEnable の最初でしか使えず、
     * それより前やテストでこのクラスを触ると初期化に失敗していた。
     */
    public static final String NAMESPACE = "impossbleescapemc";

    public static final PersistentDataType<String, String> STRING = PersistentDataType.STRING;
    public static final PersistentDataType<Integer, Integer> INTEGER = PersistentDataType.INTEGER;
    public static final PersistentDataType<Double, Double> DOUBLE = PersistentDataType.DOUBLE;
    public static final PersistentDataType<Byte, Byte> BOOLEAN = PersistentDataType.BYTE; // Boolean代わりのByte
    public static final PersistentDataType<byte[], byte[]> BYTE_ARRAY = PersistentDataType.BYTE_ARRAY;

    public static final NamespacedKey ITEM_ID = key("item_id");
    public static final NamespacedKey DURABILITY = key("durability");
    public static final NamespacedKey AMMO = key("ammo");
    public static final NamespacedKey CURRENT_AMMO_ID = key("current_ammo_id");
    public static final NamespacedKey CHAMBER_LOADED = key("chamber_loaded");
    public static final NamespacedKey CHAMBER_AMMO_ID = key("chamber_ammo_id");
    public static final NamespacedKey JAMMED = key("jammed");
    public static final NamespacedKey ATTACHMENTS = key("attachments");
    public static final NamespacedKey ARMOR_CLASS = key("armor_class_int");
    public static final NamespacedKey LOOT_TABLE_ID = key("loot_table_id");
    public static final NamespacedKey CORPSE_INVENTORY = key("corpse_inventory");
    public static final NamespacedKey GUI_TRIGGER = key("gui_trigger");
    public static final NamespacedKey ITEM_WEIGHT = key("item_weight");
    public static final NamespacedKey ITEM_COST = key("item_cost");
    public static final NamespacedKey COST_SLOT_PLACEHOLDER = key("cost_slot_placeholder");
    public static final NamespacedKey BACKPACK_DATA = key("backpack_data");
    public static final NamespacedKey BACKPACK_UID = key("backpack_uid");
    public static final NamespacedKey MAP_ZOOM = key("map_zoom");
    public static final NamespacedKey LOCKED_SLOT_PLACEHOLDER = key("locked_slot_placeholder");
    public static final NamespacedKey FIND_IN_RAID = key("find_in_raid");
    public static final NamespacedKey RAID_BROUGHT_IN = key("raid_brought_in");
    public static final NamespacedKey DROPPED_BY_PLAYER = key("dropped_by_player");
    public static final NamespacedKey QUEST_ID = key("quest_id");
    public static final NamespacedKey RAID_MAP_ID = key("raid_map_id");
    public static final NamespacedKey GUI_TYPE = key("gui_type");
    /** ヘルメットに付けたバイザーのアイテムID */
    public static final NamespacedKey HELMET_VISOR = key("helmet_visor");
    /** 単体・装着中のバイザーの残耐久。ヘルメット本体のdurabilityには混ぜない。 */
    public static final NamespacedKey VISOR_DURABILITY = key("visor_durability");
    /** 支給装備 (無料の装備) の品。売値 0 (トレーダーで処分はできる)・納品できない・FIR にならない */
    public static final NamespacedKey SUPPLIED = key("supplied");

    public static NamespacedKey affix(String stat) {
        return key("affix_" + stat);
    }

    /** 従来の new NamespacedKey(プラグイン, 名前) と同じく、名前は小文字にして検証する */
    private static NamespacedKey key(String name) {
        NamespacedKey key = NamespacedKey.fromString(NAMESPACE + ":" + name.toLowerCase(java.util.Locale.ROOT));
        if (key == null) throw new IllegalArgumentException("不正なキーの名前: " + name);
        return key;
    }
}
