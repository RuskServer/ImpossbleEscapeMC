package com.lunar_prototype.impossbleEscapeMC.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.ZipFile;

/**
 * 文字の幅の表 ({@link FontWidths}) を、サーバーのバージョンのクライアントのフォントから作って、データフォルダーに置く。
 * <p>
 * 表が無い時 (初めての起動・バージョンを上げた時) だけ、Mojang の配布元からそのバージョンのクライアントの jar と
 * フォントのアセットを取ってきて作る (jar は作り終えたら消す)。取れなければ表なしで動く (ダイアログの字の位置がずれる)
 */
public final class FontWidthCache {

    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String RESOURCES = "https://resources.download.minecraft.net/";
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private FontWidthCache() {
    }

    /** 表を読み込む (無ければ作る)。起動の最初に呼ぶ */
    public static void load(Plugin plugin) {
        String version = Bukkit.getMinecraftVersion();
        Path table = plugin.getDataFolder().toPath().resolve("font").resolve("default_widths-" + version + ".txt");
        try {
            if (!Files.isRegularFile(table)) {
                plugin.getLogger().info("文字の幅の表を作ります (" + version + " のクライアントのフォントを Mojang から取得。初回だけ)");
                long start = System.currentTimeMillis();
                generate(version, table);
                plugin.getLogger().info("文字の幅の表を作りました (" + (System.currentTimeMillis() - start) / 1000 + " 秒): " + table);
            }
            FontWidths.install(table);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.SEVERE, "文字の幅の表を用意できませんでした。ダイアログの文字の位置がずれます", e);
        }
    }

    static void generate(String version, Path table) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        JsonObject versionInfo = null;
        for (JsonElement v : json(http, MANIFEST, null).getAsJsonArray("versions")) {
            JsonObject o = v.getAsJsonObject();
            if (version.equals(o.get("id").getAsString())) {
                versionInfo = json(http, o.get("url").getAsString(), o.get("sha1").getAsString());
                break;
            }
        }
        if (versionInfo == null) throw new IOException("Mojang の一覧にバージョン " + version + " がありません");
        JsonObject client = versionInfo.getAsJsonObject("downloads").getAsJsonObject("client");
        JsonObject assetIndex = versionInfo.getAsJsonObject("assetIndex");
        JsonObject objects = json(http, assetIndex.get("url").getAsString(), assetIndex.get("sha1").getAsString())
                .getAsJsonObject("objects");

        Files.createDirectories(table.getParent());
        Path jarFile = table.resolveSibling("client-" + version + ".jar.tmp");
        try {
            Files.write(jarFile, download(http, client.get("url").getAsString(), client.get("sha1").getAsString()));
            try (ZipFile jar = new ZipFile(jarFile.toFile())) {
                List<FontWidthTable.Run> runs = FontWidthTable.generate(path -> {
                    byte[] inJar = FontWidthTable.readJar(jar, path);
                    if (inJar != null) return inJar;
                    JsonObject object = objects.getAsJsonObject(path);
                    if (object == null) throw new IOException("アセットにありません: " + path);
                    String hash = object.get("hash").getAsString();
                    try {
                        return download(http, RESOURCES + hash.substring(0, 2) + "/" + hash, hash);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException(e);
                    }
                });
                Path tmp = table.resolveSibling(table.getFileName() + ".tmp");
                Files.writeString(tmp, FontWidthTable.format(runs, version + " のクライアント"), StandardCharsets.UTF_8);
                Files.move(tmp, table, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
        } finally {
            Files.deleteIfExists(jarFile);
        }
    }

    private static JsonObject json(HttpClient http, String url, String sha1) throws IOException, InterruptedException {
        return JsonParser.parseString(new String(download(http, url, sha1), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /** 取ってくる (sha1 があれば中身を確かめる) */
    private static byte[] download(HttpClient http, String url, String sha1) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("取得に失敗 (HTTP " + response.statusCode() + "): " + url);
        byte[] body = response.body();
        if (sha1 != null && !sha1.equalsIgnoreCase(sha1Of(body))) throw new IOException("中身が一致しません (sha1): " + url);
        return body;
    }

    private static String sha1Of(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
