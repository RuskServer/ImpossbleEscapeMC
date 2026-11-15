package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectPlayback.Change.*;

class ScreenEffectPlaybackTest {
    private static Key key(String name) { return Key.key("iem", name); }
    private static ScreenEffectPlayback.Step step(String name, int ticks) { return new ScreenEffectPlayback.Step(name == null ? null : key(name), ticks, false); }
    @Test void waitingDoesNotConsumeTimeAndFirstReadyTickOnlyStarts() {
        var playback = new ScreenEffectPlayback(List.of(step("intro_0", 2), step("intro_1", 1)), true, "intro");
        assertEquals(key("intro_0"), playback.visibleEffect());
        for (int now = 0; now < 400; now++) assertEquals(NONE, playback.advance(now, now, false, 10).change());
        assertEquals(STARTED, playback.advance(400, 400, true, 10).change());
        assertEquals(NONE, playback.advance(401, 401, true, 10).change());
        assertEquals(STEP, playback.advance(402, 402, true, 10).change());
        assertEquals(key("intro_1"), playback.visibleEffect());
        assertEquals(FINISHED, playback.advance(403, 403, true, 10).change());
        assertNull(playback.visibleEffect());
        assertEquals(NONE, playback.advance(404, 404, true, 10).change());
    }
    @Test void activePlaybackPausesAndResumesWithRemainingDuration() {
        var playback = new ScreenEffectPlayback(List.of(step("death_impact", 6)), false, null);
        assertNull(playback.visibleEffect()); playback.begin();
        assertNull(playback.begin(), "The start callback is not emitted twice");
        for (int i = 1; i <= 3; i++) assertEquals(NONE, playback.advance(i, i, true, 10).change());
        for (int i = 4; i < 100; i++) assertEquals(NONE, playback.advance(i, i, false, 10).change());
        assertEquals(NONE, playback.advance(100, 100, true, 10).change());
        assertEquals(NONE, playback.advance(101, 101, true, 10).change());
        assertEquals(FINISHED, playback.advance(102, 102, true, 10).change());
    }
    @Test void adrenalineTimingAndHeartbeatPhaseArePreserved() {
        var playback = new ScreenEffectPlayback(List.of(
                new ScreenEffectPlayback.Step(key("adrenaline_onset"), 4, true),
                new ScreenEffectPlayback.Step(key("adrenaline"), 24, true),
                new ScreenEffectPlayback.Step(key("adrenaline_fade"), 12, true)), false, null);
        playback.begin();
        var stages = new ArrayList<Integer>(); var beats = new ArrayList<Integer>();
        for (int now = 1; now <= 40; now++) {
            var change = playback.advance(now, now + 3, true, 10);
            if (change.heartbeat()) beats.add(now);
            if (change.change() != NONE) stages.add(now);
        }
        assertEquals(List.of(4, 28, 40), stages);
        assertEquals(List.of(7, 17, 27, 37), beats);
        assertEquals(7, playback.recoveryDelay(40, 10));
        assertFalse(playback.heartbeatActive());
    }
    @Test void callbacksAreEmittedAsTransitionsAndNullFinalStepHasItsOwnTick() {
        List<String> callbacks = new ArrayList<>();
        var playback = new ScreenEffectPlayback(List.of(new ScreenEffectPlayback.Step(key("pda_screen"), 2, false, () -> callbacks.add("boot")),
                new ScreenEffectPlayback.Step(null, 1, false, () -> callbacks.add("finish"))), true, "pda_intro");
        var start = playback.advance(0, 0, true, 10);
        assertTrue(callbacks.isEmpty(), "The clock cannot execute side effects"); start.entered().onStart().run();
        assertEquals(NONE, playback.advance(1, 1, true, 10).change());
        var finishStep = playback.advance(2, 2, true, 10); finishStep.entered().onStart().run();
        assertNull(playback.visibleEffect());
        assertEquals("pda_intro", playback.name());
        assertEquals(FINISHED, playback.advance(3, 3, true, 10).change());
        assertEquals(List.of("boot", "finish"), callbacks);
    }
    @Test void replacementAndDiscardCannotInvokeCancelledCompletion() {
        var state = new ScreenEffectState();
        var old = new ScreenEffectPlayback(List.of(step("pda_black", 1), new ScreenEffectPlayback.Step(null, 1, false, () -> fail("Cancelled completion"))), true, "pda");
        state.replacePlayback(old); old.begin(); state.recovery = new AdrenalineHeartbeatRecovery(1);
        var next = new ScreenEffectPlayback(List.of(step("emp_flash", 3)), false, null); state.replacePlayback(next);
        assertNull(state.recovery); assertSame(next, state.playback);
        assertEquals(NONE, old.advance(10, 10, true, 10).change()); assertNull(old.visibleEffect());
        state.discard(); assertNull(state.playback); assertEquals(NONE, next.advance(20, 20, true, 10).change());
    }
    @Test void loadingWaitExpiryAndDuplicateLoadedNotificationKeepPreviousRules() {
        var state = new ScreenEffectState(); state.awaitingLoadSince = 100;
        assertFalse(state.canAdvance(101, false)); assertTrue(state.worldLoaded(150, 10));
        assertFalse(state.worldLoaded(151, 10));
        assertFalse(state.canAdvance(159, false)); assertTrue(state.canAdvance(160, false)); assertFalse(state.canAdvance(160, true));
        state.awaitingLoadSince = 200; state.expireLoadWaits(599, 400); assertFalse(state.canAdvance(599, false));
        state.expireLoadWaits(600, 400); assertTrue(state.canAdvance(600, false));
        state.awaitingRespawn = true; assertFalse(state.canAdvance(601, false));
    }
    @Test void deathResetsCombatLayersAndPreservesIndependentOverlays() {
        var state = new ScreenEffectState(); state.controlDebug = state.menuClear = state.cover = state.graded = state.flashlit = true;
        state.sky = state.flareLight = state.visor = state.wound = key("old"); state.dazzle = 2; state.hitTick = 5;
        state.probe = key("probe"); state.visorWet = 30; state.sentWet = .5f; state.awaitingLoadSince = 6;
        var death = new ScreenEffectPlayback(List.of(step("death_impact", 6)), false, null); state.death(death);
        assertTrue(state.awaitingRespawn); assertFalse(state.graded); assertFalse(state.flashlit);
        assertNull(state.sky); assertNull(state.flareLight); assertNull(state.visor); assertNull(state.wound); assertNull(state.dazzle); assertNull(state.hitTick);
        assertTrue(state.controlDebug && state.menuClear && state.cover); assertEquals(key("probe"), state.probe);
        assertEquals(30, state.visorWet); assertEquals(.5f, state.sentWet); assertEquals(6, state.awaitingLoadSince);
        assertSame(death, state.playback); assertNull(death.visibleEffect());
    }
}
