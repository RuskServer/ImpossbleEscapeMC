package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RaidBossAlertStateTest {
    @Test void startsAfterTitleAndOnlyOnceRegardlessOfLaterTicks() {
        var s=new RaidBossAlertState("Voronov",100,false);
        assertFalse(s.start(179,true));assertFalse(s.start(180,false));assertTrue(s.start(181,true));
        assertEquals(0,s.frame(181));assertEquals(211,s.frame(392));assertEquals(-1,s.frame(393));
        assertFalse(s.pending(393));assertFalse(s.start(5000,true));assertEquals("Voronov",s.name());
    }
    @Test void cancellationBeforeStartOrDuringPlaybackNeverReplays() {
        var pending=new RaidBossAlertState("Boss",0,false);pending.cancel();assertFalse(pending.start(100,true));assertFalse(pending.pending(100));
        var playing=new RaidBossAlertState("Boss",0,true);assertTrue(playing.start(0,true));playing.cancel();
        assertEquals(-1,playing.frame(1));assertFalse(playing.start(900,true));
    }
    @Test void endSequenceCanReserveRadioBeforeBossPlaybackBegins() {
        var radio=new RaidRadioState();var boss=new RaidBossAlertState("Boss",0,false);
        radio.urgent(10,200);assertFalse(boss.start(80,radio.idle(80)));
        boss.cancel();assertFalse(boss.start(210,radio.idle(210)));
    }
    @Test void previewHasNoEntryDelayButStillUsesNormalBusyGuard() {
        var s=new RaidBossAlertState("Boss",50,true);assertFalse(s.start(50,false));assertTrue(s.start(50,true));
        assertEquals(-1,s.frame(49));assertTrue(s.pending(51));
    }
}
