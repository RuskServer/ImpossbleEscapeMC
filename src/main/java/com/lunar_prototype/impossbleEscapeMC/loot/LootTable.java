package com.lunar_prototype.impossbleEscapeMC.loot;

import java.util.ArrayList;
import java.util.List;

public class LootTable {
    public String id;
    public int minRolls = 1;
    public int maxRolls = 3;
    public List<LootEntry> entries = new ArrayList<>();

    public static class LootEntry {
        public String itemId;
        public double weight;
        public int minAmount = 1;
        public int maxAmount = 1;
        public double chance = 1.0; // 0.0 to 1.0
    }
}
