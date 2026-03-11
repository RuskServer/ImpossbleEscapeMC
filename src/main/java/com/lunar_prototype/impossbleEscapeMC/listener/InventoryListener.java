package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.AttachmentGUI;
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
                    if (packet.getWindowId() == 0 && packet.getSlot() == 4) {
                        event.setCancelled(true); // パケット握り潰し

                        int stateId = packet.getStateId().orElse(0);

                        // GUI処理だけ自前で実行
                        Player player = (Player) event.getPlayer();

                        WrapperPlayServerSetSlot cursorClear = new WrapperPlayServerSetSlot(
                                -1,
                                stateId,
                                -1,
                                SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(Material.AIR))
                        );
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player, cursorClear);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            ItemStack mainHand = player.getInventory().getItemInMainHand();
                            String itemId = mainHand.hasItemMeta() ?
                                    mainHand.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
                            ItemDefinition def = ItemRegistry.get(itemId);

                            if (def != null && "GUN".equals(def.type)) {
                                new AttachmentGUI(player, mainHand).open();
                            } else {
                                player.sendMessage("§cメインハンドに有効な銃を持っていません");
                            }
                        });
                    }
                }
            }
        });
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
                InventoryView view = player.getOpenInventory();
                if (isPlayerCraftingGrid(view)) {
                    ItemStack current = view.getItem(4);
                    if (!isGuiTrigger(current)) {
                        view.setItem(4, getGuiTriggerButton());
                    }
                }
            }
        }, 0L, 5L).getTaskId();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // トリガーアイテムが不正な位置にあれば削除
        if (isGuiTrigger(event.getCurrentItem())) {
            if (!isPlayerCraftingGrid(event.getView()) || event.getRawSlot() != 4) {
                event.setCurrentItem(null);
            }
        }
        if (isGuiTrigger(event.getCursor())) {
            event.setCursor(null);
        }

        InventoryView view = event.getView();
        if (!isPlayerCraftingGrid(view)) return;

        // スロット4（クラフトグリッド右下）への干渉は常に制限
        if (event.getRawSlot() == 4) {
            event.setCancelled(true);
            
            Player player = (Player) event.getWhoClicked();
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            
            // 銃を持っている場合のみGUIを開く
            String itemId = (mainHand.hasItemMeta()) ? mainHand.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING) : null;
            ItemDefinition def = ItemRegistry.get(itemId);

            if (def != null && "GUN".equals(def.type)) {
                new AttachmentGUI(player, mainHand).open();
            } else if (event.getCurrentItem() != null && isGuiTrigger(event.getCurrentItem())) {
                player.sendMessage("§cメインハンドに有効な銃を持っていません");
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (isGuiTrigger(event.getItem().getItemStack())) {
            event.getItem().remove();
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isGuiTrigger(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (isPlayerCraftingGrid(event.getView())) {
            event.getView().setItem(4, null);
            Player player = (Player) event.getPlayer();
        }
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
        if (event.getRawSlots().contains(4) && isPlayerCraftingGrid(event.getView())) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
        }
    }
}
