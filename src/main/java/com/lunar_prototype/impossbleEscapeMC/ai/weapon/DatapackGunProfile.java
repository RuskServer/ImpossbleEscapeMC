package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

/**
 * Toi's Armoryデータパックの銃データ (storage toisarm:data gun[]) のうち、SCAVのAIが使う値。
 *
 * @param reloadTicks      マガジンに弾が残っている時のリロード時間 (tick)
 * @param emptyReloadTicks 弾切れからのリロード時間 (tick)
 * @param maxRange         弾の最大射程 (ブロック)
 */
public record DatapackGunProfile(
        String id,
        String displayName,
        double rpm,
        boolean automatic,
        boolean manualAction,
        int magazineSize,
        int reloadTicks,
        int emptyReloadTicks,
        double maxRange) {
}
