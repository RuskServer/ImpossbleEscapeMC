package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.item.factory.HelmetVisors;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
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
 * {@link HelmetAttachmentGUI} の操作。
 * バイザーのスロットをバイザーを持ってクリックすると装着、何も持たずにクリックすると取り外し。
 * 自分のインベントリのバイザーをシフトクリックしても装着できる。編集中のヘルメットは動かせない
 */
public class HelmetAttachmentGUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof HelmetAttachmentGUI gui)) return;
        Player player = gui.player();

        // 編集中のヘルメット (手に持っている時) を持ち上げる・入れ替える操作は受け付けない
        boolean clickedHeld = event.getClickedInventory() == player.getInventory() && event.getSlot() == gui.inventorySlot();
        boolean swapsHeld = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() == gui.inventorySlot();
        if (clickedHeld || swapsHeld) {
            event.setCancelled(true);
            return;
        }

        ItemStack helmet = gui.currentHelmet();
        if (helmet == null) {
            event.setCancelled(true);
            player.closeInventory();
            Messages.chat(player, "iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_001", MessageText.of("iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_001", MessageText.payload(MessageText.of("iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }

        int raw = event.getRawSlot();
        if (raw >= event.getInventory().getSize()) {
            // 自分のインベントリ: 普通のクリックはそのまま (バイザーをカーソルに取る)、シフトクリックで装着
            if (!event.isShiftClick()) return;
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (HelmetVisors.visorDef(item) == null) return;
            if (attach(gui, helmet, item)) {
                item.setAmount(item.getAmount() - 1);
                event.getClickedInventory().setItem(event.getSlot(), item.getAmount() <= 0 ? null : item);
                gui.refresh();
            }
            return;
        }

        event.setCancelled(true);
        if (raw != HelmetAttachmentGUI.VISOR_SLOT) return;

        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            if (attach(gui, helmet, cursor)) {
                ItemStack rest = cursor.clone();
                rest.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(rest.getAmount() <= 0 ? null : rest);
                gui.refresh();
            }
            return;
        }

        String visorId = HelmetVisors.visorOf(helmet);
        if (visorId == null) return;
        ItemStack visor = HelmetVisors.attachedItem(helmet);
        if (visor == null) return;
        gui.setHelmet(HelmetVisors.withoutVisor(helmet));
        if (visor != null) give(player, visor);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 0.8f);
        Messages.chat(player, "iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_002", MessageText.of("iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_002", MessageText.payload(MessageText.of("iem.inventory.helmetattachmentguilistener.oninventoryclick.notice_002.text_01", displayName(visorId)))));
        gui.refresh();
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof HelmetAttachmentGUI gui)) return;
        int size = event.getInventory().getSize();
        // 画面の中と、編集中のヘルメット (手に持っている時) の場所にはドラッグで置かせない
        int heldRaw = gui.inventorySlot() < 9 ? size + 27 + gui.inventorySlot() : -1;
        for (int raw : event.getRawSlots()) {
            if (raw < size || raw == heldRaw) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * ヘルメットにバイザーを1つ付ける (付いていた物は手元に戻す)。
     * 付けられない時は理由を伝えてfalse。呼び出し側がバイザーを1つ減らす
     */
    private boolean attach(HelmetAttachmentGUI gui, ItemStack helmet, ItemStack item) {
        Player player = gui.player();
        ItemDefinition visor = HelmetVisors.visorDef(item);
        if (visor == null) {
            Messages.chat(player, "iem.inventory.helmetattachmentguilistener.attach.notice_001", MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_001", MessageText.payload(MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_001.text_01").color(NamedTextColor.RED))));
            return false;
        }
        if (!HelmetVisors.fits(gui.helmetDef(), visor.id)) {
            Messages.chat(player, "iem.inventory.helmetattachmentguilistener.attach.notice_002", MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_002", MessageText.payload(MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_002.text_01").color(NamedTextColor.RED))));
            return false;
        }
        String previous = HelmetVisors.visorOf(helmet);
        ItemStack old = previous != null ? HelmetVisors.attachedItem(helmet) : null;
        if (previous != null && old == null) return false;
        gui.setHelmet(HelmetVisors.withVisor(helmet, item));
        if (previous != null) {
            if (old != null) give(player, old);
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.2f);
        Messages.chat(player, "iem.inventory.helmetattachmentguilistener.attach.notice_003", MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_003", MessageText.payload(MessageText.of("iem.inventory.helmetattachmentguilistener.attach.notice_003.text_01", visor.displayName))));
        return true;
    }

    private static String displayName(String itemId) {
        ItemDefinition def = ItemRegistry.get(itemId);
        return def != null ? def.displayName : itemId;
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).forEach((i, drop) -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
    }
}
