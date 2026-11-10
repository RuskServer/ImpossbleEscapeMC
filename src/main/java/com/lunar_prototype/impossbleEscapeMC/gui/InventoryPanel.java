package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * チェスト画面の下の欄 (自分の持ち物) の名前と、バックパックの表示の背景。タイトルの字として描く
 * (絵とフォントはリソースパックの IEM_shader/tools/inventory_panel.py。文字の並びはそちらと同じにする)。
 * <p>
 * バニラの「インベントリ」の字はパックで空にしてあり (タイトルより上に描かれ、バックパックの名前と重なるため)、
 * IEM の画面ではここで同じ位置 (8, 20 + 18*段数)・同じ色に書く。縦の位置は iem:shift_&lt;14 + 18*段数&gt; のフォント。
 * <ul>
 *   <li>いつもの表示: 「インベントリ」</li>
 *   <li>バックパックの表示 (漁る画面・スタッシュで、持ち物の 3 段にバックパックの中身を出している時): 欄を黄土色の絵にし、
 *       左に袋の名前、右に使っているマス / 容量</li>
 *   <li>表示を切り替えるボタン (ホットバーの右端のスロット) がある画面では、そのスロットを色の枠で囲み (表示ごとに色が違う)、
 *       「右下で切替」(狭ければ「▼切替」)と添える。ボタンのアイコンは今の入れ物の絵のままにして、押せることだけを示す。
 *       ボタンの無い画面は bag に null を渡す</li>
 * </ul>
 * 位置はタイトルの字の左端 (画面の左から 8) を 0 として数える ({@link DialogLine})
 */
public final class InventoryPanel {

    private static final Key FONT = Key.key("iem", "inventory_panel");
    private static final Key SPACE_FONT = Key.key("iem", "gui");
    private static final char SPACE_POS = '', SPACE_NEG = '';
    private static final int FIRST = 0xE000;
    private static final int TILE_W = 44, COLUMNS = 4, TILE_ROWS = 2;
    private static final int TITLE_X = 8;
    /** 切り替えボタンの枠 (画面の左から 149、幅 23)。文字は 0xE060 + (段数 - 1) * 2 + (バックパックの表示なら 1) */
    private static final char TOGGLE_FIRST = '';
    private static final int TOGGLE_X = 149, TOGGLE_W = 23;
    /** 「右下で切替」の色 (枠の黄土色に合わせた茶色) と、名前・数との間の最低限の間 */
    private static final TextColor HINT = TextColor.color(0x8C6420);
    /** 案内の文 (入る一番長いものを出す) */
    static final String TOGGLE_HINT = "右下で切替";
    static final String TOGGLE_HINT_SHORT = "▼切替";
    private static final int HINT_GAP = 6;
    /** 名前の左端と、使っているマスの右端 (画面の左から) */
    private static final int LABEL_X = 8, LABEL_RIGHT = 168;
    /** バニラの「インベントリ」の字の色 */
    private static final TextColor LABEL = TextColor.color(0x404040);
    private static final TextColor OVER = TextColor.color(0xA02A1C);
    static final String INVENTORY = "インベントリ";

    /** バックパックの表示で出す袋: 名前、使っているマス (コストの合計)、容量 */
    public record Bag(String name, int used, int capacity) {
        /** 切り替えボタンのある画面の、いつもの表示 (背景はいつもの「インベントリ」で、ボタンの枠と案内だけ付ける) */
        public static final Bag PLAYER = new Bag("", -1, -1);

        public boolean isPlayer() {
            return capacity < 0;
        }
    }

    private InventoryPanel() {
    }

    /**
     * line (タイトルの続き) に、段数 rows のチェストの下の欄の名前と背景を書き足す。bag が null なら切り替えボタンの無い画面のいつもの
     * 「インベントリ」、{@link Bag#PLAYER} なら切り替えボタンのある画面のいつもの表示、それ以外ならバックパックの表示
     */
    public static void append(DialogLine line, int rows, Bag bag) {
        Key labelFont = Key.key("iem", "shift_" + (14 + 18 * rows));
        boolean backpack = bag != null && !bag.isPlayer();
        if (!backpack) {
            line.moveTo(LABEL_X - TITLE_X).text(labelFont, INVENTORY, LABEL, null, null);
            if (bag != null) hint(line, labelFont, LABEL_X + FontWidths.width(INVENTORY), LABEL_RIGHT);
        } else {
            int base = FIRST + (rows - 1) * COLUMNS * TILE_ROWS;
            for (int tr = 0; tr < TILE_ROWS; tr++) {
                for (int tc = 0; tc < COLUMNS; tc++) {
                    line.moveTo(tc * TILE_W - TITLE_X).glyph(FONT, (char) (base + tr * COLUMNS + tc), TILE_W, NamedTextColor.WHITE, null, null);
                }
            }
            String count = bag.used() + " / " + bag.capacity();
            int countWidth = FontWidths.width(count);
            String name = FontWidths.truncate(bag.name(), LABEL_RIGHT - countWidth - 6 - LABEL_X);
            line.moveTo(LABEL_X - TITLE_X).text(labelFont, name, LABEL, null, null);
            line.moveTo(LABEL_RIGHT - countWidth - TITLE_X).text(labelFont, count, bag.used() > bag.capacity() ? OVER : LABEL, null, null);
            hint(line, labelFont, LABEL_X + FontWidths.width(name), LABEL_RIGHT - countWidth);
        }
        if (bag != null) {
            char frame = (char) (TOGGLE_FIRST + (rows - 1) * 2 + (backpack ? 1 : 0));
            line.moveTo(TOGGLE_X - TITLE_X).glyph(FONT, frame, TOGGLE_W, NamedTextColor.WHITE, null, null);
        }
    }

    /** 案内を、left (左の字の右端) と right (右の字の左端) の間の右寄せで。長い文が入らなければ短い文、それも入らなければ出さない */
    private static void hint(DialogLine line, Key labelFont, int left, int right) {
        for (String text : new String[]{TOGGLE_HINT, TOGGLE_HINT_SHORT}) {
            int x = right - HINT_GAP - FontWidths.width(text);
            if (x < left + HINT_GAP) continue;
            line.moveTo(x - TITLE_X).text(labelFont, text, HINT, null, null);
            return;
        }
    }

    /** 背景の無い画面 (漁る画面など) のタイトル: バニラのタイトルの字 text の後に、下の欄の名前と背景 */
    public static Component title(String text, int rows, Bag bag) {
        DialogLine line = new DialogLine(SPACE_FONT, SPACE_POS, SPACE_NEG);
        line.text(text, LABEL);
        append(line, rows, bag);
        return line.build().shadowColor(ShadowColor.none());
    }
}
