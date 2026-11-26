package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.Map;
import java.util.Set;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class HideoutRaidTravelTest {
    @Test void publicVisitRejectsRaidBeforeAllocatingOrBuildingHideout() throws Exception {
        var server = MockBukkit.mock();
        try (var messages = MessageTestSupport.forwarding()) {
            var player = server.addPlayer();
            var origin = player.getLocation().clone();
            var raid = allocate(RaidInstance.class);
            set(RaidInstance.class, raid, "players", Set.of(player.getUniqueId()));
            var raids = allocate(RaidModule.class);
            set(RaidModule.class, raids, "activeRaids", Map.of("map", raid));
            var hideout = allocate(HideoutModule.class);
            set(HideoutModule.class, hideout, "raids", raids);
            assertFalse(hideout.visit(player));
            assertEquals(origin, player.getLocation());
        } finally { MockBukkit.unmock(); }
    }
}
