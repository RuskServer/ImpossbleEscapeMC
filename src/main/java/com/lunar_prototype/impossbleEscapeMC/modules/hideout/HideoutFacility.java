package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import org.bukkit.Material;

import java.util.List;

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
    /** 保管庫の各レベルの費用 (Lv.1 から。仮の値) */
    private static final long[] STASH_COST = {5_000, 25_000, 60_000, 120_000};

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

    /** エディターで作れるレベルの下限 (建物は 0 = ベースそのもの) */
    public int minEditLevel() {
        return this == BASE ? 0 : 1;
    }

    /** エディターで作れるレベルの上限 */
    public int editLimit() {
        return this == BASE ? BASE_EDIT_LIMIT : maxLevel;
    }

    /** そのレベルにする費用 (₽)。条件の決まっていない設備は 0 */
    public long moneyCost(int level) {
        if (this == STASH && level >= 1 && level <= STASH_COST.length) return STASH_COST[level - 1];
        return 0;
    }

    /** 素材 1 種類 (アイテム ID と数)。スタッシュと持ち物から取る (支給品は使えない) */
    public record ItemCost(String itemId, int amount) {
    }

    /** そのレベルにする素材 (決まっていなければ空) */
    public List<ItemCost> itemCosts(int level) {
        if (this == SKILL_TREE && level == 1) {
            return List.of(new ItemCost("tool_box", 1), new ItemCost("mini_battery", 3), new ItemCost("bolts", 4), new ItemCost("usb", 1));
        }
        // 仮の値 (docs/SKILL_TREE.md)
        if (this == SKILL_TREE && level == 2) {
            return List.of(new ItemCost("tool_box", 2), new ItemCost("water_filter", 2), new ItemCost("wrench", 4), new ItemCost("usb", 2));
        }
        if (this == SKILL_TREE && level == 3) {
            return List.of(new ItemCost("graphics_card", 1), new ItemCost("tool_box", 3), new ItemCost("mini_battery", 6),
                    new ItemCost("mechanical_golden_watch", 1));
        }
        return List.of();
    }

    /** そのレベルの強化の条件が決まっているか (決まっていなければ、今は条件なしで上げられる) */
    public boolean hasRequirements(int level) {
        return moneyCost(level) > 0 || !itemCosts(level).isEmpty();
    }

    /** 画面に出す説明 (決まっていなければ null) */
    public String description() {
        if (this == SKILL_TREE) return "スキルを習得する端末";
        return this == STASH ? "スタッシュを開ける棚" : null;
    }

    public static HideoutFacility byId(String id) {
        for (HideoutFacility facility : values()) {
            if (facility.id.equalsIgnoreCase(id)) return facility;
        }
        return null;
    }
}
