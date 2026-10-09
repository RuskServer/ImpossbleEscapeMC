package com.lunar_prototype.impossbleEscapeMC.ai.weapon;

/**
 * SCAVが手に持つ銃。AIは射撃間隔・射撃モード・弾数をこのインターフェース経由で参照し、
 * 実際の射撃方式 (Toi's Armoryデータパック銃 / プラグイン独自の銃) を意識しない。
 */
public interface ScavWeapon {

    /** 銃ID (データパック銃ならデータパックのID、プラグイン銃ならアイテムID) */
    String id();

    /** 毎分の発射数 */
    double rpm();

    /** フルオートか */
    boolean isAutomatic();

    /** ボルトアクション・ポンプアクションなど、1発ごとに手動で次弾を送る銃か */
    boolean isManualAction();

    /** 装弾数 */
    int magazineSize();

    /** この銃で戦いやすい距離 (ブロック)。SCAVはこれに個体差を掛けた距離を保とうとする */
    double preferredRange();

    /** 現在撃てる弾数 (薬室を含む) */
    int ammo();

    /** リロード中・準備中で撃てないか */
    boolean isReloading();

    /**
     * 引き金を1回引く。inaccuracy はBulletTaskの拡散量と同じ尺度
     *
     * @param fullAuto フルオートの銃で撃ち続けるか。false ならフルオートの銃でも1発だけ撃つ
     * @return 引き金を引けた場合true (構え直し中・リロード中などで撃てなかった場合false)
     */
    boolean fire(double inaccuracy, boolean fullAuto);

    /** 敵を認識している間に呼ぶ。撃つ前の準備に時間がかかる銃は、ここで準備を始めておく */
    default void prepare() {
    }

    /** SCAVの終了時・持ち替え時に呼ぶ */
    void release();

    default double ammoRatio() {
        int size = magazineSize();
        return size > 0 ? Math.min(1.0, (double) ammo() / size) : 1.0;
    }

    default boolean needsReload() {
        return isReloading() || ammo() <= 0;
    }
}
