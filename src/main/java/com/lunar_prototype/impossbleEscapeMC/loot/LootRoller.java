package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.GameItems;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LootRoller {
    private static final Random random = new Random();

    public static List<ItemStack> roll(org.bukkit.World world, LootTable table) {
        List<ItemStack> results = new ArrayList<>();
        if (table.items.isEmpty()) return results;

        // Shuffle entries for unbiased independent checks
        List<LootTable.LootEntry> pool = new ArrayList<>(table.items);
        Collections.shuffle(pool);

        for (LootTable.LootEntry entry : pool) {
            if (results.size() >= table.maxItems) break;

            // Independent chance check (0.0 to 100.0)
            if (random.nextDouble() * 100.0 <= entry.chance) {
                ItemStack item = GameItems.create(world, entry.itemId, entry.displayName);

                if (item != null) {
                    int amount = entry.minAmount;
                    if (entry.maxAmount > entry.minAmount) {
                        amount += random.nextInt(entry.maxAmount - entry.minAmount + 1);
                    }
                    item.setAmount(amount);
                    results.add(item);
                } else {
                    ImpossbleEscapeMC.getInstance().getLogger().warning("Loot Error: Item ID '" + entry.itemId + "' could not be created!");
                }
            }
        }

        // Ensure minimum items if possible
        int safetyBreak = 0;
        while (results.size() < table.minItems && !pool.isEmpty() && safetyBreak < 20) {
            safetyBreak++;
            // 確率に比例して選ぶ (均等に選ぶと、品物の多い表で確率の低い物ばかり出やすくなる)
            LootTable.LootEntry entry = pickByChance(pool);
            
            ItemStack item = GameItems.create(world, entry.itemId, entry.displayName);

            if (item != null) {
                int amount = entry.minAmount;
                if (entry.maxAmount > entry.minAmount) {
                    amount += random.nextInt(entry.maxAmount - entry.minAmount + 1);
                }
                item.setAmount(amount);
                results.add(item);
            }
        }

        return results;
    }

    private static LootTable.LootEntry pickByChance(List<LootTable.LootEntry> pool) {
        double total = 0;
        for (LootTable.LootEntry entry : pool) total += Math.max(0, entry.chance);
        if (total <= 0) return pool.get(random.nextInt(pool.size()));
        double r = random.nextDouble() * total;
        for (LootTable.LootEntry entry : pool) {
            r -= Math.max(0, entry.chance);
            if (r < 0) return entry;
        }
        return pool.get(pool.size() - 1);
    }
}
