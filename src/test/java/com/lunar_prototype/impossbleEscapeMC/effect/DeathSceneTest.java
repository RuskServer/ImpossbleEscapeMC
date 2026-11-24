package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeathSceneTest {
    @Test
    void deathSiteAndLobbyWaitForReadinessWithBoundedFallback() {
        var scene = new DeathScene(null, true);
        assertFalse(scene.locksPlayer());
        scene.enter(DeathScene.Phase.WAIT_FIELD, 101);
        assertTrue(scene.locksPlayer());
        assertFalse(scene.readyAtSite(102, false));
        assertTrue(scene.readyAtSite(102, true));
        assertTrue(scene.readyAtSite(201, false));

        scene.enter(DeathScene.Phase.TRANSFER, 300);
        assertTrue(scene.locksPlayer());
        assertFalse(scene.canRevealLobby(301, false));
        assertFalse(scene.canRevealLobby(313, true));
        assertTrue(scene.canRevealLobby(313, false));
        assertTrue(scene.canRevealLobby(400, true));
        scene.enter(DeathScene.Phase.RETURN, 400);
        assertFalse(scene.locksPlayer());
        assertEquals(6, scene.elapsed(406));
    }

    @Test
    void sameEffectStaysMountedFromFieldThroughTransition() {
        var state = new ScreenEffectState();
        var scene = new DeathScene(null, true);
        state.deathScene = scene;
        assertNull(state.layers(0).playback());
        var effect = Key.key("iem", "death");
        for (var phase : new DeathScene.Phase[]{DeathScene.Phase.FIELD, DeathScene.Phase.TRANSFER, DeathScene.Phase.RETURN}) {
            scene.enter(phase, 72);
            assertEquals(effect, state.layers(72).playback());
        }
        state.discard();
        assertNull(state.layers(72).playback());
    }

    @Test
    void deathClockHasIndependentFieldHoldAndReturnRanges() {
        long base = 48000;
        assertEquals(66500, DeathScene.Signal.FIELD.worldAge(base, 0));
        assertEquals(66596, DeathScene.Signal.FIELD.worldAge(base, DeathScene.FIELD_TICKS));
        assertEquals(66750, DeathScene.Signal.HOLD.worldAge(base, 400));
        assertEquals(66906, DeathScene.Signal.RETURN.worldAge(base, DeathScene.RETURN_TICKS));
        assertTrue(DeathScene.Signal.RETURN.worldAge(base, 500) < base + 20000);
    }
}
