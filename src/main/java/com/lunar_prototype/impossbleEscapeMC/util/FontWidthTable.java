package com.lunar_prototype.impossbleEscapeMC.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * バニラの既定のフォント (minecraft:default) の、字ごとの送り幅 (GUI の画素) の表を、クライアントのフォントから作る
 * ({@link FontWidths} が読む。表はプラグインに入れず、サーバーで作る: {@link FontWidthCache})。
 * <p>
 * クライアントの計算 (26.3 の表示と同じになるようにしたもの):
 * <ul>
 *   <li>字はプロバイダーを上から探し、最初に持っているものを使う: include/space → include/default (絵の字) → include/unifont</li>
 *   <li>絵の字: 幅 = 塗られている一番右の列 + 1、送り = (int)(0.5 + 幅 x 倍率) + 1。倍率 = height / 絵の1マスの高さ</li>
 *   <li>unifont (unihex): 幅 = right - left + 1 (上書きの範囲はその値、ほかは塗られている範囲)、送り = 幅 / 2 + 1 (整数)</li>
 *   <li>「Unicode フォントを強制」の時だけのプロバイダー (filter.uniform) と日本語の字形 (filter.jp。送りは同じ) は飛ばす</li>
 * </ul>
 * 表の 1 行は「開始 終了 送り 種類」(16 進の字の番号、送りは整数、種類は b = 絵の字 / u = unifont / s = 空白)。
 * 手元で作る時: {@code java -cp <プラグインと依存> ...FontWidthTable <クライアントの jar> <assets フォルダ> <アセットの index の ID> <出力先>}
 */
public final class FontWidthTable {

    /** アセット (assets/ より下のパス。例 minecraft/font/default.json) を読む */
    public interface Assets {
        byte[] read(String path) throws IOException;
    }

    /** 送りの同じ字の続き */
    public record Run(int from, int to, int advance, char kind) {
    }

    private final Assets assets;
    private final Map<Integer, int[]> widths = new TreeMap<>();   // 字 → {送り, 種類}

    private FontWidthTable(Assets assets) {
        this.assets = assets;
    }

    /** 表を作る */
    public static List<Run> generate(Assets assets) throws IOException {
        FontWidthTable table = new FontWidthTable(assets);
        table.loadFont("minecraft:default");
        List<Run> runs = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : table.widths.entrySet()) {
            int cp = e.getKey(), advance = e.getValue()[0];
            char kind = (char) e.getValue()[1];
            Run last = runs.isEmpty() ? null : runs.getLast();
            if (last != null && last.to() == cp - 1 && last.advance() == advance && last.kind() == kind) {
                runs.set(runs.size() - 1, new Run(last.from(), cp, advance, kind));
            } else {
                runs.add(new Run(cp, cp, advance, kind));
            }
        }
        return runs;
    }

    /** 表の文字列 (FontWidths が読む形) */
    public static String format(List<Run> runs, String source) {
        StringBuilder out = new StringBuilder("# バニラの既定のフォントの送り幅 (FontWidthTable が " + source + " から作る)\n");
        for (Run r : runs) {
            out.append(Integer.toHexString(r.from()).toUpperCase()).append(' ').append(Integer.toHexString(r.to()).toUpperCase())
                    .append(' ').append(r.advance()).append(' ').append(r.kind()).append('\n');
        }
        return out.toString();
    }

    /**
     * 手元のクライアントの jar と assets フォルダ (indexes/ と objects/ がある所) から読む。
     * jar に無い物と、jar の中の空の include/unifont.json (本物はアセットにある) はアセットから読む
     */
    public static Assets local(ZipFile clientJar, Path assetsDir, String indexId) throws IOException {
        JsonObject objects = JsonParser.parseString(Files.readString(assetsDir.resolve("indexes/" + indexId + ".json")))
                .getAsJsonObject().getAsJsonObject("objects");
        return path -> {
            byte[] inJar = readJar(clientJar, path);
            if (inJar != null) return inJar;
            JsonObject object = objects.getAsJsonObject(path);
            if (object == null) throw new IOException("アセットにありません: " + path);
            String hash = object.get("hash").getAsString();
            return Files.readAllBytes(assetsDir.resolve("objects/" + hash.substring(0, 2) + "/" + hash));
        };
    }

    /** jar の中の物 (無い・空の unifont.json なら null) */
    static byte[] readJar(ZipFile jar, String path) throws IOException {
        ZipEntry entry = jar.getEntry("assets/" + path);
        if (entry == null) return null;
        try (InputStream in = jar.getInputStream(entry)) {
            byte[] data = in.readAllBytes();
            if (path.endsWith("unifont.json") && !new String(data, StandardCharsets.UTF_8).contains("\"type\"")) return null;
            return data;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("使い方: FontWidthTable <クライアントの jar> <assets フォルダ> <アセットの index の ID> <出力先>");
            System.exit(2);
        }
        try (ZipFile jar = new ZipFile(args[0])) {
            List<Run> runs = generate(local(jar, Path.of(args[1]), args[2]));
            Files.writeString(Path.of(args[3]), format(runs, Path.of(args[0]).getFileName().toString()));
            System.out.println("FontWidthTable: " + runs.size() + " runs");
        }
    }

    // ---- フォントの読み方 (クライアントと同じ順) ----

    private void add(int cp, int advance, char kind) {
        widths.putIfAbsent(cp, new int[]{advance, kind});
    }

    /** 「ns:path」を「ns/folder/path」に */
    private byte[] resource(String id, String folder, String ext) throws IOException {
        int colon = id.indexOf(':');
        String ns = colon >= 0 ? id.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        return assets.read(ns + "/" + (folder.isEmpty() ? "" : folder + "/") + path + ext);
    }

    private void loadFont(String id) throws IOException {
        JsonObject font = JsonParser.parseString(new String(resource(id, "font", ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement element : font.getAsJsonArray("providers")) {
            JsonObject p = element.getAsJsonObject();
            JsonObject filter = p.getAsJsonObject("filter");
            if (filter != null && (isTrue(filter, "uniform") || isTrue(filter, "jp"))) continue;
            switch (p.get("type").getAsString()) {
                case "reference" -> loadFont(p.get("id").getAsString());
                case "space" -> {
                    for (Map.Entry<String, JsonElement> e : p.getAsJsonObject("advances").entrySet()) {
                        add(e.getKey().codePointAt(0), e.getValue().getAsInt(), 's');
                    }
                }
                case "bitmap" -> bitmap(p);
                case "unihex" -> unihex(p);
                default -> {
                }
            }
        }
    }

    private static boolean isTrue(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isBoolean() && o.get(key).getAsBoolean();
    }

    private void bitmap(JsonObject p) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(resource(p.get("file").getAsString(), "textures", "")));
        JsonArray chars = p.getAsJsonArray("chars");
        List<int[]> rows = new ArrayList<>();
        for (JsonElement row : chars) rows.add(row.getAsString().codePoints().toArray());
        int cellH = img.getHeight() / rows.size();
        int cellW = img.getWidth() / rows.getFirst().length;
        int height = p.has("height") ? p.get("height").getAsInt() : 8;
        double scale = (double) height / cellH;
        for (int r = 0; r < rows.size(); r++) {
            int[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                int cp = row[c];
                if (cp == 0 || widths.containsKey(cp)) continue;
                int actual = 0;
                for (int x = cellW - 1; x >= 0 && actual == 0; x--) {
                    for (int y = 0; y < cellH; y++) {
                        if ((img.getRGB(c * cellW + x, r * cellH + y) >>> 24) != 0) {
                            actual = x + 1;
                            break;
                        }
                    }
                }
                add(cp, (int) (0.5 + actual * scale) + 1, 'b');
            }
        }
    }

    private void unihex(JsonObject p) throws IOException {
        List<int[]> overrides = new ArrayList<>();   // {from, to, left, right}
        if (p.has("size_overrides")) {
            for (JsonElement e : p.getAsJsonArray("size_overrides")) {
                JsonObject o = e.getAsJsonObject();
                overrides.add(new int[]{o.get("from").getAsString().codePointAt(0), o.get("to").getAsString().codePointAt(0),
                        o.get("left").getAsInt(), o.get("right").getAsInt()});
            }
        }
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(resource(p.get("hex_file").getAsString(), "", "")))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".hex")) continue;
                for (String line : new String(zip.readAllBytes(), StandardCharsets.US_ASCII).split("\n")) {
                    unihexLine(line.trim(), overrides);
                }
            }
        }
    }

    private void unihexLine(String line, List<int[]> overrides) {
        int colon = line.indexOf(':');
        if (colon <= 0 || !isHex(line, 0, colon) || colon + 1 >= line.length()) return;
        int end = colon + 1;
        while (end < line.length() && Character.digit(line.charAt(end), 16) >= 0) end++;
        if (end == colon + 1) return;
        int cp = Integer.parseInt(line.substring(0, colon), 16);
        if (widths.containsKey(cp)) return;
        String hex = line.substring(colon + 1, end);
        int w = hex.length() * 4 / 16;
        int left = -1, right = -1;
        for (int[] o : overrides) {
            if (o[0] <= cp && cp <= o[1]) {
                left = o[2];
                right = o[3];
                break;
            }
        }
        if (left < 0) {
            boolean[] cols = new boolean[w];
            int digits = w / 4;
            for (int row = 0; row < 16; row++) {
                long v = Long.parseLong(hex.substring(row * digits, (row + 1) * digits), 16);
                for (int x = 0; x < w; x++) {
                    if ((v & (1L << (w - 1 - x))) != 0) cols[x] = true;
                }
            }
            left = 0;
            right = 0;
            for (int x = 0; x < w; x++) {
                if (cols[x]) {
                    left = x;
                    break;
                }
            }
            for (int x = w - 1; x >= 0; x--) {
                if (cols[x]) {
                    right = x;
                    break;
                }
            }
        }
        add(cp, (right - left + 1) / 2 + 1, 'u');
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) if (Character.digit(s.charAt(i), 16) < 0) return false;
        return true;
    }
}
