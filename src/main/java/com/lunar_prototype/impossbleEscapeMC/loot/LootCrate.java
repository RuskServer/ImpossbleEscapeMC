package com.lunar_prototype.impossbleEscapeMC.loot;

import java.util.HashMap;
import java.util.Map;

public class LootCrate {
    public String id;
    public String color = "WHITE"; // Default color
    public Map<String, Double> tableWeights = new HashMap<>(); // Table ID -> Weight
}
