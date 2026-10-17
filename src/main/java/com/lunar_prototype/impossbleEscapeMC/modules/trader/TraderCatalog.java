package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility.ItemCost;

import java.util.ArrayList;
import java.util.List;

/**
 * トレーダーと品物の定義。
 * <p>
 * トレーダーIDはクエスト (依頼主) と Citizens の NPC 番号、品物のIDはアイテム・弾・データパック銃・データパックの
 * アタッチメントのIDを指す。銃のIDはデータパック銃 (表示名を付けた品物は、その名前でデータパックの銃を作る)。
 * 起動時に {@link TraderModule} が品物のIDを確かめる。
 * limit は1日の購入上限 (0 なら無制限)、level は解放に必要なプレイヤーレベル、quest は解放に必要なクエスト (無ければnull)。
 * 交換の取引は {@link #barter} (渡す品物と、1 回でもらう数。買うトレーダーだけ)
 */
final class TraderCatalog {

    private TraderCatalog() {
    }

    static List<TraderDefinition> create() {
        List<TraderDefinition> traders = new ArrayList<>();

        traders.add(trader("kovacs", "§cKovacs (Buy Only)", TraderType.BUY, 1,
                false, true, List.of(
                        item("ak74", 15000, 0, 1, "AK-74M", null),
                        item("mossberg_590", 18000, 2, 4, "Mossberg 590", "kovacs_close_supply"),
                        item("uzi", 22000, 2, 4, "UZI", "kovacs_close_supply"),
                        item("svd", 65000, 2, 10, "SVD", "kovacs_marksman_02"),
                        // クラス1弾は全口径を初期販売。標準弾・貫通弾は既存の条件で解放する。
                        // 初期の銃 (AK-74M) と初期販売の弾は購入制限なし
                        item("545x39_hp", 100, 0, 1, null, null),
                        item("556x45_rrlp", 110, 0, 1, null, null),
                        item("762x51_sp", 180, 0, 1, null, null),
                        item("762x54r_hp_bt", 180, 0, 1, null, null),
                        item("9x39_hp", 160, 0, 1, null, null),
                        item("9x19_rip", 70, 0, 1, null, null),
                        item("45acp_hydra_shok", 90, 0, 1, null, null),
                        item("12x70_steel_buckshot", 180, 0, 1, null, null),
                        item("545x39_ps", 150, 0, 1, null, null),
                        item("tactical_sling_bag", 6000, 10, 1, null, null),
                        item("d3rcx", 10000, 10, 8, null, "bastion_carrying_capacity"),
                        item("762x54r_ps", 240, 120, 3, null, "kovacs_eastern_supply"),
                        item("9x39_sp5", 220, 120, 3, null, "kovacs_eastern_supply"),
                        item("556x45_hp", 160, 240, 3, null, "kovacs_western_supply"),
                        item("762x51_fmj_28", 240, 120, 3, null, "kovacs_western_supply"),
                        item("9x19_pst_gzh", 90, 180, 4, null, "kovacs_close_supply"),
                        item("45acp_match_fmj_26", 120, 180, 4, null, "kovacs_close_supply"),
                        item("ekp_8", 9000, 3, 5, null, "kovacs_optic_reserve"),
                        item("545x39_pp", 350, 60, 8, null, "kovacs_penetration"),
                        item("556x45_m855", 350, 60, 8, null, "kovacs_penetration"),
                        item("762x51_m62", 450, 60, 8, null, "kovacs_penetration"),
                        item("762x54r_lps_gzh", 450, 60, 8, null, "kovacs_penetration"),
                        item("9x39_sp6", 450, 60, 8, null, "kovacs_penetration"),
                        item("9x19_7n21", 250, 60, 8, null, "kovacs_penetration"),
                        item("45acp_ap", 250, 60, 8, null, "kovacs_penetration"),
                        item("12x70_tss", 350, 24, 8, null, "kovacs_penetration"),
                        // 交換 (仮の値)
                        barter("556x45_m855", 30, 2, 4, "kovacs_rewiring", cost("copper_wire", 2), cost("bolts", 2))
                )));

        traders.add(trader("pharmakon", "§aPharmakon (Buy Only)", TraderType.BUY, 2,
                false, false, List.of(
                        item("ai2", 3200, 50, 1, null, null),
                        item("CAT", 1200, 50, 1, null, null),
                        // 交換 (仮の値)
                        barter("CAT", 1, 3, 1, null, cost("cigarettes", 2), cost("toilet_paper", 1))
                )));

        traders.add(trader("bastion", "§bBastion (Buy Only)", TraderType.BUY, 3,
                true, false, List.of(
                        item("UNTAR", 8200, 10, 1, null, null),
                        item("MF-UNTAR", 9800, 10, 1, null, null),
                        item("micro_rig", 6700, 15, 1, null, null),
                        item("mbss", 12000, 10, 4, null, "bastion_logistics_02"),
                        item("FAST-MT", 43000, 10, 8, null, null),
                        item("FAST-MT_visor", 14000, 10, 15, null, "bastion_face_protection"),
                        item("PVS-14", 38000, 2, 9, null, "bastion_night_optics_supply"),
                        item("Trooper", 62000, 10, 10, null, "bastion_protection_selection"),
                        // 最初から買える
                        item("flashlight", 2800, 0, 1, null, null),
                        // 交換 (仮の値)
                        barter("micro_rig", 1, 1, 3, null, cost("tool_box", 1), cost("bolts", 4)),
                        barter("PVS-14", 1, 1, 3, "bastion_night_optics_trial",
                                cost("mini_battery", 1), cost("copper_wire", 2), cost("tool_box", 1)),
                        // Spectre試作装備: 防具とリグを素材に、電子部品で制御系を組む。
                        barter("sp_v", 1, 1, 20, "bastion_monolith_prototype",
                                cost("Trooper", 2), cost("d3rcx", 1),
                                cost("graphics_card", 1), cost("tool_box", 2))
                )));

        traders.add(trader("broker", "§dBroker (Sell Only)", TraderType.SELL, 0,
                false, false, List.of(
                        // --- Common Items ---
                        item("scraps", 450, 0, 1, null, null),
                        item("toilet_paper", 300, 0, 1, null, null),
                        item("cigarettes", 400, 0, 1, null, null),
                        item("wrench", 1200, 0, 1, null, null),
                        item("screwdriver", 1000, 0, 1, null, null),
                        item("bolts", 1500, 0, 1, null, null),
                        item("copper_wire", 1300, 0, 1, null, null),
                        item("dry_fuel", 1800, 0, 1, null, null),
                        item("water_filter", 3500, 0, 1, null, null),
                        item("545x39_ps", 15, 0, 1, null, null),
                        // --- Weapons ---
                        item("ak74", 12000, 0, 1, null, null),
                        item("m4a1", 24000, 0, 1, null, null),
                        item("m700", 28000, 0, 1, null, null),
                        item("mossberg_590", 9600, 0, 1, null, null),
                        item("usp45", 4800, 0, 1, null, null),
                        // --- Armor & Helmets ---
                        item("Slick", 40000, 0, 1, null, null),
                        item("Trooper", 7800, 0, 1, null, null),
                        item("MF-UNTAR", 6200, 0, 1, null, null),
                        item("Altyn", 24000, 0, 1, null, null),
                        item("FAST-MT", 6500, 0, 1, null, null),
                        item("FAST-MT_visor", 2100, 0, 1, null, null),
                        item("PVS-14", 7500, 0, 1, null, null),
                        item("UNTAR", 5800, 0, 1, null, null),
                        // --- Gear (Bags & Rigs) ---
                        item("tactical_sling_bag", 4800, 0, 1, null, null),
                        item("mbss", 9600, 0, 1, null, null),
                        item("d3rcx", 8000, 0, 1, null, null),
                        item("micro_rig", 5300, 0, 1, null, null),
                        item("sp_v", 115000, 0, 1, null, null),
                        // --- Medical ---
                        item("ai2", 2500, 0, 1, null, null),
                        item("CAT", 900, 0, 1, null, null),
                        // --- Rare/Valuable Items ---
                        item("mini_battery", 2500, 0, 1, null, null),
                        item("flashlight", 900, 0, 1, null, null),
                        item("eotech_xps2", 8500, 0, 1, null, null),
                        item("tool_box", 4500, 0, 1, null, null),
                        item("graphics_card", 85000, 0, 1, null, null),
                        item("usb", 32000, 0, 1, null, null),
                        item("mechanical_golden_watch", 120000, 0, 1, null, null)
                )));

        return traders;
    }

    /**
     * @param npcId           Citizens の NPC 番号 (無ければ -1)
     * @param canRepairArmor  防具を修理できるか
     * @param canRepairWeapon 武器を修理できるか
     */
    private static TraderDefinition trader(String id, String displayName, TraderType type, int npcId,
                                           boolean canRepairArmor, boolean canRepairWeapon, List<TraderItem> items) {
        return new TraderDefinition(id, displayName, type, new ArrayList<>(items), npcId, canRepairArmor, canRepairWeapon);
    }

    /**
     * @param limit       1日の購入上限 (0 なら無制限)
     * @param level       解放に必要なプレイヤーレベル
     * @param displayName 表示名 (データパック銃を、この名前で作る。無ければnull)
     * @param quest       解放に必要なクエスト (無ければnull)
     */
    private static TraderItem item(String itemId, double price, int limit, int level, String displayName, String quest) {
        TraderItem item = new TraderItem(itemId, price, limit, level, quest);
        item.displayName = displayName;
        return item;
    }

    /**
     * 交換の取引: costs を渡して itemId を count 個もらう (代金なし)
     *
     * @param limit 1日の交換の回数の上限 (0 なら無制限)
     * @param level 解放に必要なプレイヤーレベル
     * @param quest 解放に必要なクエスト (無ければnull)
     */
    private static TraderItem barter(String itemId, int count, int limit, int level, String quest, ItemCost... costs) {
        TraderItem item = new TraderItem(itemId, 0, limit, level, quest);
        item.count = Math.max(1, count);
        item.barter = List.of(costs);
        return item;
    }

    private static ItemCost cost(String itemId, int amount) {
        return new ItemCost(itemId, amount);
    }
}
