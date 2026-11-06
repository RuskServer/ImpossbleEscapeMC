package com.lunar_prototype.impossbleEscapeMC.diag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 銃のアニメーションの異常の判定。健全な遷移では何も出さず、決めた異常だけを出す。 */
class GunTraceRulesTests {
    private static final String IDLE = "toisarm:gun/ak74/idle";
    private static final String ADS = "toisarm:gun/ak74/ads";
    private static final String RELOAD = "toisarm:gun/ak74/reload";

    private GunTraceRules.State state;
    private long tick;

    @BeforeEach
    void setUp() {
        state = new GunTraceRules.State();
        tick = 1000;
    }

    private GunTraceSample sample(String gun, int id, int changed, float frame, int strings, String model, String world) {
        return new GunTraceSample(tick++, gun, id, changed, frame, strings, model, new int[]{-1, -1, -1, -1, -1, -1, -1, -1}, world, 2, null);
    }

    private GunTraceSample ok(int id, String model) {
        return sample("ak74", id, 0, 0f, 12, model, "world");
    }

    private List<String> feed(int count, int id, String model) {
        List<String> all = new ArrayList<>();
        for (int i = 0; i < count; i++) all.addAll(state.accept(ok(id, model)));
        return all;
    }

    @Test
    void healthyTransitionsReportNothing() {
        assertTrue(feed(5, 0, IDLE).isEmpty());
        // idle -> ads -> idle -> reload -> idle。モデルは ID に合わせて切り替わる
        assertTrue(feed(5, 1, ADS).isEmpty());
        assertTrue(feed(5, 0, IDLE).isEmpty());
        assertTrue(feed(8, 5, RELOAD).isEmpty());
        assertTrue(feed(5, 0, IDLE).isEmpty());
    }

    @Test
    void negativeFrameIsReported() {
        feed(4, 0, IDLE);
        List<String> found = state.accept(sample("ak74", 8, 0, -3f, 12, IDLE, "world"));
        assertTrue(found.stream().anyMatch(s -> s.startsWith("frame_negative")), found.toString());
    }

    @Test
    void missingOrShortCustomModelDataIsReported() {
        feed(4, 0, IDLE);
        assertTrue(state.accept(sample("ak74", 0, 0, Float.NaN, 0, IDLE, "world")).contains("cmd_missing"));
        assertTrue(state.accept(sample("ak74", 0, 0, 0f, 5, IDLE, "world")).stream().anyMatch(s -> s.startsWith("cmd_strings_short")));
    }

    @Test
    void modelThatDidNotSwitchAfterAnIdChangeIsReported() {
        feed(5, 0, IDLE);
        feed(5, 1, ADS);              // ads のモデルは ADS だと覚える
        feed(5, 0, IDLE);
        // ads にしたのに、item_model が idle のまま (専用エンティティでの書き換えが失敗した形)
        List<String> found = feed(5, 1, IDLE);
        assertTrue(found.stream().anyMatch(s -> s.startsWith("model_not_switched(")), found.toString());
    }

    @Test
    void anUnlearnedStateWithUnchangedModelIsOnlyAWeakReport() {
        feed(5, 0, IDLE);
        List<String> found = feed(5, 1, IDLE);   // ads のモデルをまだ見ていない
        assertTrue(found.stream().anyMatch(s -> s.startsWith("model_not_switched?(")), found.toString());
        assertFalse(found.stream().anyMatch(s -> s.startsWith("model_not_switched(")), found.toString());
    }

    @Test
    void anUnexpectedModelForALearnedIdIsReported() {
        feed(5, 0, IDLE);
        feed(5, 1, ADS);
        feed(5, 0, IDLE);
        List<String> found = feed(5, 1, RELOAD);  // ads のはずが reload のモデル
        assertTrue(found.stream().anyMatch(s -> s.startsWith("model_unexpected(")), found.toString());
    }

    @Test
    void animationChangedStuckNeedsTwoInARow() {
        feed(4, 0, IDLE);
        assertFalse(state.accept(sample("ak74", 0, 1, 0f, 12, IDLE, "world")).stream().anyMatch(s -> s.startsWith("animation_changed_stuck")));
        assertTrue(state.accept(sample("ak74", 0, 1, 0f, 12, IDLE, "world")).stream().anyMatch(s -> s.startsWith("animation_changed_stuck")));
        // 0 に戻れば数え直し
        state.accept(sample("ak74", 0, 0, 0f, 12, IDLE, "world"));
        assertFalse(state.accept(sample("ak74", 0, 1, 0f, 12, IDLE, "world")).stream().anyMatch(s -> s.startsWith("animation_changed_stuck")));
    }

    @Test
    void workEntityMissingNeedsTwoInARow() {
        feed(4, 0, IDLE);
        assertFalse(state.accept(sample("ak74", 0, 0, 0f, 12, IDLE, null)).stream().anyMatch(s -> s.startsWith("work_entity_missing")));
        assertTrue(state.accept(sample("ak74", 0, 0, 0f, 12, IDLE, null)).stream().anyMatch(s -> s.startsWith("work_entity_missing")));
    }

    @Test
    void rapidIdChangesAreReportedAsFlapping() {
        feed(3, 0, IDLE);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < 8; i++) found.addAll(state.accept(ok(i % 2 == 0 ? 0 : 2, i % 2 == 0 ? IDLE : "toisarm:gun/ak74/bolt_action")));
        assertTrue(found.stream().anyMatch(s -> s.startsWith("id_flapping")), found.toString());
    }

    @Test
    void switchingGunsResetsWhatWasLearned() {
        feed(5, 0, IDLE);
        feed(5, 1, ADS);
        feed(5, 0, IDLE);
        // 別の銃に持ち替えた直後は、前の銃の学習を引き継がない (モデル名が違うので誤検出になる)
        List<String> found = new ArrayList<>();
        for (int i = 0; i < 6; i++) found.addAll(state.accept(sample("m4a1", 1, 0, 0f, 12, "toisarm:gun/m4a1/ads", "world")));
        assertEquals(List.of(), found);
    }

    @Test
    void shortModelKeepsOnlyTheStateName() {
        assertEquals("idle", GunTraceSample.shortModel("toisarm:gun/ak74/idle"));
        assertEquals("plain", GunTraceSample.shortModel("plain"));
    }
}
