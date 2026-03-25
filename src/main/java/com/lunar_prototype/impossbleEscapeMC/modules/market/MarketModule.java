package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * グローバルマーケットのメインモジュール
 */
public class MarketModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final File dataFile;
    private final Gson gson;
    private final Map<UUID, MarketListing> listings = new ConcurrentHashMap<>();

    public MarketModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "market.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void onEnable(ServiceContainer container) {
        loadListings();
    }

    @Override
    public void onDisable() {
        saveListings();
    }

    /**
     * 出品を追加
     */
    public void addListing(MarketListing listing) {
        listings.put(listing.getId(), listing);
        saveListings();
    }

    /**
     * 出品を削除
     */
    public void removeListing(UUID id) {
        listings.remove(id);
        saveListings();
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
        return pdc.has(PDCKeys.FIND_IN_RAID, PDCKeys.BOOLEAN);
    }

    private void loadListings() {
        if (!dataFile.exists()) return;

        try (Reader reader = new FileReader(dataFile)) {
            Type listType = new TypeToken<List<MarketListing>>() {}.getType();
            List<MarketListing> list = gson.fromJson(reader, listType);
            if (list != null) {
                for (MarketListing listing : list) {
                    listings.put(listing.getId(), listing);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load market listings");
            e.printStackTrace();
        }
    }

    private void saveListings() {
        try (Writer writer = new FileWriter(dataFile)) {
            gson.toJson(new ArrayList<>(listings.values()), writer);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save market listings");
            e.printStackTrace();
        }
    }
}
