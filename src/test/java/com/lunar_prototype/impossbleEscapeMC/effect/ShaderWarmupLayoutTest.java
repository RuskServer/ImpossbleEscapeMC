package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.gui.DialogLine;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * シェーダー準備の画面 ({@link ShaderWarmup#bodyLines}) の、どの行も途中で幅を超えず、ちょうど行の幅で終わることを確かめる。
 * 環境変数 IEM_DIALOG_DUMP にファイル名を渡すと、見本の画面を JSON で書き出す (tools/dialog_render.py で絵にする)
 */
public final class ShaderWarmupLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void checkLines(List<DialogLine> lines, String what) {
        for (DialogLine l : lines) {
            check(l.max <= ShaderWarmup.LINE_W && l.x == ShaderWarmup.LINE_W, what + " " + l.max + " / " + l.x + " (幅 " + ShaderWarmup.LINE_W + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        int total = 18;
        List<ShaderWarmup.View> views = List.of(
                new ShaderWarmup.View(0, total, "cover", 0),
                new ShaderWarmup.View(1, total, "grade_visor", 1),
                new ShaderWarmup.View(7, total, "grade_visor_rain_6", 7),
                new ShaderWarmup.View(13, total, "muzzle_flash_hip", 13),
                new ShaderWarmup.View(17, total, "lore_intro", 17),
                new ShaderWarmup.View(total, total, "", total),
                // 長い呼び名は切る
                new ShaderWarmup.View(5, total, "grade_night_vision_camera", 5));
        for (ShaderWarmup.View v : views) {
            List<DialogLine> lines = ShaderWarmup.bodyLines(v);
            check(lines.size() == 15, "行数 " + lines.size());
            checkLines(lines, "step " + v.done());
        }

        String dump = System.getenv("IEM_DIALOG_DUMP");
        if (dump != null) {
            var gson = GsonComponentSerializer.gson();
            List<String> screens = new ArrayList<>();
            for (int i : new int[]{0, 2, 4, 5}) {
                ShaderWarmup.View v = views.get(i);
                boolean finished = v.done() >= v.total();
                screens.add("{\"title\":\"" + (finished ? "準備完了" : "シェーダーを準備しています") + "\",\"footer\":[\"スキップ\"],\"elements\":[{\"kind\":\"header\",\"width\":"
                        + ShaderWarmup.BODY_W + ",\"component\":" + gson.serialize(ShaderWarmup.body(v)) + "}]}");
            }
            Files.writeString(Path.of(dump), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        }
        System.out.println("ShaderWarmupLayoutTest: every line fits its width");
    }
}
