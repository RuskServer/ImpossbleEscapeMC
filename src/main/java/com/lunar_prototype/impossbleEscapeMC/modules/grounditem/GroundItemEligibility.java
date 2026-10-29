package com.lunar_prototype.impossbleEscapeMC.modules.grounditem;

import java.util.Set;

/** 拾える戦利品だけを扱い、データパックの装飾用itemエンティティには触れない。 */
public final class GroundItemEligibility {
    private GroundItemEligibility() {}
    public static boolean accepts(Set<String> tags, int pickupDelay, boolean playerPickup) {
        return playerPickup && pickupDelay < 32767 && !tags.contains("toisarm.casing");
    }
}
