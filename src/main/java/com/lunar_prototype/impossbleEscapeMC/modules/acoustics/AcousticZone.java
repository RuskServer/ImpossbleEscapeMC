package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

/**
 * 音の響き方で分けた場所の種類。足音などの音は場所ごとに響きを焼き込んだ素材 (iem:step.&lt;材質&gt;.&lt;id&gt;) を鳴らし分ける。
 * 素材はリソースパックの IEM_shader/tools/footsteps.py が作る (ZONES と同じ id)
 */
public enum AcousticZone {
    OUTDOOR("outdoor", "屋外"),
    INDOOR("indoor", "屋内"),
    HALL("hall", "広い屋内"),
    UNDERGROUND("underground", "地下");

    /** 上半分の光線のうち、空まで抜けた割合がこれ以上なら屋外 (地平線寄りの光線は遠くの建物に当たるので、半分より低くする) */
    static final double OPEN_SKY_SHARE = 0.35;
    /** 頭の上の固いブロックがこれ以上重なっていれば地下 (建物の床と屋根より厚い) */
    static final int UNDERGROUND_SOLID = 6;
    /** 天井の高さ (ブロック) がこれ以上なら広い屋内 */
    static final double HALL_CEILING = 7.0;
    /** ほぼ横の光線の、壁までの距離の平均 (ブロック) がこれ以上なら広い屋内 */
    static final double HALL_SPAN = 12.0;

    public final String id;
    public final String label;

    AcousticZone(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static AcousticZone byId(String id) {
        for (AcousticZone zone : values()) {
            if (zone.id.equalsIgnoreCase(id)) return zone;
        }
        return null;
    }

    /**
     * 測った値から場所を決める
     *
     * @param openUp         上半分の光線のうち、空まで抜けた本数
     * @param upRays         上半分の光線の本数
     * @param solidAbove     頭の上に重なっている固いブロックの数
     * @param ceiling        ほぼ真上の光線の、天井までの距離の中央値 (無ければ大きな値)
     * @param meanHorizontal ほぼ横の光線の、壁までの距離の平均
     */
    static AcousticZone classify(int openUp, int upRays, int solidAbove, double ceiling, double meanHorizontal) {
        if (openUp >= upRays * OPEN_SKY_SHARE) return OUTDOOR;
        if (solidAbove >= UNDERGROUND_SOLID) return UNDERGROUND;
        if (ceiling >= HALL_CEILING || meanHorizontal >= HALL_SPAN) return HALL;
        return INDOOR;
    }
}
