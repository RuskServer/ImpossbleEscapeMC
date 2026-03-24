package com.lunar_prototype.impossbleEscapeMC.modules.backpack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class BackpackListener implements Listener {
    private static final int BACKPACK_BUTTON_SLOT = 2;

    private final BackpackModule backpackModule;
    private final ImpossbleEscapeMC plugin;

    public BackpackListener(BackpackModule backpackModule) {
        this.backpackModule = backpackModule;
        this.plugin = ImpossbleEscapeMC.getInstance();
        startButtonTask();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                    WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
                    // ウィンドウID 0 (インベントリ) かつ スロット 2 (バックパックボタン)
                    if (packet.getWindowId() == 0 && packet.getSlot() == BACKPACK_BUTTON_SLOT) {
                        event.setCancelled(true); // パケットを握り潰す

                        Player player = (Player) event.getPlayer();
                        if (!isPlayableMode(player)) return;

                        // クライアントのカーソルをサーバー側でAIRに強制同期（ゴースト防止）
                        int stateId = packet.getStateId().orElse(0);
                        WrapperPlayServerSetSlot cursorClear = new WrapperPlayServerSetSlot(
                                -1,
                                stateId,
                                -1,
                                SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(Material.AIR))
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player, cursorClear);

                        // メインロジックは同期スレッドで実行
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            backpackModule.openBackpackFromOffhand(player);
                        });
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();

        // クラフトグリッドのボタンクリック検知は PacketEvents で処理済みのため、
        // ここではボタンそのものへのクリックをキャンセルする最低限の処理のみ行う
        if (isPlayerCraftingGrid(view) && event.getRawSlot() == BACKPACK_BUTTON_SLOT) {
            event.setCancelled(true);
            return;
        }

        // Trigger item cleanup
        if (isBackpackTrigger(event.getCurrentItem()) && (!isPlayerCraftingGrid(view) || event.getRawSlot() != BACKPACK_BUTTON_SLOT)) {
            event.setCurrentItem(null);
        }
        if (isBackpackTrigger(event.getCursor())) {
            event.setCursor(null);
        }
        // NUMBER_KEY でホットバーのトリガーと入れ替えようとした場合も消去
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (isBackpackTrigger(hotbarItem)) {
                event.getWhoClicked().getInventory().setItem(event.getHotbarButton(), null);
                event.setCancelled(true);
            }
        }

        // Right-click to open backpack from player's inventory
        if (event.getWhoClicked() instanceof Player player
                && event.getClick().isRightClick()
                && event.getClickedInventory() != null
                && event.getClickedInventory().equals(player.getInventory())
                && backpackModule.isBackpackItem(event.getCurrentItem())) {

            if (!isPlayableMode(player)) return;

            // バックパックスロットはオフハンド固定
            if (event.getSlot() != 40) {
                player.sendMessage("§cバックパックはオフハンドに装備して開いてください。");
                event.setCancelled(true);
                return;
            }

            event.setCancelled(true);
            backpackModule.openBackpackFromOffhand(player);
            return;
        }

        // オフハンドスロット(40)の変更後にビジュアル表示を更新
        if (event.getWhoClicked() instanceof Player player && event.getSlot() == 40) {
            Bukkit.getScheduler().runTask(plugin, () -> updateDisplay(player));
        }

        // Backpack in backpack prohibition
        if (event.getView().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            if (event.getClickedInventory() == null) return;

            // placing item into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())) {
                ItemStack cursor = event.getCursor();
                if (backpackModule.isBackpackItem(cursor)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

            // shift-click moving from player inv into backpack
            if (event.getClickedInventory().equals(event.getWhoClicked().getInventory())
                    && event.isShiftClick()
                    && backpackModule.isBackpackItem(event.getCurrentItem())) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                }
            }
            // hotbar number key move into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())
                    && event.getClick() == ClickType.NUMBER_KEY) {
                ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                if (backpackModule.isBackpackItem(hotbarItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

            // F-key offhand swap move into backpack
            if (event.getClickedInventory().equals(event.getView().getTopInventory())
                    && event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = event.getWhoClicked().getInventory().getItemInOffHand();
                if (backpackModule.isBackpackItem(offhandItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                    }
                    return;
                }
            }

        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            if (backpackModule.isBackpackItem(event.getOldCursor())) {
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot < event.getView().getTopInventory().getSize()) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player player) {
                            player.sendMessage("§cバックパックの中にバックパックは入れられません。");
                        }
                        return;
                    }
                }
            }
        }

        if (isPlayerCraftingGrid(event.getView()) && event.getRawSlots().contains(BACKPACK_BUTTON_SLOT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isPlayerCraftingGrid(event.getView())) {
            event.getView().setItem(BACKPACK_BUTTON_SLOT, null);
            Player player = (Player) event.getPlayer();
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (isBackpackTrigger(item)) {
                    player.getInventory().setItem(i, null);
                }
            }
        }

        if (event.getPlayer() instanceof Player player) {
            backpackModule.handleClose(player, event.getInventory());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
        }
        // swap後のオフハンドアイテムで表示を更新（1tick後に実際のインベントリ状態で判定）
        Bukkit.getScheduler().runTask(plugin, () -> updateDisplay(player));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isBackpackTrigger(event.getItem().getItemStack())) {
            event.getItem().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isBackpackTrigger(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isBackpackTrigger(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private void startButtonTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                InventoryView view = player.getOpenInventory();
                if (!isPlayerCraftingGrid(view)) continue;

                ItemStack current = view.getItem(BACKPACK_BUTTON_SLOT);
                boolean shouldHave = isPlayableMode(player) && backpackModule.isBackpackItem(player.getInventory().getItemInOffHand());
                if (shouldHave) {
                    if (!isBackpackTrigger(current)) {
                        view.setItem(BACKPACK_BUTTON_SLOT, getBackpackTriggerButton());
                    }
                } else if (isBackpackTrigger(current)) {
                    view.setItem(BACKPACK_BUTTON_SLOT, null);
                }
            }
        }, 0L, 5L);
    }

    private ItemStack getBackpackTriggerButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a[ Backpack ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7オフハンドのバックパックを開きます");
            lore.add("");
            lore.add("§eクリックで開く");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, 3);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isBackpackTrigger(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer trigger = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER);
        return trigger != null && trigger == 3;
    }

    private boolean isPlayerCraftingGrid(InventoryView view) {
        return view.getType() == InventoryType.CRAFTING;
    }

    private boolean isPlayableMode(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    // ---------------------------------------------------------------
    // ビジュアル表示制御
    // ---------------------------------------------------------------

    /**
     * プレイヤーのオフハンドを確認し、バックパックがあれば表示、なければ非表示にする。
     */
    private void updateDisplay(Player player) {
        BackpackDisplayManager dm = backpackModule.getDisplayManager();
        if (dm == null) return;
        dm.sync(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        BackpackDisplayManager dm = backpackModule.getDisplayManager();
        if (dm == null) return;

        // 参加した直後に自分のオフハンドを確認して表示
        Bukkit.getScheduler().runTask(plugin, () -> updateDisplay(joined));

        // 既存のバックパック装備者の表示を新参加者に送信
        for (Player other : joined.getWorld().getPlayers()) {
            if (other.equals(joined)) continue;
            dm.showToPlayer(other, joined);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BackpackDisplayManager dm = backpackModule.getDisplayManager();
        if (dm != null) dm.clearDisplay(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        BackpackDisplayManager dm = backpackModule.getDisplayManager();
        if (dm == null) return;

        // ワールド移動後に新しいワールドで再度表示を更新
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateDisplay(player);
            // 新ワールドの他プレイヤーに自分の表示を送信
            if (dm.hasDisplay(player)) {
                for (Player other : player.getWorld().getPlayers()) {
                    if (other.equals(player)) continue;
                    dm.showToPlayer(player, other);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        BackpackDisplayManager dm = backpackModule.getDisplayManager();
        if (dm == null) return;

        // テレポート後に表示を再描画（マウントの同期や位置の修正のため）
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateDisplay(player);
            if (dm.hasDisplay(player)) {
                for (Player other : player.getWorld().getPlayers()) {
                    if (other.equals(player)) continue;
                    dm.showToPlayer(player, other);
                }
            }
        });
    }
}
