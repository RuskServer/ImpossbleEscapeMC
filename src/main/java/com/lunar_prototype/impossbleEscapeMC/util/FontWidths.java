package com.lunar_prototype.impossbleEscapeMC.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * バニラの既定のフォントで、文字列がクライアントの画面で何画素 (GUI の画素) 進むか。
 * <p>
 * ダイアログの本文は行ごとに中央揃えになるので、作り込んだ画面で字を思った位置に置くには幅が要る。
 * 表はプラグインに入れず、サーバーがクライアントのフォントから作って入れる ({@link FontWidthCache} → {@link #install})。
 * テストでは環境変数 IEM_FONT_WIDTHS に表のファイル ({@link FontWidthTable} で作る) を渡す。
 * 表が入るまでは、どの字も unifont の全角の送り ({@link #FALLBACK}) とみなす。
 * 「Unicode フォントを強制」をオンにしたクライアントでは英数字の幅が変わる (その時は少しずれる)
 */
public final class FontWidths {

    /** 表に無い字の送り (unifont の全角と同じ) */
    private static final int FALLBACK = 9;

    /** 基本多言語面 (0..FFFF) の送りと、unifont の字か (太字の足し分が 0.5) */
    private static final byte[] BMP = new byte[0x10000];
    private static final boolean[] UNIHEX = new boolean[0x10000];
    private static final Map<Integer, Integer> SUPPLEMENTARY = new HashMap<>();
    private static volatile boolean loaded;

    static {
        java.util.Arrays.fill(BMP, (byte) FALLBACK);
        java.util.Arrays.fill(UNIHEX, true);
        String path = System.getenv("IEM_FONT_WIDTHS");
        if (path != null && !path.isBlank()) {
            try {
                install(Path.of(path));
            } catch (IOException e) {
                throw new IllegalStateException("IEM_FONT_WIDTHS の表を読めませんでした: " + path, e);
            }
        }
    }

    /** 表のファイルを読み込む */
    public static synchronized void install(Path table) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(table, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.trim().split(" ");
                int from = Integer.parseInt(parts[0], 16);
                int to = Integer.parseInt(parts[1], 16);
                int advance = Integer.parseInt(parts[2]);
                boolean unihex = "u".equals(parts[3]);
                for (int cp = from; cp <= to; cp++) {
                    if (cp < BMP.length) {
                        BMP[cp] = (byte) advance;
                        UNIHEX[cp] = unihex;
                    } else {
                        SUPPLEMENTARY.put(cp, advance);
                    }
                }
            }
        }
        loaded = true;
    }

    /** 表が入っているか */
    public static boolean isLoaded() {
        return loaded;
    }

    private FontWidths() {
    }

    /** 1 字の送り */
    public static float advance(int codePoint, boolean bold) {
        int base;
        boolean unihex;
        if (codePoint < BMP.length) {
            base = BMP[codePoint];
            unihex = UNIHEX[codePoint];
        } else {
            base = SUPPLEMENTARY.getOrDefault(codePoint, FALLBACK);
            unihex = true;
        }
        return bold ? base + (unihex ? 0.5f : 1f) : base;
    }

    /** 文字列の送り (小数のまま) */
    public static float width(String text, boolean bold) {
        float total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            total += advance(cp, bold);
            i += Character.charCount(cp);
        }
        return total;
    }

    /** 文字列の送り (太字でない時は整数) */
    public static int width(String text) {
        return (int) Math.ceil(width(text, false));
    }

    /** 幅が max を超えないように後ろを「…」で切る */
    public static String truncate(String text, int max) {
        if (width(text) <= max) return text;
        float ellipsis = advance('…', false);
        StringBuilder out = new StringBuilder();
        float total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            float a = advance(cp, false);
            if (total + a + ellipsis > max) break;
            out.appendCodePoint(cp);
            total += a;
            i += Character.charCount(cp);
        }
        return out.append('…').toString();
    }
}
