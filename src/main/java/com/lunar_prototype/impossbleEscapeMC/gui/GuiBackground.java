package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

/**
 * チェスト画面の背景。リソースパックの iem:gui フォントで、タイトルに背景を「文字」として描かせる
 * (タイトルはスロットとアイテムより先に描かれるので、背景はアイテムの下に来る)。
 * 画像とフォントの定義は IEM_shader/tools/gui_backgrounds.py が作る。文字の並びはそちらと同じにする。
 * <p>
 * フォントのテクスチャは 256x256 で、それより大きい文字は表示されないため、背景は 44x36 のタイル (1行に4枚) に分けてある。
 * タイトルの位置 (8, 6) を -8 の空白で打ち消し、タイルを描くごとに進む「幅 + 1」を -1 の空白で、行の終わりを -176 の空白で戻す。
 * 縦の位置はタイルの行ごとの ascent で決まっている。背景はタイトルとチェストの段までで、下の自分のインベントリはバニラのまま。
 * <p>
 * 字は絵に焼き込まない: 画面の名前 (上の帯の液晶)・案内の字・型番の刻印は、ここでバニラの字を重ねる
 * (横は iem:gui の ±2^i の空白、縦はバニラの字をずらしたフォント iem:shift_&lt;n&gt;。IEM_shader/tools/shift_fonts.py)。
 * PDA から開く画面は上の帯に小さな液晶があり、{@link #title(String)} でその右端にページ番号などを出せる (x = 136 から)
 * <p>
 * 下の欄 (自分の持ち物) の名前「インベントリ」もここで書く (バニラの字はパックで空にしてある。{@link InventoryPanel})
 */
public enum GuiBackground {
    PDA(0xE100, 3, null, null, engraving("КПК-3М", 4, false),
            label("オペレーター", 8, 0, Ink.AMBER), labelRight("通信", 154, 0, Ink.AMBER),
            label("カーソルで詳細", 8, 2, Ink.AMBER_DIM), label("閉じる", 101, 2, Ink.RUST)),
    PARTY(0xE120, 3, "パーティー", "ОТРЯД", engraving("КПК-3М", 4, false),
            label("メンバー", 8, 0, Ink.AMBER), labelRight("招待", 41, 2, Ink.AMBER), label("脱退", 135, 2, Ink.RUST)),
    PARTY_INVITE(0xE140, 6, "招待する相手", "ПРИГЛАШЕНИЕ", engraving("КПК-3М", 4, false)),
    STASH_SELECT(0xE160, 3, "スタッシュ", "ХРАНИЛИЩЕ", engraving("КПК-3М", 4, false), label("ページを選択", 8, 0, Ink.AMBER)),
    STASH_3(0xE180, 3, "スタッシュ", "ХРАНИЛИЩЕ", engraving("КПК-3М", 4, false)),
    STASH_6(0xE1A0, 6, "スタッシュ", "ХРАНИЛИЩЕ", engraving("КПК-3М", 4, false)),
    MARKET(0xE1C0, 6, "マーケット", "БАРАХОЛКА", engraving("КПК-3М", 4, false), label("出品する", 99, 5, Ink.AMBER)),
    MARKET_SELL(0xE1E0, 5, "出品するアイテム", "ПРОДАЖА", engraving("КПК-3М", 4, false), label("FIR か弾薬のみ", 8, 4, Ink.AMBER_DIM)),
    MARKET_PRICE(0xE200, 3, "価格設定", "ЦЕНА", engraving("КПК-3М", 4, false),
            labelCenter("値下げ", 52, 0, Ink.AMBER_DIM), labelCenter("値上げ", 124, 0, Ink.AMBER_DIM),
            label("キャンセル", 27, 2, Ink.RUST), label("出品を確定", 99, 2, Ink.AMBER)),
    RAID(0xE220, 6, "出撃", null, engraving("КПК-3М", 4, false), label("オートマッチ", 27, 5, Ink.AMBER)),
    QUEST(0xE240, 3, "クエスト", "ЗАДАНИЯ", engraving("КПК-3М", 4, false)),
    /** アタッチメント画面 (PDA ではなく武器職人の作業台。テープに「武器整備」、右上に刻印) */
    ATTACHMENT(0xE260, 5, null, null, engraving("ВЕРСТАК №3", 169, true), label("武器整備", 10, -1, Ink.MARKER)),
    /** 支給装備 (無料の最低限の装備) */
    SUPPLY(0xE280, 4, "支給装備", "ВЫДАЧА", engraving("КПК-3М", 4, false),
            label("武器を選ぶ", 8, 0, Ink.AMBER), label("戻る", 27, 3, Ink.RUST), labelRight("受け取る", 149, 3, Ink.AMBER));

    private static final Key GUI_FONT = Key.key("iem", "gui");
    private static final char SHIFT_TITLE = '';
    private static final char SHIFT_GAP = '';
    private static final char SHIFT_ROW = '';
    private static final char SPACE_POS = '';
    private static final char SPACE_NEG = '';
    private static final int COLUMNS = 4;
    private static final int TILE_HEIGHT = 36;
    private static final int WIDTH = 176;
    /** タイトルの字の左端 (画面の左から)。タイトルの位置合わせはここを 0 として数える */
    private static final int TITLE_X = 8;
    /** 上の帯の液晶: 名前の左端と、表示欄の左端 (gui_backgrounds.py の STRIP_RECT・LABEL_X と同じ) */
    private static final int STRIP_TEXT_X = 51, LABEL_X = 136, STRIP_RIGHT = 168;
    /** スロットの段 r の真ん中に字を置く、バニラの字のずらし (shift_fonts.py の 16・34・52・70・88・106) */
    private static final int ROW_SHIFT = 16, ROW_PITCH = 18;

    /** 字の色 (背景の画像の色と同じ) */
    private enum Ink {
        AMBER(0xDEAC4E), AMBER_DIM(0x96763C), RUST(0xC45C34), MARKER(0x24201C);

        final TextColor color;

        Ink(int rgb) {
            this.color = TextColor.color(rgb);
        }
    }

    /** 案内の字。row が -1 ならタイトルの行 (ずらさない) */
    private record Label(String text, int x, int row, Ink ink, int align) {
    }

    /** 型番の刻印 (暗い字に明るい影)。right なら x が右端 */
    private record Engraving(String text, int x, boolean right) {
    }

    private static Label label(String text, int x, int row, Ink ink) {
        return new Label(text, x, row, ink, 0);
    }

    private static Label labelRight(String text, int right, int row, Ink ink) {
        return new Label(text, right, row, ink, 2);
    }

    private static Label labelCenter(String text, int center, int row, Ink ink) {
        return new Label(text, center, row, ink, 1);
    }

    private static Engraving engraving(String text, int x, boolean right) {
        return new Engraving(text, x, right);
    }

    private final String tiles;
    private final int chestRows;
    private final String stripName;
    private final String stripSub;
    private final Engraving engraving;
    private final List<Label> labels;

    GuiBackground(int firstCodepoint, int chestRows, String stripName, String stripSub, Engraving engraving, Label... labels) {
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
        this.tiles = text.toString();
        this.chestRows = chestRows;
        this.stripName = stripName;
        this.stripSub = stripSub;
        this.engraving = engraving;
        this.labels = List.of(labels);
    }

    /** チェスト画面のタイトルに渡す背景 */
    public Component title() {
        return build("", null, null);
    }

    /** 背景の後ろに、同じフォント (iem:gui) の文字 overlay (背景の上に重ねる線など。描き終えると x = 176 に戻る) を足したタイトル */
    public Component titleWith(String overlay) {
        return build(overlay, null, null);
    }

    /** 上の帯の液晶の右端に label (ページ番号など、半角5文字くらいまで) を出すタイトル */
    public Component title(String label) {
        return build("", label, null);
    }

    /** 下の欄をバックパックの表示 (bag。null ならいつもの「インベントリ」) にしたタイトル。label は {@link #title(String)} と同じ */
    public Component title(String label, InventoryPanel.Bag bag) {
        return build("", label, bag);
    }

    /** 「1/3」のようなページ表示 (page は 0 始まり) */
    public Component pagedTitle(int page, int pageCount) {
        return title((page + 1) + "/" + pageCount);
    }

    private Component build(String overlay, String pageLabel, InventoryPanel.Bag bag) {
        // 背景 (白にしないと、タイトルの色 (暗い灰色) で背景が暗く染まる)。描き終えると画面の左から 176 にいる
        Component background = Component.text(tiles + overlay).font(GUI_FONT).color(NamedTextColor.WHITE);
        DialogLine line = new DialogLine(GUI_FONT, SPACE_POS, SPACE_NEG);
        line.x = WIDTH - TITLE_X;
        if (engraving != null) {
            int x = engraving.right() ? engraving.x() - FontWidths.width(engraving.text()) : engraving.x();
            line.moveTo(x - TITLE_X).text(Key.key("minecraft", "default"), engraving.text(), TextColor.color(0x18170F), null, null);
        }
        if (stripName != null) {
            int limit = (pageLabel != null ? LABEL_X - 6 : STRIP_RIGHT);
            line.moveTo(STRIP_TEXT_X - TITLE_X).text(stripName, Ink.AMBER.color);
            // ロシア語の名前は帯に収まる時だけ添える
            if (stripSub != null && STRIP_TEXT_X + FontWidths.width(stripName) + 5 + FontWidths.width(stripSub) <= limit) {
                line.shift(5).text(stripSub, Ink.AMBER_DIM.color);
            }
        }
        for (Label l : labels) {
            int w = FontWidths.width(l.text());
            int x = l.align() == 2 ? l.x() - w : l.align() == 1 ? l.x() - w / 2 : l.x();
            Key font = l.row() < 0 ? Key.key("minecraft", "default") : Key.key("iem", "shift_" + (ROW_SHIFT + l.row() * ROW_PITCH));
            line.moveTo(x - TITLE_X).text(font, l.text(), l.ink().color, null, null);
        }
        if (pageLabel != null) line.moveTo(LABEL_X - TITLE_X).text(pageLabel, Ink.AMBER.color);
        InventoryPanel.append(line, chestRows, bag);
        Component labelsComponent = line.build().shadowColor(ShadowColor.none());
        return Component.textOfChildren(background, labelsComponent);
    }
}
