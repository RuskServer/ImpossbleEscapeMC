package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ResourcePackListener implements Listener {

    private final ImpossbleEscapeMC plugin;
    private byte[] resourcePackHash = null;

    public ResourcePackListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    public void setResourcePackHash(byte[] hash) {
        this.resourcePackHash = hash;
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
}