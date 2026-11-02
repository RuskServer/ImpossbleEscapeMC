package com.lunar_prototype.impossbleEscapeMC.ai;

/** 戦闘横移動とピークの共通上限。入力倍率ではなく、移動属性を掛けた後の移動指示量で制御する。 */
final class ScavCombatMotion {
    // 標準的な地上横移動で、無負荷のプレイヤー歩行より遅い基準を使う。
    static final double MAX_SPEED = .08;
    static final double CHANGE_PER_TICK = .005;
    private double x, z;
    private int lastTick = Integer.MIN_VALUE;
    record Input(double x, double z) { }

    Input step(double requestedX, double requestedZ, double attribute, int tick) {
        if (!Double.isFinite(attribute) || attribute < 1e-6 || !Double.isFinite(requestedX) || !Double.isFinite(requestedZ)) {
            clear();return new Input(0, 0);
        }
        // 判断更新からの即時指示と毎tick更新が同じtickに重なっても二重に加速しない。
        if (tick == lastTick) return new Input(x / attribute, z / attribute);
        lastTick = tick;
        double desiredX = requestedX * attribute, desiredZ = requestedZ * attribute;
        double length = Math.hypot(desiredX, desiredZ);
        if (!Double.isFinite(length)) {clear();return new Input(0, 0);}
        if (length > MAX_SPEED) {desiredX *= MAX_SPEED / length;desiredZ *= MAX_SPEED / length;}
        double change = Math.hypot(desiredX - x, desiredZ - z);
        double ratio = change > CHANGE_PER_TICK ? CHANGE_PER_TICK / change : 1;
        x += (desiredX - x) * ratio;z += (desiredZ - z) * ratio;
        // MoveControl側で再び属性が掛かるので、現在の属性に対する倍率へ戻す。
        return new Input(x / attribute, z / attribute);
    }

    boolean stopped() {return Math.hypot(x,z) < .00075;}

    void clear() {x = z = 0;lastTick = Integer.MIN_VALUE;}
}
