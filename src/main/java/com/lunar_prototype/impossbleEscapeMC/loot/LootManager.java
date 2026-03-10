package com.lunar_prototype.impossbleEscapeMC.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LootManager {
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final File lootFolder;

    public LootManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.lootFolder = new File(plugin.getDataFolder(), "loot");
        if (!lootFolder.exists()) {
            lootFolder.mkdirs();
        }
        loadLootTables();
    }

    public void loadLootTables() {
        lootTables.clear();
        File[] files = lootFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                LootTable table = gson.fromJson(reader, LootTable.class);
                lootTables.put(table.id, table);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load loot table: " + file.getName());
            }
        }
        plugin.getLogger().info("Loaded " + lootTables.size() + " loot tables.");
    }

    public void saveLootTable(LootTable table) {
        File file = new File(lootFolder, table.id + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(table, writer);
            lootTables.put(table.id, table);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save loot table: " + table.id);
        }
    }

    public LootTable getLootTable(String id) {
        return lootTables.get(id);
    }

    public void refillAllContainers() {
        plugin.getLogger().info("Refilling all loot containers...");
        for (RaidMap map : plugin.getRaidManager().getMaps().values()) {
            String worldName = map.getWorldName();
            if (worldName == null) continue;

            for (RaidMap.LootContainer lc : map.getLootContainers()) {
                Location loc = lc.getLocation(worldName);
                if (loc == null) continue;

                Block block = loc.getBlock();
                if (block.getState() instanceof Container container) {
                    refillContainer(container, lc.getTableId());
                }
            }
        }
    }

    public void refillContainer(Container container, String tableId) {
        LootTable table = lootTables.get(tableId);
        if (table == null) return;

        Inventory inv = container.getInventory();
        inv.clear();

        List<ItemStack> items = LootRoller.roll(table);
        Collections.shuffle(items);

        for (int i = 0; i < Math.min(items.size(), inv.getSize()); i++) {
            inv.setItem(i, items.get(i));
        }
    }

    public void createLootTable(String id) {
        if (lootTables.containsKey(id)) return;
        LootTable table = new LootTable();
        table.id = id;
        saveLootTable(table);
    }

    public List<String> getLootTableIds() {
        return lootTables.keySet().stream().toList();
    }
}
