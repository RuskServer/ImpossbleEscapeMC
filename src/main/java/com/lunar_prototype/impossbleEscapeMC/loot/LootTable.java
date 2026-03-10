package com.lunar_prototype.impossbleEscapeMC.loot;

import java.util.ArrayList;
import java.util.List;

public class LootTable {
    public String id;
    public int minItems = 1;
    public int maxItems = 5;
    public List<LootEntry> items = new ArrayList<>();

    public static class LootEntry {
        public String itemId;
        public double chance; // 0.0 to 100.0
        public int minAmount = 1;
        public int maxAmount = 1;
    }
}
