package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.item.DatapackAttachments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * {@link DatapackAttachmentGUI} の操作。
 * スロットをアタッチメントを持ってクリックすると装着、何も持たずにクリックすると取り外し。
 * 自分のインベントリのアタッチメントをシフトクリックしても装着できる。編集中の銃は動かせない
 */
public class DatapackAttachmentGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DatapackAttachmentGUI gui)) return;
        Player player = gui.player();

        // 編集中の銃を持ち上げる・入れ替える操作は受け付けない
        boolean clickedHeld = event.getClickedInventory() == player.getInventory() && event.getSlot() == gui.heldSlot();
        boolean swapsHeld = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == gui.heldSlot();
        if (clickedHeld || swapsHeld) {
            event.setCancelled(true);
            return;
        }

        ItemStack gun = gui.currentGun();
        if (gun == null) {
            event.setCancelled(true);
            player.closeInventory();
            player.sendMessage(Component.text("編集中の銃が見つかりません", NamedTextColor.RED));
            return;
        }

        int raw = event.getRawSlot();
        if (raw >= event.getInventory().getSize()) {
            // 自分のインベントリ: 普通のクリックはそのまま (アタッチメントをカーソルに取る)、シフトクリックで装着
            if (!event.isShiftClick()) return;
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            String slot = DatapackAttachments.slotOf(item);
            if (slot == null) return;
            if (attach(gui, gun, slot, item)) {
                item.setAmount(item.getAmount() - 1);
                event.getClickedInventory().setItem(event.getSlot(), item.getAmount() <= 0 ? null : item);
                gui.refresh();
            }
            return;
        }

        event.setCancelled(true);
        String slot = gui.slotAt(raw);
        if (slot == null) return;

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (attach(gui, gun, slot, cursor)) {
                ItemStack rest = cursor.clone();
                rest.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(rest.getAmount() <= 0 ? null : rest);
                gui.refresh();
            }
            return;
        }

        ItemStack current = DatapackAttachments.attached(gun).get(slot);
        if (current == null) return;
        gui.setGun(DatapackAttachments.withoutAttachment(gun, slot));
        give(player, current);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 0.8f);
        player.sendMessage(DatapackAttachmentGUI.name(DatapackAttachments.attachmentIdOf(current))
                .append(Component.text(" を取り外しました", NamedTextColor.YELLOW)));
        gui.refresh();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof DatapackAttachmentGUI gui)) return;
        int size = event.getInventory().getSize();
        // 画面の中と、編集中の銃の場所にはドラッグで置かせない
        int heldRaw = size + 27 + gui.heldSlot();
        for (int raw : event.getRawSlots()) {
            if (raw < size || raw == heldRaw) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * 銃の slot にアタッチメントを1つ付ける (付いていた物は手元に戻す)。
     * 付けられない時は理由を伝えてfalse。呼び出し側がアタッチメントを1つ減らす
     */
    private boolean attach(DatapackAttachmentGUI gui, ItemStack gun, String slot, ItemStack attachment) {
        Player player = gui.player();
        String attachmentId = DatapackAttachments.attachmentIdOf(attachment);
        if (attachmentId == null) {
            player.sendMessage(Component.text("この銃に付けられるアタッチメントではありません", NamedTextColor.RED));
            return false;
        }
        String attachmentSlot = DatapackAttachments.slotOf(attachment);
        if (!slot.equals(attachmentSlot)) {
            player.sendMessage(Component.text("このアタッチメントは " + DatapackAttachmentGUI.slotLabel(attachmentSlot) + " 用です", NamedTextColor.RED));
            return false;
        }
        DatapackAttachments.SlotDef slotDef = DatapackAttachments.slotsOf(gui.gunId()).stream()
                .filter(s -> s.slot().equals(slot)).findFirst().orElse(null);
        if (slotDef == null || slotDef.option(attachmentId) == null) {
            player.sendMessage(Component.text("このアタッチメントはこの銃には付けられません", NamedTextColor.RED));
            return false;
        }
        ItemStack previous = DatapackAttachments.attached(gun).get(slot);
        gui.setGun(DatapackAttachments.withAttachment(gun, attachment));
        if (previous != null) give(player, previous);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.2f);
        player.sendMessage(DatapackAttachmentGUI.name(attachmentId).append(Component.text(" を装着しました", NamedTextColor.GREEN)));
        return true;
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).forEach((i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }
}
