package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class RaidItemListener implements Listener {

    private final ImpossbleEscapeMC plugin;

    public RaidItemListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        // プレイヤーが落としたアイテムにフラグを立てる（FIRロンダリング防止）
        ItemStack item = event.getItemDrop().getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(PDCKeys.DROPPED_BY_PLAYER, PDCKeys.BOOLEAN, (byte) 1);
            item.setItemMeta(meta);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // プレイヤーがレイド中か確認
        if (!plugin.getRaidModule().isInRaid(player)) return;

        ItemStack item = event.getItem().getItemStack();
        ItemFactory.applyFIR(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // プレイヤーがレイド中か確認
        if (!plugin.getRaidModule().isInRaid(player)) return;

        Inventory topInv = event.getView().getTopInventory();
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        // 外部インベントリ（チェスト、死体など）から自分のインベントリへ移動させた場合
        // または、Shiftクリックで外部インベントリから自分のインベントリへ移動した場合
        ItemStack targetItem = null;

        if (clickedInv.equals(topInv)) {
            // 外部インベントリをクリックした場合
            if (event.getCursor() != null && !event.getCursor().getType().isAir()) {
                // カーソルにアイテムを持った（取得した）
                targetItem = event.getCursor();
            } else if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
                // クリックされたアイテム（取得しようとしている）
                targetItem = event.getCurrentItem();
            }
        } else if (event.isShiftClick() && clickedInv.getType() != InventoryType.PLAYER && clickedInv.equals(topInv)) {
             // Shiftクリックで外部から自分へ
             targetItem = event.getCurrentItem();
        }

        if (targetItem != null) {
            applyFIRRecursive(targetItem);
        }
    }

    private void applyFIRRecursive(ItemStack item) {
        if (item == null || item.getType().isAir()) return;

        ItemFactory.applyFIR(item);

        // バックパックの場合は中身にも適用する必要がある（コンテナごと拾った場合など）
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule != null && backpackModule.isBackpackItem(item)) {
            Inventory inv = backpackModule.loadBackpackInventory(item);
            for (ItemStack content : inv.getContents()) {
                applyFIRRecursive(content);
            }
            backpackModule.saveBackpackInventory(item, inv);
        }
    }
}
