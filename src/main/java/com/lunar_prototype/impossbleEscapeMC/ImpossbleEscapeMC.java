package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.ai.BrainManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.command.AttachmentCommand;
import com.lunar_prototype.impossbleEscapeMC.command.GetItemCommand;
import com.lunar_prototype.impossbleEscapeMC.command.ScavCommand;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUIListener;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.listener.GunListener;
import com.lunar_prototype.impossbleEscapeMC.listener.PlayerListener;
import com.lunar_prototype.impossbleEscapeMC.listener.ResourcePackListener;
import com.lunar_prototype.impossbleEscapeMC.util.CrossbowTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;

public final class ImpossbleEscapeMC extends JavaPlugin {

    public static ImpossbleEscapeMC getInstance() {
        return instance;
    }

    private static ImpossbleEscapeMC instance;

    public ScavSpawner getScavSpawner() {
        return scavSpawner;
    }

    private ScavSpawner scavSpawner;

    private GunListener gunListener;
    private ResourcePackListener resourcePackListener;

    @Override
    public void onEnable() {
        // Plugin startup logic
        instance = this;
        saveDefaultConfig(); // Config must be saved first

        BrainManager.init(this); // 追加
        ItemRegistry.loadAllItems(this);
        gunListener = new GunListener(this);
        scavSpawner = new ScavSpawner(this, gunListener);
        resourcePackListener = new ResourcePackListener(this);

        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(), this);
        getServer().getPluginManager().registerEvents(scavSpawner, this);
        getServer().getPluginManager().registerEvents(new AttachmentGUIListener(), this);
        getCommand("getitem").setExecutor(new GetItemCommand());
        getCommand("scavspawn").setExecutor(new ScavCommand(this));
        getCommand("attachment").setExecutor(new AttachmentCommand());
        CrossbowTask.start(this);

        asyncComputeResourcePackHash();
    }

    private void asyncComputeResourcePackHash() {
        String urlStr = getConfig().getString("resource-pack.url");
        if (urlStr == null || urlStr.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                getLogger().info("Computing resource pack SHA-1 hash...");
                URL url = new URI(urlStr).toURL();
                MessageDigest digest = MessageDigest.getInstance("SHA-1");

                try (InputStream is = url.openStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, n);
                    }
                }

                byte[] hash = digest.digest();
                resourcePackListener.setResourcePackHash(hash);
                getLogger().info("Successfully computed resource pack hash.");
            } catch (Exception e) {
                getLogger().warning("Failed to compute resource pack hash: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (scavSpawner != null) {
            scavSpawner.cleanup();
        }
    }
}
