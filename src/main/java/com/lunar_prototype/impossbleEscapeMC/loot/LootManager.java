package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LootManager {
    private final ImpossbleEscapeMC plugin;
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final Map<String, LootCrate> lootCrates = new HashMap<>();
    private final File lootFile;

    public LootManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            plugin.saveResource("loot.yml", false);
        }
        loadAll();
    }

    public void loadAll() {
        lootTables.clear();
        lootCrates.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(lootFile);

        // Load Tables
        if (config.contains("tables")) {
            ConfigurationSection tablesSection = config.getConfigurationSection("tables");
            for (String tableId : tablesSection.getKeys(false)) {
                ConfigurationSection section = tablesSection.getConfigurationSection(tableId);
                LootTable table = new LootTable();
                table.id = tableId;
                table.minItems = section.getInt("min_items", 1);
                table.maxItems = section.getInt("max_items", 5);

                List<Map<?, ?>> itemList = section.getMapList("items");
                for (Map<?, ?> itemMap : itemList) {
                    LootTable.LootEntry entry = new LootTable.LootEntry();
                    entry.itemId = (String) itemMap.get("item");
                    entry.chance = ((Number) itemMap.get("chance")).doubleValue();
                    entry.minAmount = itemMap.containsKey("min") ? ((Number) itemMap.get("min")).intValue() : 1;
                    entry.maxAmount = itemMap.containsKey("max") ? ((Number) itemMap.get("max")).intValue() : 1;
                    table.items.add(entry);
                }
                lootTables.put(tableId, table);
            }
        }

        // Load Crates
        if (config.contains("crates")) {
            ConfigurationSection cratesSection = config.getConfigurationSection("crates");
            for (String crateId : cratesSection.getKeys(false)) {
                ConfigurationSection section = cratesSection.getKeys(false).contains(crateId) ? cratesSection.getConfigurationSection(crateId) : null;
                if (section == null) continue;

                LootCrate crate = new LootCrate();
                crate.id = crateId;
                crate.color = section.getString("color", "WHITE").toUpperCase();

                ConfigurationSection weightsSection = section.getConfigurationSection("tables");
                if (weightsSection != null) {
                    for (String tId : weightsSection.getKeys(false)) {
                        crate.tableWeights.put(tId, weightsSection.getDouble(tId));
                    }
                }
                lootCrates.put(crateId, crate);
            }
        }
        plugin.getLogger().info("Loaded " + lootTables.size() + " tables and " + lootCrates.size() + " crates from loot.yml.");
    }

    public void refillAllContainers() {
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

    public void refillContainer(Container container, String crateId) {
        LootCrate crate = lootCrates.get(crateId);
        LootTable tableToRoll = null;

        if (crate != null) {
            // Check and update shulker box color if needed
            String colorName = crate.color + "_SHULKER_BOX";
            Material shulkerMat = Material.matchMaterial(colorName);
            if (shulkerMat != null && container.getType() != shulkerMat) {
                Location loc = container.getLocation();
                loc.getBlock().setType(shulkerMat);
                if (loc.getBlock().getState() instanceof Container newContainer) {
                    // Update metadata on new block state
                    newContainer.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, crateId);
                    newContainer.update();
                    container = newContainer;
                }
            }
            // Select a table from crate weights
            tableToRoll = selectWeightedTable(crate);
        } else {
            // Fallback to table if crate not found (legacy compatibility)
            tableToRoll = lootTables.get(crateId);
        }

        if (tableToRoll == null) return;

        Inventory inv = container.getInventory();
        inv.clear();

        // Reset searched slots
        NamespacedKey searchedKey = new NamespacedKey(plugin, "searched_slots");
        container.getPersistentDataContainer().remove(searchedKey);
        container.update();

        List<ItemStack> items = LootRoller.roll(tableToRoll);
        Collections.shuffle(items);

        for (int i = 0; i < Math.min(items.size(), inv.getSize()); i++) {
            inv.setItem(i, items.get(i));
        }
        plugin.getLogger().info("Refilled container " + crateId + " with " + items.size() + " items.");
    }

    private LootTable selectWeightedTable(LootCrate crate) {
        double totalWeight = crate.tableWeights.values().stream().mapToDouble(d -> d).sum();
        double r = Math.random() * totalWeight;
        double cur = 0;
        for (Map.Entry<String, Double> entry : crate.tableWeights.entrySet()) {
            cur += entry.getValue();
            if (r <= cur) return lootTables.get(entry.getKey());
        }
        return null;
    }

    public List<String> getCrateIds() {
        return new ArrayList<>(lootCrates.keySet());
    }

    public LootCrate getCrate(String id) {
        return lootCrates.get(id);
    }

    public List<String> getTableIds() {
        return new ArrayList<>(lootTables.keySet());
    }
}