package com.lunar_prototype.impossbleEscapeMC.item;

import java.util.Map;

/**
 * データパック (Toi's Armory) の銃・アタッチメントの重さとサイズ。データパックには重さ・サイズの概念が無いため、プラグイン側で持つ。
 * 銃の重さは実銃の空の重さが目安 (付いているアタッチメントの重さは別に足す)。サイズはインベントリで占めるスロット数。
 * 表に無い銃は {@link #UNKNOWN_GUN} 、表に無いアタッチメントは {@link #UNKNOWN_ATTACHMENT_WEIGHT} を使う
 */
public final class DatapackItemSpecs {

    /** 重さ (グラム) とサイズ (占めるスロット数) */
    public record Spec(int weightGrams, int cost) {
    }

    /** 表に無い銃 (新しく追加された銃など)。アサルトライフル程度にしておく */
    public static final Spec UNKNOWN_GUN = new Spec(3000, 3);
    public static final int UNKNOWN_ATTACHMENT_WEIGHT = 200;

    private static final Map<String, Spec> GUNS = Map.ofEntries(
            Map.entry("ak74", new Spec(3100, 3)),
            Map.entry("m4a1", new Spec(2900, 3)),
            Map.entry("scar_h", new Spec(3600, 3)),
            Map.entry("as_val", new Spec(2500, 3)),
            Map.entry("svd", new Spec(4300, 4)),
            Map.entry("m700", new Spec(4000, 4)),
            Map.entry("mossberg_590", new Spec(3300, 3)),
            Map.entry("mp5", new Spec(2500, 2)),
            Map.entry("uzi", new Spec(3500, 2)),
            Map.entry("scorpion_evo3", new Spec(2800, 2)),
            Map.entry("usp45", new Spec(750, 1)));

    private static final Map<String, Integer> ATTACHMENT_WEIGHTS = Map.ofEntries(
            Map.entry("eotech_xps2", 320),
            Map.entry("pso_1", 600),
            Map.entry("2_stage_suppressor", 450),
            Map.entry("svd_long_barrel", 600),
            Map.entry("scar_h_drum_magazine", 900),
            Map.entry("9x39mm_30round_magazine", 200),
            Map.entry("uzi_short_magazine", 150),
            Map.entry("uzi_mini_barrel", 200),
            Map.entry("uzi_folded_stock", 300),
            // デバッグ用
            Map.entry("infinity_magazine", 0),
            Map.entry("no_recoil_grip", 0));

    private DatapackItemSpecs() {
    }

    public static Spec gun(String gunId) {
        return GUNS.getOrDefault(gunId, UNKNOWN_GUN);
    }

    public static int attachmentWeight(String attachmentId) {
        return ATTACHMENT_WEIGHTS.getOrDefault(attachmentId, UNKNOWN_ATTACHMENT_WEIGHT);
    }
}
