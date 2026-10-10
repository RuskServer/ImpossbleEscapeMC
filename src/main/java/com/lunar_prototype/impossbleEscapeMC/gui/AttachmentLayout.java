package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.Map;

/**
 * アタッチメント画面 (プラグイン銃・データパック銃とも) のパーツの位置と名前。
 * 5段で、銃 (中央) のまわりに1マスずつ空けて銃の形に合わせて並べ (上にサイトなど、左にストック、右にバレルとマズル、
 * 下にグリップとマガジン)、銃から各パーツへ線を伸ばす。
 * 背景 (作業台の整備マット) と線は IEM_shader/tools/workbench.py が作る。線と枠は位置ごとに別の文字で、
 * その銃にあるスロットの位置の分だけタイトルの背景の後ろに足す ({@link #title})。
 * 空きスロットにはパーツの絵文字 (リソースパックの iem:attachment_slot_<種類>) を出す
 */
public final class AttachmentLayout {
    public static final int SIZE = 45;
    public static final int GUN_SLOT = 22;

    private static final Map<String, Integer> POSITIONS = Map.ofEntries(
            Map.entry("slide", 0),
            Map.entry("receiver", 2),
            Map.entry("sight", 4),
            Map.entry("handguard", 6),
            Map.entry("accessory", 8),
            Map.entry("shell", 18),
            Map.entry("stock", 20),
            Map.entry("barrel", 24),
            Map.entry("muzzle", 26),
            Map.entry("spare_magazine", 36),
            Map.entry("rear_grip", 38),
            Map.entry("magazine", 40),
            Map.entry("underbarrel", 42));
    /** 位置の決まっていない種類のスロットを置く場所 (空いている順に使う。44 以外は線が無い) */
    private static final int[] SPARE_POSITIONS = {44, 9, 17, 27, 35};
    /** 線を伸ばせる位置。この順に線の文字 (U+E300 から4文字ずつ) が割り当ててある (workbench.py と同じ順) */
    private static final int[] LINE_POSITIONS = {0, 2, 4, 6, 8, 18, 20, 24, 26, 36, 38, 40, 42, 44};
    private static final char FIRST_LINE_CHAR = '\uE300';
    private static final char SHIFT_GAP = '\uE002';

    private AttachmentLayout() {
    }

    /** slot (データパックのスロット名、またはプラグインの AttachmentSlot の小文字名) の位置。決まっていなければ -1 */
    public static int position(String slot) {
        return POSITIONS.getOrDefault(slot, -1);
    }

    public static int[] sparePositions() {
        return SPARE_POSITIONS.clone();
    }

    /** 背景と、positions (スロットを置いた位置) への銃からの線と枠を描くタイトル */
    public static Component title(Collection<Integer> positions) {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < LINE_POSITIONS.length; i++) {
            if (!positions.contains(LINE_POSITIONS[i])) continue;
            // [線の左端へ戻る空白][左半分][-1][右半分][-1][背景の後ろの位置へ進む空白]
            char first = (char) (FIRST_LINE_CHAR + 4 * i);
            lines.append(first).append((char) (first + 1)).append(SHIFT_GAP)
                    .append((char) (first + 2)).append(SHIFT_GAP).append((char) (first + 3));
        }
        return GuiBackground.ATTACHMENT.titleWith(lines.toString());
    }

    /** 空きスロットに出すパーツの絵文字のモデル */
    public static NamespacedKey slotIcon(String slot) {
        return new NamespacedKey("iem", "attachment_slot_" + (POSITIONS.containsKey(slot) ? slot : "generic"));
    }

    public static String label(String slot) {
        return switch (slot) {
            case "sight" -> "サイト";
            case "barrel" -> "バレル";
            case "muzzle" -> "マズル";
            case "magazine" -> "マガジン";
            case "spare_magazine" -> "予備マガジン";
            case "rear_grip" -> "グリップ";
            case "stock" -> "ストック";
            case "underbarrel" -> "アンダーバレル";
            case "handguard" -> "ハンドガード";
            case "receiver" -> "レシーバー";
            case "accessory" -> "アクセサリー";
            case "slide" -> "スライド";
            case "shell" -> "シェル";
            default -> slot;
        };
    }
}
