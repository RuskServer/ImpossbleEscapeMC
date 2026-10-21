package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * コンパスの題名を、クライアントと同じ進め方 (iem:hud の文字は 幅 + 1、空白は決まった幅、バニラの数字は 6、コロンは 2) で
 * 並べ直し、帯の幅・方位・脱出地点とタスクの地点の印・残り時間の位置を確かめる。サーバーは要らない
 */
public final class RaidHudTest {
    private static final Key HUD = Key.key("iem", "hud");
    /** hud_compass.py の文字の幅 */
    private static final Map<Character, Integer> WIDTH = new HashMap<>();

    static {
        WIDTH.put('', 181);
        WIDTH.put('', 1);
        char[] directions = {'', '', '', '', '', '', '', ''};
        int[] widths = {5, 5, 5, 5, 8, 7, 9, 10};
        for (int i = 0; i < directions.length; i++) WIDTH.put(directions[i], widths[i]);
        WIDTH.put('', 3);
        WIDTH.put('', 11);
        WIDTH.put('', 4);
        WIDTH.put('', 4);
        for (int i = 0; i < 10; i++) WIDTH.put((char) (0xE410 + i), 3);
        WIDTH.put('', 5);
        WIDTH.put('', 9);
        WIDTH.put('', 4);
        WIDTH.put('', 4);
        for (int i = 0; i < 10; i++) WIDTH.put((char) (0xE430 + i), 3);
        WIDTH.put('', 5);
    }

    private record Placed(char ch, int x, TextColor color) {
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** 題名の文字を、置かれる位置 (帯の左端からの GUI px) と一緒に並べる。戻り値の最後は全体の幅 */
    private static int layout(Component title, List<Placed> placed) {
        int cursor = 0;
        for (Component child : title.children()) {
            String text = ((TextComponent) child).content();
            boolean hud = HUD.equals(child.style().font());
            for (char c : text.toCharArray()) {
                if (hud && c >= '' && c <= '') {
                    cursor += 1 << (c - '');
                } else if (hud && c >= '' && c <= '') {
                    cursor -= 1 << (c - '');
                } else if (hud) {
                    Integer width = WIDTH.get(c);
                    check(width != null, "unknown glyph " + Integer.toHexString(c));
                    placed.add(new Placed(c, cursor, child.style().color()));
                    cursor += width + 1;
                } else {
                    placed.add(new Placed(c, cursor, child.style().color()));
                    cursor += c == ':' ? 2 : 6;
                }
            }
        }
        return cursor;
    }

    private static int xOf(List<Placed> placed, char ch) {
        return placed.stream().filter(p -> p.ch() == ch).mapToInt(Placed::x).findFirst().orElse(-1);
    }

    public static void main(String[] args) {
        // 北を向き、真北 100m に脱出地点
        List<Placed> placed = new ArrayList<>();
        int width = layout(RaidHud.compose(180, List.of(new double[]{0, -100}), List.of(), 600), placed);
        check(width == RaidHud.STRIP, "strip width " + width);
        check(xOf(placed, '') == 88, "N centered: " + xOf(placed, ''));
        check(xOf(placed, '') == 85, "extract centered: " + xOf(placed, ''));
        check(xOf(placed, '') == -1, "E at +90 is outside the visible 86 degrees");
        // 東を向くと、北の脱出地点は左の端の矢印
        placed.clear();
        layout(RaidHud.compose(-90, List.of(new double[]{0, -100}), List.of(), 600), placed);
        check(xOf(placed, '') == 0, "left edge arrow");
        check(xOf(placed, '') == -1, "no in-strip marker behind");
        // 西を向くと右の端
        placed.clear();
        layout(RaidHud.compose(90, List.of(new double[]{0, -100}), List.of(), 600), placed);
        check(xOf(placed, '') == RaidHud.STRIP - 4, "right edge arrow");
        // 残り時間: 10:00 は白の印の色、帯の中央の 6px 左で終わる。2分以下は赤
        placed.clear();
        layout(RaidHud.compose(0, List.of(), List.of(), 600), placed);
        List<Placed> timer = placed.stream().filter(p -> p.color() != null && p.color().equals(TextColor.color(240, 240, 241))).toList();
        check(timer.size() == 5, "timer chars " + timer.size());
        Placed last = timer.getLast();
        check(last.x() + 6 == 90 + 1 - 6, "timer ends left of center: " + (last.x() + 6));
        placed.clear();
        layout(RaidHud.compose(0, List.of(), List.of(), 120), placed);
        check(placed.stream().anyMatch(p -> TextColor.color(255, 80, 65).equals(p.color())), "warning color under 2 min");
        // タスクの地点: 北を向き、真北 45m は琥珀色の菱形 (幅 9 の中央) と琥珀色の数字。真南は端の琥珀色の矢印
        placed.clear();
        layout(RaidHud.compose(180, List.of(), List.of(new double[]{0, -45}), 600), placed);
        check(xOf(placed, '') == 86, "quest marker centered: " + xOf(placed, ''));
        check(xOf(placed, '') >= 0 && xOf(placed, '') >= 0 && xOf(placed, '') >= 0, "quest distance 45m");
        check(xOf(placed, '') == -1 && xOf(placed, '') == -1, "no extract glyphs for a quest");
        placed.clear();
        layout(RaidHud.compose(180, List.of(), List.of(new double[]{-1, 100}), 600), placed);
        check(xOf(placed, '') == 0, "quest left edge arrow");
        placed.clear();
        layout(RaidHud.compose(180, List.of(), List.of(new double[]{1, 100}), 600), placed);
        check(xOf(placed, '') == RaidHud.STRIP - 4, "quest right edge arrow");
        // どの向きでも幅は帯と同じ (中央揃えがずれない)
        for (int yaw = -180; yaw < 180; yaw += 7) {
            check(layout(RaidHud.compose(yaw, List.of(new double[]{30, 40}, new double[]{-500, 2}),
                    List.of(new double[]{-12, 7}, new double[]{800, -3000}), yaw + 300), new ArrayList<>())
                    == RaidHud.STRIP, "width at yaw " + yaw);
        }
        System.out.println("RaidHudTest: strip width, headings, extract and quest markers, edge arrows and timer placement passed");
    }
}
