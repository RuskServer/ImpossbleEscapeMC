package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.List;
import java.util.UUID;

/** 表示した出撃先と同行者への、一度限りの同意。待機秒数の経過だけでは失効させない。 */
final class RaidDeploymentApproval {
    enum Mode {IMMEDIATE, LATE_JOIN, QUEUE}
    enum Result {INVALID, CHANGED, ACCEPTED}
    record Snapshot(String mapId, Mode mode, long cycleStart, RaidConditions conditions,
                    UUID leader, List<UUID> partyMembers, List<UUID> onlineMembers, String queuedMap) {
        Snapshot {
            partyMembers = List.copyOf(partyMembers);
            onlineMembers = List.copyOf(onlineMembers);
        }
    }
    static final int LIFETIME_TICKS = 20 * 120;
    private final String id = UUID.randomUUID().toString();
    private final Snapshot snapshot;
    private final int openedTick;
    private boolean consumed;

    RaidDeploymentApproval(Snapshot snapshot, int openedTick) {
        this.snapshot = snapshot;
        this.openedTick = openedTick;
    }
    String id() {return id;}
    Snapshot snapshot() {return snapshot;}
    boolean matches(String id) {return !consumed && this.id.equals(id);}
    boolean expired(int now) {return now - openedTick >= LIFETIME_TICKS;}
    Result accept(String id, Snapshot current, int now) {
        if (!matches(id) || expired(now)) return Result.INVALID;
        consumed = true;
        return snapshot.equals(current) ? Result.ACCEPTED : Result.CHANGED;
    }
}
