package com.lunar_prototype.impossbleEscapeMC.item;

public class AttachmentDefinition {
    public String id; // e.g. "red_dot_sight"
    public String displayName;
    public String material; // Bukkit material (e.g. "IRON_NUGGET")
    public AttachmentSlot slot; // 装着先スロット
    public int weight; // weight in grams
    public String modelId; // CustomModelData用のモデルID (strings配列に入る値)
    public int customModelData;
    public int rarity;
    public GunStats.AnimationStats aimAnimation; // エイム時のアニメーションを上書き
    public GunStats.AnimationStats equipAnimation; // 持ち替え時のアニメーションを上書き
    public GunStats.ScopeStats scope; // スコープ（ズーム）設定を上書き
    public java.util.Map<String, Double> modifiers = new java.util.HashMap<>(); // ステータス修正値 (例: "recoil" -> -0.1)
}
