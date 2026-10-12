package com.lunar_prototype.impossbleEscapeMC.modules.trader;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * トレーダーの画面 (ダイアログ) の文字の組み立てを確かめる。サーバーは要らない。
 * <ul>
 *   <li>バニラの字の幅の表が読めて、代表的な字の幅がクライアントと同じ</li>
 *   <li>空白の組み合わせ (±2^i) が狙いの幅になる</li>
 *   <li>上の端末・品物の行・確認の行のどの行も、決まった幅ちょうどになる (中央揃えでもずれない)</li>
 * </ul>
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、作った文字を JSON で書き出す
 * (IEM_shader/tools/dialog_render.py がクライアントと同じ規則で絵にする)
 */
public final class TraderDialogLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        // バニラの字の幅
        check(FontWidths.width("A") == 6, "A " + FontWidths.width("A"));
        check(FontWidths.width("i") == 2, "i");
        check(FontWidths.width(" ") == 4, "space");
        check(FontWidths.width("あ弾ア") == 27, "kana/kanji 9 each");
        check(FontWidths.width("₽") == 7, "ruble");
        check(FontWidths.width("1,234") == 26, "digits " + FontWidths.width("1,234"));
        check(FontWidths.truncate("とても長い名前の品物です", 50).endsWith("…"), "truncate");
        check(FontWidths.width(FontWidths.truncate("とても長い名前の品物です", 50)) <= 50, "truncate width");

        // 空白の組み合わせ
        for (int n : new int[]{-330, -25, -1, 0, 1, 7, 298, 1023}) {
            String s = TraderDialog.spaces(n);
            int total = 0;
            for (char c : s.toCharArray()) {
                int i = c >= '' ? c - '' : c - '';
                total += c >= '' ? -(1 << i) : (1 << i);
            }
            check(total == n, "spaces " + n + " -> " + total);
        }

        // 行の幅: Line はどの行も決まった幅で終わる
        TraderDialog.Line line = new TraderDialog.Line();
        line.stamp('', 22, -25).moveTo(5).text("AK-74M", null).moveTo(298);
        check(line.x == 298, "line width " + line.x);
        TraderDialog.Line boxes = new TraderDialog.Line().moveTo(8);
        boxes.box("アタッチメント 1", 0, null, null, null);
        check(boxes.x == 8 + 96, "box width " + boxes.x);   // 字の幅 73 + 8 は 80 に入らない

        // 見本の画面
        TraderDialog.HeaderView header = new TraderDialog.HeaderView("KOVACS", 72550, 12, 17, 8, "種類", false,
                "武器を修理する", List.of(new TraderDialog.Tab("すべて 30", true, "tab/all"),
                new TraderDialog.Tab("銃 4", false, "tab/gun"), new TraderDialog.Tab("弾薬 23", false, "tab/ammo"),
                new TraderDialog.Tab("装備 2", false, "tab/gear"), new TraderDialog.Tab("アタッチメント 1", false, "tab/attachment")));
        var amber = net.kyori.adventure.text.format.TextColor.color(0xDEAC4E);
        var note = net.kyori.adventure.text.format.TextColor.color(0xAAA48C);
        var ok = net.kyori.adventure.text.format.TextColor.color(0x78CD60);
        var ng = net.kyori.adventure.text.format.TextColor.color(0xD64A34);
        List<TraderDialog.RowView> rows = List.of(
                new TraderDialog.RowView("AK-74M", "15,000₽", false, false, "item/0",
                        List.of(new TraderDialog.Part("装弾数 30", note), new TraderDialog.Part("サイズ 4マス", note)), "制限なし", false),
                new TraderDialog.RowView("UZI", "22,000₽", false, true, null,
                        List.of(new TraderDialog.Part("装弾数 32", note), new TraderDialog.Part("サイズ 2マス", note)), "売り切れ (本日 0/4)", true),
                new TraderDialog.RowView("5.45x39mm PS弾", "150₽", false, false, "item/12",
                        List.of(new TraderDialog.Part("5.45x39mm", amber), new TraderDialog.Part("貫通 3", note),
                                new TraderDialog.Part("1マスに60発", note)), "本日 残り 40/60", false),
                new TraderDialog.RowView("SVD", "65,000₽", true, false, null,
                        List.of(new TraderDialog.Part("Lv.10", ng), new TraderDialog.Part("クエスト「精密射手 II」", ok)), null, false));
        TraderDialog.ConfirmView confirm = new TraderDialog.ConfirmView("5.45x39mm PS弾", "150₽", "72,550₽", 5, 60, "制限なし", false);

        Component headerComponent = TraderDialog.header(header);
        check(PlainTextComponentSerializer.plainText().serialize(headerComponent).split("\n").length == 6, "header lines");
        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            StringBuilder json = new StringBuilder("[");
            json.append("{\"kind\":\"header\",\"width\":338,\"component\":").append(GsonComponentSerializer.gson().serialize(headerComponent)).append('}');
            for (TraderDialog.RowView row : rows) {
                json.append(",{\"kind\":\"row\",\"width\":306,\"locked\":").append(row.locked())
                        .append(",\"component\":").append(GsonComponentSerializer.gson().serialize(TraderDialog.row(row))).append('}');
            }
            json.append(",{\"kind\":\"confirm\",\"width\":306,\"component\":")
                    .append(GsonComponentSerializer.gson().serialize(TraderDialog.confirmRow(confirm))).append("}]");
            Files.writeString(Path.of(dump), json.toString(), StandardCharsets.UTF_8);
        }
        System.out.println("TraderDialogLayoutTest: font widths, spaces, line widths and sample screens passed");
    }
}
