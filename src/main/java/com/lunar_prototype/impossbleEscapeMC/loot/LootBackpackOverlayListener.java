package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndEvent;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndReason;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionStartEvent;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LootBackpackOverlayListener implements Listener {
    private static final int OVERLAY_START = 9;
    private static final int OVERLAY_SIZE = 27;
    private static final int TOGGLE_SLOT = 8;

    private static final int GUI_TRIGGER_TOGGLE = 91;
    private static final int GUI_TRIGGER_SCROLL_UP = 92;
    private static final int GUI_TRIGGER_SCROLL_INFO = 93;
    private static final int GUI_TRIGGER_SCROLL_DOWN = 94;
    private static final int GUI_TRIGGER_UNAVAILABLE = 95;

    private static final List<Integer> SCROLL_BUTTON_SLOTS = Arrays.asList(17, 26, 35);
    private static final int SCROLL_UP_SLOT = 17;
    private static final int SCROLL_INFO_SLOT = 26;
    private static final int SCROLL_DOWN_SLOT = 35;

    private static final List<Integer> BACKPACK_DISPLAY_SLOTS = createBackpackDisplaySlots();

    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, OverlaySession> sessions = new HashMap<>();

    public LootBackpackOverlayListener(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootSessionStart(LootSessionStartEvent event) {
        Player player = event.getPlayer();
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule == null) return;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!backpackModule.isBackpackItem(offhand)) return;

        endSession(player, LootSessionEndReason.OTHER);

        String backpackUid = backpackModule.ensureBackpackUid(offhand);
        OverlaySession session = new OverlaySession(backpackUid, backpackModule.loadBackpackInventory(offhand));
        snapshotInitialState(player, session);
        sessions.put(player.getUniqueId(), session);

        setSystemSuppressed(player, true);
        switchMode(player, session, ViewMode.BACKPACK);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLootSessionEnd(LootSessionEndEvent event) {
        endSession(event.getPlayer(), event.getReason());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder)) return;

        cleanupMisplacedButtons(player, session);

        // セッション中にオフハンドを持ち替えられると保存先が不安定になるため禁止
        if (event.getSlot() == 40 || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(player.getInventory())) {
            int slot = event.getSlot();

            if (slot == TOGGLE_SLOT) {
                event.setCancelled(true);
                toggleMode(player, session);
                return;
            }

            if (session.mode == ViewMode.BACKPACK && SCROLL_BUTTON_SLOTS.contains(slot)) {
                event.setCancelled(true);
                if (slot == SCROLL_UP_SLOT) {
                    moveScroll(player, session, -1);
                } else if (slot == SCROLL_DOWN_SLOT) {
                    moveScroll(player, session, 1);
                }
                return;
            }
        }

        if (isOverlayControlItem(event.getCurrentItem()) || isOverlayControlItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY && event.getHotbarButton() == TOGGLE_SLOT) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> syncSessionState(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder)) return;

        if (session.mode == ViewMode.BACKPACK) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize()) continue;
                int converted = event.getView().convertSlot(rawSlot);
                if (converted == TOGGLE_SLOT || SCROLL_BUTTON_SLOTS.contains(converted)) {
                    event.setCancelled(true);
                    return;
                }
            }
        } else {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize()) continue;
                int converted = event.getView().convertSlot(rawSlot);
                if (converted == TOGGLE_SLOT) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> syncSessionState(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (!sessions.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!sessions.containsKey(player.getUniqueId())) return;
        if (event.getInventory().getHolder() instanceof SearchGUI.SearchHolder) return;

        // SearchGUIのClose経由で終端イベントが来る想定だが、保険として復元を保証する
        endSession(player, LootSessionEndReason.OTHER);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!sessions.containsKey(player.getUniqueId())) return;
        Bukkit.getPluginManager().callEvent(new LootSessionEndEvent(
                player,
                com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionSourceType.UNKNOWN,
                null,
                null,
                LootSessionEndReason.PLAYER_QUIT
        ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!sessions.containsKey(player.getUniqueId())) return;
        Bukkit.getPluginManager().callEvent(new LootSessionEndEvent(
                player,
                com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionSourceType.UNKNOWN,
                null,
                null,
                LootSessionEndReason.PLAYER_DEATH
        ));
    }

    public boolean isSessionActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private void endSession(Player player, LootSessionEndReason reason) {
        OverlaySession session = sessions.remove(player.getUniqueId());
        if (session == null) return;

        try {
            syncSessionState(player, session);
            persistBackpack(player, session);
        } finally {
            restorePlayerState(player, session);
            setSystemSuppressed(player, false);
            if (player.isOnline()) {
                player.updateInventory();
            }
        }
    }

    private void syncSessionState(Player player) {
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        syncSessionState(player, session);
    }

    private void syncSessionState(Player player, OverlaySession session) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder)) return;

        if (session.mode == ViewMode.BACKPACK) {
            syncBackpackFromDisplay(player, session);
            renderBackpackMode(player, session);
            return;
        }

        capturePlayerRows(player, session);
        renderToggleButton(player, session);
    }

    private void snapshotInitialState(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < OVERLAY_SIZE; i++) {
            session.playerRowsShadow[i] = cloneOrNull(inv.getItem(OVERLAY_START + i));
        }
        session.savedToggleSlotItem = cloneOrNull(inv.getItem(TOGGLE_SLOT));
    }

    private void restorePlayerState(Player player, OverlaySession session) {
        if (session.mode == ViewMode.BACKPACK) {
            syncBackpackFromDisplay(player, session);
        } else {
            capturePlayerRows(player, session);
        }

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < OVERLAY_SIZE; i++) {
            inv.setItem(OVERLAY_START + i, cloneOrNull(session.playerRowsShadow[i]));
        }
        inv.setItem(TOGGLE_SLOT, cloneOrNull(session.savedToggleSlotItem));
    }

    private void renderBackpackMode(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        int startIndex = getBackpackWindowStartIndex(session);

        for (int i = 0; i < BACKPACK_DISPLAY_SLOTS.size(); i++) {
            int playerSlot = BACKPACK_DISPLAY_SLOTS.get(i);
            int backpackSlot = startIndex + i;
            ItemStack item = backpackSlot < session.backpackInventory.getSize()
                    ? session.backpackInventory.getItem(backpackSlot)
                    : createUnavailableSlotPlaceholder();
            inv.setItem(playerSlot, cloneOrNull(item));
        }

        int maxRow = getMaxRow(session.backpackInventory.getSize());
        inv.setItem(SCROLL_UP_SLOT, createScrollButton("§e▲ 上へ", session.rowOffset > 0, GUI_TRIGGER_SCROLL_UP));
        inv.setItem(SCROLL_INFO_SLOT, createScrollInfoButton(session.rowOffset + 1, maxRow + 1));
        inv.setItem(SCROLL_DOWN_SLOT, createScrollButton("§e▼ 下へ", session.rowOffset < maxRow, GUI_TRIGGER_SCROLL_DOWN));
        renderToggleButton(player, session);
    }

    private void renderPlayerMode(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < OVERLAY_SIZE; i++) {
            inv.setItem(OVERLAY_START + i, cloneOrNull(session.playerRowsShadow[i]));
        }
        renderToggleButton(player, session);
    }

    private void syncBackpackFromDisplay(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        int startIndex = getBackpackWindowStartIndex(session);

        for (int i = 0; i < BACKPACK_DISPLAY_SLOTS.size(); i++) {
            int backpackSlot = startIndex + i;
            if (backpackSlot >= session.backpackInventory.getSize()) continue;
            int playerSlot = BACKPACK_DISPLAY_SLOTS.get(i);
            ItemStack current = inv.getItem(playerSlot);
            if (isUnavailableSlotPlaceholder(current)) {
                session.backpackInventory.setItem(backpackSlot, null);
            } else {
                session.backpackInventory.setItem(backpackSlot, cloneOrNull(current));
            }
        }
    }

    private void capturePlayerRows(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < OVERLAY_SIZE; i++) {
            session.playerRowsShadow[i] = cloneOrNull(inv.getItem(OVERLAY_START + i));
        }
    }

    private void toggleMode(Player player, OverlaySession session) {
        if (session.mode == ViewMode.BACKPACK) {
            switchMode(player, session, ViewMode.PLAYER);
            return;
        }
        switchMode(player, session, ViewMode.BACKPACK);
    }

    private void switchMode(Player player, OverlaySession session, ViewMode nextMode) {
        if (session.mode == nextMode) {
            if (nextMode == ViewMode.BACKPACK) {
                renderBackpackMode(player, session);
            } else {
                renderPlayerMode(player, session);
            }
            return;
        }

        if (session.mode == ViewMode.BACKPACK) {
            syncBackpackFromDisplay(player, session);
        } else {
            capturePlayerRows(player, session);
        }

        session.mode = nextMode;

        if (nextMode == ViewMode.BACKPACK) {
            renderBackpackMode(player, session);
        } else {
            renderPlayerMode(player, session);
        }
    }

    private void moveScroll(Player player, OverlaySession session, int deltaRow) {
        syncBackpackFromDisplay(player, session);
        int maxRow = getMaxRow(session.backpackInventory.getSize());
        session.rowOffset = Math.max(0, Math.min(maxRow, session.rowOffset + deltaRow));
        renderBackpackMode(player, session);
    }

    private int getBackpackWindowStartIndex(OverlaySession session) {
        return session.rowOffset * 9;
    }

    private int getMaxRow(int backpackSize) {
        int maxStart = Math.max(0, backpackSize - BACKPACK_DISPLAY_SLOTS.size());
        return (int) Math.ceil(maxStart / 9.0);
    }

    private void persistBackpack(Player player, OverlaySession session) {
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule == null) return;

        ItemStack target = findBackpackByUid(player, session.backpackUid, backpackModule);
        if (target == null) return;

        backpackModule.saveBackpackInventory(target, session.backpackInventory);
    }

    private ItemStack findBackpackByUid(Player player, String uid, BackpackModule backpackModule) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (matchesBackpackUid(offhand, uid, backpackModule)) return offhand;

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i <= 40; i++) {
            ItemStack item = inv.getItem(i);
            if (matchesBackpackUid(item, uid, backpackModule)) {
                return item;
            }
        }
        return null;
    }

    private boolean matchesBackpackUid(ItemStack item, String uid, BackpackModule backpackModule) {
        if (!backpackModule.isBackpackItem(item)) return false;
        return uid.equals(backpackModule.ensureBackpackUid(item));
    }

    private void setSystemSuppressed(Player player, boolean suppressed) {
        RaidMapManager mapManager = plugin.getRaidMapManager();
        if (mapManager != null) {
            mapManager.setLootSessionSuppressed(player.getUniqueId(), suppressed);
            if (!suppressed) {
                Bukkit.getScheduler().runTask(plugin, () -> mapManager.updateMapSlot(player));
            }
        }

        RigModule rigModule = plugin.getServiceContainer().get(RigModule.class);
        if (rigModule != null) {
            rigModule.setLootSessionSuppressed(player.getUniqueId(), suppressed);
            if (!suppressed) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    rigModule.enforceLockedSlots(player);
                    rigModule.syncLockedSlotPlaceholders(player);
                });
            }
        }
    }

    private void cleanupMisplacedButtons(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot <= 40; slot++) {
            if (slot == TOGGLE_SLOT) continue;
            if (session.mode == ViewMode.BACKPACK && SCROLL_BUTTON_SLOTS.contains(slot)) continue;
            ItemStack item = inv.getItem(slot);
            if (isOverlayControlItem(item)) {
                inv.setItem(slot, null);
            }
        }
    }

    private void renderToggleButton(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        inv.setItem(TOGGLE_SLOT, createToggleButton(session.mode));
    }

    private ItemStack createToggleButton(ViewMode mode) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(mode == ViewMode.BACKPACK ? "§a[ 表示: バックパック ]" : "§b[ 表示: 通常インベントリ ]");
            List<String> lore = new ArrayList<>();
            lore.add("§7クリックで表示を切り替え");
            lore.add(mode == ViewMode.BACKPACK ? "§f-> 通常インベントリへ" : "§f-> バックパックへ");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, GUI_TRIGGER_TOGGLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createScrollButton(String title, boolean enabled, int triggerId) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(enabled ? title : "§8" + title.replace("§e", ""));
            meta.setLore(Collections.singletonList(enabled ? "§7クリックで1行スクロール" : "§8これ以上スクロールできません"));
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, triggerId);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createScrollInfoButton(int currentPage, int maxPage) {
        ItemStack item = new ItemStack(Material.CYAN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b[ スクロール ]");
            meta.setLore(Collections.singletonList("§7行: " + currentPage + " / " + Math.max(1, maxPage)));
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, GUI_TRIGGER_SCROLL_INFO);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isOverlayControlItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer trigger = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER);
        return trigger != null && (trigger == GUI_TRIGGER_TOGGLE
                || trigger == GUI_TRIGGER_SCROLL_UP
                || trigger == GUI_TRIGGER_SCROLL_INFO
                || trigger == GUI_TRIGGER_SCROLL_DOWN
                || trigger == GUI_TRIGGER_UNAVAILABLE);
    }

    private boolean isUnavailableSlotPlaceholder(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Integer trigger = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER);
        return trigger != null && trigger == GUI_TRIGGER_UNAVAILABLE;
    }

    private ItemStack createUnavailableSlotPlaceholder() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8[ 使用不可 ]");
            meta.setLore(Collections.singletonList("§7バックパック容量外のスロットです"));
            meta.getPersistentDataContainer().set(PDCKeys.GUI_TRIGGER, PDCKeys.INTEGER, GUI_TRIGGER_UNAVAILABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<Integer> createBackpackDisplaySlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = OVERLAY_START; slot < OVERLAY_START + OVERLAY_SIZE; slot++) {
            if (SCROLL_BUTTON_SLOTS.contains(slot)) continue;
            slots.add(slot);
        }
        return slots;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private enum ViewMode {
        BACKPACK,
        PLAYER
    }

    private static class OverlaySession {
        final String backpackUid;
        final Inventory backpackInventory;
        final ItemStack[] playerRowsShadow = new ItemStack[OVERLAY_SIZE];
        ItemStack savedToggleSlotItem;
        int rowOffset = 0;
        ViewMode mode = ViewMode.BACKPACK;

        private OverlaySession(String backpackUid, Inventory backpackInventory) {
            this.backpackUid = backpackUid;
            this.backpackInventory = backpackInventory;
        }
    }
}
