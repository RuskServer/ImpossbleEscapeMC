package com.lunar_prototype.impossbleEscapeMC.modules.movement;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;

/** プレイヤー・SCAV・移動診断の共通倍率。調整はこの値だけを変更する。スタミナ消費には使わない。 */
public final class MovementTuning {
    public static final double MULTIPLIER = 0.8;
    private static final NamespacedKey KEY = new NamespacedKey("iem", "global_movement_speed");
    static {
        if (!Double.isFinite(MULTIPLIER) || MULTIPLIER <= 0) throw new IllegalStateException("Movement multiplier must be positive and finite");
    }
    private MovementTuning() { }

    public static double scale(double base) {return base * MULTIPLIER;}

    /** 重量・疲労などを合算した後に独立して乗算し、元の基本値や他の補正を上書きしない。 */
    public static void applyTo(AttributeInstance speed) {
        if (speed == null) return;
        var current = speed.getModifier(KEY);
        double amount = MULTIPLIER - 1;
        if (current != null && Double.compare(current.getAmount(), amount) == 0
                && current.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) return;
        speed.removeModifier(KEY);
        if (amount != 0) speed.addModifier(new AttributeModifier(KEY, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    public static void removeFrom(AttributeInstance speed) {
        if (speed != null) speed.removeModifier(KEY);
    }
}
