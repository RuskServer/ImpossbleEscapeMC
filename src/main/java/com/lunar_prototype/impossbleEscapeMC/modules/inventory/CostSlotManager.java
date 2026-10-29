package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.factory.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.inventory.ItemWeights;
import com.lunar_prototype.impossbleEscapeMC.map.RaidMapManager;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 持ち物の大きさ (コスト) の制限。1スタックはコストの数だけスロットを使い、合計は使えるスロットの数を超えられない。
 * 本体のスロット以外に使う分は、空きスロットに占有スロットを置いて示す。メインの段の物の分はその物のいちばん近くの空きに
 * (どの物の分か分かるように、占有スロットの名前にも書く)、ホットバーの物の分は右下から置く。
 * <p>
 * 占有スロットは空きスロットにしか置けないので、制限を超える操作は入る前に止める ({@code CostCapacityListener})。
 * 直接渡す経路も addItem で事前判定する。既に超過している持ち物は自動で捨てない。
 */
public class CostSlotManager {

    /** プレイヤーのインベントリのうち、ホットバーのスロット */
    private static final int HOTBAR_SIZE = 9;

    /**
     * インベントリ内の合計コストを計算し、占有スロットを配置・更新します。超過アイテムは自動で捨てません。
     */
    public static void updateInventory(Player player, Inventory inventory) {
        if (inventory == null) return;

        // ロビーは装備準備中の超過を許可する。占有表示が物理スロットを塞がないよう、プレイヤー欄では表示しない。
        // 超過状態は容量ゲージで確認でき、出撃待機列への参加時に検査する。
        if (inventory.getHolder() instanceof Player && !isInRaid(player)) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (ItemFactory.isCostSlotPlaceholder(inventory.getItem(slot))) inventory.setItem(slot, null);
            }
            return;
        }

        // 1. 現在の有効なストレージスロットを特定
        List<Integer> storageSlots = getEnabledStorageSlots(player, inventory);
        if (storageSlots.isEmpty()) return;

        // TODO(ai-collab): 受け取り前に容量を判定する。既存の超過アイテムを削除・排出しない。

        // 3. 占有スロットを置く場所と、どの物の分か
        boolean playerInventory = inventory.getHolder() instanceof Player;
        List<ItemStack> contents = new ArrayList<>();
        for (int slot : storageSlots) contents.add(inventory.getItem(slot));
        int firstMain = 0;
        while (playerInventory && firstMain < storageSlots.size() && storageSlots.get(firstMain) < HOTBAR_SIZE) firstMain++;
        int[] costs = new int[contents.size()];
        boolean[] free = new boolean[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            ItemStack item = contents.get(i);
            free[i] = item == null || item.getType() == Material.AIR || ItemFactory.isCostSlotPlaceholder(item);
            costs[i] = free[i] ? 0 : stackCost(item);
        }
        int[] owners = placeholderOwners(costs, free, firstMain);
        Map<Integer, ItemStack> wanted = new HashMap<>();
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] < 0) continue;
            ItemStack owner = contents.get(owners[i]);
            wanted.put(storageSlots.get(i), ItemFactory.createCostSlotPlaceholder(
                    PlainTextComponentSerializer.plainText().serialize(owner.effectiveName()), stackCost(owner)));
        }

        // 4. 変わるスロットだけ書き換える。全部消して置き直すと、プレイヤーのインベントリは書き換えのたびに
        // クライアントへ送られるので、ホットバーの占有スロットが毎回拾った時のように跳ねる。
        // 使えないスロット (地図のスロット・リグで塞がれた所) に残った占有スロットも消す
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            boolean placeholder = ItemFactory.isCostSlotPlaceholder(current);
            ItemStack desired = wanted.get(slot);
            if (desired != null) {
                if (!desired.isSimilar(current)) inventory.setItem(slot, desired);
            } else if (placeholder) {
                inventory.setItem(slot, null);
            }
        }
    }

    /**
     * インベントリの中身から、置くべき占有マスだけを計算して返す (インベントリは書き換えない)。キーは占有マスを置くスロット。
     * バックパックの表示のように、実体のインベントリを汚さずに占有マスを見せたい所で使う。
     */
    public static Map<Integer, ItemStack> costPlaceholders(Inventory inventory) {
        Map<Integer, ItemStack> placeholders = new HashMap<>();
        if (inventory == null) return placeholders;

        int size = inventory.getSize();
        int[] costs = new int[size];
        boolean[] free = new boolean[size];
        for (int i = 0; i < size; i++) {
            ItemStack item = inventory.getItem(i);
            free[i] = item == null || item.getType() == Material.AIR || ItemFactory.isCostSlotPlaceholder(item);
            costs[i] = free[i] ? 0 : stackCost(item);
        }

        int[] owners = placeholderOwners(costs, free, 0);
        for (int i = 0; i < size; i++) {
            if (owners[i] < 0) continue;
            ItemStack owner = inventory.getItem(owners[i]);
            placeholders.put(i, ItemFactory.createCostSlotPlaceholder(
                    PlainTextComponentSerializer.plainText().serialize(owner.effectiveName()), stackCost(owner)));
        }
        return placeholders;
    }

    /** ロビー向けのクライアント表示専用の占有マス。実インベントリは変更せず、配置操作も妨げない。 */
    public static Map<Integer, ItemStack> lobbyCostPlaceholders(Player player) {
        if (isInRaid(player)) return Map.of();
        Inventory inventory = player.getInventory();
        List<Integer> storageSlots = getEnabledStorageSlots(player, inventory);
        if (storageSlots.isEmpty()) return Map.of();
        List<ItemStack> contents = new ArrayList<>();
        for (int slot : storageSlots) contents.add(inventory.getItem(slot));
        int firstMain = 0;
        while (firstMain < storageSlots.size() && storageSlots.get(firstMain) < HOTBAR_SIZE) firstMain++;
        int[] costs = new int[contents.size()];
        boolean[] free = new boolean[contents.size()];
        for (int i = 0; i < contents.size(); i++) {
            ItemStack item = contents.get(i);
            free[i] = item == null || item.getType().isAir() || ItemFactory.isCostSlotPlaceholder(item);
            costs[i] = free[i] ? 0 : stackCost(item);
        }
        int[] owners = placeholderOwners(costs, free, firstMain);
        Map<Integer, ItemStack> placeholders = new HashMap<>();
        for (int i = 0; i < owners.length; i++) {
            if (owners[i] < 0) continue;
            ItemStack owner = contents.get(owners[i]);
            placeholders.put(storageSlots.get(i), ItemFactory.createCostSlotPlaceholder(
                    PlainTextComponentSerializer.plainText().serialize(owner.effectiveName()), stackCost(owner)));
        }
        return placeholders;
    }

    /**
     * 占有スロットの割り当て。costs・free は使えるスロットの中身のコストと空きか (並び順)、firstMain はメインの段が始まる番号
     * (それより前はホットバー。バックパックなら 0)。各番号に置く占有スロットがどの番号の物の分か (置かなければ -1) を返す。
     * <ol>
     *   <li>メインの段の物から順に、その物にいちばん近い空き (同じ距離なら後ろ) を、メインの段の中から取る</li>
     *   <li>ホットバーの物の分と、メインの段に空きが足りなかった分は、右下 (末尾) の空きから取る</li>
     * </ol>
     * 空きは、何も無いスロットと今の占有スロット。物の場所だけで決まるので、物が動かなければ占有スロットも動かない
     */
    static int[] placeholderOwners(int[] costs, boolean[] freeSlots, int firstMain) {
        int size = costs.length;
        int[] owners = new int[size];
        java.util.Arrays.fill(owners, -1);
        boolean[] free = freeSlots.clone();
        List<int[]> leftover = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int need = costs[i] - 1;
            if (need <= 0) continue;
            if (i >= firstMain) {
                for (int distance = 1; need > 0 && distance < size; distance++) {
                    for (int j : new int[]{i + distance, i - distance}) {
                        if (need > 0 && j >= firstMain && j < size && free[j]) {
                            free[j] = false;
                            owners[j] = i;
                            need--;
                        }
                    }
                }
            }
            if (need > 0) leftover.add(new int[]{i, need});
        }
        for (int[] rest : leftover) {
            for (int j = size - 1; j >= 0 && rest[1] > 0; j--) {
                if (!free[j]) continue;
                free[j] = false;
                owners[j] = rest[0];
                rest[1]--;
            }
        }
        return owners;
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

    /** 漁りや購入など直接渡す経路用。入らない分は呼び出し元へ返し、元の場所に残す。 */
    public static Map<Integer, ItemStack> addItem(Player player, ItemStack... items) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)
            return player.getInventory().addItem(items);
        List<Integer> slots = acquisitionSlots(player);
        Map<Integer, ItemStack> left = new HashMap<>();
        for (int i = 0; i < items.length; i++) {
            ItemStack rest = insert(player.getInventory(), slots, slots.size(), items[i]);
            if (rest != null) left.put(i, rest);
        }
        if (!left.isEmpty()) CapacityGauge.flash(player);
        return left;
    }

    /** 一括受け取りが必要な取引用。全て入ると分かった時だけ反映する。 */
    public static boolean tryAddAll(Player player, ItemStack item) {
        Inventory planned = org.bukkit.Bukkit.createInventory(null,36);
        planned.setMaxStackSize(player.getInventory().getMaxStackSize());
        planned.setContents(PlayerStorageSnapshot.cloneItems(player.getInventory().getStorageContents()));
        List<Integer> slots = acquisitionSlots(player);
        if (insert(planned,slots,slots.size(),item) != null) {
            CapacityGauge.flash(player);
            return false;
        }
        for (int slot : slots) player.getInventory().setItem(slot,planned.getItem(slot));
        return true;
    }

    /** 画面のオーバーレイに影響されない、本物の持ち物の受け取り先。地図欄は除く。 */
    public static List<Integer> acquisitionSlots(Player player) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < HOTBAR_SIZE; i++) if (i != RaidMapManager.MAP_SLOT) slots.add(i);
        RigModule rig = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
        int end = rig == null ? 35 : rig.getUnlockedMainInventoryEndSlot(player);
        for (int i = 9; i <= end; i++) slots.add(i);
        return slots;
    }

    /** 指定スロットだけを使い、capacity を超えない個数を入れる。引数のスタックは変更しない。 */
    public static ItemStack insert(Inventory inventory, List<Integer> slots, int capacity, ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        int[] rooms = new int[slots.size()];
        boolean[] empty = new boolean[slots.size()];
        int used = 0, max = Math.min(item.getMaxStackSize(), inventory.getMaxStackSize());
        for (int i = 0; i < slots.size(); i++) {
            ItemStack current = inventory.getItem(slots.get(i));
            used += stackCost(current);
            empty[i] = current == null || current.getType().isAir() || ItemFactory.isCostSlotPlaceholder(current)
                    || RigModule.isLockedSlotPlaceholder(current);
            if (!empty[i] && current.isSimilar(item)) rooms[i] = Math.max(0, Math.min(max, current.getMaxStackSize()) - current.getAmount());
        }
        int[] moves = CostInsertion.plan(capacity, used, stackCost(item), item.getAmount(), max, rooms, empty);
        int remaining = item.getAmount();
        for (int i = 0; i < moves.length; i++) {
            if (moves[i] == 0) continue;
            ItemStack placed = empty[i] ? item.clone() : inventory.getItem(slots.get(i)).clone();
            placed.setAmount(empty[i] ? moves[i] : placed.getAmount() + moves[i]);
            inventory.setItem(slots.get(i), placed);
            remaining -= moves[i];
        }
        if (remaining == 0) return null;
        ItemStack rest = item.clone();rest.setAmount(remaining);return rest;
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

    private static boolean isInRaid(Player player) {
        RaidModule raidModule = ImpossbleEscapeMC.getInstance().getRaidModule();
        return raidModule != null && raidModule.isInRaid(player);
    }
}
