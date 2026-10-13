package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 隠れ家の画面のパネル: どの行も行の幅を超えず、ちょうど行の幅で終わる (クライアントが途中で改行しない・中央揃えでずれない)。
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す (IEM_shader/tools/dialog_render.py で絵にする)
 */
public final class HideoutDialogLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean[] built(int... indices) {
        boolean[] b = new boolean[HideoutFacility.values().length];
        for (int i : indices) b[i] = true;
        return b;
    }

    public static void main(String[] args) throws Exception {
        int stash = HideoutFacility.STASH.ordinal();
        List<HideoutDialog.PanelView> views = List.of(
                // 保管庫 Lv.1 → Lv.2 (足りる)
                new HideoutDialog.PanelView("保管庫", 1, 4, stash, built(0, 2, 4, 7, 8, 9), "スタッシュを開ける棚",
                        true, 25_000, 72_550, null),
                // 保管庫 未設置 (足りない)
                new HideoutDialog.PanelView("保管庫", 0, 4, stash, built(), "スタッシュを開ける棚",
                        true, 5_000, 1_200, "所持金が足りません"),
                // スキルツリー (条件は未定・建築は Lv.1 まで)
                new HideoutDialog.PanelView("スキルツリー", 1, 3, HideoutFacility.SKILL_TREE.ordinal(), built(6), null,
                        false, 0, 72_550, "Lv.2 の建築はまだ作られていません"),
                // 建物 (改良が 9 まで。目盛りは文字に重なるので出さない)
                new HideoutDialog.PanelView("建物", 2, 9, 0, built(0), null, false, 0, 0, null),
                // 最大レベル
                new HideoutDialog.PanelView("弾薬製造", 1, 1, HideoutFacility.AMMO.ordinal(), built(5), null, false, 0, 0, "最大レベルです"));

        for (HideoutDialog.PanelView v : views) {
            List<HideoutDialog.Line> lines = HideoutDialog.panelLines(v);
            check(lines.size() == HideoutDialog.LINES, "line count");
            for (int i = 0; i < lines.size(); i++) {
                HideoutDialog.Line l = lines.get(i);
                check(l.max <= HideoutDialog.LINE_W && l.x == HideoutDialog.LINE_W,
                        v.label() + " line " + i + ": max " + l.max + " end " + l.x);
            }
        }
        check(HideoutDialog.upgradeLabel(views.get(0)).equals("強化する (Lv.2)"), "upgrade label");
        check(HideoutDialog.upgradeLabel(views.get(1)).equals("設置する"), "build label");
        check(HideoutDialog.upgradeLabel(views.get(4)).equals("最大レベル"), "max label");
        // 空白の字: 戻す・進めるが行き来できる
        for (int n = -300; n <= 500; n++) {
            int sum = 0;
            for (char c : HideoutDialog.spaces(n).toCharArray()) sum += c >= '' ? -(1 << (c - '')) : (1 << (c - ''));
            check(sum == n, "spaces " + n);
        }

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            var gson = GsonComponentSerializer.gson();
            List<String> screens = new ArrayList<>();
            for (HideoutDialog.PanelView v : views) {
                Component body = Component.join(JoinConfiguration.newlines(), HideoutDialog.panelLines(v).stream().map(HideoutDialog.Line::build).toList());
                screens.add("{\"title\":\"隠れ家 ― " + v.label() + "\",\"footer\":[\"" + HideoutDialog.upgradeLabel(v) + "\",\"閉じる\"],"
                        + "\"elements\":[{\"kind\":\"text\",\"width\":" + HideoutDialog.BODY_W + ",\"component\":" + gson.serialize(body) + "}]}");
            }
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("HideoutDialogLayoutTest OK");
    }
}
