package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LootRoller {
    private static final Random random = new Random();

    public static List<ItemStack> roll(LootTable table) {
        List<ItemStack> results = new ArrayList<>();
        if (table.entries.isEmpty()) return results;

        int rollCount = table.minRolls + random.nextInt(Math.max(1, table.maxRolls - table.minRolls + 1));
        double totalWeight = table.entries.stream().mapToDouble(e -> e.weight).sum();

        for (int i = 0; i < rollCount; i++) {
            LootTable.LootEntry entry = selectEntry(table.entries, totalWeight);
            if (entry == null) continue;

            // Check individual chance
            if (random.nextDouble() > entry.chance) continue;

            ItemStack item = ItemFactory.create(entry.itemId);
            if (item != null) {
                int amount = entry.minAmount + random.nextInt(Math.max(1, entry.maxAmount - entry.minAmount + 1));
                item.setAmount(amount);
                results.add(item);
            }
        }

        return results;
    }

    private static LootTable.LootEntry selectEntry(List<LootTable.LootEntry> entries, double totalWeight) {
        double r = random.nextDouble() * totalWeight;
        double cur = 0;
        for (LootTable.LootEntry entry : entries) {
            cur += entry.weight;
            if (r <= cur) return entry;
        }
        return null;
    }
}
