package com.lunar_prototype.impossbleEscapeMC.gui;

import net.kyori.adventure.text.format.TextColor;

/**
 * ダイアログの字の色 (くたびれたオリーブの端末と琥珀色の液晶。IEM_shader/tools/font_common.py の色と同じ系統)
 */
public final class TerminalPalette {
    /** 端末の刻印・液晶の地の字 */
    public static final TextColor INK = TextColor.color(0xE0D6AA);
    /** 品物・依頼の名前 */
    public static final TextColor NAME = TextColor.color(0xEEE8D2);
    /** 補足 */
    public static final TextColor NOTE = TextColor.color(0xAAA48C);
    /** 使えない物 (未解放など) */
    public static final TextColor DIM = TextColor.color(0x827E6E);
    public static final TextColor AMBER = TextColor.color(0xDEAC4E);
    public static final TextColor AMBER_DIM = TextColor.color(0x96763C);
    /** 琥珀色の札の上の字 */
    public static final TextColor DARK = TextColor.color(0x18140A);
    /** 足りている・できる */
    public static final TextColor OK = TextColor.color(0x78CD60);
    /** 足りない・できない */
    public static final TextColor NG = TextColor.color(0xD64A34);

    private TerminalPalette() {
    }
}
