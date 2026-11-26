package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.HandInPlaces;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class RepairResultSkipTest {
    static class Data extends PlayerDataModule {
        Data() { super(null, null); }
        @Override public PlayerData getPlayerData(UUID id) { return new PlayerData(id); }
    }

    @Test void emptyPostRaidRepairAdvancesWithoutOpeningOrRetainingASession() throws Exception {
        var server = MockBukkit.mock();
        Object oldRepair = get(RepairDialog.class, null, "instance");
        Object oldPlaces = get(HandInPlaces.class, null, "instance");
        try {
            var plugin = MockBukkit.createMockPlugin();
            var traders = new TraderModule(null, allocate(Data.class), null, null, () -> null, () -> null, () -> null);
            var places = new HandInPlaces(plugin, null, null); places.start();
            var repair = new RepairDialog(plugin, traders); repair.start();
            var player = server.addPlayer();
            var advanced = new AtomicInteger(); var back = new AtomicInteger();
            assertFalse(RepairDialog.hasRepairsAfterRaid(player));
            RepairDialog.openAfterRaid(player, back::incrementAndGet, advanced::incrementAndGet, () -> "次へ: キルログ");
            assertEquals(1, advanced.get());
            assertEquals(0, back.get());
            assertTrue(((Map<?,?>) get(RepairDialog.class, null, "SESSIONS")).isEmpty());
        } finally {
            set(RepairDialog.class, null, "instance", oldRepair);
            set(HandInPlaces.class, null, "instance", oldPlaces);
            MockBukkit.unmock();
        }
    }
}
