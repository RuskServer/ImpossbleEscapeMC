package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * 下の欄の名前と背景: タイトルをクライアントと同じ進め方 (iem:gui の空白は ±2^i、背景のタイルは 44 + 1、字はバニラの送り) で並べ直し、
 * タイルが画面の左端から 44 ごとに並ぶこと、名前が 8、使っているマスの右端が 168 に来ること、縦のずらしのフォントが段数に合うことを確かめる。
 * 文字の幅の表 (IEM_FONT_WIDTHS) が要る
 */
public final class InventoryPanelTest {
    private record Placed(String text, Key font, int x) {
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 置かれた字 (画面の左からの位置) */
    private static List<Placed> layout(Component title) {
        List<Placed> out = new ArrayList<>();
        int[] x = {8};
        walk(title, null, x, out);
        return out;
    }

    private static void walk(Component c, Key inherited, int[] x, List<Placed> out) {
        Key font = c.style().font() != null ? c.style().font() : inherited;
        if (c instanceof TextComponent t && !t.content().isEmpty()) {
            String s = t.content();
            if (Key.key("iem", "gui").equals(font)) {
                for (char ch : s.toCharArray()) {
                    if (ch >= '' && ch <= '') x[0] += 1 << (ch - '');
                    else if (ch >= '' && ch <= '') x[0] -= 1 << (ch - '');
                    else throw new AssertionError("unexpected gui char " + Integer.toHexString(ch));
                }
            } else if (Key.key("iem", "inventory_panel").equals(font)) {
                for (char ch : s.toCharArray()) {
                    out.add(new Placed(String.valueOf(ch), font, x[0]));
                    x[0] += 45;
                }
            } else {
                out.add(new Placed(s, font, x[0]));
                x[0] += FontWidths.width(s);
            }
        }
        for (Component child : c.children()) walk(child, font, x, out);
    }

    public static void main(String[] args) {
        check(FontWidths.isLoaded(), "IEM_FONT_WIDTHS is required");

        // いつもの表示: 「インベントリ」を (8, 20 + 18*段数) に
        for (int rows = 1; rows <= 6; rows++) {
            List<Placed> p = layout(InventoryPanel.title("Searching...", rows, null));
            Placed label = p.stream().filter(e -> e.text().equals(InventoryPanel.INVENTORY)).findFirst().orElseThrow();
            check(label.x() == 8, "label x " + label.x());
            check(Key.key("iem", "shift_" + (14 + 18 * rows)).equals(label.font()), "label font " + label.font());
            check(p.stream().noneMatch(e -> Key.key("iem", "inventory_panel").equals(e.font())), "no tiles in normal view");
        }

        // バックパックの表示: タイルは段数ごとの文字で 0・44・88・132、名前は 8、使っているマスは右端 168
        for (int rows = 1; rows <= 6; rows++) {
            List<Placed> p = layout(InventoryPanel.title("Searching...", rows, new InventoryPanel.Bag("MBSS Backpack", 12, 36)));
            List<Placed> tiles = p.stream().filter(e -> Key.key("iem", "inventory_panel").equals(e.font())).toList();
            check(tiles.size() == 8, "eight tiles: " + tiles.size());
            for (int i = 0; i < 8; i++) {
                check(tiles.get(i).x() == (i % 4) * 44, "tile " + i + " x " + tiles.get(i).x());
                check(tiles.get(i).text().charAt(0) == 0xE000 + (rows - 1) * 8 + i, "tile char " + i);
            }
            Placed name = p.stream().filter(e -> e.text().equals("MBSS Backpack")).findFirst().orElseThrow();
            Placed count = p.stream().filter(e -> e.text().equals("12 / 36")).findFirst().orElseThrow();
            check(name.x() == 8, "name x " + name.x());
            check(count.x() + FontWidths.width("12 / 36") == 168, "count right " + (count.x() + FontWidths.width("12 / 36")));
            check(Key.key("iem", "shift_" + (14 + 18 * rows)).equals(count.font()), "count font");
        }

        // 長い名前は使っているマスと重ならないように切る
        List<Placed> p = layout(InventoryPanel.title("x", 3, new InventoryPanel.Bag("Pilgrim Tourist Backpack With A Very Long Name", 45, 45)));
        Placed name = p.stream().filter(e -> e.text().startsWith("Pilgrim")).findFirst().orElseThrow();
        Placed count = p.stream().filter(e -> e.text().equals("45 / 45")).findFirst().orElseThrow();
        check(name.x() + FontWidths.width(name.text()) <= count.x() - 6, "long name truncated: " + name.text());
        check(name.text().endsWith("…"), "ellipsis");
        System.out.println("InventoryPanelTest OK");
    }
}
