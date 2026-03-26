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
                final int STAMINA_INCREASE_START_WEIGHT = 20000; // 20kg
                final int HEAVY_UPPER_LIMIT = 35000; // 35kg
                final double STAMINA_MULTIPLIER_AT_20KG = 1.0; // Multiplier at 20kg (no penalty yet)
                final double STAMINA_MULTIPLIER_AT_35KG = 1.6; // Multiplier at 35kg (example)

                if (weightGrams <= STAMINA_INCREASE_START_WEIGHT) {
                    yield 1.0; // No penalty up to 20kg within HEAVY stage
                } else {
                    double ratio = (double) (weightGrams - STAMINA_INCREASE_START_WEIGHT) / (HEAVY_UPPER_LIMIT - STAMINA_INCREASE_START_WEIGHT);
                    yield STAMINA_MULTIPLIER_AT_20KG + (ratio * (STAMINA_MULTIPLIER_AT_35KG - STAMINA_MULTIPLIER_AT_20KG));
                }
            }
            case CRITICAL -> 1.5; // (以前は 2.0)
        };
    }

    public long getStaminaRecoveryDelayPenalty(int weightGrams) {
        return switch (this) {
            case LIGHT -> -150L; // -0.15 seconds (以前は -0.3)
            case NORMAL -> 0L;
            case HEAVY -> {
                final int STAMINA_PENALTY_START_WEIGHT = 20000; // 20kg
                final int HEAVY_UPPER_LIMIT = 35000; // 35kg
                final long STAMINA_PENALTY_AT_20KG = 0L; // No penalty up to 20kg
                final long STAMINA_PENALTY_AT_35KG = 1000L; // 1000ms penalty at 35kg (example)

                if (weightGrams <= STAMINA_PENALTY_START_WEIGHT) {
                    yield 0L; // No penalty up to 20kg within HEAVY stage
                } else {
                    double ratio = (double) (weightGrams - STAMINA_PENALTY_START_WEIGHT) / (HEAVY_UPPER_LIMIT - STAMINA_PENALTY_START_WEIGHT);
                    yield (long) (STAMINA_PENALTY_AT_20KG + (ratio * (STAMINA_PENALTY_AT_35KG - STAMINA_PENALTY_AT_20KG)));
                }
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
