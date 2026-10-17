package com.lunar_prototype.impossbleEscapeMC.gui;

import com.lunar_prototype.impossbleEscapeMC.item.HelmetVisors;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
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
            player.sendMessage(Component.text("編集中のヘルメットが見つかりません", NamedTextColor.RED));
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
        gui.setHelmet(HelmetVisors.withoutVisor(helmet));
        ItemStack visor = ItemFactory.create(visorId);
        if (visor != null) give(player, visor);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 0.8f);
        player.sendMessage(displayName(visorId) + " §eを取り外しました");
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
            player.sendMessage(Component.text("ヘルメットに付けられるアタッチメントではありません", NamedTextColor.RED));
            return false;
        }
        if (!HelmetVisors.fits(gui.helmetDef(), visor.id)) {
            player.sendMessage(Component.text("この装着品はこのヘルメットには付けられません", NamedTextColor.RED));
            return false;
        }
        String previous = HelmetVisors.visorOf(helmet);
        gui.setHelmet(HelmetVisors.withVisor(helmet, visor.id));
        if (previous != null) {
            ItemStack old = ItemFactory.create(previous);
            if (old != null) give(player, old);
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.2f);
        player.sendMessage(visor.displayName + " §aを装着しました");
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
