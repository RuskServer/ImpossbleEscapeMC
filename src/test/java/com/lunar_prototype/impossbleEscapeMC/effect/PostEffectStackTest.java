package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PostEffectStackTest {
    private static Key key(String name) { return Key.key("iem", name); }
    private static List<String> names(List<Key> keys) { return keys.stream().map(Key::asString).toList(); }
    @Test void allLayersKeepExistingOrderAndExternalEffects() {
        var state = new ScreenEffectState();
        state.controlDebug = state.flashlit = state.graded = state.menuClear = state.cover = true;
        state.sky = key("emp_precursor_3"); state.flareLight = key("flare_light_4"); state.visor = key("visor_rain_2");
        state.dazzle = 2; state.wound = key("bleeding"); state.probe = key("probe"); state.hitTick = 100;
        state.playback = new ScreenEffectPlayback(List.of(new ScreenEffectPlayback.Step(key("adrenaline"), 10, true)), true, null);
        List<Key> current = List.of(Key.key("other", "one"), key("old"), Key.key("other", "two"));
        assertEquals(List.of("other:one", "other:two", "iem:control_debug", "iem:emp_precursor_3", "iem:flare_light_4", "iem:flashlight",
                "iem:grade_visor_rain_2", "iem:dazzle_2", "iem:bleeding", "iem:adrenaline", "iem:hit", "iem:menu_clear", "iem:probe", "iem:intro_0"),
                names(PostEffectStack.compose(current, state.layers(100))));
        assertEquals(List.of(Key.key("other", "one"), key("old"), Key.key("other", "two")), current, "Composition never mutates the current stack");
    }
    @Test void gradeAndControlAreExclusiveAndDazzleRequiresGrade() {
        var state = new ScreenEffectState(); state.visor = key("night_vision_full"); state.dazzle = 3;
        assertEquals(List.of("iem:control"), names(PostEffectStack.compose(List.of(), state.layers(0))));
        state.graded = true;
        assertEquals(List.of("iem:grade_night_vision_full", "iem:dazzle_3"), names(PostEffectStack.compose(List.of(), state.layers(0))));
        state.visor = null;
        assertEquals(List.of("iem:grade", "iem:dazzle_3"), names(PostEffectStack.compose(List.of(), state.layers(0))));
    }
    @Test void hitBoundaryAndWaitingPlaybackVisibilityRemainSeparate() {
        var state = new ScreenEffectState(); state.hitTick = 20;
        state.playback = new ScreenEffectPlayback(List.of(new ScreenEffectPlayback.Step(key("death_impact"), 6, false)), false, null);
        assertEquals(List.of("iem:control", "iem:hit"), names(PostEffectStack.compose(List.of(), state.layers(21))));
        assertEquals(List.of("iem:control", "iem:hit_fade"), names(PostEffectStack.compose(List.of(), state.layers(22))));
        state.playback.begin();
        assertEquals(List.of("iem:control", "iem:death_impact", "iem:hit_fade"), names(PostEffectStack.compose(List.of(), state.layers(23))));
    }
    @Test void coverAndIntroDuplicateAreRetainedAndOutputIsImmutable() {
        var state = new ScreenEffectState(); state.cover = true;
        state.playback = new ScreenEffectPlayback(List.of(new ScreenEffectPlayback.Step(key("intro_0"), 20, false)), true, null);
        var stack = PostEffectStack.compose(List.of(), state.layers(0));
        assertEquals(List.of("iem:control", "iem:intro_0", "iem:intro_0"), names(stack));
        assertThrows(UnsupportedOperationException.class, () -> stack.add(key("extra")));
    }
}
