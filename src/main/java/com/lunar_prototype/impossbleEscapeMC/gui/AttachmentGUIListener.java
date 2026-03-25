package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.item.*;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AttachmentGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AttachmentGUI gui))
            return;

        if (!(event.getWhoClicked() instanceof Player player))
            return;

        int clickedSlot = event.getRawSlot();
        int guiSize = event.getInventory().getSize();
        ItemStack cursor = event.getCursor();
        ItemStack gunItem = gui.getGunItem();

        // プレイヤーのインベントリ部分 (GUI外) のクリック
        if (clickedSlot >= guiSize) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta())
                    return;

                // 編集中の銃自体をシフトクリックした場合は無視
                if (clickedItem.isSimilar(gunItem))
                    return;

                String itemId = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                AttachmentDefinition attDef = ItemRegistry.getAttachment(itemId);

                if (attDef != null) {
                    AttachmentSlot targetSlot = attDef.slot;
                    List<String> attachments = getAttachmentList(gunItem);
                    ensureListSize(attachments, targetSlot.getId() + 1);

                    // 既存のアタッチメントがあれば排出
                    String existing = attachments.get(targetSlot.getId());
                    if (existing != null && !existing.isEmpty()) {
                        ItemStack ejected = ItemFactory.create(existing);
                        if (ejected != null) {
                            player.getInventory().addItem(ejected).forEach(
                                    (i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                        }
                    }

                    // 新しいアタッチメントをセット
                    attachments.set(targetSlot.getId(), attDef.id);
                    saveAttachmentList(gunItem, attachments);
                    ItemFactory.updateLore(gunItem);

                    // アイテムを1つ消費
                    clickedItem.setAmount(clickedItem.getAmount() - 1);

                    // GUIを更新
                    new AttachmentGUI(player, gunItem).open();
                    player.sendMessage("§a" + attDef.displayName + " を装着しました");
                }
            }
            return;
        }

        // GUI内のクリックはキャンセル (デフォルト動作を防ぐ)
        event.setCancelled(true);

        // 中央の銃スロット（13）はクリック不可
        if (clickedSlot == 13)
            return;

        AttachmentSlot targetSlot = AttachmentGUI.getSlotFromGuiSlot(clickedSlot);
        if (targetSlot == null)
            return;

        // カーソルにアタッチメントを持っている場合 -> 装着
        if (cursor != null && cursor.getType() != Material.AIR && cursor.hasItemMeta()) {
            String cursorItemId = cursor.getItemMeta().getPersistentDataContainer()
                    .get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            AttachmentDefinition attDef = ItemRegistry.getAttachment(cursorItemId);

            if (attDef != null && attDef.slot == targetSlot) {
                // 装着処理
                List<String> attachments = getAttachmentList(gunItem);
                ensureListSize(attachments, targetSlot.getId() + 1);

                // 既存のアタッチメントがあれば排出
                String existing = attachments.get(targetSlot.getId());
                if (existing != null && !existing.isEmpty()) {
                    ItemStack ejected = ItemFactory.create(existing);
                    if (ejected != null) {
                        player.getInventory().addItem(ejected).forEach(
                                (i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                    }
                }

                // 新しいアタッチメントをセット
                attachments.set(targetSlot.getId(), attDef.id);
                saveAttachmentList(gunItem, attachments);
                ItemFactory.updateLore(gunItem);

                // カーソルのアイテムを消費
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor.getAmount() <= 0 ? null : cursor);

                // GUIを更新 (再オープン)
                new AttachmentGUI(player, gunItem).open();
                player.sendMessage("§a" + attDef.displayName + " を装着しました");
                return;
            } else if (attDef != null) {
                player.sendMessage("§cこのアタッチメントは " + attDef.slot.name() + " スロット用です");
                return;
            }
        }

        // カーソルが空で、スロットにアタッチメントがある場合 -> 取り外し
        ItemStack slotItem = event.getCurrentItem();
        if ((cursor == null || cursor.getType() == Material.AIR) && slotItem != null && slotItem.hasItemMeta()) {
            String slotItemId = slotItem.getItemMeta().getPersistentDataContainer()
                    .get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            AttachmentDefinition attDef = ItemRegistry.getAttachment(slotItemId);

            if (attDef != null) {
                // 取り外し処理
                List<String> attachments = getAttachmentList(gunItem);
                ensureListSize(attachments, targetSlot.getId() + 1);
                
                // デフォルトのアタッチメントを取得
                String defaultAtt = "";
                String itemId = gunItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
                ItemDefinition gunDef = ItemRegistry.get(itemId);
                if (gunDef != null && gunDef.gunStats != null && gunDef.gunStats.defaultAttachments != null) {
                    if (gunDef.gunStats.defaultAttachments.size() > targetSlot.getId()) {
                        defaultAtt = gunDef.gunStats.defaultAttachments.get(targetSlot.getId());
                    }
                }
                
                attachments.set(targetSlot.getId(), defaultAtt);
                saveAttachmentList(gunItem, attachments);
                ItemFactory.updateLore(gunItem);

                // アタッチメントアイテムをプレイヤーに返却
                ItemStack ejected = ItemFactory.create(attDef.id);
                if (ejected != null) {
                    player.getInventory().addItem(ejected).forEach(
                            (i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
                }

                // GUIを更新
                new AttachmentGUI(player, gunItem).open();
                player.sendMessage("§e" + attDef.displayName + " を取り外しました");
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AttachmentGUI) {
            event.setCancelled(true);
        }
    }

    private List<String> getAttachmentList(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return new ArrayList<>();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
        if (joined == null || joined.isEmpty())
            return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(joined.split(",")));
    }

    private void saveAttachmentList(ItemStack item, List<String> attachments) {
        if (item == null)
            return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PDCKeys.ATTACHMENTS, PDCKeys.STRING, String.join(",", attachments));
        item.setItemMeta(meta);
    }

    private void ensureListSize(List<String> list, int size) {
        while (list.size() < size) {
            list.add("");
        }
    }
}
