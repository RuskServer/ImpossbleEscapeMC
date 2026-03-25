package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PDASettingsGUI implements Listener {
    private final Player player;
    private final Inventory inventory;
    private final PlayerDataModule dataModule;
    private final PlayerData data;

    private final List<String> availableKeys = Arrays.asList("DROP", "SWAP_HAND", "LEFT_CLICK_SNEAK", "NONE");

    public PDASettingsGUI(Player player) {
        this.player = player;
        this.inventory = Bukkit.createInventory(null, 27, Component.text("PDA - 操作設定").decoration(TextDecoration.ITALIC, false));
        this.dataModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(PlayerDataModule.class);
        this.data = dataModule.getPlayerData(player.getUniqueId());
        Bukkit.getPluginManager().registerEvents(this, ImpossbleEscapeMC.getInstance());
    }

    public void open() {
        setupGUI();
        player.openInventory(inventory);
    }

    private void setupGUI() {
        inventory.clear();

        // 背景
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.empty());
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        // リロードキー設定
        inventory.setItem(11, getSettingItem("リロードキー (RELOAD)", "RELOAD"));
        // 射撃モード切替キー設定
        inventory.setItem(13, getSettingItem("射撃モード切替キー (FIREMODE)", "FIREMODE"));

        // ADSダッシュ解除設定
        ItemStack adsItem = new ItemStack(data.isCancelAdsOnSprint() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta adsMeta = adsItem.getItemMeta();
        adsMeta.displayName(Component.text("ADSをダッシュで解除", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> adsLore = new ArrayList<>();
        adsLore.add(Component.text("現在の設定: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(data.isCancelAdsOnSprint() ? "ON" : "OFF", data.isCancelAdsOnSprint() ? NamedTextColor.GREEN : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        adsLore.add(Component.empty());
        adsLore.add(Component.text("クリックでON/OFFを切り替え", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        adsMeta.lore(adsLore);
        adsItem.setItemMeta(adsMeta);
        inventory.setItem(15, adsItem);

        // 戻るボタン
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(Component.text("戻る", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(22, back);
    }

    private ItemStack getSettingItem(String displayName, String actionType) {
        String currentKey = data.getKeybinds().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(actionType))
                .map(java.util.Map.Entry::getValue)
                .findFirst().orElse("NONE");

        ItemStack item = new ItemStack(Material.COMPARATOR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(displayName, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("現在のキー: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(currentKey, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        lore.add(Component.empty());
        lore.add(Component.text("クリックでキーを変更", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void cycleKeybind(String actionType) {
        String currentKey = data.getKeybinds().entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(actionType))
                .map(java.util.Map.Entry::getValue)
                .findFirst().orElse("NONE");

        int index = availableKeys.indexOf(currentKey.toUpperCase());
        if (index == -1) index = 0;
        int nextIndex = (index + 1) % availableKeys.size();
        String nextKey = availableKeys.get(nextIndex);

        if (nextKey.equals("NONE")) {
            data.getKeybinds().remove(actionType.toUpperCase());
            data.getKeybinds().remove(actionType.toLowerCase()); // 両方のケースで一応削除
        } else {
            // 他のアクションで同じキーが使われていれば削除（競合防止）
            data.getKeybinds().entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(nextKey));
            data.getKeybinds().put(actionType.toUpperCase(), nextKey);
        }
        data.setDirty(true);
        dataModule.saveAsync(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 22) {
            player.closeInventory();
            new PDAGUI(player).open();
        } else if (slot == 11) {
            cycleKeybind("RELOAD");
            setupGUI(); // 再描画
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (slot == 13) {
            cycleKeybind("FIREMODE");
            setupGUI(); // 再描画
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        } else if (slot == 15) {
            data.setCancelAdsOnSprint(!data.isCancelAdsOnSprint());
            data.setDirty(true);
            dataModule.saveAsync(player.getUniqueId());
            setupGUI(); // 再描画
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            HandlerList.unregisterAll(this);
        }
    }
}
