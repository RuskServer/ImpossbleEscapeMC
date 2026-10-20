package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager;
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
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (!(top.getHolder() instanceof SearchGUI.SearchHolder)) return;
        beginSession(event.getPlayer(), top);
    }

    /**
     * バックパックのオーバーレイを始める (ルート漁り・スタッシュ)。オフハンドにバックパックが無ければ何もしない。
     * 画面を開いた後に呼ぶこと (カーソルに載っていたアイテムは InventoryOpenEvent で持ち物へ戻っている)。
     *
     * @return 始めたら true
     */
    public boolean beginSession(Player player, Inventory hostInventory) {
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule == null || hostInventory == null) return false;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!backpackModule.isBackpackItem(offhand)) return false;

        endSession(player, LootSessionEndReason.OTHER);

        String backpackUid = backpackModule.ensureBackpackUid(offhand);
        OverlaySession session = new OverlaySession(backpackUid, backpackModule.loadBackpackInventory(offhand), hostInventory);
        snapshotInitialState(player, session);
        sessions.put(player.getUniqueId(), session);

        setSystemSuppressed(player, true);
        switchMode(player, session, ViewMode.BACKPACK);
        return true;
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
        if (!session.isHost(event.getView().getTopInventory())) return;

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
        if (!session.isHost(event.getView().getTopInventory())) return;

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

        // 漁り (SearchGUI) は自分でも LootSessionEndEvent を出すが、終了済みなら no-op。
        // スタッシュなどほかのホストはここで終える。
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

    /**
     * 漁っている途中で死んだら、死体を作る前 (LOWEST) に表示を戻す。戻す前に死体を作ると、持ち物の欄に出していた
     * バックパックの中身が死体に入り (バックパックの中にも残るので増える)、隠していた本来の持ち物は死体に入らない
     */
    @EventHandler(priority = EventPriority.LOWEST)
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

    /** バックパックの表示中か (持ち物の欄がバックパックの中身の表示になっている) */
    public boolean isBackpackView(Player player) {
        OverlaySession session = sessions.get(player.getUniqueId());
        return session != null && session.mode == ViewMode.BACKPACK;
    }

    /**
     * 漁って取った物を、装備しているバックパックに先に入れる。入りきらなかった分を返す (全部入れば null)。
     * バックパックのコスト (大きい物は複数スロット分) を守り、同じ物のスタックに足してから空きスロットに入れる。
     * バックパックはバックパックに入れない。漁っていない (セッションが無い) 時は何もしない
     */
    public ItemStack addToBackpackFirst(Player player, ItemStack item) {
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null || item == null || item.getType().isAir()) return item;
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule != null && backpackModule.isBackpackItem(item)) return item;
        if (session.mode == ViewMode.BACKPACK) syncBackpackFromDisplay(player, session);
        ItemStack rest = insertWithCost(session.backpackInventory, item);
        if (session.mode == ViewMode.BACKPACK) renderBackpackMode(player, session);
        return rest;
    }

    /** バックパックの表示中に入りきらなかった物を、本物の持ち物であるホットバー (1〜8) に入れる。入りきらなかった分を返す */
    public ItemStack addToHotbar(Player player, ItemStack item) {
        if (item == null) return null;
        PlayerInventory inv = player.getInventory();
        ItemStack rest = item.clone();
        for (int slot = 0; slot < TOGGLE_SLOT && rest.getAmount() > 0; slot++) {
            ItemStack current = inv.getItem(slot);
            if (current == null || !current.isSimilar(rest)) continue;
            int move = Math.min(current.getMaxStackSize() - current.getAmount(), rest.getAmount());
            if (move <= 0) continue;
            current.setAmount(current.getAmount() + move);
            inv.setItem(slot, current);
            rest.setAmount(rest.getAmount() - move);
        }
        for (int slot = 0; slot < TOGGLE_SLOT && rest.getAmount() > 0; slot++) {
            ItemStack current = inv.getItem(slot);
            if (current != null && !current.getType().isAir()) continue;
            ItemStack stack = rest.clone();
            stack.setAmount(Math.min(rest.getAmount(), rest.getMaxStackSize()));
            inv.setItem(slot, stack);
            rest.setAmount(rest.getAmount() - stack.getAmount());
        }
        return rest.getAmount() > 0 ? rest : null;
    }

    /** コストを守って入れる (同じ物のスタックに足す → 空きスロットに新しいスタック)。入りきらなかった分を返す */
    private static ItemStack insertWithCost(Inventory inv, ItemStack item) {
        ItemStack rest = item.clone();
        for (int slot = 0; slot < inv.getSize() && rest.getAmount() > 0; slot++) {
            ItemStack current = inv.getItem(slot);
            if (current == null || !current.isSimilar(rest)) continue;
            int move = Math.min(current.getMaxStackSize() - current.getAmount(), rest.getAmount());
            if (move <= 0) continue;
            current.setAmount(current.getAmount() + move);
            inv.setItem(slot, current);
            rest.setAmount(rest.getAmount() - move);
        }
        while (rest.getAmount() > 0) {
            int used = 0;
            int empty = -1;
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack current = inv.getItem(slot);
                used += CostSlotManager.stackCost(current);
                if (empty < 0 && (current == null || current.getType().isAir())) empty = slot;
            }
            if (empty < 0 || used + CostSlotManager.stackCost(rest) > inv.getSize()) break;
            ItemStack stack = rest.clone();
            stack.setAmount(Math.min(rest.getAmount(), rest.getMaxStackSize()));
            inv.setItem(empty, stack);
            rest.setAmount(rest.getAmount() - stack.getAmount());
        }
        return rest.getAmount() > 0 ? rest : null;
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
        if (!session.isHost(player.getOpenInventory().getTopInventory())) return;

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
        // バックパックのコスト (大きい物は複数マス) の占有マスを、実体を汚さずに表示だけする
        Map<Integer, ItemStack> placeholders = CostSlotManager.costPlaceholders(session.backpackInventory);

        for (int i = 0; i < BACKPACK_DISPLAY_SLOTS.size(); i++) {
            int playerSlot = BACKPACK_DISPLAY_SLOTS.get(i);
            int backpackSlot = startIndex + i;
            ItemStack item;
            if (backpackSlot < session.backpackInventory.getSize()) {
                ItemStack raw = session.backpackInventory.getItem(backpackSlot);
                if (isForbiddenPlaceholder(raw)) {
                    item = null;
                } else if (raw == null || raw.getType().isAir()) {
                    item = placeholders.get(backpackSlot);
                } else {
                    item = raw;
                }
            } else {
                item = createUnavailableSlotPlaceholder();
            }
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
            if (isUnavailableSlotPlaceholder(current) || isForbiddenPlaceholder(current)) {
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
                    // 次のtickまでに別の漁るセッションが始まっていたら、持ち物の欄はバックパックの表示なので触らない
                    // (触ると、塞がれた欄に並べたバックパックの中身を全部足元に落とす)
                    if (rigModule.isControlSuppressed(player)) return;
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

    private boolean isForbiddenPlaceholder(ItemStack item) {
        return com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule.isLockedSlotPlaceholder(item)
                || com.lunar_prototype.impossbleEscapeMC.item.ItemFactory.isCostSlotPlaceholder(item);
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
        final Inventory hostInventory;
        final ItemStack[] playerRowsShadow = new ItemStack[OVERLAY_SIZE];
        ItemStack savedToggleSlotItem;
        int rowOffset = 0;
        ViewMode mode = ViewMode.BACKPACK;

        private OverlaySession(String backpackUid, Inventory backpackInventory, Inventory hostInventory) {
            this.backpackUid = backpackUid;
            this.backpackInventory = backpackInventory;
            this.hostInventory = hostInventory;
        }

        /** オーバーレイの対象になる画面 (スタッシュ・漁り) か */
        private boolean isHost(Inventory inventory) {
            return hostInventory != null && hostInventory.equals(inventory);
        }
    }
}
