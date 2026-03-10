package com.lunar_prototype.impossbleEscapeMC;

import com.lunar_prototype.impossbleEscapeMC.ai.BrainManager;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.command.AttachmentCommand;
import com.lunar_prototype.impossbleEscapeMC.command.GetItemCommand;
import com.lunar_prototype.impossbleEscapeMC.command.ItemReloadCommand;
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
    private com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager minigameManager;
    private com.lunar_prototype.impossbleEscapeMC.raid.RaidManager raidManager;
    private com.lunar_prototype.impossbleEscapeMC.party.PartyManager partyManager;
    private com.lunar_prototype.impossbleEscapeMC.loot.LootManager lootManager;
    private com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI searchGUI;

    public com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager getMinigameManager() {
        return minigameManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.raid.RaidManager getRaidManager() {
        return raidManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.party.PartyManager getPartyManager() {
        return partyManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.LootManager getLootManager() {
        return lootManager;
    }

    public com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI getSearchGUI() {
        return searchGUI;
    }

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
        minigameManager = new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameManager(this);
        raidManager = new com.lunar_prototype.impossbleEscapeMC.raid.RaidManager(this);
        partyManager = new com.lunar_prototype.impossbleEscapeMC.party.PartyManager(this);
        lootManager = new com.lunar_prototype.impossbleEscapeMC.loot.LootManager(this);
        searchGUI = new com.lunar_prototype.impossbleEscapeMC.loot.SearchGUI(this);

        getServer().getPluginManager().registerEvents(gunListener, this);
        getServer().getPluginManager().registerEvents(resourcePackListener, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(scavSpawner, this);
        getServer().getPluginManager().registerEvents(new AttachmentGUIListener(), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameListener(minigameManager), this);
        getServer().getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.raid.RaidSelectionGUI(raidManager), this);
        getServer().getPluginManager().registerEvents(searchGUI, this);

        getCommand("getitem").setExecutor(new GetItemCommand());
        getCommand("scavspawn").setExecutor(new ScavCommand(this));
        getCommand("attachment").setExecutor(new AttachmentCommand());
        getCommand("itemreload").setExecutor(new ItemReloadCommand(this));
        com.lunar_prototype.impossbleEscapeMC.minigame.MinigameCommand mgCmd = new com.lunar_prototype.impossbleEscapeMC.minigame.MinigameCommand(minigameManager);
        getCommand("mg").setExecutor(mgCmd);
        getCommand("mg").setTabCompleter(mgCmd);

        com.lunar_prototype.impossbleEscapeMC.raid.RaidCommand raidCmd = new com.lunar_prototype.impossbleEscapeMC.raid.RaidCommand(raidManager);
        getCommand("raid").setExecutor(raidCmd);
        getCommand("raid").setTabCompleter(raidCmd);

        com.lunar_prototype.impossbleEscapeMC.party.PartyCommand partyCmd = new com.lunar_prototype.impossbleEscapeMC.party.PartyCommand(partyManager);
        getCommand("party").setExecutor(partyCmd);
        getCommand("party").setTabCompleter(partyCmd);

        com.lunar_prototype.impossbleEscapeMC.loot.LootCommand lootCmd = new com.lunar_prototype.impossbleEscapeMC.loot.LootCommand(this);
        getCommand("loot").setExecutor(lootCmd);
        getCommand("loot").setTabCompleter(lootCmd);

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
        if (minigameManager != null) {
            minigameManager.stopGame();
        }
        if (raidManager != null) {
            raidManager.stopAllRaids();
        }
    }
}
