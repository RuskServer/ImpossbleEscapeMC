package com.lunar_prototype.impossbleEscapeMC.ai;

/** 入力は平滑化せず、制圧の状態遷移と通常の移動指示だけを安定させる。 */
final class ScavDecisionStability {
    static final int MOVE_HOLD_TICKS = 15;
    private boolean suppressed;
    private int movement = -1;
    private int movementUntil;

    boolean updateSuppression(float value) {
        if (value >= 0.6f) suppressed = true;
        else if (value <= 0.35f) suppressed = false;
        return suppressed;
    }

    boolean suppressed() { return suppressed; }

    int movement(int proposed, int tick, boolean interrupt) {
        if (movement < 0 || interrupt || tick >= movementUntil) {
            movement = proposed;
            movementUntil = tick + MOVE_HOLD_TICKS;
        }
        return movement;
    }

    void clearMovement() { movement = -1; }

    // 個体差を残しつつ、判断周期や視認の途切れで距離を引き直さない。
    static double engagementRange(double preferredRange, double trait) {
        return preferredRange * trait;
    }
}
