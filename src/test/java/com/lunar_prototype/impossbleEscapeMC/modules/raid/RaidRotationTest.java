package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.ArrayList;
import java.util.List;

/**
 * マップの周期の並び: 等分の枠、ずれた並び (全部が同時に始まる) からも数周で等間隔に戻ること、
 * 1 周期の長さの伸び縮みが上限の中に収まること、3 マップならいつ来てもどこかに途中参加できること。
 * RaidModule の 1 秒ごとの処理 (回転の時刻を進め、残り時間が 0 なら始めて次の長さを決める) と同じ手順で回す
 */
public final class RaidRotationTest {
    private static final int CYCLE = RaidModule.CYCLE_DURATION;
    private static final int LATE_JOIN = RaidModule.LATE_JOIN_WINDOW;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 各マップの開始の時刻 (秒) を回す。timers は始めの残り時間 */
    private static List<List<Integer>> simulate(int[] timers, int seconds) {
        int maps = timers.length;
        int elapsed = 0;
        List<List<Integer>> starts = new ArrayList<>();
        for (int i = 0; i < maps; i++) starts.add(new ArrayList<>());
        for (int t = 1; t <= seconds; t++) {
            elapsed = (elapsed + 1) % CYCLE;
            for (int i = 0; i < maps; i++) {
                if (timers[i] <= 0) {
                    starts.get(i).add(t);
                    timers[i] = RaidRotation.nextLength(CYCLE, maps, i, elapsed);
                } else {
                    timers[i]--;
                }
            }
        }
        return starts;
    }

    private static void checkRotation(int maps) {
        int spacing = RaidRotation.spacing(CYCLE, maps);
        // ずれた並び: 全部が同時に始まる
        int[] timers = new int[maps];
        java.util.Arrays.fill(timers, 1);
        int hours = 3;
        List<List<Integer>> starts = simulate(timers, hours * 3600);

        for (int i = 0; i < maps; i++) {
            List<Integer> s = starts.get(i);
            for (int k = 1; k < s.size(); k++) {
                int length = s.get(k) - s.get(k - 1);
                check(length >= CYCLE - RaidRotation.MAX_ADJUST && length <= CYCLE + RaidRotation.MAX_ADJUST + 1,
                        maps + " maps: cycle length " + length);
            }
        }

        // 最後の 1 時間: どの開始も、一つ前に始まったマップから間隔 ± 2 秒
        List<int[]> all = new ArrayList<>();
        for (int i = 0; i < maps; i++) for (int t : starts.get(i)) all.add(new int[]{t, i});
        all.sort((a, b) -> Integer.compare(a[0], b[0]));
        int from = (hours - 1) * 3600;
        for (int k = 1; k < all.size(); k++) {
            if (all.get(k)[0] < from) continue;
            int gap = all.get(k)[0] - all.get(k - 1)[0];
            check(Math.abs(gap - spacing) <= 2, maps + " maps: gap " + gap + " at " + all.get(k)[0]);
        }

        // 最後の 1 時間で、途中参加の枠 (始まってから LATE_JOIN 秒) にどこかのマップがいない秒の数
        int closed = 0;
        for (int t = from; t < hours * 3600; t++) {
            boolean open = false;
            for (int i = 0; i < maps; i++) {
                int last = -1;
                for (int start : starts.get(i)) if (start <= t) last = start;
                if (last >= 0 && t - last < LATE_JOIN) open = true;
            }
            if (!open) closed++;
        }
        if (spacing < LATE_JOIN) check(closed == 0, maps + " maps: closed seconds " + closed);
        else check(closed > 0, maps + " maps: spacing " + spacing + " should leave gaps with a " + LATE_JOIN + "s window");
        System.out.println(maps + " maps: spacing " + spacing + "s, closed " + closed + "s in the last hour");
    }

    public static void main(String[] args) {
        // ---- 枠 ----
        check(RaidRotation.spacing(CYCLE, 2) == 300 && RaidRotation.spacing(CYCLE, 3) == 200, "spacing");
        check(RaidRotation.slot(CYCLE, 3, 2) == 400, "slot");
        check(RaidRotation.timerOnSlot(CYCLE, 2, 0, 0) == CYCLE, "first map starts now");
        check(RaidRotation.timerOnSlot(CYCLE, 2, 1, 0) == 300, "second map in five minutes");
        check(RaidRotation.timerOnSlot(CYCLE, 3, 2, 100) == 300, "third map");

        // ---- 次の周期の長さ ----
        check(RaidRotation.nextLength(CYCLE, 2, 1, 300) == CYCLE, "on the slot: full cycle");
        check(RaidRotation.nextLength(CYCLE, 2, 1, 290) == CYCLE + 10, "10s early: lengthen");
        check(RaidRotation.nextLength(CYCLE, 2, 1, 330) == CYCLE - 30, "30s late: shorten");
        check(RaidRotation.nextLength(CYCLE, 2, 1, 0) == CYCLE + RaidRotation.MAX_ADJUST, "far off: capped");

        // ---- 回してみる ----
        checkRotation(2);
        checkRotation(3);
        checkRotation(4);
        System.out.println("RaidRotationTest OK");
    }
}
