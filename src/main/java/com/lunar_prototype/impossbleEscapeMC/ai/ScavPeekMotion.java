package com.lunar_prototype.impossbleEscapeMC.ai;

/** 顔出し専用のワールド方向入力。速度や座標を直接書かず、地上移動の加速・摩擦はMobに任せる。 */
final class ScavPeekMotion {
    static final double MAX_INPUT = .35;
    static final double INPUT_CHANGE = .06;
    static final double STOP_RADIUS = .18;
    private double x, z;
    record Input(double x, double z) { }

    Input step(double dx, double dz) {
        double distance = Math.hypot(dx, dz);
        // 角から出切る前に入力を弱め、切り返しも前の入力から連続的に変える。
        double speed = MAX_INPUT * Math.clamp((distance - STOP_RADIUS) / .7, 0, 1);
        double desiredX = distance > 1e-6 ? dx / distance * speed : 0;
        double desiredZ = distance > 1e-6 ? dz / distance * speed : 0;
        double change = Math.hypot(desiredX - x, desiredZ - z);
        double ratio = change > INPUT_CHANGE ? INPUT_CHANGE / change : 1;
        x += (desiredX - x) * ratio;
        z += (desiredZ - z) * ratio;
        return new Input(x, z);
    }

    boolean stopped() { return Math.hypot(x, z) < .005; }
    void clear() { x = z = 0; }

    /** 最大3ブロック程度の出入りを想定。低速での接近と入力の立ち上がりにも猶予を置く。 */
    static int travelSteps(double distance) {
        return (int)Math.clamp(Math.ceil((distance / .06 + 12) / ScavController.STEP_TICKS), 6, 36);
    }
}
