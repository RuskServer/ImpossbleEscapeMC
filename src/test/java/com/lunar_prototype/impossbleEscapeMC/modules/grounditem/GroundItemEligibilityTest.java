package com.lunar_prototype.impossbleEscapeMC.modules.grounditem;

import org.junit.jupiter.api.Test;
import java.util.Set;
public final class GroundItemEligibilityTest {
    private static void check(boolean ok,String message){ org.junit.jupiter.api.Assertions.assertTrue(ok, message); }
    @Test
    void groundItemEligibility(){
        check(!GroundItemEligibility.accepts(Set.of("THIS","toisarm.casing"),32767,true),"datapack casing rejected");
        check(!GroundItemEligibility.accepts(Set.of("toisarm.casing"),0,true),"casing remains excluded even with changed pickup delay");
        check(!GroundItemEligibility.accepts(Set.of(),32767,true),"permanently unpickable decoration rejected");
        check(!GroundItemEligibility.accepts(Set.of(),Integer.MAX_VALUE,true),"extended permanent delay rejected");
        check(!GroundItemEligibility.accepts(Set.of(),0,false),"player pickup disabled rejected");
        check(GroundItemEligibility.accepts(Set.of(),0,true),"normal collectible accepted");
        check(GroundItemEligibility.accepts(Set.of("THIS"),10,true),"short spawn delay and generic tag remain valid");
        check(GroundItemEligibility.accepts(Set.of("another_plugin.item"),0,true),"unrelated tags do not exclude real loot");

    }
}
