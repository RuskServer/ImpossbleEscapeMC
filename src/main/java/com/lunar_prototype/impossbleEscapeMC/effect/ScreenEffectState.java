package com.lunar_prototype.impossbleEscapeMC.effect;

import net.kyori.adventure.key.Key;

/** プレイヤー1人分の表示・再生・読み込み状態。退出時はこのオブジェクトごと破棄する。 */
final class ScreenEffectState {
    static final int HIT_TICKS = 2, HIT_FADE_TICKS = 3;
    private static final Key HIT = Key.key("iem", "hit"), HIT_FADE = Key.key("iem", "hit_fade");
    boolean controlDebug, menuClear, flashlit, helmetFlashlight, graded, cover, awaitingRespawn;
    Key sky, flareLight, visor, wound, probe;
    Integer dazzle, hitTick, awaitingLoadSince, settlingUntil;
    int visorWet;
    Float sentWet;
    ScreenEffectPlayback playback;
    AdrenalineHeartbeatRecovery recovery;
    DeathScene deathScene;
    org.bukkit.Location pendingDeathOrigin;
    boolean pendingRaidDeath;
    private static final Key DEATH = Key.key("iem", "death");

    PostEffectStack.Layers layers(int now) {
        Key hit = hitTick == null ? null : now - hitTick < HIT_TICKS ? HIT : HIT_FADE;
        boolean deadScene = deathScene != null;
        Key effect = deadScene ? switch (deathScene.phase) {
            case WAIT_RESPAWN, WAIT_FIELD -> null;
            case FIELD, TRANSFER, RETURN -> DEATH;
        } : playback == null ? null : playback.visibleEffect();
        return new PostEffectStack.Layers(controlDebug, sky, flareLight, flashlit, helmetFlashlight, graded, visor, dazzle, wound,
                effect, deadScene ? null : hit, menuClear, probe, cover);
    }
    void expireLoadWaits(int now, int timeout) {
        if (awaitingLoadSince != null && now - awaitingLoadSince >= timeout) awaitingLoadSince = null;
        if (settlingUntil != null && now >= settlingUntil) settlingUntil = null;
    }
    boolean canAdvance(int now, boolean warming) {
        return !awaitingRespawn && awaitingLoadSince == null && (settlingUntil == null || now >= settlingUntil) && !warming;
    }
    boolean worldLoaded(int now, int delay) {
        if (awaitingLoadSince == null) return false;
        awaitingLoadSince = null;
        settlingUntil = now + delay;
        return true;
    }
    void replacePlayback(ScreenEffectPlayback next) {
        if (playback != null) playback.cancel();
        recovery = null;
        playback = next;
    }
    void death(ScreenEffectPlayback next) {
        awaitingRespawn = true;
        sky = flareLight = visor = wound = null;
        helmetFlashlight = flashlit = graded = false;
        dazzle = hitTick = null;
        replacePlayback(next);
    }
    void discard() {
        if (playback != null) playback.cancel();
        playback = null;
        recovery = null;
        deathScene = null;
        pendingDeathOrigin = null;
        pendingRaidDeath = false;
    }
}
