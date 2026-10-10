package com.lunar_prototype.impossbleEscapeMC.ai;

/** Priority in comparable units, with hysteresis to prevent responders oscillating. */
final class ScavHelpPolicy {
    static double priority(double healthFraction, double suppression, boolean recentlyHit,
                           boolean recentlySeen, int supporters, double distance) {
        return 60 * (1 - Math.max(0, Math.min(1, healthFraction)))
                + 35 * Math.max(0, Math.min(1, suppression)) + (recentlyHit ? 25 : 0)
                + (recentlySeen ? 15 : 0) - Math.max(0, supporters) * 8 - Math.max(0, distance) * 1.2;
    }
    static boolean shouldSwitch(double current, double offered, int heldTicks, boolean currentActive) {
        return !currentActive || (heldTicks >= 40 && offered > current + 20);
    }
}
