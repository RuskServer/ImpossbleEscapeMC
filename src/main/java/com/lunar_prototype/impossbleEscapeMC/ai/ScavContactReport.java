package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Immutable observation, never a reference to the enemy entity or its live position. */
record ScavContactReport(UUID targetId, UUID observerId, Location position, Vector velocity, int observedTick) {
    static final int MAX_AGE = 200;
    ScavContactReport {
        position = position.clone();
        velocity = velocity.clone();
    }
    @Override public Location position() { return position.clone(); }
    @Override public Vector velocity() { return velocity.clone(); }
    boolean fresh(int now) { return now >= observedTick && now - observedTick < MAX_AGE; }
    double uncertainty(int now) { return Math.min(12.0, 1.5 + Math.max(0, now - observedTick) * 0.08); }
    Location predicted(int now) {
        Vector lead = velocity.clone().multiply(Math.min(20, Math.max(0, now - observedTick)));
        if (lead.lengthSquared() > 36) lead.normalize().multiply(6);
        return position.clone().add(lead);
    }
}
