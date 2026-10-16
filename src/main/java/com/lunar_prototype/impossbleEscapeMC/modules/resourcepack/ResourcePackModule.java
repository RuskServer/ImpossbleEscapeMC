package com.lunar_prototype.impossbleEscapeMC.modules.resourcepack;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

/**
 * サーバーのリソースパック (config の resource-pack)。参加した人に送る。
 * 起動時にパックを別スレッドで読んで SHA-1 を計算しておき、送る時に付ける (パックが更新された時に、クライアントが読み直す)。
 * 計算が終わる前に参加した人には、ハッシュなしで送る
 */
public class ResourcePackModule implements IModule, Listener {

    private final ImpossbleEscapeMC plugin;
    /** パックの SHA-1 (計算が終わるまで null。別スレッドで書いてメインスレッドで読む) */
    private volatile byte[] resourcePackHash = null;

    public ResourcePackModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnable(ServiceContainer container) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        computeHashAsync();
    }

    @Override
    public void onDisable() {
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String url = plugin.getConfig().getString("resource-pack.url");

        if (url == null || url.isEmpty()) return;

        String prompt = plugin.getConfig().getString("resource-pack.prompt", "Apply resource pack");
        boolean force = plugin.getConfig().getBoolean("resource-pack.force", false);

        // 1.17以降推奨のプロンプト付き適用メソッド
        // ハッシュ値があると、更新時に再ダウンロードを促せます
        player.setResourcePack(url, resourcePackHash, prompt, force);
    }

    private void computeHashAsync() {
        String urlStr = plugin.getConfig().getString("resource-pack.url");
        if (urlStr == null || urlStr.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                plugin.getLogger().info("Computing resource pack SHA-1 hash...");
                URL url = new URI(urlStr).toURL();
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream is = url.openStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                    }
                }

                resourcePackHash = digest.digest();
                plugin.getLogger().info("Successfully computed resource pack hash.");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to compute resource pack hash: " + e.getMessage());
            }
        });
    }
}
