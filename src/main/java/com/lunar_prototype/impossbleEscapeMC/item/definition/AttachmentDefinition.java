package com.lunar_prototype.impossbleEscapeMC.item.definition;

public class AttachmentDefinition {
    public String id; // e.g. "red_dot_sight"
    public String displayName;
    public String material; // Bukkit material (e.g. "IRON_NUGGET")
    public AttachmentSlot slot; // 装着先スロット
    public int cost = 1; // inventory slots
    public java.util.List<String> description = new java.util.ArrayList<>();
    public int weight; // weight in grams
    public String modelId; // CustomModelData用のモデルID (strings配列に入る値)
    public int customModelData;
    public int rarity;
}
