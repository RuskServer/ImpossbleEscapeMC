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
            case LIGHT -> 0.9f; // -10% (以前は -20%)
            case NORMAL -> 1.0f;
            case HEAVY -> {
                // 15,001〜35,000g: +0% → +25% (以前は +50%)
                float ratio = (float) (weightGrams - 15000) / (35000 - 15000);
                yield 1.0f + (ratio * 0.25f);
            }
            case CRITICAL -> 1.25f; // (以前は 1.5)
        };
    }

    public double getFootstepAlertDistanceMultiplier(int weightGrams) {
        // base × (1 + weightRatio) where weightRatio is weight / 30,000 (以前は 15,000)
        return 1.0 + ((double) weightGrams / 30000.0);
    }

    public double getStaminaCostMultiplier(int weightGrams) {
        return switch (this) {
            case LIGHT -> 0.9; // (以前は 0.8)
            case NORMAL -> 1.0;
            case HEAVY -> {
                // 15,001〜35,000g: 1.1 → 1.4 (linear) (以前は 1.2 → 1.8)
                double ratio = (double) (weightGrams - 15000) / (35000 - 15000);
                yield 1.1 + (ratio * 0.3); // 1.4 - 1.1 = 0.3
            }
            case CRITICAL -> 1.5; // (以前は 2.0)
        };
    }

    public long getStaminaRecoveryDelayPenalty(int weightGrams) {
        return switch (this) {
            case LIGHT -> -150L; // -0.15 seconds (以前は -0.3)
            case NORMAL -> 0L;
            case HEAVY -> {
                // 15,001〜35,000g: +250ms → +1000ms (linear) (以前は +500 → +2000)
                double ratio = (double) (weightGrams - 15000) / (35000 - 15000);
                yield (long) (250 + (ratio * 750)); 
            }
            case CRITICAL -> 1500L; // +1.5 seconds (以前は +3.0)
        };
    }

    public static WeightStage getFromWeight(int weightGrams) {
        if (weightGrams <= LIGHT.upperLimit) return LIGHT;
        if (weightGrams <= NORMAL.upperLimit) return NORMAL;
        if (weightGrams <= HEAVY.upperLimit) return HEAVY;
        return CRITICAL;
    }
}
