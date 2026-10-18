package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.item.CapacityGauge;
import com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
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
import io.papermc.paper.event.player.PlayerSwapWithEquipmentSlotEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * レイド中は、持ち物の大きさ (コスト) の合計が使えるスロットの数を超える操作を、入る前に止める。
 * 占有スロット (バリア) は空きスロットにしか置けないため、止めないと大きな物でインベントリを埋めて制限を素通りできてしまう。
 * <p>
 * ロビーのプレイヤーインベントリではコスト超過を許可し、出撃開始時に容量内かを確認する。
 * バックパック内部の容量と、リグ交換で実物がロックされる操作はロビーでも制限する。
 * リグの装備欄を外す・小さいリグに替える操作も、レイド中に持ち物が入りきらなくなるなら止める (落とさずに済むように)。
 * 止めた時は音を鳴らし、容量のゲージ ({@link CapacityGauge}) を赤く光らせる。
 * 止められない経路であふれた分は {@link CostSlotManager} が片付ける。
 */
public class CostCapacityListener implements Listener {

    /** プレイヤーのインベントリでのオフハンドのスロット (オフハンドとの入れ替えでホットバーの番号の代わりに使う) */
    private static final int OFFHAND_SLOT = 40;
    /** プレイヤーのインベントリでのズボン (リグ) のスロット */
    private static final int LEGGINGS_SLOT = 37;
    /** 装甲リグを着ける胸のスロット */
    private static final int CHEST_SLOT = 38;

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
                // ロビーのプレイヤーインベントリは装備準備中の超過を許可する。バックパック内部の容量は維持。
                if (inventory.getHolder() instanceof Player && !isRaidCapacityEnforced(player)) continue;
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
        if (clicked.equals(player.getInventory())
                && (event.getSlot() == LEGGINGS_SLOT || event.getSlot() == CHEST_SLOT)) {
            RigModule rigModule = rigModule();
            if (rigModule != null && rigModule.isRigItem(current)
                    && locksOccupiedSlots(player, rigModule, nextRigForClick(player, event))) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("このリグを替える前に、閉じるスロットの荷物を移してください", NamedTextColor.RED));
                CapacityGauge.flash(player);
                return;
            }
            Overflow rig = isRaidCapacityEnforced(player) ? rigChange(player, event, current) : null;
            if (rig != null) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("リグを替えると持ち物が入りきりません (" + rig.used() + " / " + rig.capacity() + " マス)",
                        NamedTextColor.RED));
                CapacityGauge.flash(player);
                return;
            }
        }
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

    /** 装備交換イベントで、交換後のコストとリグ容量を事前判定する。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRigHotSwap(PlayerSwapWithEquipmentSlotEvent event) {
        Player player = event.getPlayer();
        if (!isLimited(player)) return;
        RigModule rigModule = rigModule();
        ItemStack hand = event.getItemInHand();
        ItemStack worn = rigModule == null ? null : rigModule.getEquippedRig(player);
        if (rigModule == null || !rigModule.isRigItem(hand) || isEmpty(worn)) return;
        if (locksOccupiedSlots(player, rigModule, hand)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("このリグに替える前に、閉じるスロットの荷物を移してください", NamedTextColor.RED));
            CapacityGauge.flash(player);
            return;
        }
        if (!isRaidCapacityEnforced(player)) return;
        int handSlot = getHandSlot(player, hand);
        Overflow overflow = afterRigChange(player, rigModule, worn, hand, swapDelta(player, handSlot, worn, hand));
        if (overflow == null) return;
        event.setCancelled(true);
        player.sendActionBar(Component.text("このリグに替えると持ち物が入りきりません (" + overflow.used() + " / " + overflow.capacity() + " マス)",
                NamedTextColor.RED));
        CapacityGauge.flash(player);
    }

    private static int getHandSlot(Player player, ItemStack eventHand) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!isEmpty(mainHand) && mainHand.isSimilar(eventHand)) return player.getInventory().getHeldItemSlot();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return !isEmpty(offHand) && offHand.isSimilar(eventHand) ? OFFHAND_SLOT : player.getInventory().getHeldItemSlot();
    }

    /**
     * リグの装備欄をクリックした結果、リグが外れる・替わって容量が足りなくなるなら、その時の使用量と容量 (足りれば null)。
     * 外したリグはカーソル・ホットバー・持ち物のどれかに行く
     */
    private static Overflow rigChange(Player player, InventoryClickEvent event, ItemStack worn) {
        RigModule rigModule = rigModule();
        if (rigModule == null) return null;
        return switch (event.getAction()) {
            // カーソルに取る・捨てる
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, DROP_ALL_SLOT, DROP_ONE_SLOT ->
                    afterRigChange(player, rigModule, worn, null, 0);
            // カーソルの物と替える
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR ->
                    afterRigChange(player, rigModule, worn, event.getCursor(), 0);
            // 数字キー・オフハンドキーで、ホットバー (オフハンド) の物と替える
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int hotbarSlot = event.getHotbarButton() >= 0 ? event.getHotbarButton() : OFFHAND_SLOT;
                ItemStack other = player.getInventory().getItem(hotbarSlot);
                yield afterRigChange(player, rigModule, worn, other, swapDelta(player, hotbarSlot, worn, other));
            }
            // シフトクリックで持ち物に移す
            case MOVE_TO_OTHER_INVENTORY -> afterRigChange(player, rigModule, worn, null, CostSlotManager.stackCost(worn));
            default -> null;
        };
    }

    private static ItemStack nextRigForClick(Player player, InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> event.getCursor();
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> event.getHotbarButton() >= 0
                    ? player.getInventory().getItem(event.getHotbarButton())
                    : player.getInventory().getItemInOffHand();
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY -> null;
            default -> event.getCurrentItem();
        };
    }

    /** 外したリグ worn がスロット slot に入り、そこにあった other と入れ替わる時の使用量の変化 */
    private static int swapDelta(Player player, int slot, ItemStack worn, ItemStack other) {
        if (!CostSlotManager.isStorageSlot(player, player.getInventory(), slot)) return 0;
        return CostSlotManager.stackCost(worn) - CostSlotManager.stackCost(other);
    }

    /** リグを小さくすると使えなくなるスロットに実物が残っていないか。 */
    private static boolean locksOccupiedSlots(Player player, RigModule rigModule, ItemStack next) {
        int before = rigModule.getEnabledMainInventorySlots(player);
        int after = rigModule.rigSlots(next);
        for (int slot = RigModule.MAIN_INVENTORY_START + after;
             slot < RigModule.MAIN_INVENTORY_START + before; slot++) {
            if (CostSlotManager.stackCost(player.getInventory().getItem(slot)) > 0) return true;
        }
        return false;
    }

    /**
     * リグが worn から next に替わり、使用量が usedDelta 変わった後に容量を超えるか。替わる前より超え方が悪くならなければ止めない
     */
    private static Overflow afterRigChange(Player player, RigModule rigModule, ItemStack worn, ItemStack next, int usedDelta) {
        if (!rigModule.isRigItem(worn)) return null;
        int capacityBefore = CostSlotManager.capacity(player, player.getInventory());
        if (capacityBefore == 0) return null;
        int usedBefore = CostSlotManager.usedCost(player, player.getInventory());
        int capacity = capacityBefore - rigModule.rigSlots(worn) + rigModule.rigSlots(next);
        int used = usedBefore + usedDelta;
        return used > capacity && used - capacity > usedBefore - capacityBefore ? new Overflow(used, capacity) : null;
    }

    private static RigModule rigModule() {
        return ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
    }

    private static void reject(Overflow overflow, Player player, Runnable cancel) {
        if (overflow == null) return;
        cancel.run();
        player.sendActionBar(Component.text("容量が足りません (入れると " + overflow.used() + " / " + overflow.capacity() + " マス)",
                NamedTextColor.RED));
        CapacityGauge.flash(player);
    }

    /** 制限がかかるプレイヤーか (クリエイティブ・観戦と、SCAVのデータパック銃用FakePlayerは除く) */
    private static boolean isLimited(Player player) {
        GameMode mode = player.getGameMode();
        return (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && !DatapackGunnerManager.isGunner(player);
    }

    /** ロビーでは容量超過の装備準備を許し、レイド中だけコスト上限を強制する。 */
    private static boolean isRaidCapacityEnforced(Player player) {
        if (!isLimited(player)) return false;
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getRaidModule();
        return raidModule != null && raidModule.isInRaid(player);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
