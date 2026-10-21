package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * ルートの配置の道具 (OP 用)。
 * <ul>
 *   <li>設置エッグ: 右クリックした所にルートコンテナを置き、マップに登録する</li>
 *   <li>地面ルートの設置エッグ: 右クリックした面の前のブロックの床を、地面ルートの地点として登録する</li>
 *   <li>削除ツール: 右クリックしたルートコンテナの登録を外して消す。コンテナでなければ、そのブロックか面の前のブロックの地面ルートの地点を外す</li>
 * </ul>
 * 地面ルートのエッグか削除ツールを持っている間は、近くの地面ルートの地点に粒子を出す (本人にだけ)
 */
public class LootEggListener implements Listener {
    /** 地面ルートの地点を粒子で示す間隔 (tick) と距離 (ブロック) */
    private static final int MARKER_TICKS = 10;
    private static final double MARKER_RANGE = 48;

    private final ImpossbleEscapeMC plugin;
    private final NamespacedKey EGG_MAP_KEY;
    private final NamespacedKey EGG_CRATE_KEY;
    private final NamespacedKey GROUND_EGG_MAP_KEY;
    private final NamespacedKey GROUND_EGG_CRATE_KEY;
    private final NamespacedKey REMOVER_KEY;

    public LootEggListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.EGG_MAP_KEY = new NamespacedKey(plugin, "loot_egg_map");
        this.EGG_CRATE_KEY = new NamespacedKey(plugin, "loot_egg_crate");
        this.GROUND_EGG_MAP_KEY = new NamespacedKey(plugin, "ground_loot_egg_map");
        this.GROUND_EGG_CRATE_KEY = new NamespacedKey(plugin, "ground_loot_egg_crate");
        this.REMOVER_KEY = new NamespacedKey(plugin, "loot_remover");
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::showGroundLootMarkers, MARKER_TICKS, MARKER_TICKS);
    }

    /** 地面ルートの設置エッグ */
    public ItemStack createGroundEgg(String mapId, String crateId) {
        ItemStack egg = new ItemStack(Material.RABBIT_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        meta.displayName(Component.text("Ground Loot Placer: " + crateId, NamedTextColor.GOLD));
        meta.lore(java.util.List.of(
                Component.text("Map: ", NamedTextColor.GRAY).append(Component.text(mapId, NamedTextColor.YELLOW)),
                Component.text("Crate: ", NamedTextColor.GRAY).append(Component.text(crateId, NamedTextColor.YELLOW)),
                Component.text("右クリックした面の前の床を地面ルートの地点に登録", NamedTextColor.AQUA),
                Component.text("レイドの始めに 50% で中身から 1 個を置く", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(GROUND_EGG_MAP_KEY, PDCKeys.STRING, mapId);
        meta.getPersistentDataContainer().set(GROUND_EGG_CRATE_KEY, PDCKeys.STRING, crateId);
        egg.setItemMeta(meta);
        return egg;
    }

    /** ルートの削除ツール */
    public ItemStack createRemover() {
        ItemStack tool = new ItemStack(Material.BARRIER);
        ItemMeta meta = tool.getItemMeta();
        meta.displayName(Component.text("Loot Remover", NamedTextColor.RED));
        meta.lore(java.util.List.of(
                Component.text("ルートコンテナを右クリック: 登録を外して消す", NamedTextColor.AQUA),
                Component.text("地面ルートの地点の床を右クリック: 地点を外す", NamedTextColor.AQUA)));
        meta.getPersistentDataContainer().set(REMOVER_KEY, PDCKeys.BOOLEAN, (byte) 1);
        tool.setItemMeta(meta);
        return tool;
    }

    public ItemStack createEgg(String mapId, String crateId) {
        LootCrate crate = plugin.getLootManager().getCrateIds().contains(crateId) ? null : null; // Validation logic if needed
        
        ItemStack egg = new ItemStack(Material.CHICKEN_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        meta.displayName(Component.text("Loot Placer: " + crateId, NamedTextColor.GOLD));
        
        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Map: ", NamedTextColor.GRAY).append(Component.text(mapId, NamedTextColor.YELLOW)));
        lore.add(Component.text("Crate: ", NamedTextColor.GRAY).append(Component.text(crateId, NamedTextColor.YELLOW)));
        lore.add(Component.text("右クリックでチェストを設置・登録", NamedTextColor.AQUA));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(EGG_MAP_KEY, PDCKeys.STRING, mapId);
        pdc.set(EGG_CRATE_KEY, PDCKeys.STRING, crateId);
        
        egg.setItemMeta(meta);
        return egg;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || event.getClickedBlock() == null || !item.hasItemMeta()) return;
        PersistentDataContainer tags = item.getItemMeta().getPersistentDataContainer();
        if (tags.has(GROUND_EGG_MAP_KEY, PDCKeys.STRING)) {
            event.setCancelled(true);
            placeGroundLoot(event.getPlayer(), event.getClickedBlock().getRelative(event.getBlockFace()),
                    tags.get(GROUND_EGG_MAP_KEY, PDCKeys.STRING), tags.get(GROUND_EGG_CRATE_KEY, PDCKeys.STRING));
            return;
        }
        if (tags.has(REMOVER_KEY, PDCKeys.BOOLEAN)) {
            event.setCancelled(true);
            remove(event.getPlayer(), event.getClickedBlock(), event.getClickedBlock().getRelative(event.getBlockFace()));
            return;
        }
        if (item.getType() != Material.CHICKEN_SPAWN_EGG) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(EGG_MAP_KEY, PDCKeys.STRING) || !pdc.has(EGG_CRATE_KEY, PDCKeys.STRING)) return;

        event.setCancelled(true);
        
        String mapId = pdc.get(EGG_MAP_KEY, PDCKeys.STRING);
        String crateId = pdc.get(EGG_CRATE_KEY, PDCKeys.STRING);
        Player player = event.getPlayer();

        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.getType().isAir()) {
            player.sendMessage(Component.text("設置場所が空気ではありません。", NamedTextColor.RED));
            return;
        }

        // マップの存在確認
        RaidMap map = plugin.getRaidModule().getMap(mapId);
        if (map == null) {
            player.sendMessage(Component.text("エラー: 指定されたマップ " + mapId + " が見つかりません。", NamedTextColor.RED));
            return;
        }

        // クレート設定から色を取得
        LootCrate crateDef = plugin.getLootManager().getCrate(crateId);
        Material boxMat = Material.WHITE_SHULKER_BOX;
        if (crateDef != null) {
            Material m = Material.matchMaterial(crateDef.color + "_SHULKER_BOX");
            if (m != null) boxMat = m;
        }
        
        target.setType(boxMat);
        if (target.getState() instanceof Container container) {
            // メタデータ付与
            container.getPersistentDataContainer().set(PDCKeys.LOOT_TABLE_ID, PDCKeys.STRING, crateId);
            container.update();
            
            // マップに登録
            map.addLootContainer(target.getLocation(), crateId);
            plugin.getRaidModule().saveMap(map);
            
            // 補充を即座に行う
            plugin.getLootManager().refillContainer(container, crateId);
            
            player.sendMessage(Component.text("ルートコンテナを設置・登録しました: " + crateId + " (" + mapId + ")", NamedTextColor.GREEN));
        }
    }

    private void placeGroundLoot(Player player, Block target, String mapId, String crateId) {
        if (target.getType().isSolid()) {
            player.sendMessage(Component.text("置く所がふさがっています (床の上の空いたブロックを選んでください)。", NamedTextColor.RED));
            return;
        }
        RaidMap map = plugin.getRaidModule().getMap(mapId);
        if (map == null) {
            player.sendMessage(Component.text("エラー: 指定されたマップ " + mapId + " が見つかりません。", NamedTextColor.RED));
            return;
        }
        if (plugin.getLootManager().getCrate(crateId) == null) {
            player.sendMessage(Component.text("エラー: クレートの種類 " + crateId + " がありません。", NamedTextColor.RED));
            return;
        }
        if (!map.isWorld(target.getWorld())) {
            player.sendMessage(Component.text("エラー: マップ " + mapId + " のワールドではありません。", NamedTextColor.RED));
            return;
        }
        if (map.getGroundLoot().stream().anyMatch(p -> p.getLocation(map.getWorldName()).getBlock().equals(target))) {
            player.sendMessage(Component.text("ここはもう地面ルートの地点です。", NamedTextColor.YELLOW));
            return;
        }
        map.addGroundLoot(target.getLocation(), crateId);
        plugin.getRaidModule().saveMap(map);
        player.sendMessage(Component.text("地面ルートの地点を登録しました: " + crateId + " (" + mapId + "、" + map.getGroundLoot().size() + " 個目)", NamedTextColor.GREEN));
    }

    /** ルートコンテナを消すか、地面ルートの地点を外す */
    private void remove(Player player, Block clicked, Block front) {
        java.util.List<String> containers = plugin.getLootManager().removeLootContainer(clicked);
        if (!containers.isEmpty()) {
            player.sendMessage(Component.text("ルートコンテナの登録を外し、ブロックを消しました (" + String.join(", ", containers) + ")", NamedTextColor.GREEN));
            return;
        }
        int removed = 0;
        for (RaidMap map : plugin.getRaidModule().getMaps().values()) {
            int n = map.removeGroundLootAt(front.getLocation()) + map.removeGroundLootAt(clicked.getLocation());
            if (n > 0) {
                plugin.getRaidModule().saveMap(map);
                removed += n;
            }
        }
        player.sendMessage(removed > 0
                ? Component.text("地面ルートの地点を外しました (" + removed + " 個)", NamedTextColor.GREEN)
                : Component.text("ここには登録されたルートコンテナも地面ルートの地点もありません。", NamedTextColor.RED));
    }

    /** 地面ルートのエッグか削除ツールを持っている OP に、近くの地点を粒子で示す */
    private void showGroundLootMarkers() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (!player.isOp()) continue;
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!held.hasItemMeta()) continue;
            PersistentDataContainer tags = held.getItemMeta().getPersistentDataContainer();
            if (!tags.has(GROUND_EGG_MAP_KEY, PDCKeys.STRING) && !tags.has(REMOVER_KEY, PDCKeys.BOOLEAN)) continue;
            for (RaidMap map : plugin.getRaidModule().getMaps().values()) {
                if (!map.isWorld(player.getWorld())) continue;
                for (RaidMap.GroundLootPoint point : map.getGroundLoot()) {
                    org.bukkit.Location loc = point.getLocation(map.getWorldName());
                    if (loc == null || loc.distanceSquared(player.getLocation()) > MARKER_RANGE * MARKER_RANGE) continue;
                    player.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, loc.add(0, 0.3, 0), 4, 0.15, 0.2, 0.15, 0);
                }
            }
        }
    }
}
