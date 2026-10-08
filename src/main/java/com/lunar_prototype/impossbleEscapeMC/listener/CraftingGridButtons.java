package com.lunar_prototype.impossbleEscapeMC.listener;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientClickWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
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
    private static final int CURSOR_WINDOW_ID = -1;
    private static final int CURSOR_SLOT = -1;

    /**
     * @param slot    インベントリ画面 (window 0) のクラフトグリッドのスロット (1〜4)
     * @param visible 表示する条件 (メインスレッドで評価)
     * @param onClick クリック時の処理 (メインスレッドで実行)
     */
    public record Button(int slot, ItemStack icon, Predicate<Player> visible, Consumer<Player> onClick) {
    }

    private static CraftingGridButtons instance;

    private final ImpossbleEscapeMC plugin;
    private final Map<Integer, Button> buttons = new ConcurrentHashMap<>();
    /** パケット送信スレッドで使うため、アイコンは事前に変換しておく */
    private final Map<Integer, com.github.retrooper.packetevents.protocol.item.ItemStack> packetIcons = new ConcurrentHashMap<>();
    /** 各プレイヤーのクライアントに今ボタンを見せているスロット */
    private final Map<UUID, Set<Integer>> shown = new ConcurrentHashMap<>();

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
        if (desired.equals(current)) return;

        Set<Integer> changed = new HashSet<>(desired);
        changed.addAll(current);
        changed.removeIf(slot -> desired.contains(slot) && current.contains(slot));
        // 先に表示状態を更新してから送る (送るパケットは onPacketSend でボタンに書き換わる)
        shown.put(player.getUniqueId(), Set.copyOf(desired));
        for (int slot : changed) {
            sendSlot(player, menu.getStateId(), slot, CraftItemStack.asBukkitCopy(menu.getSlot(slot).getItem()));
        }
    }

    private void sendSlot(Player player, int stateId, int slot, ItemStack item) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerSetSlot(
                slot == CURSOR_SLOT ? CURSOR_WINDOW_ID : PLAYER_INVENTORY_WINDOW_ID,
                stateId,
                slot,
                SpigotConversionUtil.fromBukkitItemStack(item)));
    }

    // --- パケット ---

    /** 本人に送るインベントリのパケットに、見せているボタンを差し込む */
    @Override
    public void onPacketSend(PacketSendEvent event) {
        Set<Integer> slots = shownFor(event.getUser());
        if (slots.isEmpty()) return;

        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            WrapperPlayServerSetSlot packet = new WrapperPlayServerSetSlot(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID && slots.contains(packet.getSlot())) {
                packet.setItem(packetIcons.get(packet.getSlot()));
                event.markForReEncode(true);
            }
        } else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
            WrapperPlayServerWindowItems packet = new WrapperPlayServerWindowItems(event);
            if (packet.getWindowId() == PLAYER_INVENTORY_WINDOW_ID) {
                List<com.github.retrooper.packetevents.protocol.item.ItemStack> items = new ArrayList<>(packet.getItems());
                for (int slot : slots) {
                    if (slot < items.size()) items.set(slot, packetIcons.get(slot));
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
            sendSlot(player, stateId, CURSOR_SLOT, player.getItemOnCursor());
            button.onClick().accept(player);
        });
    }

    private Set<Integer> shownFor(User user) {
        if (user == null || user.getUUID() == null) return Set.of();
        return shown.getOrDefault(user.getUUID(), Set.of());
    }
}
