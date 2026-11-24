package com.lunar_prototype.impossbleEscapeMC.effect;

import org.bukkit.Location;

/** 画面の時間はシェーダーが進め、サーバーは再生開始・移動・到着だけを管理する。 */
final class DeathScene {
    static final int FIELD_TICKS = 96;
    static final int RETURN_TICKS = 6;
    static final String SOUND = "iem:death.sequence";
    static final int LOAD_TIMEOUT = 100;
    private static final int ARRIVAL_BLACK_TICKS = 2;

    enum Phase { WAIT_RESPAWN, WAIT_FIELD, FIELD, TRANSFER, RETURN }
    enum Signal {
        // death.fsh と共通。EMP の 20000 以降とは重ならない。
        FIELD(18500), HOLD(18750), RETURN(18900);

        private final int offset;
        Signal(int offset) { this.offset = offset; }

        long worldAge(long base, int elapsed) {
            return base + offset + (this == HOLD ? 0 : Math.clamp(elapsed, 0, 100));
        }
    }

    final Location origin;
    final boolean raid;
    Phase phase = Phase.WAIT_RESPAWN;
    private int since;
    boolean moving;

    DeathScene(Location origin, boolean raid) {
        this.origin = origin;
        this.raid = raid;
    }

    void enter(Phase next, int now) {
        phase = next;
        since = now;
    }

    int elapsed(int now) { return now - since; }

    boolean readyAtSite(int now, boolean clientReady) {
        return clientReady || elapsed(now) >= LOAD_TIMEOUT;
    }

    boolean canRevealLobby(int now, boolean loading) {
        return elapsed(now) >= ARRIVAL_BLACK_TICKS && (!loading || elapsed(now) >= LOAD_TIMEOUT);
    }
    boolean locksPlayer() {
        return raid && (phase == Phase.WAIT_FIELD || phase == Phase.FIELD || phase == Phase.TRANSFER);
    }
}
