package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import java.util.UUID;
import java.util.Set;

public final class QuestTradeUnlockTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var module = new TraderModule(null);
        Set<String> starter = Set.of("ak74", "545x39_hp", "556x45_rrlp", "762x51_sp", "762x54r_hp_bt",
                "9x39_hp", "9x19_rip", "45acp_hydra_shok", "12x70_steel_buckshot");
        var beginner = new PlayerData(new UUID(0, 2)); beginner.setLevel(1);
        int starterFound = 0;
        for (var trader : TraderCatalog.create()) {
            for (var item : trader.items) {
                if (trader.id.equals("kovacs") && starter.contains(item.itemId)) {
                    starterFound++;
                    check(module.isUnlocked(beginner, item), "starter gear available without completed quests: " + item.itemId);
                    check(item.dailyLimit == 0, "starter gear has no purchase cap: " + item.itemId);
                } else if (trader.id.equals("kovacs") && item.requiredQuestId != null) {
                    check(item.dailyLimit > 0, "quest-unlocked gear keeps its purchase cap: " + item.itemId);
                }
                if (trader.id.equals("bastion")) {
                    int levelCap = item.itemId.equals("sp_v") ? 20 : item.itemId.equals("FAST-MT_visor") ? 15 : 10;
                    check(item.requiredLevel <= levelCap, "Bastion equipment level cap");
                }
                String quest = trader.id.equals("bastion") && item.itemId.equals("mbss") ? "bastion_logistics_02" : null;
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
        check(starterFound == starter.size(), "all starter guns/ammo found exactly once");
        System.out.println("QuestTradeUnlockTest: all checks passed");
    }
}
