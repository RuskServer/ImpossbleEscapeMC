package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility.ItemCost;

import java.util.List;
import java.util.Map;

/**
 * 交換の取引: 本日の回数の鍵 (ルーブルの取引は前からの鍵のまま、交換は別に数える)・持っている数で何回できるか・回数分の品物。
 * カタログの交換は買うトレーダーにだけあり、渡す品物と数が正しい
 */
public final class TraderBarterTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        TraderDefinition kovacs = new TraderDefinition("kovacs", "Kovacs", TraderType.BUY, List.of(), 1, false, false);
        TraderItem cash = new TraderItem("556x45_m855", 350, 8, 8, "kovacs_penetration");
        TraderItem trade = new TraderItem("556x45_m855", 0, 2, 4, "kovacs_rewiring");
        trade.count = 30;
        trade.barter = List.of(new ItemCost("copper_wire", 2), new ItemCost("bolts", 2));
        TraderItem other = new TraderItem("556x45_m855", 0, 2, 4, null);
        other.barter = List.of(new ItemCost("scraps", 5));

        // ---- 鍵 ----
        check(TraderModule.limitKey(kovacs, cash).equals("kovacs_556x45_m855"), "cash key unchanged");
        check(!TraderModule.limitKey(kovacs, trade).equals(TraderModule.limitKey(kovacs, cash)), "barter counted apart from cash");
        check(!TraderModule.limitKey(kovacs, trade).equals(TraderModule.limitKey(kovacs, other)), "two barters counted apart");

        // ---- 何回できるか ----
        check(TraderModule.affordableTrades(trade.barter, Map.of("copper_wire", 5, "bolts", 9)) == 2, "limited by copper wire");
        check(TraderModule.affordableTrades(trade.barter, Map.of("copper_wire", 5)) == 0, "missing item");
        List<ItemCost> twice = List.of(new ItemCost("bolts", 2), new ItemCost("bolts", 1));
        check(TraderModule.affordableTrades(twice, Map.of("bolts", 7)) == 2, "same item listed twice adds up");

        // ---- 回数分の品物 ----
        List<ItemCost> three = TraderModule.barterCosts(trade, 3);
        check(three.equals(List.of(new ItemCost("copper_wire", 6), new ItemCost("bolts", 6))), "costs scale with trades");
        check(trade.barter.getFirst().amount() == 2, "catalog entry left untouched");

        // ---- カタログ ----
        int barters = 0;
        for (TraderDefinition trader : TraderCatalog.create()) {
            for (TraderItem item : trader.items) {
                if (!item.isBarter()) continue;
                barters++;
                check(trader.type == TraderType.BUY, "barter only at buy traders: " + item.itemId);
                check(item.count >= 1, "count at least 1: " + item.itemId);
                for (ItemCost cost : item.barter) check(cost.amount() > 0, "positive cost: " + item.itemId);
            }
        }
        check(barters > 0, "catalog has barters");
        System.out.println("TraderBarterTest OK");
    }
}
