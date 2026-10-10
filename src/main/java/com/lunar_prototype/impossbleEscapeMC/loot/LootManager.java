package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public class LootManager {
    private final ImpossbleEscapeMC plugin;
    private final Map<String, LootTable> lootTables = new HashMap<>();
    private final Map<String, LootCrate> lootCrates = new HashMap<>();

    public LootManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        loadAll();
    }

    /** ルート表とクレートを {@link LootCatalog} から読み込み、品物のIDとクレートのルート表を確かめる */
    public void loadAll() {
        lootTables.clear();
        lootCrates.clear();
        for (LootTable table : LootCatalog.tables()) {
            if (lootTables.put(table.id, table) != null) {
                plugin.getLogger().severe("ルート表のIDが重複しています: " + table.id);
            }
            for (LootTable.LootEntry entry : table.items) {
                if (!GameItems.exists(entry.itemId)) {
                    plugin.getLogger().warning("ルート表 " + table.id + ": 品物 " + entry.itemId + " がアイテム・弾・データパック銃・アタッチメントのどれにもありません");
                }
            }
        }
        for (LootCrate crate : LootCatalog.crates()) {
            if (lootCrates.put(crate.id, crate) != null) {
                plugin.getLogger().severe("クレートのIDが重複しています: " + crate.id);
            }
            for (String tableId : crate.tableWeights.keySet()) {
                if (!lootTables.containsKey(tableId)) {
                    plugin.getLogger().warning("クレート " + crate.id + ": ルート表 " + tableId + " がありません");
                }
            }
        }
        if (new File(plugin.getDataFolder(), "loot.yml").exists()) {
            plugin.getLogger().warning("loot.yml は読み込まれません。ルート表は LootCatalog (Java) で定義します");
        }
        plugin.getLogger().info("Loaded " + lootTables.size() + " loot tables and " + lootCrates.size() + " crates.");
    }

    public void refillAllContainers() {
        for (RaidMap map : plugin.getRaidModule().getMaps().values()) {
            refillContainers(map);
        }
    }

    public void refillContainers(RaidMap map) {
        String worldName = map.getWorldName();
        if (worldName == null) return;

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        for (RaidMap.LootContainer lc : map.getLootContainers()) {
            Location loc = lc.getLocation(worldName);
            if (loc == null) continue;

            // チャンクが未ロードの場合は一時的にロードしてブロックデータを正しく取得する
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            boolean wasLoaded = world.isChunkLoaded(chunkX, chunkZ);
            if (!wasLoaded) {
                world.getChunkAt(chunkX, chunkZ).load();
            }

            try {
                Block block = loc.getBlock();
                if (block.getState() instanceof Container container) {
                    refillContainer(container, lc.getTableId());
                }
            } finally {
                if (!wasLoaded) {
                    world.getChunkAt(chunkX, chunkZ).unload(true);
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

        List<ItemStack> items = LootRoller.roll(container.getWorld(), tableToRoll);
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
