package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.ExpReward;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.MoneyReward;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoronovHuntQuestTest {
    private static final String FACTORY = "ノヴォザリエ薬品工場";

    private QuestDefinition quest() {
        return QuestCatalog.create(null).stream().filter(q -> q.getId().equals("kovacs_voronov_hunt"))
                .findFirst().orElseThrow();
    }

    @Test
    void huntIsLateKovacsWorkWithExplicitTargetAndRewards() {
        var quest = quest();
        assertEquals("kovacs", quest.getTraderId());
        var data = new PlayerData(UUID.randomUUID());
        data.setLevel(12);
        data.completeQuest("kovacs_penetration");
        assertFalse(quest.getConditions().stream().allMatch(c -> c.isMet(data)));
        data.completeQuest("kovacs_marksman_02");
        assertTrue(quest.getConditions().stream().allMatch(c -> c.isMet(data)));
        data.setLevel(11);
        assertFalse(quest.getConditions().stream().allMatch(c -> c.isMet(data)));
        assertEquals(1, quest.getObjectives().size());
        var kill = (KillEntityObjective) quest.getObjectives().getFirst();
        assertEquals(BossDefinition.DEFAULT_ID, kill.getBossId());
        assertEquals(FACTORY, kill.getMapId());
        assertTrue(kill.getDescription().contains("ヴォロノフ部隊長"));
        assertTrue(quest.getRewards().stream().anyMatch(r -> r instanceof MoneyReward m && m.getAmount() == 150000));
        assertTrue(quest.getRewards().stream().anyMatch(r -> r instanceof ExpReward e && e.getAmount() == 10000));
    }

    @Test
    void onlyTheSpecifiedLeaderOnTheCorrectMapAdvancesOnce() {
        var quest = quest();
        var kill = quest.getObjectives().getFirst();
        var active = new ActiveQuest(quest.getId());
        var params = new HashMap<String, Object>(Map.of("entityType", "BOSS", "bossId", BossDefinition.DEFAULT_ID,
                "mapId", FACTORY));
        params.put("entityType", "SCAV");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("entityType", "BOSS");
        params.put("bossId", "another_boss");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.remove("bossId");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("bossId", BossDefinition.DEFAULT_ID);
        params.put("mapId", "ノヴォザリエ沿岸工業地区");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.remove("mapId");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("mapId", FACTORY);
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.RAID_EXTRACT, params));
        assertTrue(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        assertTrue(kill.isCompleted(active, 0));
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        assertEquals(1, active.getProgress(0));
    }

    @Test
    void bossFilterSurvivesExistingKillConditionModifiers() {
        var kill = new KillEntityObjective("BOSS", 2, 20.0, 60.0).boss(BossDefinition.DEFAULT_ID)
                .onMap(FACTORY).headshot().with(WeaponClass.MARKSMAN).withWeapon("svd", "SVD");
        var params = new HashMap<String, Object>(Map.of("entityType", "BOSS", "bossId", BossDefinition.DEFAULT_ID,
                "mapId", FACTORY, "headshot", true, "weaponClass", "MARKSMAN", "weaponId", "svd", "killDistance", 40.0));
        var active = new ActiveQuest("test");
        assertTrue(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("bossId", "another_boss");
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("bossId", BossDefinition.DEFAULT_ID);
        params.put("headshot", false);
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        params.put("headshot", true);
        params.put("killDistance", 10.0);
        assertFalse(kill.updateProgress(null, null, active, 0, QuestTrigger.KILL_ENTITY, params));
        assertThrows(IllegalStateException.class, () -> new KillEntityObjective("SCAV", 1).boss("test"));
        assertThrows(IllegalArgumentException.class, () -> new KillEntityObjective("BOSS", 1).boss(" "));
    }
}
