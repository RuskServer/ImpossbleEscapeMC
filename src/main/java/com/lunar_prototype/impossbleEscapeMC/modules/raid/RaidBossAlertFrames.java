package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** リソースパックのtools/boss_alert.pyが生成する、重複排除済みの文字番号表。 */
final class RaidBossAlertFrames {
    private static final char[][] GLYPHS=load();
    private RaidBossAlertFrames() {}
    static char glyph(int frame,int tile){return GLYPHS[frame][tile];}
    private static char[][] load() {
        try(var reader=new InputStreamReader(Objects.requireNonNull(RaidBossAlertFrames.class.getResourceAsStream("/boss_alert_frames.json")),StandardCharsets.UTF_8)) {
            var manifest=JsonParser.parseReader(reader).getAsJsonObject();
            var rows=manifest.getAsJsonArray("glyphs");
            if(rows.size()!=RaidBossAlertState.DURATION||manifest.get("fps").getAsInt()!=20)throw new IllegalArgumentException("Boss animation timing mismatch");
            char[][] result=new char[rows.size()][6];
            for(int i=0;i<result.length;i++) {
                var tiles=rows.get(i).getAsJsonArray();if(tiles.size()!=6)throw new IllegalArgumentException("Invalid boss frame tiles");
                for(int j=0;j<6;j++) {
                    int code=tiles.get(j).getAsInt();if(code<0xE000||code>0xF8FF)throw new IllegalArgumentException("Invalid boss glyph");
                    result[i][j]=(char)code;
                }
            }
            return result;
        }catch(Exception ex){throw new ExceptionInInitializerError(ex);}
    }
}
