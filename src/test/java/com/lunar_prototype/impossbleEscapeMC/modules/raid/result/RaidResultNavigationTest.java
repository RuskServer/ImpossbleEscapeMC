package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResultDialog.Step.*;
import static org.junit.jupiter.api.Assertions.*;

class RaidResultNavigationTest {
    @ParameterizedTest
    @CsvSource({"false,false,KILLS,LOOT", "true,false,REPAIR,REPAIR",
            "false,true,HANDIN,HANDIN", "true,true,REPAIR,HANDIN"})
    void optionalPagesHaveMatchingForwardAndBackDestinations(boolean repairs, boolean handIn,
            RaidResultDialog.Step next, RaidResultDialog.Step back) {
        var route = new RaidResultDialog.Navigation(repairs, handIn);
        assertEquals(next, route.afterLoot());
        assertEquals(back, route.beforeKills());
        assertEquals(handIn ? HANDIN : KILLS, route.afterRepair());
        assertEquals(repairs ? REPAIR : LOOT, route.beforeHandIn());
        assertEquals("page/kills", KILLS.action);
        assertEquals("loot", LOOT.action);
    }
}
