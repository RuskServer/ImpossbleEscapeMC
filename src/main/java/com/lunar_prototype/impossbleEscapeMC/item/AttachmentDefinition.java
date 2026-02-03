package com.lunar_prototype.impossbleEscapeMC.item;

public class AttachmentDefinition {
    public String id; // e.g. "red_dot_sight"
    public String displayName;
    public String material; // Bukkit material (e.g. "IRON_NUGGET")
    public AttachmentSlot slot; // 装着先スロット
    public String modelId; // CustomModelData用のモデルID (strings配列に入る値)
    public int rarity;
}
