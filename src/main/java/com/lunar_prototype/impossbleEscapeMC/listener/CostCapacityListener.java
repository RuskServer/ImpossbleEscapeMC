package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 持ち物の大きさ (コスト) の合計が使えるスロットの数を超える操作を、入る前に止める。
 * 占有スロット (バリア) は空きスロットにしか置けないため、止めないと大きな物でインベントリを埋めて制限を素通りできてしまう。
 * <p>
 * 1回の操作で各インベントリのスロットがどう変わるかを先に求めて判定する。すでに超えている時 (ロビーで整理中など) は、
 * 使用量を増やす操作だけを止め、減らす・並べ替える操作はできるようにする。
 * 止められない経路であふれた分は {@link CostSlotManager} が片付ける。
 */
public class CostCapacityListener implements Listener {

    /** プレイヤーのインベントリでのオフハンドのスロット (オフハンドとの入れ替えでホットバーの番号の代わりに使う) */
    private static final int OFFHAND_SLOT = 40;

    /** 容量を超えるインベントリと、操作した後の使用量 */
    private record Overflow(int used, int capacity) {
    }

    /** 1回の操作で各インベントリがどう変わるか */
    private static final class Changes {
        private final Player player;
        private final Map<Inventory, Map<Integer, ItemStack>> slots = new IdentityHashMap<>();
        private final Map<Inventory, Integer> added = new IdentityHashMap<>();

        private Changes(Player player) {
            this.player = player;
        }

        /** スロットの中身が item になる */
        void set(Inventory inventory, int slot, ItemStack item) {
            slots.computeIfAbsent(inventory, key -> new HashMap<>()).put(slot, item);
        }

        /** 空いている所に入る (同じ物のスタックに全部入りきるなら新しいスロットを使わない) */
        void add(Inventory inventory, ItemStack item) {
            if (CostSlotManager.fitsIntoExistingStack(player, inventory, item)) return;
            added.merge(inventory, CostSlotManager.stackCost(item), Integer::sum);
        }

        /** 操作した後に容量を超え、しかも使用量が増えるインベントリがあれば、その使用量と容量 */
        Overflow overflow() {
            Set<Inventory> touched = Collections.newSetFromMap(new IdentityHashMap<>());
            touched.addAll(slots.keySet());
            touched.addAll(added.keySet());
            for (Inventory inventory : touched) {
                int capacity = CostSlotManager.capacity(player, inventory);
                if (capacity == 0) continue;
                int before = CostSlotManager.usedCost(player, inventory);
                int after = before + added.getOrDefault(inventory, 0);
                for (Map.Entry<Integer, ItemStack> change : slots.getOrDefault(inventory, Map.of()).entrySet()) {
                    if (!CostSlotManager.isStorageSlot(player, inventory, change.getKey())) continue;
                    after += CostSlotManager.stackCost(change.getValue()) - CostSlotManager.stackCost(inventory.getItem(change.getKey()));
                }
                if (after > capacity && after > before) return new Overflow(after, capacity);
            }
            return null;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isLimited(player)) return;
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) return;
        ItemStack current = event.getCurrentItem();
        Changes changes = new Changes(player);
        switch (event.getAction()) {
            // 空いたスロットに置く (同じ物のスタックに足すだけなら新しいスロットを使わない)
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> {
                if (isEmpty(current)) changes.set(clicked, event.getSlot(), event.getCursor());
            }
            case SWAP_WITH_CURSOR -> changes.set(clicked, event.getSlot(), event.getCursor());
            // 数字キー・オフハンドキーで、クリックしたスロットとホットバー (オフハンド) を入れ替える
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int hotbarSlot = event.getHotbarButton() >= 0 ? event.getHotbarButton() : OFFHAND_SLOT;
                changes.set(clicked, event.getSlot(), player.getInventory().getItem(hotbarSlot));
                changes.set(player.getInventory(), hotbarSlot, current);
            }
            // シフトクリック。自分のインベントリだけの画面では、ホットバーとメインの間など自分の中で動く
            case MOVE_TO_OTHER_INVENTORY -> {
                InventoryView view = event.getView();
                boolean ownInventoryOnly = view.getType() == InventoryType.CRAFTING;
                Inventory destination = clicked == view.getBottomInventory() && !ownInventoryOnly
                        ? view.getTopInventory() : view.getBottomInventory();
                changes.set(clicked, event.getSlot(), null);
                changes.add(destination, current);
            }
            default -> {
                return;
            }
        }
        reject(changes.overflow(), player, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isLimited(player)) return;
        InventoryView view = event.getView();
        Changes changes = new Changes(player);
        for (Map.Entry<Integer, ItemStack> placed : event.getNewItems().entrySet()) {
            int rawSlot = placed.getKey();
            // 同じ物のスタックに足すだけなら新しいスロットを使わない
            if (!isEmpty(view.getItem(rawSlot))) continue;
            Inventory inventory = view.getInventory(rawSlot);
            if (inventory != null) changes.set(inventory, view.convertSlot(rawSlot), placed.getValue());
        }
        reject(changes.overflow(), player, () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isLimited(player)) return;
        Changes changes = new Changes(player);
        changes.add(player.getInventory(), event.getItem().getItemStack());
        reject(changes.overflow(), player, () -> event.setCancelled(true));
    }

    private static void reject(Overflow overflow, Player player, Runnable cancel) {
        if (overflow == null) return;
        cancel.run();
        player.sendActionBar(Component.text("容量が足りません (入れると " + overflow.used() + " / " + overflow.capacity() + ")",
                NamedTextColor.RED));
    }

    /** 制限がかかるプレイヤーか (クリエイティブ・観戦と、SCAVのデータパック銃用FakePlayerは除く) */
    private static boolean isLimited(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !DatapackGunnerManager.isGunner(player);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
