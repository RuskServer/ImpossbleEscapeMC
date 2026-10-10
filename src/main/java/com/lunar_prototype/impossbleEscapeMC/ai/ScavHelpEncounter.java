package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shared, cumulative reinforcement budget. Accessed on the server thread. */
final class ScavHelpEncounter {
    static final int MAX_REINFORCEMENTS = 2;
    static final int QUIET_TICKS = 200;

    private ScavHelpEncounter parent = this;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> reinforcements = new HashSet<>();
    private int lastContactTick;

    private ScavHelpEncounter(int now) { lastContactTick = now; }

    private ScavHelpEncounter root() {
        if (parent != this) parent = parent.root();
        return parent;
    }

    boolean active(int now) { return now - root().lastContactTick < QUIET_TICKS; }
    boolean full() { return root().reinforcements.size() >= MAX_REINFORCEMENTS; }
    boolean contains(UUID id) { return root().members.contains(id); }

    boolean recruit(UUID id, int now) {
        ScavHelpEncounter root = root();
        if (!active(now)) return false;
        if (root.reinforcements.contains(id)) return true; // renew the same responder
        if (root.members.contains(id) || full()) return false;
        root.members.add(id);
        root.reinforcements.add(id);
        return true;
    }

    static final class Registry {
        private final Map<UUID, ScavHelpEncounter> byTarget = new HashMap<>();

        ScavHelpEncounter contact(ScavHelpEncounter inherited, UUID member, UUID target, int now) {
            byTarget.values().removeIf(encounter -> !encounter.active(now));
            ScavHelpEncounter encounter = inherited != null && inherited.active(now) ? inherited.root() : null;
            ScavHelpEncounter known = target == null ? null : byTarget.get(target);
            if (encounter == null) encounter = known != null ? known.root() : new ScavHelpEncounter(now);
            else if (known != null && known.root() != encounter) {
                // Existing fights can meet: keep all spent slots, never grant a new budget.
                ScavHelpEncounter other = known.root();
                encounter.members.addAll(other.members);
                encounter.reinforcements.addAll(other.reinforcements);
                other.parent = encounter;
            }
            encounter.lastContactTick = now;
            encounter.members.add(member);
            if (target != null) byTarget.put(target, encounter);
            return encounter;
        }
    }
}
