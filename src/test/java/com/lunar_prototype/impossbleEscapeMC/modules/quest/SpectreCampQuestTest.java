package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.CompletedQuestCondition;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.ReachLocationObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;

import java.util.Map;

/** 軍事キャンプの二地点と、討伐タスクの間に入る順序を確かめる。 */
public final class SpectreCampQuestTest {
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
        QuestDefinition camp = quest("bastion_spectre_camp_recon");
        check(requires(camp, "bastion_rifle_drill"), "camp follows rifle drill");
        check(requires(quest("bastion_close_quarters"), camp.getId()), "close quarters follows camp");
        check(camp.getObjectives().size() == 2, "two locations only");

        ReachLocationObjective sandbags = (ReachLocationObjective) camp.getObjectives().get(0);
        ReachLocationObjective tower = (ReachLocationObjective) camp.getObjectives().get(1);
        check(sandbags.getWorldName().equals("factory") && tower.getWorldName().equals("factory"), "factory world");
        check(sandbags.getX() == 160 && sandbags.getY() == -34 && sandbags.getZ() == 184
                && sandbags.getRadiusSquared() == 100
                && sandbags.getLocationName().equals("Spectre軍事キャンプの土嚢"), "sandbags");
        check(tower.getX() == 142 && tower.getY() == -34 && tower.getZ() == 208
                && tower.getRadiusSquared() == 100
                && tower.getLocationName().equals("Spectre軍事キャンプの電波塔"), "radio tower");

        ActiveQuest active = new ActiveQuest(camp.getId());
        sandbags.updateProgress(null, null, active, 0, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "Spectre軍事キャンプの土嚢"));
        tower.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "Spectre軍事キャンプの土嚢"));
        check(sandbags.isCompleted(active, 0) && !tower.isCompleted(active, 1), "sandbags visit only");
        tower.updateProgress(null, null, active, 1, QuestTrigger.LOCATION_REACHED,
                Map.of("locationName", "Spectre軍事キャンプの電波塔"));
        check(tower.isCompleted(active, 1), "tower visit");
        System.out.println("SpectreCampQuestTest: chain, locations, and separate progress passed");
    }
}
