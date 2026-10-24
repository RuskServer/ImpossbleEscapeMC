package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;

import java.util.Map;

/** 倉庫調査が沿岸の討伐系列に挟まり、二つの地点を別々に記録することを確かめる。 */
public final class CoastalWarehouseQuestTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static QuestDefinition quest(String id) {
        return QuestCatalog.create(null).stream().filter(q -> q.getId().equals(id)).findFirst().orElseThrow();
    }

    private static boolean requires(QuestDefinition quest, String id) {
        return quest.getConditions().stream().anyMatch(c -> c instanceof CompletedQuestCondition completed
                && completed.getRequiredQuestId().equals(id));
    }

    public static void main(String[] args) {
        QuestDefinition warehouse = quest("bastion_coastal_warehouses");
        check(requires(warehouse, "bastion_coastal_clearance"), "warehouse follows clearance");
        check(requires(quest("bastion_rifle_drill"), warehouse.getId()), "rifle drill follows warehouse");
        check(warehouse.getObjectives().size() == 2, "two locations only");

        ReachLocationObjective first = (ReachLocationObjective) warehouse.getObjectives().get(0);
        ReachLocationObjective second = (ReachLocationObjective) warehouse.getObjectives().get(1);
        check(first.getWorldName().equals("costal") && second.getWorldName().equals("costal"), "coastal world");
        check(first.getX() == -192 && first.getY() == 1 && first.getZ() == 84
                && first.getRadiusSquared() == 100 && first.getLocationName().equals("物流倉庫1"), "warehouse 1");
        check(second.getX() == -256 && second.getY() == 1 && second.getZ() == 84
                && second.getRadiusSquared() == 100 && second.getLocationName().equals("物流倉庫2"), "warehouse 2");

        ActiveQuest active = new ActiveQuest(warehouse.getId());
        first.updateProgress(null, null, active, 0, QuestTrigger.LOCATION_REACHED, Map.of("locationName", "物流倉庫1"));
        second.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED, Map.of("locationName", "物流倉庫1"));
        check(first.isCompleted(active, 0) && !second.isCompleted(active, 1), "first visit only");
        second.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED, Map.of("locationName", "物流倉庫2"));
        check(second.isCompleted(active, 1), "second visit");
        System.out.println("CoastalWarehouseQuestTest: chain, locations, and separate progress passed");
    }
}
