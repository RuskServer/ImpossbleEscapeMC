package com.lunar_prototype.impossbleEscapeMC.item;

import java.util.List;


public class ItemDefinition {
    public String id; // e.g. gun_ak74
    public String type; // GUN / MED / LOOT
    public String material; // Bukkit material
    public String displayName;
    public int rarity; // 1-5
    public int maxDurability;
    public List<Affix> affixes;
    public GunStats gunStats;
    public MedStats medStats;
}
