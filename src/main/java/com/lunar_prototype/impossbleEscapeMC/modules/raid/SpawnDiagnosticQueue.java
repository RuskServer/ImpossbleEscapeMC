package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 未完了の探索を1操作ずつ交代する。同時保持数を制限して探索ノードの増大も防ぐ。 */
final class SpawnDiagnosticQueue<T> {
    private final ArrayDeque<T> pending;
    private final ArrayDeque<T> active = new ArrayDeque<>();
    private final int parallel;

    SpawnDiagnosticQueue(Collection<T> tasks, int parallel) {
        if (parallel < 1) throw new IllegalArgumentException("parallel must be positive");
        this.pending = new ArrayDeque<>(tasks);
        this.parallel = parallel;
    }

    T next() {
        while (active.size() < parallel && !pending.isEmpty()) active.addLast(pending.removeFirst());
        if (active.isEmpty()) return null;
        T task = active.removeFirst();
        active.addLast(task);
        return task;
    }

    void complete(T task) {
        if (!active.removeFirstOccurrence(task)) throw new IllegalStateException("task is not active");
    }

    List<T> remaining() {
        List<T> result = new ArrayList<>(active);
        result.addAll(pending);
        return List.copyOf(result);
    }
}
