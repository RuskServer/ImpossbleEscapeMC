package com.lunar_prototype.impossbleEscapeMC.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

/**
 * カーソル (クリックで持ち上げたアイテム) を、別の画面を開く前に持ち物へ戻す。
 * <p>
 * Paper の {@code Player#openInventory} は、開いている自分のインベントリ画面を閉じずに別の画面へ差し替える
 * ({@code CraftEventFactory#callInventoryOpenEventWithTitle} が閉じるのはインベントリ画面以外の画面だけ)。
 * そのため、画面を閉じるときにカーソルのアイテムを持ち物へ戻す処理
 * ({@code AbstractContainerMenu#removed}) が実行されず、カーソルに載せたアイテムが持ち物へ戻らないまま
 * インベントリ画面に残ってしまう。
 * <p>
 * すると次の画面を開いている間はアイテムがどこにも見えず、画面の閉じた後にまたカーソルへ戻ってくる。
 * バニラでもコンテナを開くときはカーソルのアイテムは持ち物へ戻すので、ここでも同じ状態にしてから画面を開く。
 * <p>
 * {@link InventoryOpenEvent} は差し替え前 (自分のインベントリ画面のまま) に呼ばれるので、
 * この時点では {@code player.getItemOnCursor()} が通常のカーソルのアイテムを指す。
 */
public class CursorItemListener implements Listener {

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
