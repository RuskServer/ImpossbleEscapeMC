package com.lunar_prototype.impossbleEscapeMC.item;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class CostSlotManager {

    /**
     * インベントリ内の合計コストを計算し、右下から占有スロットを配置・更新します。
     */
    public static void updateInventory(Player player, Inventory inventory) {
        if (inventory == null) return;

        // 1. 現在の有効なストレージスロットを特定
        List<Integer> storageSlots = getEnabledStorageSlots(player, inventory);
        if (storageSlots.isEmpty()) return;

        // 2. 合計追加コストを計算 (cost - 1)
        int extraSlotsNeeded = 0;
        for (int slot : storageSlots) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            if (ItemFactory.isCostSlotPlaceholder(item)) continue;

            // RigModuleのロック用プレースホルダーも無視
            if (RigModule.isLockedSlotPlaceholder(item)) continue;

            int cost = item.getItemMeta().getPersistentDataContainer().getOrDefault(PDCKeys.ITEM_COST, PDCKeys.INTEGER, 1);
            if (cost > 1) {
                extraSlotsNeeded += (cost - 1);
            }
        }

        // 3. 既存のコスト占有スロットをクリア
        for (int slot : storageSlots) {
            ItemStack item = inventory.getItem(slot);
            if (ItemFactory.isCostSlotPlaceholder(item)) {
                inventory.setItem(slot, null);
            }
        }

        // 4. 右下（リストの末尾）から必要な数だけ占有スロットを配置
        // ただし、アイテムが既にあるスロットはスキップする
        int placed = 0;
        for (int i = storageSlots.size() - 1; i >= 0 && placed < extraSlotsNeeded; i--) {
            int slot = storageSlots.get(i);
            ItemStack current = inventory.getItem(slot);
            
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, ItemFactory.createCostSlotPlaceholder());
                placed++;
            }
        }

        // もしスロットが足りなくて配置しきれなかった場合（アイテムで埋まっている等）
        // アイテムをドロップさせるなどの処理が将来的に必要になる可能性がある
    }

    /**
     * 指定されたインベントリにおいて、アイテムを配置可能なスロットのリストを返します。
     */
    private static List<Integer> getEnabledStorageSlots(Player player, Inventory inventory) {
        List<Integer> slots = new ArrayList<>();

        if (inventory.getHolder() instanceof Player) {
            // メインインベントリ（ホットバー + リグ解放分）
            // ホットバー (0-8)
            for (int i = 0; i < 9; i++) {
                slots.add(i);
            }
            // リグ解放分 (9-35)
            RigModule rigModule = ImpossbleEscapeMC.getInstance().getServiceContainer().get(RigModule.class);
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
