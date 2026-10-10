package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ルート表とコンテナ (クレート) の定義。
 * <ul>
 *   <li>ルート表: 品物ごとに確率 (0〜100) で別々に抽選する。min_items / max_items はコンテナに入る品物の数の下限・上限</li>
 *   <li>クレート: 重みでルート表を1つ選ぶ。IDは /loot container set &lt;map&gt; &lt;crateId&gt; とマップのデータが参照するため変えない
 *       (以前の loot.yml のキーと同じ)</li>
 * </ul>
 * 品物のIDは {@link com.lunar_prototype.impossbleEscapeMC.item.GameItems} で探す (プラグインのアイテム・弾、データパックの銃・アタッチメント)。
 * 銃の表示名を変えたい時だけ {@link #gun} で名前を付ける。
 * 弾は口径が多いので、決まった弾は個別に書き、残りは {@link #addAmmo} で貫通クラスごとにまとめて入れる
 */
final class LootCatalog {

    private LootCatalog() {
    }

    static List<LootTable> tables() {
        List<LootTable> tables = new ArrayList<>();

        // --- ガラクタ・基本物資 ---
        tables.add(table("common_pool", 2, 3,
                item("scraps", 60.0, 1, 2),
                item("toilet_paper", 40.0),
                item("dry_fuel", 40.0),
                item("cigarettes", 40.0),
                item("bolts", 50.0, 1, 2),
                item("screwdriver", 40.0),
                item("ai2", 25.0),
                item("545x39_ps", 30.0, 10, 30),
                item("556x45_hp", 30.0, 10, 30),
                item("12x70_steel_buckshot", 30.0, 4, 12),
                item("tactical_sling_bag", 10.0)));
        // 粗悪な弾 (クラス1〜2) を少し
        addAmmo(tables.getLast(), 1, 3.0, 5, 20);
        addAmmo(tables.getLast(), 2, 3.0, 5, 20);

        // --- 中級の装備・貴重品 ---
        tables.add(table("rare_pool", 1, 4,
                item("mini_battery", 35.0),
                item("wrench", 35.0),
                item("water_filter", 35.0),
                item("CAT", 25.0),
                item("usb", 5.0),
                item("eotech_xps2", 15.0),
                item("ekp_8", 15.0),
                gun("ak74", "AK-74M", 10.0),
                gun("mossberg_590", "Mossberg 590", 10.0),
                gun("usp45", "USP 45", 15.0),
                item("UNTAR", 10.0),
                item("MF-UNTAR", 10.0),
                item("FAST-MT", 8.0),
                item("Trooper", 8.0),
                item("mbss", 8.0),
                item("micro_rig", 15.0),
                item("d3rcx", 10.0),
                item("762x51_fmj_28", 20.0, 5, 15),
                item("45acp_match_fmj_26", 20.0, 10, 20)));
        // 標準弾と貫通力を上げた弾 (クラス3〜4)
        addAmmo(tables.getLast(), 3, 4.0, 5, 15);
        addAmmo(tables.getLast(), 4, 3.0, 5, 15);

        // --- 上級の装備・レア品 ---
        tables.add(table("legendary_pool", 1, 2,
                item("graphics_card", 5.0),
                item("tool_box", 15.0),
                item("m4a1", 5.0),
                item("mechanical_golden_watch", 5.0),
                gun("m700", "M700", 5.0),
                item("Altyn", 3.0),
                item("Slick", 3.0),
                item("pt_1", 10.0),
                item("545x39_bs", 10.0, 5, 15)));
        // 2030年代の弾 (クラス5〜6)
        addAmmo(tables.getLast(), 5, 2.0, 5, 15);
        addAmmo(tables.getLast(), 6, 0.25, 3, 10);

        // --- 医療品 ---
        tables.add(table("medical_pool", 2, 5,
                item("ai2", 80.0, 1, 2),
                item("CAT", 50.0)));

        // --- 弾薬 ---
        tables.add(table("ammo_pool", 3, 6,
                item("545x39_ps", 70.0, 30, 60),
                item("545x39_bs", 15.0, 15, 30),
                item("556x45_hp", 60.0, 30, 60),
                item("762x51_fmj_28", 40.0, 20, 40),
                item("45acp_match_fmj_26", 60.0, 20, 50),
                item("12x70_steel_buckshot", 70.0, 8, 24)));
        addAmmo(tables.getLast(), 1, 5.0, 20, 40);
        addAmmo(tables.getLast(), 2, 6.0, 20, 40);
        addAmmo(tables.getLast(), 3, 8.0, 15, 30);
        addAmmo(tables.getLast(), 4, 6.0, 15, 30);
        addAmmo(tables.getLast(), 5, 2.5, 10, 20);
        addAmmo(tables.getLast(), 6, 0.5, 5, 10);

        return tables;
    }

    static List<LootCrate> crates() {
        List<LootCrate> crates = new ArrayList<>();
        crates.add(crate("standard_crate", "LIGHT_GRAY", Map.of("common_pool", 80.0, "rare_pool", 20.0)));
        crates.add(crate("secret_crate", "BLUE", Map.of("common_pool", 50.0, "rare_pool", 40.0, "legendary_pool", 10.0)));
        crates.add(crate("weapon_box", "GREEN", Map.of("rare_pool", 80.0, "legendary_pool", 20.0)));
        crates.add(crate("medical_case", "WHITE", Map.of("medical_pool", 100.0)));
        crates.add(crate("ammo_case", "GOLD", Map.of("ammo_pool", 100.0)));
        return crates;
    }

    private static LootTable table(String id, int minItems, int maxItems, LootTable.LootEntry... entries) {
        LootTable table = new LootTable();
        table.id = id;
        table.minItems = minItems;
        table.maxItems = maxItems;
        table.items.addAll(List.of(entries));
        return table;
    }

    private static LootTable.LootEntry item(String itemId, double chance) {
        return item(itemId, chance, 1, 1);
    }

    /** @param chance 確率 (0〜100) */
    private static LootTable.LootEntry item(String itemId, double chance, int min, int max) {
        LootTable.LootEntry entry = new LootTable.LootEntry();
        entry.itemId = itemId;
        entry.chance = chance;
        entry.minAmount = min;
        entry.maxAmount = max;
        return entry;
    }

    /** その貫通クラスの弾のうち、表にまだ書いていないものを全部、同じ確率・個数で入れる */
    private static void addAmmo(LootTable table, int ammoClass, double chance, int min, int max) {
        Set<String> listed = new HashSet<>();
        table.items.forEach(entry -> listed.add(entry.itemId));
        ItemRegistry.getAmmoIds().stream()
                .sorted()
                .filter(id -> ItemRegistry.getAmmo(id).ammoClass == ammoClass && !listed.contains(id))
                .forEach(id -> table.items.add(item(id, chance, min, max)));
    }

    /** 表示名を付けたデータパック銃 */
    private static LootTable.LootEntry gun(String gunId, String displayName, double chance) {
        LootTable.LootEntry entry = item(gunId, chance);
        entry.displayName = displayName;
        return entry;
    }

    /** @param tableWeights ルート表のID → 重み */
    private static LootCrate crate(String id, String color, Map<String, Double> tableWeights) {
        LootCrate crate = new LootCrate();
        crate.id = id;
        crate.color = color;
        crate.tableWeights.putAll(tableWeights);
        return crate;
    }
}
