package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * チェスト画面の背景のタイトル (背景のタイル + 名前・案内の字) を JSON で書き出す (サーバーは要らない)。
 * IEM_shader/tools/dialog_render.py がクライアントと同じ規則で絵にする。使い方: java ... GuiBackgroundDump <出力先.json>
 */
public final class GuiBackgroundDump {
    public static void main(String[] args) throws Exception {
        List<String> screens = new ArrayList<>();
        Object[][] samples = {{GuiBackground.PDA, 3, null}, {GuiBackground.PARTY, 3, null}, {GuiBackground.STASH_6, 6, "2/5"},
                {GuiBackground.MARKET_PRICE, 3, null}, {GuiBackground.MARKET_SELL, 5, null}, {GuiBackground.SUPPLY, 4, "12:00"},
                {GuiBackground.ATTACHMENT, 5, null}, {GuiBackground.PARTY_INVITE, 6, "1/2"}};
        for (Object[] s : samples) {
            GuiBackground bg = (GuiBackground) s[0];
            Component title = s[2] != null ? bg.title((String) s[2]) : bg.title();
            screens.add("{\"title\":\"\",\"footer\":[],\"elements\":[{\"kind\":\"chest\",\"rows\":" + s[1] + ",\"items\":0,\"component\":"
                    + GsonComponentSerializer.gson().serialize(title) + "}]}");
        }
        Files.writeString(Path.of(args[0]), "{\"screens\":[" + String.join(",", screens) + "]}", StandardCharsets.UTF_8);
        System.out.println("dumped " + screens.size() + " backgrounds");
    }
}
