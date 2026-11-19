package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * グローバルマーケットのメインモジュール
 */
@Service
public class MarketModule implements IModule {
    private final CommandRegistry commands;
    private final LevelModule levels;
    private final com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule economy;

    private final ImpossbleEscapeMC plugin;
    private final File dataFile;
    private final Gson gson;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();
    private final com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule raids;
    private boolean loaded;

    @Inject
    public MarketModule(ImpossbleEscapeMC plugin,
            CommandRegistry commands,
            LevelModule levels,
            com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule economy,
            com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule raids) {
        this.raids = raids;
        this.commands = commands;
        this.levels = levels;
        this.economy = economy;
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "market.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void onEnable(ServiceContainer container) {
        loadListings();
        commands.register(
                new MarketCommand(this, levels, plugin, economy));
    }

    @Override
    public void onDisable() {
        saveListings();
    }

    /**
     * 出品を追加
     */
    public boolean addListing(MarketListing listing) {
        if (!loaded || listings.putIfAbsent(listing.getId(), listing) != null) return false;
        if (saveListings()) return true;
        listings.remove(listing.getId(), listing);
        return false;
    }

    /**
     * 出品を削除
     */
    public boolean removeListing(UUID id) {
        if (!loaded) return false;
        MarketListing previous = listings.remove(id);
        if (previous == null) return false;
        if (saveListings()) return true;
        listings.put(id, previous);
        return false;
    }

    /** 開いた後に出撃した場合も、確定操作の直前で再確認する。 */
    public boolean requireAccess(org.bukkit.entity.Player player) {
        String key = !loaded ? "iem.market.unavailable" : raids.isInRaid(player) ? "iem.market.raid_blocked" : null;
        if (key == null) return true;
        com.lunar_prototype.impossbleEscapeMC.api.message.Messages.chat(player, key,
                com.lunar_prototype.impossbleEscapeMC.api.message.MessageText.of(key));
        return false;
    }

    /**
     * 出品を取得
     */
    public MarketListing getListing(UUID id) {
        return listings.get(id);
    }

    /**
     * 全ての出品を取得
     */
    public Collection<MarketListing> getAllListings() {
        return listings.values();
    }

    /**
     * アイテムがFIR（Found In Raid）かどうかを確認
     */
    public boolean isFir(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.getOrDefault(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN, (byte) 0) == 1;
    }

    /**
     * マーケットに出品可能か確認 (FIRアイテム、または弾薬)
     */
    public boolean canSellOnMarket(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        // 支給品は出品できない
        if (com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout.isSupplied(item)) return false;
        
        // FIRなら出品可能
        if (isFir(item)) return true;

        // 弾薬ならFIRなしでも出品可能
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        if (itemId != null && com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry.getAmmo(itemId) != null) {
            return true;
        }

        return false;
    }

    private void loadListings() {
        loaded = false;
        try {
            List<MarketListing> values = new MarketStorage(dataFile.toPath(), gson).load();
            listings.clear();
            values.forEach(value -> listings.put(value.getId(), value));
            loaded = true;
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "マーケットを読み込めないため取引と保存を停止します。元ファイルは保持します", e);
        }
    }

    private boolean saveListings() {
        if (!loaded) return false;
        try {
            new MarketStorage(dataFile.toPath(), gson).save(listings.values());
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "マーケットを保存できませんでした", e);
            return false;
        }
    }
}
