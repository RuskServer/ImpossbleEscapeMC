package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.minecraft.world.inventory.InventoryMenu;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * プレイヤーインベントリのクラフトグリッド (2x2) に置くボタン。
 *
 * ボタンはサーバー上のクラフトグリッドには置かず、本人に送るインベントリのパケットにだけ差し込む。
 * サーバー上のグリッドは空のままなので、インベントリを閉じた時・死亡時などにバニラがグリッドの中身を
 * 返却・ドロップしても、ボタンが落ちることはない。クリックはパケットの段階で受け取って処理する。
 */
public final class CraftingGridButtons extends PacketListenerAbstract {

    /** プレイヤー自身のインベントリ画面 */
    private static final int PLAYER_INVENTORY_WINDOW_ID = 0;
    private static final int CRAFTING_RESULT_SLOT = 0;
    /**
     * @param slot    インベントリ画面 (window 0) のクラフトグリッドのスロット (1〜4)
     * @param visible 表示する条件 (メインスレッドで評価)
     * @param onClick クリック時の処理 (メインスレッドで実行)
     * @param dynamicIcon プレイヤーごとに変わるアイコン (メインスレッドで {@code refresh} のたびに作る)。null なら icon を使う
     */
    public record Button(int slot, ItemStack icon, Predicate<Player> visible, Consumer<Player> onClick,
                         Function<Player, ItemStack> dynamicIcon) {
        public Button(int slot, ItemStack icon, Predicate<Player> visible, Consumer<Player> onClick) {
            this(slot, icon, visible, onClick, null);
        }
    }

    private static CraftingGridButtons instance;

    private final ImpossbleEscapeMC plugin;
    private final Map<Integer, Button> buttons = new ConcurrentHashMap<>();
    /** パケット送信スレッドで使うため、アイコンは事前に変換しておく */
    private final Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> packetIcons = new ConcurrentHashMap<>();
    /** 各プレイヤーのクライアントに今ボタンを見せているスロット */
    private final Map<UUID, Set<Integer>> shown = new ConcurrentHashMap<>();
    /** プレイヤーごとのアイコン (dynamicIcon のボタン)。比べる用の元のアイテムと、パケット用に変換した物 */
    private final Map<UUID, Map<Integer, ItemStack>> playerIcons = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> playerPacketIcons = new ConcurrentHashMap<>();
    /** ロビーの容量表示。サーバー側スロットには置かないため、操作を塞がない */
    private final Map<UUID, Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack>> costSlotOverlays = new ConcurrentHashMap<>();

    private CraftingGridButtons(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 0L, 5L);
    }

    public static synchronized CraftingGridButtons get() {
        if (instance == null) {
            instance = new CraftingGridButtons(ImpossbleEscapeMC.getInstance());
        }
        return instance;
    }

    public void register(Button button) {
        buttons.put(button.slot(), button);
        packetIcons.put(button.slot(), SpigotConversionUtil.fromBukkitItemStack(button.icon()));
    }

    /** ボタンを置くスロットか (クリック処理側で、グリッドへのアイテム配置を禁止するのに使う) */
    public boolean isButtonSlot(int slot) {
        return buttons.containsKey(slot);
    }

    // --- 表示の更新 (メインスレッド) ---

    private void refreshAll() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (DatapackGunnerManager.isGunner(player)) continue;
            online.add(player.getUniqueId());
            refresh(player);
        }
        shown.keySet().retainAll(online);
        playerIcons.keySet().retainAll(online);
        playerPacketIcons.keySet().retainAll(online);
        costSlotOverlays.keySet().retainAll(online);
    }

    /** 次の更新を待たずに、そのプレイヤーのボタンを作り直して送る */
    public void refreshNow(Player player) {
        if (player.isOnline() && !DatapackGunnerManager.isGunner(player)) refresh(player);
    }

    private void refresh(Player player) {
        InventoryMenu menu = ((CraftPlayer) player).getHandle().inventoryMenu;
        Set<Integer> desired = new HashSet<>();
        for (Button button : buttons.values()) {
            // サーバー上でそのスロットに実物がある場合は隠さない
            if (button.visible().test(player) && menu.getSlot(button.slot()).getItem().isEmpty()) {
                desired.add(button.slot());
            }
        }
        Set<Integer> current = shown.getOrDefault(player.getUniqueId(), Set.of());
        Set<Integer> iconChanged = updateIcons(player, desired);
        refreshCostSlotOverlays(player, menu);
        if (desired.equals(current) && iconChanged.isEmpty()) return;

        Set<Integer> changed = new HashSet<>(desired);
        changed.addAll(current);
        changed.removeIf(slot -> desired.contains(slot) && current.contains(slot) && !iconChanged.contains(slot));
        // 先に表示状態を更新してから送る (送るパケットは onPacketSend でボタンに書き換わる)
        shown.put(player.getUniqueId(), Set.copyOf(desired));
        for (int slot : changed) {
            sendSlot(player, menu.getStateId(), slot, CraftItemStack.asBukkitCopy(menu.getSlot(slot).getItem()));
        }

    }

    private void refreshCostSlotOverlays(Player player, InventoryMenu menu) {
        Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> next = new ConcurrentHashMap<>();
        // ほかの画面 (スタッシュ・漁る画面など) を開いている間は出さない。下の欄は同じ持ち物を見せているので、偽の占有マスが
        // そこに見えてしまい (バックパックの表示中は中身の上に)、クライアントとサーバーの中身が食い違う。閉じたら次の更新で戻る
        boolean ownInventory = player.getOpenInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING;
        if (ownInventory) CostSlotManager.lobbyCostPlaceholders(player).forEach((inventorySlot, item) -> {
            int menuSlot = inventorySlot >= 0 && inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
            next.put(menuSlot, SpigotConversionUtil.fromBukkitItemStack(item));
        });
        UUID id = player.getUniqueId();
        Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> previous = costSlotOverlays.getOrDefault(id, Map.of());
        Set<Integer> changed = new HashSet<>(previous.keySet());
        changed.addAll(next.keySet());
        changed.removeIf(slot -> java.util.Objects.equals(previous.get(slot), next.get(slot)));
        if (changed.isEmpty()) return;
        costSlotOverlays.put(id, Map.copyOf(next));
        for (int slot : changed) {
            sendSlot(player, menu.getStateId(), slot, CraftItemStack.asBukkitCopy(menu.getSlot(slot).getItem()));
        }
    }

    /** 見せるボタンのうち dynamicIcon の物のアイコンを作り直す。変わったスロットを返す */
    private Set<Integer> updateIcons(Player player, Set<Integer> desired) {
        Set<Integer> changed = new HashSet<>();
        Map<Integer, ItemStack> icons = playerIcons.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> packets =
                playerPacketIcons.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        for (int slot : desired) {
            Button button = buttons.get(slot);
            if (button.dynamicIcon() == null) continue;
            ItemStack icon = button.dynamicIcon().apply(player);
            if (icon.equals(icons.get(slot))) continue;
            icons.put(slot, icon);
            packets.put(slot, SpigotConversionUtil.fromBukkitItemStack(icon));
            changed.add(slot);
        }
        return changed;
    }

    /** そのプレイヤーに見せるアイコン (パケット用) */
    private com.github.retrooper.packetevents.protocol.item.ItemStack iconFor(User user, int slot) {
        Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> own = playerPacketIcons.get(user.getUUID());
        var icon = own != null ? own.get(slot) : null;
        return icon != null ? icon : packetIcons.get(slot);
    }

    private void sendSlot(Player player, int stateId, int slot, ItemStack item) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetSlot(
                PLAYER_INVENTORY_WINDOW_ID,
                stateId,
                slot,
                SpigotConversionUtil.fromBukkitItemStack(item)));
    }

    /**
     * カーソルアイテムをクライアントに送る。
     * 旧プロトコルの SET_SLOT (windowId=-1) はこのバージョンのクライアントでは処理されないため、
     * SET_CURSOR_ITEM パケットを使う。
     */
    private void sendCursor(Player player, ItemStack item) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerSetCursorItem(SpigotConversionUtil.fromBukkitItemStack(item)));
    }

    // --- パケット ---

    /** 本人に送るインベントリのパケットに、見せているボタンを差し込む */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = event.getUser();
        if (user == null || user.getUUID() == null) return;
        Set<Integer> slots = shownFor(user);
        // ボタンが無くても、ロビーの占有マスは差し込む (ボタンが無い時に送り直された持ち物で、占有マスが消えていた)
        if (slots.isEmpty() && costSlotOverlays.getOrDefault(user.getUUID(), Map.of()).isEmpty()) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID) {
                if (slots.contains(packet.getSlot())) {
                    packet.setItem(iconFor(event.getUser(), packet.getSlot()));
                    event.markForReEncode(true);
                } else {
                    var overlays = costSlotOverlays.get(event.getUser().getUUID());
                    var overlay = overlays != null ? overlays.get(packet.getSlot()) : null;
                    if (overlay != null) {
                        packet.setItem(overlay);
                        event.markForReEncode(true);
                    }
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID) {
                List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(packet.getItems());
                for (int slot : slots) {
                    if (slot < items.size()) items.set(slot, iconFor(event.getUser(), slot));
                }
                Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> overlays = costSlotOverlays.get(event.getUser().getUUID());
                if (overlays != null) {
                    overlays.forEach((slot, icon) -> {
                        if (slot < items.size() && !slots.contains(slot)) items.set(slot, icon);
                    });
                }
                packet.setItems(items);
                event.markForReEncode(true);
            }
        }
    }

    /** 見せているボタンへのクリックはサーバーに渡さず、ボタンの処理を実行する */
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLICK_WINDOW) return;
        WrapperPlayClientClickWindow packet = new WrapperPlayClientClickWindow(event);
        if (packet.getWindowId() != PLAYER_INVENTORY_WINDOW_ID) return;
        int slot = packet.getSlot();
        if (!shownFor(event.getUser()).contains(slot)) return;

        event.setCancelled(true);
        Button button = buttons.get(slot);
        Player player = event.getPlayer();
        if (button == null || player == null) return;
        int stateId = packet.getStateId().orElse(0);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            // クライアントはボタンを掴んだと予測しているので、ボタン・クラフト結果・カーソルを元に戻す
            InventoryMenu menu = ((CraftPlayer) player).getHandle().inventoryMenu;
            sendSlot(player, stateId, slot, CraftItemStack.asBukkitCopy(menu.getSlot(slot).getItem()));
            sendSlot(player, stateId, CRAFTING_RESULT_SLOT, CraftItemStack.asBukkitCopy(menu.getSlot(CRAFTING_RESULT_SLOT).getItem()));
            sendCursor(player, player.getItemOnCursor());
            button.onClick().accept(player);
        });
    }

    private Set<Integer> shownFor(User user) {
        if (user == null || user.getUUID() == null) return Set.of();
        return shown.getOrDefault(user.getUUID(), Set.of());
    }
}
