package com.lunar_prototype.impossbleEscapeMC.modules.craft;

import java.util.ArrayList;
import java.util.List;

/**
 * 製作のキューの時間: 順に進む・完成した物は残っても後ろを止めない・取り消すと後ろが前に詰まる・上限はレベルと同じ数。
 * ログアウト中も進む (時刻は実時間で持つ) ので、時刻を進めて確かめる
 */
public final class CraftQueuesTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        long t = 1_000_000L;

        // ---- 上限 ----
        check(CraftQueues.limit(0) == 1 && CraftQueues.limit(1) == 1 && CraftQueues.limit(3) == 3, "limit");

        // ---- 順に進む ----
        List<CraftJob> queue = new ArrayList<>();
        CraftJob a = CraftQueues.enqueue(queue, "a", 60_000, t);
        CraftJob b = CraftQueues.enqueue(queue, "b", 30_000, t + 10_000);
        CraftJob c = CraftQueues.enqueue(queue, "c", 20_000, t + 20_000);
        check(a.getEndMillis() == t + 60_000, "a ends after its own time");
        check(b.getEndMillis() == t + 90_000, "b starts when a ends: " + b.getEndMillis());
        check(c.getEndMillis() == t + 110_000, "c starts when b ends: " + c.getEndMillis());
        long now = t + 30_000;
        check(CraftQueues.state(a, now) == CraftQueues.State.CRAFTING, "a crafting");
        check(CraftQueues.state(b, now) == CraftQueues.State.WAITING && CraftQueues.state(c, now) == CraftQueues.State.WAITING, "b c waiting");
        check(Math.abs(CraftQueues.progress(a, now) - 0.5) < 1e-9, "a half done");
        check(CraftQueues.progress(b, now) == 0.0, "b not started");
        check(CraftQueues.order(queue, 0, now) == 0 && CraftQueues.order(queue, 1, now) == 1 && CraftQueues.order(queue, 2, now) == 2, "order");

        // ---- ログアウト中に全部終わる ----
        long later = t + 3_600_000;
        for (CraftJob job : queue) check(CraftQueues.state(job, later) == CraftQueues.State.DONE, "all done later");
        check(CraftQueues.progress(c, later) == 1.0, "c full");

        // ---- 完成した物は残っていても、後から入れた製作は待たない ----
        List<CraftJob> withDone = new ArrayList<>();
        CraftQueues.enqueue(withDone, "done", 10_000, t);
        CraftJob fresh = CraftQueues.enqueue(withDone, "fresh", 10_000, t + 50_000);
        check(fresh.getEndMillis() == t + 60_000, "fresh starts now: " + fresh.getEndMillis());
        check(CraftQueues.state(fresh, t + 50_000) == CraftQueues.State.CRAFTING, "fresh crafting");

        // ---- 完成した物は取り消せない ----
        check(CraftQueues.cancel(withDone, 0, t + 50_000) == null && withDone.size() == 2, "done cannot be cancelled");
        check(CraftQueues.cancel(withDone, 5, t) == null && CraftQueues.cancel(withDone, -1, t) == null, "bad index");

        // ---- 製作中を取り消す: 待ちが今から始まる ----
        List<CraftJob> q2 = new ArrayList<>();
        CraftQueues.enqueue(q2, "x", 60_000, t);
        CraftJob y = CraftQueues.enqueue(q2, "y", 30_000, t);
        CraftJob z = CraftQueues.enqueue(q2, "z", 20_000, t);
        now = t + 40_000;
        check(CraftQueues.cancel(q2, 0, now).getRecipeId().equals("x"), "cancel x");
        check(y.getEndMillis() == now + 30_000, "y starts now: " + y.getEndMillis());
        check(z.getEndMillis() == now + 50_000, "z after y: " + z.getEndMillis());
        check(CraftQueues.state(y, now) == CraftQueues.State.CRAFTING, "y crafting");

        // ---- 待ちを取り消す: 製作中はそのまま、後ろは詰まる ----
        List<CraftJob> q3 = new ArrayList<>();
        CraftJob p = CraftQueues.enqueue(q3, "p", 60_000, t);
        CraftQueues.enqueue(q3, "q", 30_000, t);
        CraftJob r = CraftQueues.enqueue(q3, "r", 20_000, t);
        now = t + 10_000;
        CraftQueues.cancel(q3, 1, now);
        check(p.getEndMillis() == t + 60_000, "p unchanged");
        check(r.getEndMillis() == t + 80_000, "r right after p: " + r.getEndMillis());
        check(CraftQueues.order(q3, 1, now) == 1, "r is next");

        System.out.println("CraftQueuesTest OK");
    }
}
