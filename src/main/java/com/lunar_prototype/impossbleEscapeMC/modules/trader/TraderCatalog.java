package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import java.util.ArrayList;
import java.util.List;

/**
 * トレーダーと品物の定義。
 * <p>
 * トレーダーIDはクエスト (依頼主) と Citizens の NPC 番号、品物のIDはアイテム・弾・データパック銃・データパックの
 * アタッチメントのIDを指す。銃のIDはデータパック銃 (表示名を付けた品物は、その名前でデータパックの銃を作る)。
 * 起動時に {@link TraderModule} が品物のIDを確かめる。
 * limit は1日の購入上限 (0 なら無制限)、level は解放に必要なプレイヤーレベル、quest は解放に必要なクエスト (無ければnull)
 */
final class TraderCatalog {

    private TraderCatalog() {
    }

    static List<TraderDefinition> create() {
        List<TraderDefinition> traders = new ArrayList<>();

        traders.add(trader("kovacs", "§cKovacs (Buy Only)", TraderType.BUY, 1,
                false, true, List.of(
                        item("ak74", 15000, 2, 1, "AK-74M", "field_deployment"),
                        item("545x39_ps", 150, 300, 1, null, null),
                        item("tactical_sling_bag", 6000, 10, 1, null, null),
                        item("d3rcx", 10000, 10, 8, null, null)
                )));

        traders.add(trader("pharmakon", "§aPharmakon (Buy Only)", TraderType.BUY, 2,
                false, false, List.of(
                        item("ai2", 3200, 50, 1, null, null),
                        item("CAT", 1200, 50, 1, null, null)
                )));

        traders.add(trader("bastion", "§bBastion (Buy Only)", TraderType.BUY, 3,
                true, false, List.of(
                        item("UNTAR", 8200, 10, 1, null, null),
                        item("MF-UNTAR", 9800, 10, 1, null, null),
                        item("micro_rig", 6700, 15, 1, null, null),
                        item("mbss", 12000, 10, 8, null, "bastion_logistics_02"),
                        item("FAST-MT", 43000, 10, 20, null, null),
                        item("Trooper", 62000, 10, 20, null, null)
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
                        item("UNTAR", 5800, 0, 1, null, null),
                        // --- Gear (Bags & Rigs) ---
                        item("tactical_sling_bag", 4800, 0, 1, null, null),
                        item("mbss", 9600, 0, 1, null, null),
                        item("d3rcx", 8000, 0, 1, null, null),
                        item("micro_rig", 5300, 0, 1, null, null),
                        // --- Medical ---
                        item("ai2", 2500, 0, 1, null, null),
                        item("CAT", 900, 0, 1, null, null),
                        // --- Rare/Valuable Items ---
                        item("mini_battery", 2500, 0, 1, null, null),
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
}
