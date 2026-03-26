package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUI;
import com.lunar_prototype.impossbleEscapeMC.gui.PDAGUI;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class InventoryListener implements Listener {

    private final ImpossbleEscapeMC plugin;
    private int taskId = -1;

    public InventoryListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        startButtonTask();

        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() == PacketType.Play.Client.CLICK_WINDOW) {
                    WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
                    if (packet.getWindowId() == 0 && (packet.getSlot() == 4 || packet.getSlot() == 1)) {
                        event.setCancelled(true); // パケット握り潰し

                        int stateId = packet.getStateId().orElse(0);
                        Player player = (Player) event.getPlayer();
                        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && 
                            player.getGameMode() != org.bukkit.GameMode.ADVENTURE) return;

                        final int slot = packet.getSlot();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            syncClientTriggerState(player, stateId, slot);
                            if (slot == 4) {
                                ItemStack mainHand = player.getInventory().getItemInMainHand();
                                String itemId = mainHand.hasItemMeta() ?
                                        mainHand.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
                                ItemDefinition def = ItemRegistry.get(itemId);

                                if (def != null && "GUN".equals(def.type)) {
                                    new AttachmentGUI(player, mainHand).open();
                                } else {
                                    player.sendMessage("§cメインハンドに有効な銃を持っていません");
                                }
                            } else if (slot == 1) {
                                new PDAGUI(player).open();
                            }
                        });
                    }
                }
            }
        });
    }

    private void syncClientTriggerState(Player player, int stateId, int clickedSlot) {
        InventoryView view = player.getOpenInventory();
        if (!isPlayerCraftingGrid(view)) return;

        sendSlotUpdate(player, stateId, clickedSlot, view.getItem(clickedSlot));
        sendSlotUpdate(player, stateId, 0, view.getItem(0));
        sendSlotUpdate(player, stateId, -1, player.getItemOnCursor());
    }

    private void sendSlotUpdate(Player player, int stateId, int slot, ItemStack item) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetSlot(
                slot == -1 ? -1 : 0,
                stateId,
                slot,
                SpigotConversionUtil.fromBukkitItemStack(item == null ? new ItemStack(Material.AIR) : item)
        ));
    }

    private ItemStack getGuiTriggerButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6[ 武器カスタマイズ ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7メインハンドの武器を改造します");
            lore.add("");
            lore.add("§eクリックでカスタマイズGUIを開く");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getPdaButton() {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b[ PDA - 個人端末 ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7自分のステータスや進捗を確認します");
            lore.add("");
            lore.add("§eクリックでPDAを開く");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, 2);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isGuiTrigger(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER);
    }

    private boolean isPlayerCraftingGrid(InventoryView view) {
        return view.getType() == InventoryType.CRAFTING;
    }

    private void startButtonTask() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                // 定期的なコスト更新
                com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.updateInventory(player, player.getInventory());
                
                InventoryView view = player.getOpenInventory();
                if (isPlayerCraftingGrid(view)) {
                    // サバイバルとアドベンチャーのみに限定
                    boolean shouldHaveButton = (player.getGameMode() == org.bukkit.GameMode.SURVIVAL || 
                                               player.getGameMode() == org.bukkit.GameMode.ADVENTURE);
                    
                    ItemStack current4 = view.getItem(4);
                    ItemStack current1 = view.getItem(1);
                    if (shouldHaveButton) {
                        if (!isGuiTrigger(current4)) {
                            view.setItem(4, getGuiTriggerButton());
                        }
                        if (!isGuiTrigger(current1)) {
                            view.setItem(1, getPdaButton());
                        }
                    } else {
                        // もし既にボタンがあれば消去する
                        if (isGuiTrigger(current4)) {
                            view.setItem(4, null);
                        }
                        if (isGuiTrigger(current1)) {
                            view.setItem(1, null);
                        }
                    }
                }
            }
        }, 0L, 5L).getTaskId();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // トリガーアイテムが不正な位置にあれば削除 (どんなクリックでも)
        if (isGuiTrigger(event.getCurrentItem())) {
            if (!isPlayerCraftingGrid(event.getView()) || (event.getRawSlot() != 4 && event.getRawSlot() != 1)) {
                event.setCurrentItem(null);
            }
        }
        if (isGuiTrigger(event.getCursor())) {
            event.setCursor(null);
        }

        // コスト制限用アイテムの操作禁止
        if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(event.getCurrentItem()) ||
            com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        // ホットキーによる入れ替え防止
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbarItem = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
            if (isGuiTrigger(hotbarItem)) {
                event.getWhoClicked().getInventory().setItem(event.getHotbarButton(), null);
                event.setCancelled(true);
            }
            if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(hotbarItem)) {
                event.setCancelled(true);
            }
        }

        InventoryView view = event.getView();
        if (!isPlayerCraftingGrid(view)) {
            // 他のGUI（バックパック等）でもコスト更新をスケジュール
            scheduleCostUpdate((Player) event.getWhoClicked(), event.getView());
            return;
        }

        Player player = (Player) event.getWhoClicked();
        if (player.getGameMode() != org.bukkit.GameMode.SURVIVAL && 
            player.getGameMode() != org.bukkit.GameMode.ADVENTURE) return;

        // クラフトグリッドのトリガーボタン押下自体は PacketEvents 側で処理する。
        if (event.getRawSlot() == 4 || event.getRawSlot() == 1) {
            event.setCancelled(true);
            return;
        }

        scheduleCostUpdate(player, view);
    }

    private void scheduleCostUpdate(Player player, InventoryView view) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.updateInventory(player, player.getInventory());
            if (view.getTopInventory() != null && !(view.getTopInventory().getHolder() instanceof Player)) {
                com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.updateInventory(player, view.getTopInventory());
            }
        });
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isGuiTrigger(event.getItem().getItemStack())) {
            event.getItem().remove();
            event.setCancelled(true);
            return;
        }

        if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(event.getItem().getItemStack())) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Player player) {
            scheduleCostUpdate(player, player.getOpenInventory());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isGuiTrigger(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            return;
        }
        if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            return;
        }

        scheduleCostUpdate(event.getPlayer(), event.getPlayer().getOpenInventory());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isPlayerCraftingGrid(event.getView())) {
            event.getView().setItem(4, null);
            event.getView().setItem(1, null);
        }
        
        Player player = (Player) event.getPlayer();
        // 念のためプレイヤーのインベントリ全体をスキャンしてボタンやコストプレースホルダーが混じっていれば消去
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isGuiTrigger(item) || com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(item)) {
                player.getInventory().setItem(i, null);
            }
        }
        
        // 閉じるときにコストを再計算して占有スロットを再配置
        com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.updateInventory(player, player.getInventory());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isGuiTrigger(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if ((event.getRawSlots().contains(4) || event.getRawSlots().contains(1)) && isPlayerCraftingGrid(event.getView())) {
            event.setCancelled(true);
        }

        if (com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(event.getOldCursor())) {
            event.setCancelled(true);
            return;
        }

        scheduleCostUpdate((Player) event.getWhoClicked(), event.getView());
    }
}
