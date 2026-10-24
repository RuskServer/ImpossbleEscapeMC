package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;

import java.util.Map;

/** オフィスビルの二地点と、Pharmakon序盤の受領順を確かめる。 */
public final class PharmakonOfficeQuestTest {
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
        QuestDefinition office = quest("pharmakon_office_recon");
        check(requires(office, "stop_the_bleed"), "office follows stop the bleed");
        check(requires(quest("sanitary_maintenance"), office.getId()), "sanitary maintenance follows office");
        check(office.getObjectives().size() == 2, "two locations only");

        ReachLocationObjective breakRoom = (ReachLocationObjective) office.getObjectives().get(0);
        ReachLocationObjective lab = (ReachLocationObjective) office.getObjectives().get(1);
        check(breakRoom.getWorldName().equals("factory") && lab.getWorldName().equals("factory"), "factory world");
        check(breakRoom.getX() == 178 && breakRoom.getY() == -33 && breakRoom.getZ() == 119
                && breakRoom.getRadiusSquared() == 100
                && breakRoom.getLocationName().equals("オフィスビル1階の休憩所"), "break room");
        check(lab.getX() == 195 && lab.getY() == -34 && lab.getZ() == 107
                && lab.getRadiusSquared() == 100
                && lab.getLocationName().equals("オフィスビル1階の研究室"), "laboratory");

        ActiveQuest active = new ActiveQuest(office.getId());
        breakRoom.updateProgress(null, null, active, 0, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "オフィスビル1階の休憩所"));
        lab.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "オフィスビル1階の休憩所"));
        check(breakRoom.isCompleted(active, 0) && !lab.isCompleted(active, 1), "break room visit only");
        lab.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "オフィスビル1階の研究室"));
        check(lab.isCompleted(active, 1), "laboratory visit");
        System.out.println("PharmakonOfficeQuestTest: chain, locations, and separate progress passed");
    }
}
