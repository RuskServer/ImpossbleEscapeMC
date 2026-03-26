package com.lunar_prototype.impossbleEscapeMC.item;

import java.util.List;

public class ItemDefinition {
    public String id; // e.g. gun_ak74
    public String type; // GUN / MED / LOOT
    public String material; // Bukkit material
    public String displayName;
    public int rarity; // 1-5
    public int customModelData;
    public int maxDurability;
    public int weight; // weight in grams
    public int cost = 1; // slots occupied
    public boolean stackable = false;
    public List<Affix> affixes;
    public GunStats gunStats;
    public MedStats medStats;
    public ArmorStats armorStats;
    public BackpackStats backpackStats;
    public RigStats rigStats;
}
