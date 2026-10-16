package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.key.Key;

/**
 * ダイアログで共通の部品の字 (リソースパックのフォント iem:trader・iem:quest)。
 * 番号・寸法は IEM_shader/tools/font_common.py の Trader・Quest と、trader_font.py・quest_font.py と同じにする。
 * <ul>
 *   <li>iem:trader: 空白 (±2^i)、上の端末 (108 x 55 を 3 枚)、品物の札 (100 x 26 を 3 枚。未解放は + 3)、品物の枠、購入の札、錠、
 *       タブ・札の箱 (字の幅 + 8 が入る一番小さい幅)</li>
 *   <li>iem:quest: 状態の札、報告・完了の札、目標の目盛り、進み具合の棒、済の印、詳細の上の端末、見出しの線</li>
 * </ul>
 * 札の字は、バニラの字を 5 ずらしたフォント ({@link #CHIP_TEXT_FONT}) で札の縦の真ん中に重ねる
 */
public final class TerminalFont {

    public static final Key TRADER_FONT = Key.key("iem", "trader");
    public static final Key QUEST_FONT = Key.key("iem", "quest");
    /** 札の字のフォント: バニラの字を 5 ずらしたもの (札の縦の真ん中に来る。IEM_shader/tools/shift_fonts.py) */
    public static final Key CHIP_TEXT_FONT = Key.key("iem", "shift_5");

    // ---- iem:trader ----
    /** 空白: + i で +2^i、-2^i (i = 0..9) */
    public static final char SPACE_POS = '', SPACE_NEG = '';
    public static final char HEADER_BASE = '';
    public static final int HEADER_TILES = 3, HEADER_TILE_W = 108;
    /** 品物の札 (+ 枚目。未解放は + PLATE_TILES + 枚目) */
    public static final char PLATE_BASE = '';
    public static final int PLATE_TILES = 3, PLATE_TILE_W = 100;
    public static final char RING = '';
    public static final int RING_W = 22;
    /** 購入の札 (売り切れは CHIP_SOLD) */
    public static final char CHIP = '', CHIP_SOLD = '';
    /** 札の幅 (購入の札・状態の札) */
    public static final int CHIP_W = 36;
    public static final char LOCK = '';
    public static final int LOCK_W = 7;
    /** タブ・札の箱 (+ 幅の番号 * 3 + 種類) */
    public static final char BOX_BASE = '';
    public static final int BOX_TAB = 0, BOX_TAB_ON = 1, BOX_CHIP = 2;
    private static final int[] BOX_WIDTHS = {24, 32, 40, 48, 56, 64, 72, 80, 96, 112};

    // ---- iem:quest ----
    /** 状態の札 (+ 種類) */
    public static final char CHIP_BASE = '';
    public static final int CHIP_REPORT = 0, CHIP_ACTIVE = 1, CHIP_START = 2, CHIP_DONE = 3, CHIP_HAND_IN = 4;
    /** 報告できる行・済んだ目標の札 (+ 枚目) */
    public static final char PLATE_REPORT_BASE = '', PLATE_DONE_BASE = '';
    /** 目標の目盛り (+ (目標の数 - 1) * 6 + 済んだ数) */
    public static final char SEG_BASE = '';
    public static final int SEG_W = 40, SEG_MAX = 5;
    /** 進み具合の棒 (+ 段 0..BAR_STEPS) */
    public static final char BAR_BASE = '', BAR_DONE_BASE = '';
    public static final int BAR_W = 80, BAR_STEPS = 20;
    public static final char CHECK_OFF = '', CHECK_ON = '';
    public static final int CHECK_W = 8;
    /** 詳細の上の端末 (108 x 40 を 3 枚) */
    public static final char DETAIL_BASE = '';
    public static final int DETAIL_TILES = 3, DETAIL_TILE_W = 108;
    /** 見出しの線 (+ (幅 - RULE_MIN) / RULE_STEP) */
    public static final char RULE_BASE = '';
    public static final int RULE_MIN = 8, RULE_MAX = 248, RULE_STEP = 8;

    // ---- 品物の行 (アイテム欄 + 説明。札・枠の絵の位置は行の頭から) ----
    /** 説明の要素の幅と、行の幅 (要素の幅 - 余白 8) */
    public static final int ROW_BODY_W = 308, ROW_LINE_W = ROW_BODY_W - 8;
    /** アイテム欄の大きさ (品物は左上に 16 x 16 で描かれる) */
    public static final int ITEM_BOX = 16;
    /** 札は行の頭の 1 つ左から、品物の枠は 25 左から。字の頭は 5 */
    public static final int PLATE_X = -1, RING_X = -25, TEXT_X = 5;

    private TerminalFont() {
    }

    /** 空白が iem:trader の行 */
    public static DialogLine line() {
        return new DialogLine(TRADER_FONT, SPACE_POS, SPACE_NEG);
    }

    /** 字の幅 + 8 が入る一番小さい箱の番号 */
    public static int boxIndex(int textWidth) {
        int i = 0;
        while (i < BOX_WIDTHS.length - 1 && BOX_WIDTHS[i] < textWidth + 8) i++;
        return i;
    }

    /** その字が入る箱の絵の幅 */
    public static int boxArtWidth(String label) {
        return BOX_WIDTHS[boxIndex(FontWidths.width(label))];
    }

    /** 箱の送り (箱の絵の幅 + 1) */
    public static int boxWidth(String label) {
        return boxArtWidth(label) + 1;
    }

    /** その字が入る箱の字 (kind: BOX_TAB・BOX_TAB_ON・BOX_CHIP) */
    public static char boxGlyph(String label, int kind) {
        return (char) (BOX_BASE + boxIndex(FontWidths.width(label)) * 3 + kind);
    }

    /** n だけ進む (負なら戻る) 空白の文字列 */
    public static String spaces(int n) {
        return line().spaces(n);
    }
}
