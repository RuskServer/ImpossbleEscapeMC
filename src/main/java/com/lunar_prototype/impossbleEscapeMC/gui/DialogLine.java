package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.util.FontWidths;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * リソースパックのフォントの絵とバニラの字を並べて、1 行を「行の頭からの位置 (GUI の画素)」で組み立てる。
 * ダイアログの本文は行ごとに中央揃えになるので、最後に {@link #moveTo} で決まった幅にそろえる (中央揃えでもずれない)。
 * チェストの画面のタイトルにも使える (頭はタイトルの位置)。
 * <p>
 * 空白は、±2^i 進む字 (i = 0..9) を並べたフォントの字で作る。絵の字は「幅 + 1」進む。
 * 行の途中で一度でも折り返し幅を超えるとクライアントが改行するので、{@link #max} で確かめる
 */
public final class DialogLine {
    private final Key spaceFont;
    private final char spacePos;
    private final char spaceNeg;
    private final TextComponent.Builder out = Component.text();
    /** 今の位置 */
    public int x;
    /** 一番右まで進んだ位置 */
    public int max;

    /**
     * @param spaceFont 空白の字があるフォント
     * @param spacePos  +1 進む字 (+ i で +2^i)
     * @param spaceNeg  -1 進む字 (+ i で -2^i)
     */
    public DialogLine(Key spaceFont, char spacePos, char spaceNeg) {
        this.spaceFont = spaceFont;
        this.spacePos = spacePos;
        this.spaceNeg = spaceNeg;
    }

    public DialogLine shift(int n) {
        if (n != 0) out.append(Component.text(spaces(n)).font(spaceFont));
        x += n;
        max = Math.max(max, x);
        return this;
    }

    public DialogLine moveTo(int target) {
        return shift(target - x);
    }

    /** 絵の字を今の位置に描いて進む (幅 + 1)。click・hover は null なら付けない */
    public DialogLine glyph(Key font, char c, int width, ClickEvent click, Component hover) {
        return glyph(font, c, width, NamedTextColor.WHITE, click, hover);
    }

    /** 色を付けた絵の字 (白で描いた絵は、字の色がそのまま絵の色になる) */
    public DialogLine glyph(Key font, char c, int width, TextColor color, ClickEvent click, Component hover) {
        Component g = Component.text(String.valueOf(c)).font(font).color(color).shadowColor(ShadowColor.none());
        if (click != null) g = g.clickEvent(click);
        if (hover != null) g = g.hoverEvent(HoverEvent.showText(hover));
        out.append(g);
        x += width + 1;
        max = Math.max(max, x);
        return this;
    }

    /** 絵の字を at に描き、今の位置に戻る */
    public DialogLine stamp(Key font, char c, int width, int at) {
        return stamp(font, c, width, at, NamedTextColor.WHITE);
    }

    /** 色を付けた絵の字を at に描き、今の位置に戻る */
    public DialogLine stamp(Key font, char c, int width, int at, TextColor color) {
        int back = x;
        moveTo(at);
        glyph(font, c, width, color, null, null);
        return moveTo(back);
    }

    /** 共通アイコンの字 */
    public DialogLine icon(UiIcon icon) {
        out.append(icon.glyph());
        x += UiIcon.GLYPH_ADVANCE;
        max = Math.max(max, x);
        return this;
    }

    public DialogLine text(String s, TextColor color) {
        return text(s, color, null, null);
    }

    public DialogLine text(String s, TextColor color, ClickEvent click, Component hover) {
        Component t = Component.text(s, color);
        if (click != null) t = t.clickEvent(click);
        if (hover != null) t = t.hoverEvent(HoverEvent.showText(hover));
        out.append(t);
        x += FontWidths.width(s);
        max = Math.max(max, x);
        return this;
    }

    /**
     * 別のフォントの字 (バニラの字を縦にずらしたフォント iem:shift_&lt;n&gt; など)。送りはバニラの字と同じ ({@link FontWidths})
     */
    public DialogLine text(Key font, String s, TextColor color, ClickEvent click, Component hover) {
        Component t = Component.text(s, color).font(font);
        if (click != null) t = t.clickEvent(click);
        if (hover != null) t = t.hoverEvent(HoverEvent.showText(hover));
        out.append(t);
        x += FontWidths.width(s);
        max = Math.max(max, x);
        return this;
    }

    /** 右端を right にそろえて字を書く */
    public DialogLine textRight(String s, TextColor color, int right) {
        return moveTo(right - FontWidths.width(s)).text(s, color);
    }

    /**
     * 箱の絵 (幅 width) と、その中央に字。字に影を付けない時は noShadow
     */
    public DialogLine box(Key font, char glyph, int width, String label, TextColor color, boolean noShadow, ClickEvent click, Component hover) {
        int textW = FontWidths.width(label);
        int start = x;
        glyph(font, glyph, width, click, hover);
        moveTo(start + (width - textW) / 2);
        Component t = Component.text(label, color);
        if (noShadow) t = t.shadowColor(ShadowColor.none());
        if (click != null) t = t.clickEvent(click);
        if (hover != null) t = t.hoverEvent(HoverEvent.showText(hover));
        out.append(t);
        x += textW;
        max = Math.max(max, x);
        return moveTo(start + width + 1);
    }

    /**
     * 札の絵 (幅 width) と、その中央に textFont の字 (札の縦の真ん中に来るよう、バニラの字をずらしたフォント iem:shift_&lt;n&gt;)。
     * 札も字も押せる。字に影を付けない時は noShadow
     */
    public DialogLine chip(Key font, char glyph, int width, Key textFont, String label, TextColor color, boolean noShadow,
                           ClickEvent click, Component hover) {
        int textW = FontWidths.width(label);
        int start = x;
        glyph(font, glyph, width, click, hover);
        moveTo(start + (width - textW) / 2);
        Component t = Component.text(label, color).font(textFont);
        if (noShadow) t = t.shadowColor(ShadowColor.none());
        if (click != null) t = t.clickEvent(click);
        if (hover != null) t = t.hoverEvent(HoverEvent.showText(hover));
        out.append(t);
        x += textW;
        max = Math.max(max, x);
        return moveTo(start + width + 1);
    }

    public Component build() {
        return out.build();
    }

    /** n だけ進む (負なら戻る) 空白の文字列 */
    public String spaces(int n) {
        StringBuilder s = new StringBuilder();
        int abs = Math.abs(n);
        char base = n >= 0 ? spacePos : spaceNeg;
        for (int bit = 0; abs > 0; bit++, abs >>= 1) {
            if ((abs & 1) != 0) s.append((char) (base + bit));
        }
        return s.toString();
    }
}
