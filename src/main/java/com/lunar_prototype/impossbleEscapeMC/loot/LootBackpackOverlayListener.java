package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.gui.InventoryPanel;
import com.lunar_prototype.impossbleEscapeMC.gui.OpenTitle;
import com.lunar_prototype.impossbleEscapeMC.modules.inventory.CostSlotManager;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndEvent;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionEndReason;
import com.lunar_prototype.impossbleEscapeMC.loot.event.LootSessionStartEvent;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.function.Function;

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
        int rows = top.getSize() / 9;
        beginSession(event.getPlayer(), top, bag -> SearchGUI.title(rows, bag));
    }

    /**
     * バックパックのオーバーレイを始める (ルート漁り・スタッシュ)。オフハンドにバックパックが無ければ何もしない。
     * 画面を開いた後に呼ぶこと (カーソルに載っていたアイテムは InventoryOpenEvent で持ち物へ戻っている)。
     *
     * @param title その画面のタイトル。下の欄をバックパックの表示にした時は袋を渡す (null ならいつもの「インベントリ」)
     * @return 始めたら true
     */
    public boolean beginSession(Player player, Inventory hostInventory, Function<InventoryPanel.Bag, net.kyori.adventure.text.Component> title) {
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule == null || hostInventory == null) return false;

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (!backpackModule.isBackpackItem(offhand)) return false;

        endSession(player, LootSessionEndReason.OTHER);

        String backpackUid = backpackModule.ensureBackpackUid(offhand);
        OverlaySession session = new OverlaySession(backpackUid, backpackModule.loadBackpackInventory(offhand), hostInventory, title);
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

    /** 素材数の表示専用。本物の持ち物と、保存前の装備バッグを重複なく読む。 */
    public record MaterialView(ItemStack[] storage, Inventory backpack) {}

    public MaterialView materialView(Player player) {
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null) return null;
        if (session.mode == ViewMode.BACKPACK) syncBackpackFromDisplay(player,session);
        return new MaterialView(materialStorage(player.getInventory().getStorageContents(),session.playerRowsShadow,
                session.savedToggleSlotItem,session.mode == ViewMode.BACKPACK),session.backpackInventory);
    }

    static ItemStack[] materialStorage(ItemStack[] visible, ItemStack[] shadow, ItemStack toggle, boolean backpackMode) {
        ItemStack[] storage = com.lunar_prototype.impossbleEscapeMC.modules.inventory.PlayerStorageSnapshot.cloneItems(visible);
        if (backpackMode) for (int i = 0; i < OVERLAY_SIZE; i++) storage[OVERLAY_START+i]=cloneOrNull(shadow[i]);
        storage[TOGGLE_SLOT]=cloneOrNull(toggle);
        return storage;
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
        OverlaySession session = sessions.get(player.getUniqueId());
        if (session == null) return item;
        List<Integer> allowed = CostSlotManager.acquisitionSlots(player);
        int hiddenCost = 0;
        for (int slot : allowed) if (slot >= OVERLAY_START && slot < OVERLAY_START + OVERLAY_SIZE)
            hiddenCost += CostSlotManager.stackCost(session.playerRowsShadow[slot - OVERLAY_START]);
        List<Integer> hotbar = allowed.stream().filter(slot -> slot < TOGGLE_SLOT).toList();
        return CostSlotManager.insert(player.getInventory(), hotbar, allowed.size() - hiddenCost, item);
    }

    /** コストを守って入れる (同じ物のスタックに足す → 空きスロットに新しいスタック)。入りきらなかった分を返す */
    private static ItemStack insertWithCost(Inventory inv, ItemStack item) {
        List<Integer> slots = java.util.stream.IntStream.range(0, inv.getSize()).boxed().toList();
        return CostSlotManager.insert(inv, slots, inv.getSize(), item);
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
        refreshTitle(player, session);
    }

    private void renderPlayerMode(Player player, OverlaySession session) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < OVERLAY_SIZE; i++) {
            inv.setItem(OVERLAY_START + i, cloneOrNull(session.playerRowsShadow[i]));
        }
        renderToggleButton(player, session);
        refreshTitle(player, session);
    }

    /**
     * 下の欄の名前と背景 (タイトル) を今の表示に合わせる。替える時は画面のタイトルを送り直す (中身も送り直す) ので、
     * 表示・袋の名前・使っているマスが変わった時だけ。
     * <p>
     * 送り直すのは次の tick (クリックの処理が終わってから)。クリックの処理の途中 (漁る画面が取った物をバックパックに入れる時など)
     * に送ると、取る前の中身がクライアントに届き、その後の「空になった」の知らせが送られずに、取った物が残って見える
     */
    private void refreshTitle(Player player, OverlaySession session) {
        if (session.titlePending) return;
        session.titlePending = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            session.titlePending = false;
            if (sessions.get(player.getUniqueId()) == session && player.isOnline()) sendTitle(player, session);
        });
    }

    private void sendTitle(Player player, OverlaySession session) {
        if (!session.isHost(player.getOpenInventory().getTopInventory())) return;
        InventoryPanel.Bag bag = session.mode == ViewMode.BACKPACK ? bagOf(player, session) : null;
        String key = bag == null ? "player" : bag.toString();
        if (key.equals(session.titleKey)) return;
        session.titleKey = key;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof SearchGUI.SearchHolder) {
            SearchGUI.refreshTitle(player, bag);
        } else {
            OpenTitle.set(player, session.title.apply(bag));
        }
    }

    /** バックパックの表示に出す袋 (オフハンドのバックパックの名前、中身のコストの合計、容量) */
    private InventoryPanel.Bag bagOf(Player player, OverlaySession session) {
        int used = 0;
        for (ItemStack item : session.backpackInventory.getContents()) {
            if (!isForbiddenPlaceholder(item)) used += CostSlotManager.stackCost(item);
        }
        return new InventoryPanel.Bag(itemName(player.getInventory().getItemInOffHand()), used, session.backpackInventory.getSize());
    }

    /** 品物の名前 (アイテムの定義の名前。色の記号は外す) */
    private static String itemName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return "";
        String id = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
        ItemDefinition def = id != null ? ItemRegistry.get(id) : null;
        String name = def != null && def.displayName != null ? def.displayName
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.effectiveName());
        return name.replaceAll("§.", "");
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
        inv.setItem(TOGGLE_SLOT, createToggleButton(player, session.mode));
    }

    /**
     * 切り替えボタン。今の表示の入れ物の見た目にする: バックパックの表示ならそのバックパック、いつもの表示なら着ているリグ
     * (無ければコンパス)。見た目 (モデル) だけを写し、中身などのデータは持たせない
     */
    private ItemStack createToggleButton(Player player, ViewMode mode) {
        ItemStack look = null;
        if (mode == ViewMode.BACKPACK) {
            look = player.getInventory().getItemInOffHand();
        } else {
            RigModule rigModule = plugin.getServiceContainer().get(RigModule.class);
            if (rigModule != null) look = rigModule.getEquippedRig(player);
        }
        ItemStack item;
        if (look != null && !look.getType().isAir()) {
            item = new ItemStack(look.getType());
            item.copyDataFrom(look, type -> type == io.papermc.paper.datacomponent.DataComponentTypes.ITEM_MODEL
                    || type == io.papermc.paper.datacomponent.DataComponentTypes.CUSTOM_MODEL_DATA);
        } else {
            item = new ItemStack(Material.COMPASS);
        }
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
            String model = triggerId == GUI_TRIGGER_SCROLL_UP ? "bag_scroll_up" : "bag_scroll_down";
            meta.setItemModel(new NamespacedKey("iem", enabled ? model : model + "_off"));
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
            meta.setItemModel(new NamespacedKey("iem", "bag_scroll_page"));
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
                || com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory.isCostSlotPlaceholder(item);
    }

    private static List<Integer> createBackpackDisplaySlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = OVERLAY_START; slot < OVERLAY_START + OVERLAY_SIZE; slot++) {
            if (SCROLL_BUTTON_SLOTS.contains(slot)) continue;
            slots.add(slot);
        }
        return slots;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
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
        /** 画面のタイトル (袋を渡すと下の欄をバックパックの表示にしたもの) と、最後に送ったタイトルの中身 */
        final Function<InventoryPanel.Bag, net.kyori.adventure.text.Component> title;
        String titleKey;
        /** 次の tick にタイトルを送る予定があるか */
        boolean titlePending;
        final ItemStack[] playerRowsShadow = new ItemStack[OVERLAY_SIZE];
        ItemStack savedToggleSlotItem;
        int rowOffset = 0;
        ViewMode mode = ViewMode.BACKPACK;

        private OverlaySession(String backpackUid, Inventory backpackInventory, Inventory hostInventory,
                               Function<InventoryPanel.Bag, net.kyori.adventure.text.Component> title) {
            this.backpackUid = backpackUid;
            this.backpackInventory = backpackInventory;
            this.hostInventory = hostInventory;
            this.title = title;
        }

        /** オーバーレイの対象になる画面 (スタッシュ・漁り) か */
        private boolean isHost(Inventory inventory) {
            return hostInventory != null && hostInventory.equals(inventory);
        }
    }
}
