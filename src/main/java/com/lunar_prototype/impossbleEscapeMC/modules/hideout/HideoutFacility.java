package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import org.bukkit.Material;

/**
 * 隠れ家の設備。どれも最初は未設置 (Lv.0) で、レベルごとの建築はベースへの差分 ({@link HideoutLayout})。
 * 差分は「ベース → ベースの建築改良の各レベル → 各設備の各レベル (この並び順)」で重ねる。
 * id はプレイヤーデータのレベルと、差分のファイル名に使うので変えない
 */
public enum HideoutFacility {
    /** ベースの建築改良。最大レベルは作った差分の数で決まる */
    BASE("base", "建物", Material.BRICKS, 0),
    SHOOTING_RANGE("shooting_range", "射撃場", Material.TARGET, 3),
    WORKBENCH("workbench", "ワークベンチ", Material.CRAFTING_TABLE, 4),
    GUN_RACK("gun_rack", "ガンラック", Material.CROSSBOW, 2),
    ARMOR("armor", "アーマー", Material.IRON_CHESTPLATE, 3),
    AMMO("ammo", "弾薬製造", Material.GUNPOWDER, 1),
    SKILL_TREE("skill_tree", "スキルツリー", Material.BOOKSHELF, 3),
    WATER("water", "集水設備", Material.WATER_BUCKET, 4),
    STASH("stash", "保管庫", Material.CHEST, 4),
    MEDICAL("medical", "医療設備", Material.BREWING_STAND, 4);

    /** ベースの建築改良を作れる上限 (エディターで選べるレベル) */
    public static final int BASE_EDIT_LIMIT = 9;

    public final String id;
    public final String label;
    public final Material icon;
    /** 最大レベル (ベースは 0: 作った差分の数) */
    private final int maxLevel;

    HideoutFacility(String id, String label, Material icon, int maxLevel) {
        this.id = id;
        this.label = label;
        this.icon = icon;
        this.maxLevel = maxLevel;
    }

    /** 最大レベル。ベースは作った差分の数 */
    public int maxLevel(HideoutLayout layout) {
        return this == BASE ? layout.highestLevel(this) : maxLevel;
    }

    /** エディターで作れるレベルの上限 */
    public int editLimit() {
        return this == BASE ? BASE_EDIT_LIMIT : maxLevel;
    }

    public static HideoutFacility byId(String id) {
        for (HideoutFacility facility : values()) {
            if (facility.id.equalsIgnoreCase(id)) return facility;
        }
        return null;
    }
}
