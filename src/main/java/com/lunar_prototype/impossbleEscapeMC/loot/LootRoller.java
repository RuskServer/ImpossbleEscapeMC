package com.lunar_prototype.impossbleEscapeMC.loot;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LootRoller {
    private static final Random random = new Random();

    public static List<ItemStack> roll(LootTable table) {
        List<ItemStack> results = new ArrayList<>();
        if (table.items.isEmpty()) return results;

        // Shuffle entries for unbiased independent checks
        List<LootTable.LootEntry> pool = new ArrayList<>(table.items);
        Collections.shuffle(pool);

        for (LootTable.LootEntry entry : pool) {
            if (results.size() >= table.maxItems) break;

            // Independent chance check (0.0 to 100.0)
            if (random.nextDouble() * 100.0 <= entry.chance) {
                ItemStack item = ItemFactory.create(entry.itemId);
                if (item != null) {
                    int amount = entry.minAmount;
                    if (entry.maxAmount > entry.minAmount) {
                        amount += random.nextInt(entry.maxAmount - entry.minAmount + 1);
                    }
                    item.setAmount(amount);
                    results.add(item);
                } else {
                    ImpossbleEscapeMC.getInstance().getLogger().warning("Loot Error: Item ID '" + entry.itemId + "' not found in ItemRegistry!");
                }
            }
        }

        // Ensure minimum items if possible
        int safetyBreak = 0;
        while (results.size() < table.minItems && !pool.isEmpty() && safetyBreak < 20) {
            safetyBreak++;
            // まだ選ばれていないものから優先的に選ぶ、あるいはランダムに選ぶ
            LootTable.LootEntry entry = pool.get(random.nextInt(pool.size()));
            
            ItemStack item = ItemFactory.create(entry.itemId);
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
}
