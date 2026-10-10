package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.*;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Encounter-local leases for small search sectors. All access is on the server thread. */
final class ScavSearchPlan {
    private record Lease(UUID member, Location point, int until) {}
    private record Checked(Location point, int tick) {}
    private static final int LEASE_TICKS = 60;
    private final Map<UUID, Map<Integer, Lease>> leases = new HashMap<>();
    private final Map<UUID, List<Checked>> checked = new HashMap<>();

    Location assign(UUID member, ScavContactReport report, int now, Predicate<Location> reachable) {
        if (report == null || !report.fresh(now)) { release(member); return null; }
        var slots = leases.computeIfAbsent(report.targetId(), key -> new HashMap<>());
        slots.values().removeIf(lease -> now >= lease.until());
        var cleared = checked.computeIfAbsent(report.targetId(), key -> new ArrayList<>());
        cleared.removeIf(point -> now - point.tick() >= 100 || report.observedTick() > point.tick());
        Location center = report.predicted(now);
        for (var entry : slots.entrySet()) {
            Lease lease = entry.getValue();
            if (lease.member().equals(member)) {
                if (lease.point().getWorld() == center.getWorld() && lease.point().distanceSquared(center) < 400) {
                    slots.put(entry.getKey(), new Lease(member, lease.point(), now + LEASE_TICKS));
                    return lease.point().clone();
                }
                break;
            }
        }
        slots.values().removeIf(lease -> lease.member().equals(member));
        Vector forward = report.velocity().setY(0);
        if (forward.lengthSquared() < 0.001) forward = new Vector(0, 0, 1);
        else forward.normalize();
        Vector side = new Vector(-forward.getZ(), 0, forward.getX());
        double radius = Math.max(4, Math.min(10, report.uncertainty(now)));
        // Different sides/approaches, including the likely exit and rear approach.
        for (int slot = 0; slot < 8; slot++) {
            if (slots.containsKey(slot)) continue;
            double angle = slot * Math.PI / 4;
            Location point = center.clone().add(forward.clone().multiply(Math.cos(angle) * radius))
                    .add(side.clone().multiply(Math.sin(angle) * radius));
            if (cleared.stream().anyMatch(c -> c.point().getWorld() == point.getWorld() && c.point().distanceSquared(point) < 9)) continue;
            if (slots.values().stream().anyMatch(l -> l.point().getWorld() == point.getWorld() && l.point().distanceSquared(point) < 9)) continue;
            if (!reachable.test(point.clone())) continue;
            slots.put(slot, new Lease(member, point.clone(), now + LEASE_TICKS));
            return point;
        }
        return null;
    }

    /** Only called after the assigned point has actually been inspected with clear line of sight. */
    void inspected(UUID member, UUID target, Location point, int now) {
        var slots = leases.get(target);
        if (slots == null || slots.values().stream().noneMatch(l -> now < l.until() && l.member().equals(member)
                && l.point().getWorld() == point.getWorld() && l.point().distanceSquared(point) < 0.01)) return;
        var evidence = checked.computeIfAbsent(target, key -> new ArrayList<>());
        evidence.removeIf(c -> now - c.tick() >= 100);
        evidence.add(new Checked(point.clone(), now));
        if (evidence.size() > 32) evidence.remove(0);
        release(member);
    }
    void release(UUID member) { leases.values().forEach(slots -> slots.values().removeIf(l -> l.member().equals(member))); }
    void merge(ScavSearchPlan other) {
        // Merge negative observations; discard old reservations to resolve collisions on joining encounters.
        leases.clear(); other.leases.clear();
        other.checked.forEach((target, points) -> {
            var combined = checked.computeIfAbsent(target, key -> new ArrayList<>());
            combined.addAll(points);
            while (combined.size() > 32) combined.remove(0);
        });
    }
}
