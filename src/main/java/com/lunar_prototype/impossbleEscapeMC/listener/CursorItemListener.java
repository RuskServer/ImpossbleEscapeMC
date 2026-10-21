package com.lunar_prototype.impossbleEscapeMC.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * カーソル (クリックで持ち上げたアイテム) を、サーバーとクライアントで食い違わせない。
 * <p>
 * 食い違うと、クライアントには手の銃が無いように見える (サーバーには有る) ことがある。データパック銃の ADS は左クリックの突き攻撃
 * (piercing_weapon、エンチャントの post_piercing_attack) で切り替えるが、突き攻撃を送るかはクライアントが自分の手の物で決めるので、
 * そうなると左クリックしても ADS できない。そこで次の 3 つで防ぐ。
 * <ol>
 *   <li>画面を開く前: カーソルのアイテムを持ち物へ戻す。
 *       Paper の {@code Player#openInventory} は、開いている自分のインベントリ画面を閉じずに別の画面へ差し替える
 *       ({@code CraftEventFactory#callInventoryOpenEventWithTitle} が閉じるのはインベントリ画面以外の画面だけ)。
 *       そのため、画面を閉じるときにカーソルのアイテムを持ち物へ戻す処理 ({@code AbstractContainerMenu#removed}) が実行されず、
 *       カーソルに載せたアイテムが持ち物へ戻らないままインベントリ画面に残る (次の画面を開いている間はどこにも見えず、閉じた後にまたカーソルへ戻ってくる)。
 *       バニラでもコンテナを開くときはカーソルのアイテムは持ち物へ戻すので、ここでも同じ状態にしてから画面を開く。
 *       {@link InventoryOpenEvent} は差し替え前 (自分のインベントリ画面のまま) に呼ばれるので、この時点では {@code player.getItemOnCursor()} が
 *       通常のカーソルのアイテムを指す</li>
 *   <li>画面を閉じた後・クリックが取り消された後: 次の tick に、持ち物とカーソルをクライアントへ送り直す ({@code updateInventory})。
 *       閉じた後は、画面が無いのにカーソルに残っている物を持ち物へ戻してから送る</li>
 *   <li>画面を開いていない時の操作 (視点を動かす・持つ欄を変える・クリックする・腕を振る): 画面が開いていればできない操作なので、
 *       この時にカーソルにアイテムがあれば、閉じ損ねて残った物として持ち物へ戻し、送り直す</li>
 * </ol>
 */
public class CursorItemListener implements Listener {

    private final JavaPlugin plugin;
    /** 次の tick に送り直すプレイヤー (同じ tick に何度呼ばれても 1 回にする) */
    private final Set<UUID> pending = new HashSet<>();

    public CursorItemListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 画面を開くときにカーソルのアイテムを持ち物へ戻す。
     * 開けないことに決まった場合 (イベントがキャンセルされたとき) は持ち物へ戻さない。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        returnToInventory(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) resyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.isCancelled() && event.getWhoClicked() instanceof Player player) resyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent event) {
        if (event.isCancelled() && event.getWhoClicked() instanceof Player player) resyncNextTick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (from.getYaw() == to.getYaw() && from.getPitch() == to.getPitch()) return;
        fixStaleCursor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHeld(PlayerItemHeldEvent event) {
        fixStaleCursor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;
        fixStaleCursor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwing(PlayerAnimationEvent event) {
        fixStaleCursor(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    /** 画面を開いていない (自分のインベントリ画面の扱い) のにカーソルにアイテムがあれば、持ち物へ戻して送り直す */
    private void fixStaleCursor(Player player) {
        if (!hasCursorItem(player) || player.getOpenInventory().getType() != InventoryType.CRAFTING) return;
        returnToInventory(player);
        player.updateInventory();
    }

    /** 次の tick に、画面が無いのにカーソルに残っている物を持ち物へ戻し、持ち物とカーソルを送り直す */
    private void resyncNextTick(Player player) {
        if (!pending.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (player.getOpenInventory().getType() == InventoryType.CRAFTING) returnToInventory(player);
            player.updateInventory();
        });
    }

    private static boolean hasCursorItem(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        return cursor != null && !cursor.getType().isAir();
    }

    /**
     * カーソルに載せているアイテムを持ち物へ戻す。持ち物に入らない分は足元に落とす
     * (バニラが画面を閉じるときに使うのと同じ動き)。
     */
    public static void returnToInventory(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) return;
        ItemStack item = cursor.clone();
        // 先にカーソルを空にしてから持ち物へ入れる (クライアントにも空になったことを知らせる)
        player.setItemOnCursor(null);
        for (ItemStack rest : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }
}
