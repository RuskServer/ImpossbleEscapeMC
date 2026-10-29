package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.factory.GameItems;
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
        var drawers=plugin.getServiceContainer().get(DrawerLootModule.class);
        if (drawers != null) drawers.refill(map);
        var lockers=plugin.getServiceContainer().get(LockerLootModule.class);
        if (lockers != null) lockers.refill(map);
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

        // 漁った欄の記録を消す。BlockState の書き戻し (update) は中身も読んだ時の写しで上書きするので、中身を替える前に済ませる
        // (先に中身を空にすると、書き戻しで前の中身が戻り、補充の後も残ってしまう)
        NamespacedKey searchedKey = new NamespacedKey(plugin, "searched_slots");
        container.getPersistentDataContainer().remove(searchedKey);
        container.update();
        if (container.getBlock().getState() instanceof Container live) container = live;

        Inventory inv = container.getInventory();
        inv.clear();

        List<ItemStack> items = LootRoller.roll(container.getWorld(), tableToRoll);
        Collections.shuffle(items);

        for (int i = 0; i < Math.min(items.size(), inv.getSize()); i++) {
            inv.setItem(i, items.get(i));
        }
        plugin.getLogger().info("Refilled container " + crateId + " with " + items.size() + " items.");
    }

    /** 家具の中身も通常コンテナと同じ重み・抽選を使う。 */
    public List<ItemStack> rollContents(org.bukkit.World world,String crateId) {
        LootCrate crate=lootCrates.get(crateId);
        LootTable table=crate!=null?selectWeightedTable(crate):lootTables.get(crateId);
        return table==null?List.of():LootRoller.roll(world,table);
    }

    /** 地面ルートの地点に置く確率 */
    private static final double GROUND_LOOT_CHANCE = 0.5;

    /**
     * 地面ルートを置く (レイドの始めに、レイドが動き出してから呼ぶ。置いた物はレイドが覚え、終わったら消える)。
     * 地点ごとに {@link #GROUND_LOOT_CHANCE} で、クレートと同じ種類の中身から 1 個。置いたアイテムは placed に渡す
     */
    public int spawnGroundLoot(RaidMap map, java.util.function.Consumer<org.bukkit.entity.Item> placed) {
        String worldName = map.getWorldName();
        org.bukkit.World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) return 0;
        Random random = new Random();
        int count = 0;
        for (RaidMap.GroundLootPoint point : map.getGroundLoot()) {
            if (random.nextDouble() >= GROUND_LOOT_CHANCE) continue;
            Location loc = point.getLocation(worldName);
            ItemStack item = rollOne(world, point.getCrateId(), random);
            if (loc == null || item == null) continue;
            // 未ロードのチャンクは読み込んで置く。置いた直後に下ろすと置いた物の保存と重なるので、下ろすのはサーバーに任せる
            world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4).load();
            placed.accept(world.dropItem(loc, item, dropped -> dropped.setVelocity(new org.bukkit.util.Vector())));
            count++;
        }
        return count;
    }

    /** クレートの種類の中身から 1 個 (中身が無ければ null) */
    private ItemStack rollOne(org.bukkit.World world, String crateId, Random random) {
        LootCrate crate = lootCrates.get(crateId);
        LootTable table = crate != null ? selectWeightedTable(crate) : lootTables.get(crateId);
        if (table == null) return null;
        List<ItemStack> items = LootRoller.roll(world, table);
        return items.isEmpty() ? null : items.get(random.nextInt(items.size()));
    }

    /**
     * block のルートコンテナの登録を (どのマップからも) 外し、ブロックを消す。外したマップの ID (登録が無ければ空で、何もしない)。
     * 中身は先に空にする (チェストは置き換えると中身を落とすため)
     */
    public List<String> removeLootContainer(Block block) {
        List<String> removedFrom = new ArrayList<>();
        for (RaidMap map : plugin.getRaidModule().getMaps().values()) {
            if (map.removeLootContainersAt(block.getLocation()) > 0) {
                plugin.getRaidModule().saveMap(map);
                removedFrom.add(map.getMapId());
            }
        }
        if (removedFrom.isEmpty()) return removedFrom;
        if (block.getState() instanceof org.bukkit.block.Chest chest) {
            chest.getBlockInventory().clear();
        } else if (block.getState() instanceof Container container) {
            container.getInventory().clear();
        }
        block.setType(Material.AIR);
        return removedFrom;
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
