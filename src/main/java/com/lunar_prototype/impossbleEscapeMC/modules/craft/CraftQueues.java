package com.lunar_prototype.impossbleEscapeMC.modules.craft;

import java.util.List;

/**
 * 製作のキューの時間の決まり (サーバーなしで確かめられる。CraftQueuesTest)。
 * <ul>
 *   <li>製作は 1 つずつ順に進む。入れた製作は、前の製作が終わった時 (前が無い・もう終わっていれば今) から始まる</li>
 *   <li>完成した物は受け取るまでキューに残り、上限 ({@link #limit}) に数える (完成の後の製作は待たずに進む)</li>
 *   <li>取り消すと、その後ろの製作は前に詰める (製作中の物はそのまま)</li>
 * </ul>
 */
public final class CraftQueues {

    enum State { CRAFTING, WAITING, DONE }

    private CraftQueues() {
    }

    /** キューの上限: 設備のレベルと同じ数 (Lv.1 で 1)。仮の値 */
    public static int limit(int facilityLevel) {
        return Math.max(1, facilityLevel);
    }

    static State state(CraftJob job, long now) {
        if (now >= job.getEndMillis()) return State.DONE;
        return now >= job.getEndMillis() - job.getDurationMillis() ? State.CRAFTING : State.WAITING;
    }

    /** 進み具合 (0..1) */
    static double progress(CraftJob job, long now) {
        long start = job.getEndMillis() - job.getDurationMillis();
        if (now <= start) return 0.0;
        if (now >= job.getEndMillis()) return 1.0;
        return (double) (now - start) / job.getDurationMillis();
    }

    /** 後ろに入れる (前の製作が終わってから始まる)。入れた製作 */
    static CraftJob enqueue(List<CraftJob> queue, String recipeId, long durationMillis, long now) {
        long start = now;
        for (CraftJob job : queue) start = Math.max(start, job.getEndMillis());
        CraftJob job = new CraftJob(recipeId, durationMillis, start + durationMillis);
        queue.add(job);
        return job;
    }

    /** 取り消す (完成した物は取り消せない)。後ろのまだ始まっていない製作を前に詰める。取り消した製作 (できなければ null) */
    static CraftJob cancel(List<CraftJob> queue, int index, long now) {
        if (index < 0 || index >= queue.size() || state(queue.get(index), now) == State.DONE) return null;
        CraftJob removed = queue.remove(index);
        long previousEnd = now;
        for (int i = 0; i < queue.size(); i++) {
            CraftJob job = queue.get(i);
            if (i >= index && state(job, now) == State.WAITING) {
                job.setEndMillis(Math.max(now, previousEnd) + job.getDurationMillis());
            }
            previousEnd = Math.max(previousEnd, job.getEndMillis());
        }
        return removed;
    }

    /** 待ちの順番 (製作中の次が 1)。待ちでなければ 0 */
    static int order(List<CraftJob> queue, int index, long now) {
        if (state(queue.get(index), now) != State.WAITING) return 0;
        int order = 0;
        for (int i = 0; i <= index; i++) {
            if (state(queue.get(i), now) == State.WAITING) order++;
        }
        return order;
    }
}
