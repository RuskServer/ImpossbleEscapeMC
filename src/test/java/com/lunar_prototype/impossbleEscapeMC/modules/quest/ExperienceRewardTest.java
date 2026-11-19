package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.*;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.reward.ExpReward;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.UUID;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.allocate;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class ExperienceRewardTest {
    static class Players extends PlayerDataModule {
        PlayerData data;
        int saves;
        Players() { super(null, new CommandRegistry()); }
        @Override public PlayerData getPlayerData(UUID id) { return data; }
        @Override public void saveAsync(UUID id) { saves++; }
    }

    @Test void questRewardNormalizesMultipleLevelsImmediately() {
        MockBukkit.mock();
        try {
            var data = new PlayerData(UUID.randomUUID()); data.setExperience(950);
            var levels = new LevelModule(null);
            var quests = new QuestModule(null, null, new CommandRegistry(), null, levels);
            new ExpReward(10000).apply(null, data, quests);
            assertEquals(5, data.getLevel()); assertEquals(950, data.getExperience());
            new ExpReward(4050).apply(null, data, quests);
            assertEquals(6, data.getLevel()); assertEquals(0, data.getExperience());
        } finally { MockBukkit.unmock(); }
    }

    @Test void replayedRaidReceiptSurvivesPlayerDataReload() throws Exception {
        var players = allocate(Players.class); players.data = new PlayerData(UUID.randomUUID());
        var levels = new LevelModule(players);
        assertTrue(levels.addRaidExperience(players.data.getUuid(), "same-session", 300));
        players.data = new Gson().fromJson(new Gson().toJson(players.data), PlayerData.class);
        assertFalse(levels.addRaidExperience(players.data.getUuid(), "same-session", 300));
        assertEquals(300, players.data.getExperience()); assertEquals(1, players.saves);
        assertTrue(levels.addRaidExperience(players.data.getUuid(), "next-session", 400));
        assertEquals(700, players.data.getExperience()); assertEquals(2, players.saves);
    }
}
