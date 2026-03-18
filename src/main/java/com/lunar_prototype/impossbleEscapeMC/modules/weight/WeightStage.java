package com.lunar_prototype.impossbleEscapeMC.modules.weight;

import net.kyori.adventure.text.format.NamedTextColor;

public enum WeightStage {
    LIGHT("Light", NamedTextColor.GREEN, 8000),
    NORMAL("Normal", NamedTextColor.WHITE, 15000),
    HEAVY("Heavy", NamedTextColor.YELLOW, 35000),
    CRITICAL("Critical", NamedTextColor.RED, Integer.MAX_VALUE);

    private final String displayName;
    private final NamedTextColor color;
    private final int upperLimit;

    WeightStage(String displayName, NamedTextColor color, int upperLimit) {
        this.displayName = displayName;
        this.color = color;
        this.upperLimit = upperLimit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public int getUpperLimit() {
        return upperLimit;
    }

    public float getFootstepVolumeMultiplier(int weightGrams) {
        return switch (this) {
            case LIGHT -> 0.8f; // -20%
            case NORMAL -> 1.0f;
            case HEAVY -> {
                // 15,001〜35,000g: +0% → +50% (linear)
                float ratio = (float) (weightGrams - 15000) / (35000 - 15000);
                yield 1.0f + (ratio * 0.5f);
            }
            case CRITICAL -> 1.5f;
        };
    }

    public double getFootstepAlertDistanceMultiplier(int weightGrams) {
        // base × (1 + weightRatio) where weightRatio is weight / 15,000 (normal limit)
        return 1.0 + ((double) weightGrams / 15000.0);
    }

    public double getStaminaCostMultiplier(int weightGrams) {
        return switch (this) {
            case LIGHT -> 0.8;
            case NORMAL -> 1.0;
            case HEAVY -> {
                // 15,001〜35,000g: 1.2 → 1.8 (linear)
                double ratio = (double) (weightGrams - 15000) / (35000 - 15000);
                yield 1.2 + (ratio * 0.6); // 1.8 - 1.2 = 0.6
            }
            case CRITICAL -> 2.0;
        };
    }

    public long getStaminaRecoveryDelayPenalty(int weightGrams) {
        return switch (this) {
            case LIGHT -> -300L; // -0.3 seconds
            case NORMAL -> 0L;
            case HEAVY -> {
                // 15,001〜35,000g: +500ms → +2000ms (linear)
                double ratio = (double) (weightGrams - 15000) / (35000 - 15000);
                yield (long) (500 + (ratio * 1500)); // 2000 - 500 = 1500
            }
            case CRITICAL -> 3000L; // +3 seconds
        };
    }

    public static WeightStage getFromWeight(int weightGrams) {
        if (weightGrams <= LIGHT.upperLimit) return LIGHT;
        if (weightGrams <= NORMAL.upperLimit) return NORMAL;
        if (weightGrams <= HEAVY.upperLimit) return HEAVY;
        return CRITICAL;
    }
}
