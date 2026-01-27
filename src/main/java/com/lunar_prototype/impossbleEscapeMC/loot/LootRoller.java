package com.lunar_prototype.impossbleEscapeMC.loot;

import java.util.Random;
import java.util.Map;

public class LootRoller {
    private static final Random random = new Random();


    public static String roll(LootTable table) {
        double total = table.items.values().stream().mapToDouble(d -> d).sum();
        double r = random.nextDouble() * total;
        double cur = 0;
        for (Map.Entry<String, Double> e : table.items.entrySet()) {
            cur += e.getValue();
            if (r <= cur) return e.getKey();
        }
        return null;
    }
}
