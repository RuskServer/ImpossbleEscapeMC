package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * チェスト画面の背景。リソースパックの iem:gui フォントで、タイトルに背景を「文字」として描かせる
 * (タイトルはスロットとアイテムより先に描かれるので、背景はアイテムの下に来る)。
 * 画像とフォントの定義は IEM_shader/tools/gui_backgrounds.py が作る。文字の並びはそちらと同じにする。
 * <p>
 * フォントのテクスチャは 256x256 で、それより大きい文字は表示されないため、背景は 44x36 のタイル (1行に4枚) に分けてある。
 * タイトルの位置 (8, 6) を -8 の空白で打ち消し、タイルを描くごとに進む「幅 + 1」を -1 の空白で、行の終わりを -176 の空白で戻す。
 * 縦の位置はタイルの行ごとの ascent で決まっている。背景はタイトルとチェストの段までで、下の自分のインベントリはバニラのまま。
 * <p>
 * PDA から開く画面は上の帯に小さな液晶があり、{@link #title(String)} でその右端にページ番号などを出せる
 * (背景を描き終えた位置から、表示欄の左端 x = 136 まで空白で戻る)。
 */
public enum GuiBackground {
    PDA(0xE100, 3),
    PARTY(0xE120, 3),
    PARTY_INVITE(0xE140, 6),
    STASH_SELECT(0xE160, 3),
    STASH_3(0xE180, 3),
    STASH_6(0xE1A0, 6),
    MARKET(0xE1C0, 6),
    MARKET_SELL(0xE1E0, 5),
    MARKET_PRICE(0xE200, 3),
    RAID(0xE220, 6),
    QUEST(0xE240, 3),
    /** アタッチメント画面 (PDA ではなく武器職人の作業台) */
    ATTACHMENT(0xE260, 5);

    private static final char SHIFT_TITLE = '\uE001';
    private static final char SHIFT_GAP = '\uE002';
    private static final char SHIFT_ROW = '\uE003';
    private static final char SHIFT_LABEL = '\uE004';
    /** 液晶の文字の色 (背景の画像の琥珀色と同じ) */
    private static final TextColor LCD_AMBER = TextColor.color(0xDEAC4E);
    private static final int COLUMNS = 4;
    private static final int TILE_HEIGHT = 36;

    private final String tiles;
    private final Component title;

    GuiBackground(int firstCodepoint, int chestRows) {
        int height = 18 * chestRows + 18;
        int tileRows = (height + TILE_HEIGHT - 1) / TILE_HEIGHT;
        StringBuilder text = new StringBuilder().append(SHIFT_TITLE);
        int code = firstCodepoint;
        for (int row = 0; row < tileRows; row++) {
            if (row > 0) text.append(SHIFT_ROW);
            for (int column = 0; column < COLUMNS; column++) {
                text.append((char) code++).append(SHIFT_GAP);
            }
        }
        // 白にしないと、タイトルの色 (暗い灰色) で背景が暗く染まる
        this.tiles = text.toString();
        this.title = Component.text(tiles).font(Key.key("iem", "gui")).color(NamedTextColor.WHITE);
    }

    /** チェスト画面のタイトルに渡す背景 */
    public Component title() {
        return title;
    }

    /** 背景の後ろに、同じフォント (iem:gui) の文字 overlay (背景の上に重ねる線など) を足したタイトル */
    public Component titleWith(String overlay) {
        return Component.text(tiles + overlay).font(Key.key("iem", "gui")).color(NamedTextColor.WHITE);
    }

    /** 上の帯の液晶の右端に label (ページ番号など、半角5文字くらいまで) を出すタイトル */
    public Component title(String label) {
        return Component.textOfChildren(
                Component.text(tiles + SHIFT_LABEL).font(Key.key("iem", "gui")).color(NamedTextColor.WHITE),
                Component.text(label, LCD_AMBER));
    }

    /** 「1/3」のようなページ表示 (page は 0 始まり) */
    public Component pagedTitle(int page, int pageCount) {
        return title((page + 1) + "/" + pageCount);
    }
}
