package com.lunar_prototype.impossbleEscapeMC.modules.stash;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StashCapacityTest {
    private final StashModule stash = new StashModule(null, null, null);

    @Test
    void facilityUpgradesUnlockPagesWithoutChangingTheLegacyField() {
        var data = new PlayerData(UUID.randomUUID());
        assertEquals(1, stash.getMaxUnlockedPage(data.getStashLevel()));
        int[] pages = {1, 2, 4, 5};
        for (int level = 1; level <= 4; level++) {
            data.setHideoutLevel(HideoutFacility.STASH.id, level);
            assertEquals(pages[level - 1], stash.getMaxUnlockedPage(data.getStashLevel()));
            for (int page = 1; page <= pages[level - 1]; page++) {
                assertEquals(3, stash.getRows(data.getStashLevel(), page));
            }
            assertEquals(0, stash.getRows(data.getStashLevel(), pages[level - 1] + 1));
        }
    }

    @Test
    void alreadyUpgradedSavesGainCapacityOnLoadWithoutLosingPages() {
        var gson = new Gson();
        var old = new PlayerData(UUID.randomUUID());
        old.setHideoutLevel("stash", 4);
        old.setStashPage(1, "old-page-one");
        old.setStashPage(5, "old-page-five");
        var loaded = gson.fromJson(gson.toJson(old), PlayerData.class);
        assertEquals(5, stash.getMaxUnlockedPage(loaded.getStashLevel()));
        assertEquals(Map.of(1, "old-page-one", 5, "old-page-five"), loaded.getStashPages());
        var reloaded = gson.fromJson(gson.toJson(loaded), PlayerData.class);
        assertEquals(5, stash.getMaxUnlockedPage(reloaded.getStashLevel()));
        assertEquals(loaded.getStashPages(), reloaded.getStashPages());
    }

    @Test
    void oldUnlockedCapacityIsRetainedWithoutMatchingFacilityLevels() {
        var data = new PlayerData(UUID.randomUUID());
        data.setStashLevel(4);
        assertEquals(5, stash.getMaxUnlockedPage(data.getStashLevel()));
        data.setHideoutLevel("stash", 2);
        assertEquals(5, stash.getMaxUnlockedPage(data.getStashLevel()));
    }

    @Test
    void savesWithoutFacilityDataKeepTheirStarterPage() {
        var gson = new Gson();
        var json = gson.toJsonTree(new PlayerData(UUID.randomUUID())).getAsJsonObject();
        json.remove("hideoutLevels");
        json.remove("stashLevel");
        var loaded = gson.fromJson(json, PlayerData.class);
        assertEquals(1, stash.getMaxUnlockedPage(loaded.getStashLevel()));
        assertEquals(0, stash.getRows(loaded.getStashLevel(), 2));
    }
}
