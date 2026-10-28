package com.lunar_prototype.impossbleEscapeMC.ai;

public final class ScavDecisionStabilityTest {
    public static void main(String[] args) {
        var state = new ScavDecisionStability();
        for (float input : new float[] {0.48f, 0.51f, 0.59f, 0.5f}) {
            check(!state.updateSuppression(input), "minor near misses do not enter suppression");
        }
        check(state.updateSuppression(0.6f), "enter at upper boundary before decay");
        for (float input : new float[] {0.58f, 0.49f, 0.51f, 0.36f}) {
            check(state.updateSuppression(input), "decay and repeated hits do not chatter around old threshold");
        }
        check(!state.updateSuppression(0.35f), "leave at lower boundary");
        check(!state.updateSuppression(0.59f), "reentry needs upper threshold again");

        check(state.movement(0, 100, false) == 0, "start approach");
        for (int tick = 103; tick < 115; tick += 3) {
            check(state.movement(tick % 2 == 0 ? 2 : 3, tick, false) == 0,
                    "changing utility/fear proposals cannot alternate movement every AI step");
        }
        check(state.movement(2, 115, false) == 2, "hold expires after 15 actual ticks");
        check(state.movement(8, 118, true) == 8, "damage/ammo/danger interruption bypasses hold");
        check(state.movement(0, 121, false) == 8, "interrupted action gets a fresh hold");
        state.clearMovement();
        check(state.movement(3, 122, false) == 3, "return from dedicated path does not restore stale action");

        double range = ScavDecisionStability.engagementRange(20, 1.1);
        for (int step = 0; step < 200; step++) {
            check(ScavDecisionStability.engagementRange(20, 1.1) == range,
                    "decision reevaluation or sight loss never rerolls preferred range");
        }
        check(ScavDecisionStability.engagementRange(10, 1.1) == range / 2,
                "weapon change immediately uses new preferred range with same individual trait");
        System.out.println("ScavDecisionStabilityTest OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
