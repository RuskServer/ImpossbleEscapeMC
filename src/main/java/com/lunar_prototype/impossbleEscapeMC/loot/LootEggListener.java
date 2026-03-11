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

public class LootEggListener implements Listener {
    private final ImpossbleEscapeMC plugin;
    private final NamespacedKey EGG_MAP_KEY;
    private final NamespacedKey EGG_CRATE_KEY;

    public LootEggListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.EGG_MAP_KEY = new NamespacedKey(plugin, "loot_egg_map");
        this.EGG_CRATE_KEY = new NamespacedKey(plugin, "loot_egg_crate");
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
        if (item == null || item.getType() != Material.CHICKEN_SPAWN_EGG) return;
        if (event.getClickedBlock() == null) return;

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
}
