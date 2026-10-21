package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_BODY_W;
import static com.lunar_prototype.impossbleEscapeMC.gui.TerminalFont.ROW_LINE_W;

/**
 * 修理の画面: 上の行・物の行が、行の幅を超えず、ちょうど行の幅で終わる (クライアントが途中で改行しない・中央揃えでずれない)。
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す (IEM_shader/tools/dialog_render.py で絵にする)
 */
public final class RepairDialogLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkWidths(List<DialogLine> lines, int width, String label) {
        for (int i = 0; i < lines.size(); i++) {
            DialogLine l = lines.get(i);
            check(l.max <= width && l.x == width, label + " line " + i + ": max " + l.max + " end " + l.x);
        }
    }

    private static String element(String kind, int width, String icon, List<DialogLine> lines) {
        Component body = Component.join(JoinConfiguration.newlines(), lines.stream().map(DialogLine::build).toList());
        return "{\"kind\":\"" + kind + "\",\"width\":" + width + (icon != null ? ",\"icon\":\"" + icon + "\"" : "")
                + ",\"component\":" + GsonComponentSerializer.gson().serialize(body) + "}";
    }

    public static void main(String[] args) throws Exception {
        RepairDialog.HeaderView header = new RepairDialog.HeaderView(184_500, 3, 21_340);
        RepairDialog.HeaderView poor = new RepairDialog.HeaderView(1_200, 2, 48_000);
        RepairDialog.HeaderView none = new RepairDialog.HeaderView(184_500, 0, 0);
        List<RepairDialog.RowView> rows = List.of(
                new RepairDialog.RowView("[UNTAR] ボディアーマー", "装備中 (胴)", 0.42, false, 8_700, true, true, null, "toggle/0"),
                new RepairDialog.RowView("[FAST-MT] ヘルメット", "装備中 (頭)", 0.81, false, 1_240, false, true, null, "toggle/1"),
                new RepairDialog.RowView("AK-74M", "持ち物", 0.12, true, 11_400, true, true, null, "toggle/2"),
                new RepairDialog.RowView("M700", "持ち物の MBSS Backpack", 0.55, false, 9_100, true, false,
                        "この店では武器を修理できません", "toggle/3"),
                new RepairDialog.RowView("とても長い装備の名前がここに入る場合の確認用の文字列です", "とても長いバックパックの名前の中の場所",
                        0.0, true, 999_999_999, true, true, null, "toggle/4"),
                new RepairDialog.RowView("とても長い装備の名前がここに入る場合の確認用の文字列です", "とても長いバックパックの名前の中の場所",
                        1.0, false, 0, false, false, "この店ではアーマーを修理できません", "toggle/5"));

        for (RepairDialog.HeaderView h : List.of(header, poor, none)) {
            checkWidths(RepairDialog.headerLines(h), RepairDialog.LINE_W, "header " + h);
        }
        for (RepairDialog.RowView r : rows) {
            checkWidths(RepairDialog.rowLines(r), ROW_LINE_W, "row " + r.name());
        }
        check(RepairDialog.price(1234567).equals("1,234,567₽"), "price");

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            List<String> elements = new ArrayList<>();
            elements.add(element("header", 340, null, RepairDialog.headerLines(header)));
            String[] icons = {"mc:iron_chestplate", "mc:iron_helmet", "mc:iron_sword", "mc:iron_sword"};
            for (int i = 0; i < 4; i++) elements.add(element("row", ROW_BODY_W, icons[i], RepairDialog.rowLines(rows.get(i))));
            String screen = "{\"title\":\"修理 ― Bastion\",\"footer\":[\"修理する (3 個 21,340₽)\",\"すべて選ぶ\",\"戻る\"],\"elements\":["
                    + String.join(",", elements) + "]}";
            Files.writeString(Path.of(dump), "{\"screens\":[" + screen + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("RepairDialogLayoutTest OK");
    }
}
