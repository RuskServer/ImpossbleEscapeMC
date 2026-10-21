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
    /** 名前の左端と、使っているマスの右端 (画面の左から) */
    private static final int LABEL_X = 8, LABEL_RIGHT = 168;
    /** バニラの「インベントリ」の字の色 */
    private static final TextColor LABEL = TextColor.color(0x404040);
    private static final TextColor OVER = TextColor.color(0xA02A1C);
    static final String INVENTORY = "インベントリ";

    /** バックパックの表示で出す袋: 名前、使っているマス (コストの合計)、容量 */
    public record Bag(String name, int used, int capacity) {
    }

    private InventoryPanel() {
    }

    /** line (タイトルの続き) に、段数 rows のチェストの下の欄の名前 (bag が null ならいつもの「インベントリ」) と背景を書き足す */
    public static void append(DialogLine line, int rows, Bag bag) {
        Key labelFont = Key.key("iem", "shift_" + (14 + 18 * rows));
        if (bag == null) {
            line.moveTo(LABEL_X - TITLE_X).text(labelFont, INVENTORY, LABEL, null, null);
            return;
        }
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
    }

    /** 背景の無い画面 (漁る画面など) のタイトル: バニラのタイトルの字 text の後に、下の欄の名前と背景 */
    public static Component title(String text, int rows, Bag bag) {
        DialogLine line = new DialogLine(SPACE_FONT, SPACE_POS, SPACE_NEG);
        line.text(text, LABEL);
        append(line, rows, bag);
        return line.build().shadowColor(ShadowColor.none());
    }
}
