package com.lunar_prototype.impossbleEscapeMC.map;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地図を手に持っている間だけ、本人のクライアントにはオフハンドを空として見せる。
 *
 * Minecraftは地図を持ち、かつオフハンドが空の時だけ地図を両手持ちの大きな表示にする。
 * オフハンドにバックパックを持っていると常に小さな表示になるため、本人に送るオフハンド欄の更新だけを空に書き換える。
 * サーバー上のオフハンドや、他のプレイヤーから見た装備はそのまま。
 */
public class MapOffhandHider extends PacketListenerAbstract implements Listener {

    /** プレイヤー自身のインベントリ画面 */
    private static final int PLAYER_INVENTORY_WINDOW_ID = 0;
    /** インベントリ画面 (window 0) でのオフハンド欄のスロット番号 */
    private static final int OFFHAND_CONTAINER_SLOT = 45;
    /** SetPlayerInventory パケットでのオフハンドのスロット番号 */
    private static final int OFFHAND_INVENTORY_SLOT = 40;

    private final ImpossbleEscapeMC plugin;
    private final RaidMapManager mapManager;
    /** オフハンドを空に見せているプレイヤー (パケット送信スレッドからも読む) */
    private final Set<UUID> hiding = ConcurrentHashMap.newKeySet();

    public MapOffhandHider(ImpossbleEscapeMC plugin, RaidMapManager mapManager) {
        this.plugin = plugin;
        this.mapManager = mapManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(this);
        // 地図スロットの差し替えやオフハンドの装備変更など、イベントで拾えない変化に追従する
        Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 20L, 20L);
    }

    private boolean shouldHide(Player player) {
        if (DatapackGunnerManager.isGunner(player)) return false;
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return false;
        if (mapManager.isLootSessionSuppressed(player.getUniqueId())) return false;
        return mapManager.isMapSlotItem(player.getInventory().getItemInMainHand())
                && !player.getInventory().getItemInOffHand().getType().isAir();
    }

    /** 状態が変わった時だけインベントリを送り直す。送り直したパケットは onPacketSend で書き換わる */
    private void refresh(Player player) {
        boolean hide = shouldHide(player);
        boolean changed = hide ? hiding.add(player.getUniqueId()) : hiding.remove(player.getUniqueId());
        if (changed) {
            player.updateInventory();
        }
    }

    private void refreshNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) refresh(player);
        });
    }

    // 持ち替えや装備の変更はイベント処理の後に反映されるため、次のtickで判定する

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) refreshNextTick(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hiding.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null || user.getUUID() == null || !hiding.contains(user.getUUID())) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID && packet.getSlot() == OFFHAND_CONTAINER_SLOT) {
                packet.setItem(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID && packet.getItems().size() > OFFHAND_CONTAINER_SLOT) {
                List<ItemStack> items = new ArrayList<>(packet.getItems());
                items.set(OFFHAND_CONTAINER_SLOT, ItemStack.EMPTY);
                packet.setItems(items);
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            WrapperPlayServerSetPlayerInventory packet = new WrapperPlayServerSetPlayerInventory(event);
            if (packet.getSlot() == OFFHAND_INVENTORY_SLOT) {
                packet.setStack(ItemStack.EMPTY);
                event.markForReEncode(true);
            }
        }
    }
}
