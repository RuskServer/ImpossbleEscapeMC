package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import java.util.UUID;

public final class QuestTradeUnlockTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var module = new TraderModule(null);
        for (var trader : TraderCatalog.create()) {
            for (var item : trader.items) {
                String quest = trader.id.equals("kovacs") && item.itemId.equals("ak74") ? "field_deployment"
                        : trader.id.equals("bastion") && item.itemId.equals("mbss") ? "bastion_logistics_02" : null;
                if (quest == null) continue;
                check(quest.equals(item.requiredQuestId), "catalog quest connected");
                var player = new PlayerData(new UUID(0, 1)); player.setLevel(100);
                check(!module.isUnlocked(player, item), "high level alone cannot buy");
                player.completeQuest(quest);
                check(module.isUnlocked(player, item), "completed quest unlocks sale");
                player.setLevel(1);
                check(module.isUnlocked(player, item) == (item.requiredLevel <= 1), "level requirement retained");
            }
        }
        System.out.println("QuestTradeUnlockTest: all checks passed");
    }
}
