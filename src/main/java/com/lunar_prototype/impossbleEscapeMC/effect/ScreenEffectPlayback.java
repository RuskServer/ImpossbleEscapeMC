package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;
import java.util.List;

/** 演出1本の時計。音・コールバック・送信は呼び出し側が遷移結果に応じて実行する。 */
final class ScreenEffectPlayback {
    record Step(Key effect, int ticks, boolean heartbeat, Runnable onStart) {
        Step {
            if (ticks < 1) throw new IllegalArgumentException("Step duration must be positive");
        }
        Step(Key effect, int ticks, boolean heartbeat) { this(effect, ticks, heartbeat, null); }
    }
    enum Change { NONE, STARTED, STEP, FINISHED }
    record Advance(Change change, Step entered, boolean heartbeat) {
        static final Advance IDLE = new Advance(Change.NONE, null, false);
    }

    private final List<Step> steps;
    private final boolean showWhileWaiting;
    private final String name;
    private int index;
    private int remaining;
    private boolean started;
    private boolean finished;
    private int lastHeartbeatTick = Integer.MIN_VALUE;

    ScreenEffectPlayback(List<Step> steps, boolean showWhileWaiting, String name) {
        this.steps = List.copyOf(steps);
        if (this.steps.isEmpty()) throw new IllegalArgumentException("Playback requires steps");
        this.showWhileWaiting = showWhileWaiting;
        this.name = name;
        remaining = this.steps.getFirst().ticks();
    }
    String name() { return name; }
    Step current() { return steps.get(index); }
    Key visibleEffect() { return !finished && (started || showWhileWaiting) ? current().effect() : null; }
    boolean heartbeatActive() { return !finished && started && current().heartbeat(); }
    Step begin() {
        if (started || finished) return null;
        started = true;
        return current();
    }
    Advance advance(int now, long worldTick, boolean ready, int heartbeatTicks) {
        if (!ready || finished) return Advance.IDLE;
        if (!started) return new Advance(Change.STARTED, begin(), false);
        boolean heartbeat = current().heartbeat() && worldTick % heartbeatTicks == 0;
        if (heartbeat) lastHeartbeatTick = now;
        if (--remaining > 0) return new Advance(Change.NONE, null, heartbeat);
        if (index + 1 == steps.size()) {
            finished = true;
            return new Advance(Change.FINISHED, null, heartbeat);
        }
        index++;
        remaining = current().ticks();
        return new Advance(Change.STEP, current(), heartbeat);
    }
    int recoveryDelay(int now, int heartbeatTicks) {
        return lastHeartbeatTick == Integer.MIN_VALUE ? heartbeatTicks : Math.max(1, heartbeatTicks - (now - lastHeartbeatTick));
    }
    /** 置換・退出では残りの段や完了コールバックを実行しない。 */
    void cancel() { finished = true; }
}
