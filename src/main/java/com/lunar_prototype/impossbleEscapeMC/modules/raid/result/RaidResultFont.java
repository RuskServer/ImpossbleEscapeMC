package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont;
import net.kyori.adventure.key.Key;

/**
 * レイド結果の画面の部品の字 (リソースパックのフォント iem:result。IEM_shader/tools/result_font.py と同じ番号・寸法)。
 * クエストの画面と共通の部品は iem:trader・iem:quest の字 ({@link TerminalFont})
 */
final class RaidResultFont {
    static final Key FONT = Key.key("iem", "result");
    /** 共通の部品のフォント ({@link TerminalFont}) */
    static final Key TRADER = TerminalFont.TRADER_FONT;
    static final Key QUEST = TerminalFont.QUEST_FONT;

    static final char SPACE_POS = '';
    static final char SPACE_NEG = '';

    // ---- 戦利品のページ (チェストのタイトル) ----
    /** 背景のタイル 44 x 42 を 4 列 x 3 段 (+ 段 * 4 + 列) */
    static final char CHEST_BASE = '';
    static final int TILE_W = 44, TILE_H = 42, TILE_COLS = 4, TILE_ROWS = 3;
    /**
     * 液晶の 3 行 (持ち帰り・失った装備・総利益): 字の上端はチェストの上から 9・21・33。タイトルの字は上から 6 に描かれるので、
     * バニラの字を 3・15・27 ずらしたフォント ({@link #shift}) で置く。名前は左から 48、値は右端を 168 にそろえる
     */
    static final int[] LCD_SHIFTS = {3, 15, 27};
    static final int LCD_LABEL_X = 48, LCD_VALUE_RIGHT = 168;
    /** 評価ランクの札 (+ S A B C D の番号)。チェストの左から 9、幅 34 */
    static final char RANK_BASE = '';
    static final String RANKS = "SABCD";
    static final int RANK_X = 9, RANK_W = 34;
    /** 品物の後ろの枠 (+ 段 * 8 + 段階 * 2 + クエスト)。段はチェストの 3〜5 段目 */
    static final char TIER_BASE = '';
    static final int TIER_W = 18;
    /** 価値の段階 (1 欄の価値): 金 30,000 以上・紫 10,000 以上・青 3,000 以上・灰 */
    static final long[] TIER_VALUES = {30_000, 10_000, 3_000, 0};

    // ---- 経験値のページ (ダイアログ) ----
    /** ダイアログの上の札 108 x 30 を 3 枚 (結果と、脱出地点・マップ・時間) */
    static final char HEADER_BASE = '';
    static final int HEADER_TILES = 3, HEADER_TILE_W = 108;
    static final char LEVEL_BASE = '';
    static final int LEVEL_TILES = 3, LEVEL_TILE_W = 108;
    static final char BAR_TRACK = '';
    static final int BAR_TRACK_W = 150, BAR_W = 300;
    /** 棒の中身 (+ 色 * 8 + p、幅 2^p) */
    static final char BAR_FILL = '';
    static final int BAR_AMBER = 0, BAR_LIGHT = 1, BAR_GREEN = 2;
    static final char STRIPE = '';
    static final int STRIPE_W = 160;

    // ---- キルログの札 ----
    static final char CHIP_PART = '', CHIP_PART_HEAD = '', CHIP_KIND_RED = '', CHIP_KIND_AMBER = '',
            CHIP_GREEN = '';
    static final int CHIP_PART_W = 24, CHIP_KIND_W = 32, CHIP_GREEN_W = 52;
    /** 押せる札 (36 x 18。2 行にまたがる。字は 5 ずらしたフォントで札の真ん中に重ねる) */
    static final char CHIP_ACTION = '';
    static final int CHIP_ACTION_W = 36, CHIP_TEXT_SHIFT = 5;


    private RaidResultFont() {
    }

    static DialogLine line() {
        return new DialogLine(FONT, SPACE_POS, SPACE_NEG);
    }

    /**
     * バニラの字を n 画素下にずらしたフォント (IEM_shader/tools/shift_fonts.py の SHIFTS: 3・5・15・27)。
     * 日本語は かな・全角・第 1 水準の漢字だけ (決まった語・数字に使う)
     */
    static Key shift(int n) {
        return Key.key("iem", "shift_" + n);
    }

}
