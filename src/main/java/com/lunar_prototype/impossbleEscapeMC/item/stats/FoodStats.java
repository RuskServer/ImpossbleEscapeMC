package com.lunar_prototype.impossbleEscapeMC.item.stats;

/** 空腹度・満腹度の回復量と、食べ終わるまでの秒数。 */
public record FoodStats(int nutrition, float saturation, float consumeSeconds) {
    public FoodStats {
        if (nutrition <= 0 || !Float.isFinite(saturation) || saturation < 0
                || !Float.isFinite(consumeSeconds) || consumeSeconds <= 0) {
            throw new IllegalArgumentException("Invalid food stats");
        }
    }
}
