package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.modules.inventory.CostSlotManager;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 2 マス以上使う物の説明の最後に「サイズ N マス」を足す (拾う前・入れる前に大きさが分かるように)。
 * <p>
 * アイテムそのものは変えず、プレイヤーへ送るアイテムの表示だけを書き換える (インベントリ・コンテナ・カーソル)。
 * クリエイティブではクライアントが表示どおりのアイテムを送り返してくるので、書き換えない
 */
public final class ItemSizeTooltip extends PacketListenerAbstract {

    private static ItemSizeTooltip instance;

    private ItemSizeTooltip() {
    }

    public static void init() {
        if (instance != null) return;
        instance = new ItemSizeTooltip();
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    public static void shutdown() {
        if (instance == null) return;
        PacketEvents.getAPI().getEventManager().unregisterListener(instance);
        instance = null;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        PacketType.Play.Server type = event.getPacketType() instanceof PacketType.Play.Server server ? server : null;
        if (type != PacketType.Play.Server.SET_SLOT && type != PacketType.Play.Server.WINDOW_ITEMS
                && type != PacketType.Play.Server.SET_PLAYER_INVENTORY && type != PacketType.Play.Server.SET_CURSOR_ITEM) return;
        Player player = event.getPlayer();
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;

        switch (type) {
            case SET_SLOT -> {
                WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
                var shown = withSize(packet.getItem());
                if (shown != null) {
                    packet.setItem(shown);
                    event.markForReEncode(true);
                }
            }
            case WINDOW_ITEMS -> {
                WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
                List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(packet.getItems());
                boolean changed = false;
                for (int i = 0; i < items.size(); i++) {
                    var shown = withSize(items.get(i));
                    if (shown != null) {
                        items.set(i, shown);
                        changed = true;
                    }
                }
                var carried = packet.getCarriedItem().map(ItemSizeTooltip::withSize).orElse(null);
                if (carried != null) {
                    packet.setCarriedItem(carried);
                    changed = true;
                }
                if (changed) {
                    packet.setItems(items);
                    event.markForReEncode(true);
                }
            }
            case SET_PLAYER_INVENTORY -> {
                WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
                var shown = withSize(packet.getStack());
                if (shown != null) {
                    packet.setStack(shown);
                    event.markForReEncode(true);
                }
            }
            case SET_CURSOR_ITEM -> {
                WrapperPlayServerSetCursorItem packet = new WrapperPlayServerSetCursorItem(event);
                var shown = withSize(packet.getStack());
                if (shown != null) {
                    packet.setStack(shown);
                    event.markForReEncode(true);
                }
            }
            default -> {
            }
        }
    }

    /** サイズを足した表示のアイテム。1 マスの物・占有スロットなどはnull (書き換えない) */
    private static com.github.retrooper.packetevents.protocol.item.ItemStack withSize(
            com.github.retrooper.packetevents.protocol.item.ItemStack packetItem) {
        if (packetItem == null || packetItem.isEmpty()) return null;
        ItemStack item = SpigotConversionUtil.toBukkitItemStack(packetItem);
        int cost = CostSlotManager.stackCost(item);
        if (cost < 2) return null;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("サイズ " + cost + " マス", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }
}
