package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 持ち物の大きさ (コスト) の制限。1スタックはコストの数だけスロットを使い、合計は使えるスロットの数を超えられない。
 * 本体のスロット以外に使う分は、右下から空きスロットに占有スロット (バリア) を置いて示す。
 * <p>
 * 占有スロットは空きスロットにしか置けないので、制限を超える操作は入る前に止める ({@code CostCapacityListener})。
 * 止められない経路 (プラグインが直接渡す・リグを外して使えるスロットが減る等) であふれた時は、ここで片付ける。
 */
public class CostSlotManager {

    /** プレイヤーのインベントリのうち、ホットバーのスロット (あふれた時は最後に落とす) */
    private static final int HOTBAR_SIZE = 9;

    /**
     * インベントリ内の合計コストを計算し、右下から占有スロットを配置・更新します。
     */
    public static void updateInventory(Player player, Inventory inventory) {
        if (inventory == null) return;

        // 1. 現在の有効なストレージスロットを特定
        List<Integer> storageSlots = getEnabledStorageSlots(player, inventory);
        if (storageSlots.isEmpty()) return;

        // 2. あふれていれば、先に解消する (ロビーでは知らせるだけ)
        int over = usedCost(inventory, storageSlots) - storageSlots.size();
        if (over > 0) resolveOverflow(player, inventory, storageSlots, over);

        // 3. 合計追加コストを計算 (cost - 1)
        int extraSlotsNeeded = 0;
        for (int slot : storageSlots) {
            int cost = stackCost(inventory.getItem(slot));
            if (cost > 1) {
                extraSlotsNeeded += (cost - 1);
            }
        }

        // 4. 右下（リストの末尾）から、空き (または今の占有スロット) のスロットに必要な数だけ占有スロットを置く
        Set<Integer> wanted = new HashSet<>();
        for (int i = storageSlots.size() - 1; i >= 0 && wanted.size() < extraSlotsNeeded; i--) {
            int slot = storageSlots.get(i);
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR || ItemFactory.isCostSlotPlaceholder(current)) {
                wanted.add(slot);
            }
        }

        // 5. 変わるスロットだけ書き換える。全部消して置き直すと、プレイヤーのインベントリは書き換えのたびに
        // クライアントへ送られるので、ホットバーの占有スロットが毎回拾った時のように跳ねる。
        // 使えないスロット (地図のスロット・リグで塞がれた所) に残った占有スロットも消す
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            boolean placeholder = ItemFactory.isCostSlotPlaceholder(inventory.getItem(slot));
            if (wanted.contains(slot)) {
                if (!placeholder) inventory.setItem(slot, ItemFactory.createCostSlotPlaceholder());
            } else if (placeholder) {
                inventory.setItem(slot, null);
            }
        }
    }

    /** 1スタックが使うスロットの数。空のスロット・占有スロット・リグのロック用は数えない */
    public static int stackCost(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        // システム支給の地図は収納コストも自動排出も対象外。
        if (RaidMapManager.isTacticalMapItem(item)) return 0;
        if (ItemFactory.isCostSlotPlaceholder(item) || RigModule.isLockedSlotPlaceholder(item)) return 0;
        return Math.max(1, ItemWeights.costOf(item));
    }

    /** 使えるスロットの数。コストの制限がかからないインベントリ・操作できない状態なら0 */
    public static int capacity(Player player, Inventory inventory) {
        return getEnabledStorageSlots(player, inventory).size();
    }

    /** 今の持ち物が使っているスロットの数 (コストの合計) */
    public static int usedCost(Player player, Inventory inventory) {
        return usedCost(inventory, getEnabledStorageSlots(player, inventory));
    }

    /** コストの制限がかかるスロットか */
    public static boolean isStorageSlot(Player player, Inventory inventory, int slot) {
        return getEnabledStorageSlots(player, inventory).contains(slot);
    }

    /** 同じ物のスタックに全部入りきるか (入りきれば新しいスロットを使わない) */
    public static boolean fitsIntoExistingStack(Player player, Inventory inventory, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        for (int slot : getEnabledStorageSlots(player, inventory)) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.isSimilar(item) && stack.getAmount() + item.getAmount() <= stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /** プレイヤーのインベントリが容量を超えているか (ロビーで整理するまで出撃させない) */
    public static boolean isOverCapacity(Player player) {
        List<Integer> slots = getEnabledStorageSlots(player, player.getInventory());
        return !slots.isEmpty() && usedCost(player.getInventory(), slots) > slots.size();
    }

    private static int usedCost(Inventory inventory, List<Integer> storageSlots) {
        int used = 0;
        for (int slot : storageSlots) {
            used += stackCost(inventory.getItem(slot));
        }
        return used;
    }

    /**
     * あふれた分を片付ける。レイド中は、メイン→ホットバーの順に右下のアイテムから足元に落とし、何を落としたかを伝える。
     * ロビーでは持ち物を失わせないよう落とさず、容量オーバーを知らせ続ける (整理するまで RaidModule が出撃を止める)
     */
    private static void resolveOverflow(Player player, Inventory inventory, List<Integer> storageSlots, int over) {
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getRaidModule();
        if (raidModule == null || !raidModule.isInRaid(player)) {
            player.sendActionBar(Component.text("容量オーバー (使用 " + (storageSlots.size() + over) + " / " + storageSlots.size()
                    + "): 持ち物を減らすまで出撃できません", NamedTextColor.RED));
            return;
        }

        boolean playerInventory = inventory.equals(player.getInventory());
        List<Integer> order = new ArrayList<>();
        for (int i = storageSlots.size() - 1; i >= 0; i--) {
            int slot = storageSlots.get(i);
            if (!playerInventory || slot >= HOTBAR_SIZE) order.add(slot);
        }
        for (int i = storageSlots.size() - 1; i >= 0; i--) {
            int slot = storageSlots.get(i);
            if (playerInventory && slot < HOTBAR_SIZE) order.add(slot);
        }

        List<String> dropped = new ArrayList<>();
        for (int slot : order) {
            if (over <= 0) break;
            ItemStack item = inventory.getItem(slot);
            int cost = stackCost(item);
            if (cost == 0) continue;
            inventory.setItem(slot, null);
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            dropped.add(PlainTextComponentSerializer.plainText().serialize(item.effectiveName()));
            over -= cost;
        }
        if (!dropped.isEmpty()) {
            player.sendMessage(Component.text("[容量オーバー] 入りきらない " + String.join("、", dropped) + " を足元に落としました",
                    NamedTextColor.RED));
        }
    }

    /**
     * 指定されたインベントリにおいて、アイテムを配置可能なスロットのリストを返します。
     */
    private static List<Integer> getEnabledStorageSlots(Player player, Inventory inventory) {
        List<Integer> slots = new ArrayList<>();

        if (inventory.getHolder() instanceof Player) {
            RigModule rigModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
            if (rigModule != null && rigModule.isControlSuppressed(player)) {
                return slots;
            }
            // メインインベントリ（ホットバー + リグ解放分）
            // ホットバー (0-7)。右端 (8) は地図専用で、占有スロットも置かない。
            for (int i = 0; i < HOTBAR_SIZE; i++) {
                if (i == RaidMapManager.MAP_SLOT) continue;
                slots.add(i);
            }
            // リグ解放分 (9-35)
            if (rigModule != null) {
                int unlockedEnd = rigModule.getUnlockedMainInventoryEndSlot(player);
                for (int i = RigModule.MAIN_INVENTORY_START; i <= unlockedEnd; i++) {
                    slots.add(i);
                }
            } else {
                // RigModuleがない場合は全開放（デフォルト）
                for (int i = 9; i <= 35; i++) {
                    slots.add(i);
                }
            }
        } else if (inventory.getHolder() instanceof BackpackModule.BackpackInventoryHolder) {
            // バックパック内は全スロット対象
            for (int i = 0; i < inventory.getSize(); i++) {
                slots.add(i);
            }
        }

        return slots;
    }
}
