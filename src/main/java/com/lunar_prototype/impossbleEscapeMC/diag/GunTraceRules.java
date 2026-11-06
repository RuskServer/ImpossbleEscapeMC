package com.lunar_prototype.impossbleEscapeMC.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 銃のアニメーションの異常の判定。サーバーに依存しない純粋なロジック (単体テストできる)。
 * <p>
 * 見る異常:
 * <ul>
 *   <li>frame_negative: フレームが負。第一人称のモデルは range_dispatch の最小しきい値が 0 で fallback が無く、銃が何も描かれなくなる</li>
 *   <li>cmd_missing / cmd_strings_short: custom_model_data が無い・文字列が足りない。パーツを選ぶ select が fallback を持たず、そのパーツが消える</li>
 *   <li>animation_changed_stuck: animation_changed が 2 回続けて 1。データパックは同じ tick の中で 0 に戻すので、途中で処理が止まった</li>
 *   <li>model_not_switched: animation_id が変わったのに、3 tick たっても item_model が変わらない (item_model の書き換えが失敗した)</li>
 *   <li>model_unexpected: animation_id が変わって 3 tick たった item_model が、その ID で前に見たどれとも違う</li>
 *   <li>id_flapping: 10 tick の間に animation_id が 6 回以上変わる (複数のタイマーが競合している)</li>
 *   <li>work_entity_missing: データパックの作業用エンティティが 2 回続けて見つからない (item_model の書き換えが黙って失敗する)</li>
 * </ul>
 * ID ごとの「正しい item_model」は決め打ちにせず、同じ ID・同じモデルが 3 tick 続いた状態を「健全」として覚える。
 */
final class GunTraceRules {
    static final int SETTLE_TICKS = 3;
    static final int FLAP_WINDOW = 10;
    static final int FLAP_COUNT = 6;

    private GunTraceRules() {
    }

    /** 1 人のプレイヤーの、1 丁の銃についての状態。銃が変わったら作り直す */
    static final class State {
        private final Map<Integer, Set<String>> observed = new HashMap<>();
        private final Deque<Long> changeTicks = new ArrayDeque<>();
        private GunTraceSample previous;
        private String lastGun;
        private int stableRun;
        private int pendingFrom = -1;
        private int pendingTo = -1;
        private long pendingTick;
        private String modelBefore;
        private int changedRun;
        private int missingRun;

        /** この tick の状態を渡す。異常があれば、その名前 (詳細つき) を返す */
        List<String> accept(GunTraceSample s) {
            List<String> out = new ArrayList<>();
            if (!Objects.equals(lastGun, s.gunId())) reset(s.gunId());

            if (s.hasFrame() && s.frame() < 0) out.add("frame_negative(" + s.frame() + ")");
            if (!s.hasFrame()) out.add("cmd_missing");
            else if (s.strings() < GunTraceSample.EXPECTED_STRINGS) out.add("cmd_strings_short(" + s.strings() + ")");

            changedRun = s.animationChanged() == 1 ? changedRun + 1 : 0;
            if (changedRun >= 2) out.add("animation_changed_stuck(id=" + s.animationId() + ")");

            missingRun = s.workWorld() == null ? missingRun + 1 : 0;
            if (missingRun >= 2) out.add("work_entity_missing(" + missingRun + " ticks)");

            if (previous != null && s.animationId() != previous.animationId()) {
                pendingFrom = previous.animationId();
                pendingTo = s.animationId();
                pendingTick = s.tick();
                modelBefore = previous.model();
                changeTicks.addLast(s.tick());
                stableRun = 0;
            } else if (previous != null && Objects.equals(s.model(), previous.model())) {
                stableRun++;
            } else {
                stableRun = 0;
            }
            while (!changeTicks.isEmpty() && s.tick() - changeTicks.peekFirst() > FLAP_WINDOW) changeTicks.removeFirst();
            if (changeTicks.size() >= FLAP_COUNT) out.add("id_flapping(" + changeTicks.size() + " changes/" + FLAP_WINDOW + " ticks)");

            if (pendingTo >= 0 && s.tick() - pendingTick >= SETTLE_TICKS) {
                if (s.animationId() == pendingTo) out.addAll(checkSwitch(s));
                pendingTo = -1;
            }
            // 健全な状態を覚える: 同じ ID で、同じモデルが続いた
            if (stableRun >= SETTLE_TICKS && s.model() != null) {
                observed.computeIfAbsent(s.animationId(), k -> new HashSet<>()).add(s.model());
            }
            previous = s;
            return out;
        }

        private List<String> checkSwitch(GunTraceSample s) {
            List<String> out = new ArrayList<>();
            Set<String> expected = observed.get(pendingTo);
            String arrow = "id " + pendingFrom + "->" + pendingTo + " model=" + (s.model() == null ? "-" : GunTraceSample.shortModel(s.model()));
            boolean unchanged = Objects.equals(s.model(), modelBefore);
            if (expected != null && !expected.isEmpty()) {
                if (!expected.contains(s.model())) {
                    out.add((unchanged ? "model_not_switched(" : "model_unexpected(") + arrow + ")");
                }
            } else if (unchanged) {
                // その ID のモデルをまだ見ていない: 2 つの状態が同じモデルを使う場合もあるので、確かではない
                out.add("model_not_switched?(" + arrow + ", unlearned)");
            }
            return out;
        }

        private void reset(String gunId) {
            lastGun = gunId;
            previous = null;
            stableRun = 0;
            pendingTo = -1;
            changeTicks.clear();
            changedRun = 0;
            missingRun = 0;
            // observed は銃ごとに別 (モデルの名前が違う) なので捨てる
            observed.clear();
        }
    }
}
