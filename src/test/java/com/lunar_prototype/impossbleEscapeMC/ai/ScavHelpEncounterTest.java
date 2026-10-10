package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.UUID;

/** Run without Bukkit: java ... ScavHelpEncounterTest. */
public final class ScavHelpEncounterTest {
    private static UUID id(int n) { return new UUID(0, n); }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var registry = new ScavHelpEncounter.Registry();
        var root = registry.contact(null, id(1), id(100), 0);
        check(root.recruit(id(2), 0), "first rescue accepted");
        check(root.recruit(id(3), 0), "second rescue accepted");
        check(!root.recruit(id(4), 0), "third rescue rejected");
        check(root.recruit(id(2), 40), "same rescue can renew");
        var rescue = registry.contact(root, id(2), id(100), 40);
        check(rescue == root, "rescuer keeps original encounter on contact");
        check(!rescue.recruit(id(5), 40), "rescuer cannot expand the group");
        var independent = registry.contact(null, id(6), id(100), 80);
        check(independent == root, "same enemy shares budget");
        check(!independent.recruit(id(7), 80), "independent caller cannot expand the group");
        check(!root.recruit(id(1), 80), "original caller cannot be recruited as rescue");
        // No departure/death refund: UUIDs stay spent while contact continues.
        registry.contact(root, id(1), id(100), 199);
        check(root.full(), "spent rescue slots stay spent");
        check(root.active(398), "encounter survives 199 quiet ticks");
        check(!root.active(399), "encounter expires after 200 quiet ticks");
        var next = registry.contact(root, id(1), id(100), 399);
        check(next != root && !next.full(), "new encounter gets a fresh budget");
        check(next.recruit(id(8), 399), "fresh budget accepts rescue");
        var switched = registry.contact(next, id(1), id(101), 400);
        check(switched == next, "target switch does not reset encounter budget");
        var other = registry.contact(null, id(9), id(102), 400);
        check(other.recruit(id(10), 400), "separate fight has separate budget");
        var merged = registry.contact(next, id(1), id(102), 401);
        check(merged.full() && other.full(), "meeting fights merge spent slots");
        check(!other.recruit(id(11), 401), "old references observe merged budget");
        var anonymous = registry.contact(null, id(12), null, 500);
        check(anonymous.recruit(id(13), 500), "unknown-attacker encounter works");
        check(registry.contact(anonymous, id(13), null, 501) == anonymous,
                "unknown attacker still preserves rescue chain budget");
        System.out.println("ScavHelpEncounterTest: all checks passed");
    }
}
