package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HideoutGUI implements Listener {
    private final HideoutModule module;
    private final Player player;
    private final Inventory inventory;

    public HideoutGUI(HideoutModule module, Player player) {
        this.module = module;
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("Hideout - 隠れ家管理"));
    }

    public void open() {
        updateInventory();
        Bukkit.getPluginManager().registerEvents(this, module.getPlugin());
        player.openInventory(inventory);
    }

    private void updateInventory() {
        inventory.clear();
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());

        // 背景
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, bg);

        // 各設備のスロット
        setupModuleIcon("generator", 11, Material.BLAST_FURNACE, "発電機", data);
        setupModuleIcon("workbench", 13, Material.CRAFTING_TABLE, "ワークベンチ", data);
        setupModuleIcon("lighting", 15, Material.LANTERN, "照明", data);
    }

    private void setupModuleIcon(String type, int slot, Material material, String displayName, PlayerData data) {
        int currentLevel = data.getHideoutLevel(type);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("現在のレベル: Lv." + currentLevel, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("クリックでアップグレード (Lv." + (currentLevel + 1) + ")", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("必要費用: 10,000₽", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)); // 固定値テスト

        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 11) handleUpgrade("generator");
        else if (slot == 13) handleUpgrade("workbench");
        else if (slot == 15) handleUpgrade("lighting");
    }

    private void handleUpgrade(String type) {
        PlayerData data = module.getDataModule().getPlayerData(player.getUniqueId());
        int nextLevel = data.getHideoutLevel(type) + 1;

        // コストチェック (本来は EconomyModule を使用)
        // ここでは簡易的に実行
        
        File structureFile = new File(module.getPlugin().getDataFolder(), "structures/" + type + "_" + nextLevel + ".nbt");
        if (!structureFile.exists()) {
            player.sendMessage(Component.text("エラー: Lv." + nextLevel + " のデータが見つかりません。", NamedTextColor.RED));
            return;
        }

        // レベル更新
        data.setHideoutLevel(type, nextLevel);
        
        // パッチ適用 (座標オフセット)
        Location center = module.getWorldManager().getPlayerCenter(data.getHideoutIndex());
        Location offset = getOffset(type);
        module.getStructureService().placeSmart(structureFile, center.clone().add(offset), true);

        player.sendMessage(Component.text(type + " を Lv." + nextLevel + " にアップグレードしました！", NamedTextColor.GREEN));
        updateInventory();
    }

    private Location getOffset(String type) {
        switch (type) {
            case "generator": return new Location(null, -5, 0, -5);
            case "workbench": return new Location(null, 5, 0, -5);
            case "lighting": return new Location(null, 0, 5, 0);
            default: return new Location(null, 0, 0, 0);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
