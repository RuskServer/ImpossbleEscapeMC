package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

/**
 * Toi's Armoryデータパックの銃データ (storage toisarm:data gun[]) のうち、SCAVのAIが使う値。
 *
 * @param reloadTicks      マガジンに弾が残っている時のリロード時間 (tick)
 * @param emptyReloadTicks 弾切れからのリロード時間 (tick)
 * @param maxRange         弾の最大射程 (ブロック)
 * @param projectilesPerShot 1発で出る弾の数 (ショットガンは2以上)
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
        double maxRange,
        int projectilesPerShot) {

    /** 武器の種類ごとの戦いやすい距離 (ブロック) */
    public double preferredRange() {
        if (projectilesPerShot > 1) return 8.0;   // ショットガン
        if (maxRange >= 300) return 26.0;         // 狙撃銃 (m700, svd)
        if (maxRange <= 95) return 10.0;          // SMG・拳銃
        return 16.0;                              // アサルトライフル
    }
}
