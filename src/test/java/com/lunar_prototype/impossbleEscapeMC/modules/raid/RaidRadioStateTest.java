package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class RaidRadioStateTest {
    @Test void stableEntryAndReentryStillControlZoneVoice() {
        var s=new RaidRadioState();
        assertFalse(s.observeZone("factory",100));
        assertFalse(s.observeZone("factory",119));
        assertTrue(s.observeZone("factory",120));
        assertFalse(s.observeZone("factory",200));
        assertTrue(s.takeZone(120));assertFalse(s.takeZone(121));
        s.observeZone(null,300);s.observeZone("factory",301);
        assertFalse(s.observeZone("factory",321));
        assertFalse(s.observeZone("factory",1400));
        s.observeZone(null,1401);s.observeZone("factory",1402);
        assertTrue(s.observeZone("factory",1422));
        s.urgent(1424,200);
        assertFalse(s.takeZone(1630));
    }

    @Test void revealSpeaksImmediatelyAndCooldownExpiresAtTwentySeconds() {
        var s=new RaidRadioState();
        assertTrue(s.discover("ram",100));
        assertFalse(s.discover("drill",100),"simultaneous reveals share one voice");
        assertFalse(s.discover("docs",499),"still inside the 400-tick cooldown");
        assertTrue(s.idle(499),"suppressed reveals do not reserve another voice");
        assertTrue(s.discover("drill",500),"a new reveal at exactly twenty seconds can speak");
        assertFalse(s.discover("ram",1000),"already announced item types remain deduplicated");
    }

    @Test void suppressedRevealsDoNotExtendCooldownOrConsumeAnotherItemType() {
        var s=new RaidRadioState();
        assertTrue(s.discover("ram",0));
        assertFalse(s.discover("docs",399));
        assertTrue(s.discover("docs",400),"suppressed discovery is eligible when searched again");
        assertTrue(s.idle(800),"nothing is queued after cooldown");
    }

    @Test void zoneAndUrgentRadioDropDiscoveryInsteadOfQueuingIt() {
        var s=new RaidRadioState();
        s.observeZone("factory",0);assertTrue(s.observeZone("factory",20));
        assertFalse(s.discover("ram",20),"pending zone voice has priority");
        assertTrue(s.takeZone(20));
        assertFalse(s.discover("ram",21),"playing zone voice has priority");
        assertTrue(s.idle(160));
        assertTrue(s.discover("ram",160),"only a new reveal starts voice after zone ends");
        s.urgent(600,200);
        assertFalse(s.discover("drill",799));
        assertTrue(s.discover("drill",800));
    }

    @Test void cooldownIsPerPlayerAndSurvivesRejoiningSameRaid() {
        var first=new RaidRadioState();var second=new RaidRadioState();
        assertTrue(first.discover("ram",0));
        assertTrue(second.discover("ram",1),"another player has an independent cooldown");
        first.suspend();
        assertEquals(-1,first.frame(200));
        assertFalse(first.discover("drill",399),"leaving does not reset cooldown");
        assertTrue(first.discover("drill",400));
        assertFalse(first.discover("ram",1000),"leaving does not reset item deduplication");
    }

    @Test void expiredZoneNotificationDoesNotBlockNewReveal() {
        var s=new RaidRadioState();
        s.observeZone("factory",0);assertTrue(s.observeZone("factory",20));
        assertTrue(s.discover("docs",180));
        assertFalse(s.takeZone(180));
        assertFalse(new RaidRadioState().discover(null,0));
    }

    @Test void animationStart() {
        assertEquals(-1,RaidRadioState.frameAt(-1));assertEquals(0,RaidRadioState.frameAt(0));
        assertEquals(7,RaidRadioState.frameAt(15));assertEquals(7,RaidRadioState.frameAt(79));
        assertEquals(8,RaidRadioState.frameAt(80));assertEquals(11,RaidRadioState.frameAt(99));
        assertEquals(-1,RaidRadioState.frameAt(100));
    }
}
