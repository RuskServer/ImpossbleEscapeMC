package com.lunar_prototype.impossbleEscapeMC.item;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * アイテム (ルート品・防具・医療品・バックパック・リグ) の定義。
 * <p>
 * アイテムのIDはアイテム (PDC の item_id)・ルート表・トレーダー・クエストが参照するため変えない
 * (以前の items/*.yml のキーと同じ)。銃はデータパック (Toi's Armory) の銃を使うため、ここでは定義しない。
 * 重さはグラム、cost はインベントリで占めるスロット数
 */
final class ItemCatalog {

    private ItemCatalog() {
    }

    static List<ItemDefinition> create() {
        List<ItemDefinition> items = new ArrayList<>();

        // --- ルート品 ---
        ItemDefinition bolts = item("bolts", "LOOT", Material.IRON_INGOT, "ボルト", 2, 7, 0, 250, 1);
        items.add(bolts);

        ItemDefinition toilet_paper = item("toilet_paper", "LOOT", Material.IRON_INGOT, "トイレットペーパー", 1, 1, 0, 120, 1);
        items.add(toilet_paper);

        ItemDefinition scraps = item("scraps", "LOOT", Material.IRON_INGOT, "スクラップ", 1, 2, 0, 600, 1);
        items.add(scraps);

        ItemDefinition cigarettes = item("cigarettes", "LOOT", Material.IRON_INGOT, "シガレット", 2, 6, 0, 30, 1);
        items.add(cigarettes);

        ItemDefinition mini_battery = item("mini_battery", "LOOT", Material.IRON_INGOT, "小型バッテリー", 3, 8, 0, 180, 1);
        items.add(mini_battery);

        ItemDefinition graphics_card = item("graphics_card", "LOOT", Material.IRON_INGOT, "グラフィックボード", 4, 11, 0, 1150, 1);
        items.add(graphics_card);

        ItemDefinition tool_box = item("tool_box", "LOOT", Material.IRON_INGOT, "工具箱", 4, 5, 0, 4200, 1);
        items.add(tool_box);

        ItemDefinition wrench = item("wrench", "LOOT", Material.IRON_INGOT, "レンチ", 2, 4, 0, 520, 1);
        items.add(wrench);

        ItemDefinition screwdriver = item("screwdriver", "LOOT", Material.IRON_INGOT, "ドライバー", 2, 3, 0, 180, 1);
        items.add(screwdriver);

        ItemDefinition dry_fuel = item("dry_fuel", "LOOT", Material.IRON_INGOT, "§f固形燃料", 2, 19, 0, 300, 1);
        // weight: 0.3kg
        // cost: 1スロット占有（スタック不可）
        // 2030年代の家庭用備蓄品。調理や暖房に必須。
        items.add(dry_fuel);

        ItemDefinition water_filter = item("water_filter", "LOOT", Material.IRON_INGOT, "§b浄水フィルター", 3, 17, 0, 1200, 2);
        // weight: 1.2kg
        // cost: 2x2相当。沿岸都市の水質汚染対策に重要なバルキーアイテム。
        items.add(water_filter);

        ItemDefinition usb = item("usb", "LOOT", Material.IRON_INGOT, "§e暗号化USBメモリ", 3, 18, 0, 20, 1);
        // weight: 0.02kg
        // cost: 最小サイズ。
        items.add(usb);

        ItemDefinition mechanical_golden_watch = item("mechanical_golden_watch", "LOOT", Material.IRON_INGOT, "§6機械式金時計", 4, 20, 0, 250, 1);
        // weight: 0.25kg
        // cost: 小さいが非常に高価。
        items.add(mechanical_golden_watch);

        // --- 防具 ---
        // --- クラス4 (High End) ---
        ItemDefinition altyn = item("Altyn", "ARMOR", Material.DIAMOND, "§6[Altyn] ヘルメット", 4, 0, 600, 3800, 4);
        // weight: 3.8kg
        // cost: 2x2相当のサイズ感を占有
        altyn.armorStats = armor(4, 3, "HEAD", "minecraft:misc/altyn_overray");
        items.add(altyn);

        ItemDefinition slick = item("Slick", "ARMOR", Material.DIAMOND_CHESTPLATE, "§6[Slick] アーマー", 4, 0, 600, 6500, 9);
        // weight: 6.5kg
        // cost: 3x3相当。プレートキャリアのみだが貴重品としての占有
        slick.armorStats = armor(4, 1, null, null);
        items.add(slick);

        // --- クラス3 (Mid Range) ---
        ItemDefinition fast_mt = item("FAST-MT", "ARMOR", Material.IRON_HELMET, "§b[FAST-MT] ヘルメット", 3, 0, 500, 1200, 2);
        // weight: 1.2kg
        // cost: 軽量ヘルメット、1スロット+1占有
        fast_mt.armorStats = armor(3, 2, null, null);
        items.add(fast_mt);

        ItemDefinition trooper = item("Trooper", "ARMOR", Material.IRON_CHESTPLATE, "§b[Trooper] アーマー", 3, 0, 500, 8200, 6);
        // weight: 8.2kg
        // cost: 腹部保護も含めた大型モデルのためコスト増
        trooper.armorStats = armor(3, 2, null, null);
        items.add(trooper);

        // --- クラス2 (Low End / Standard) ---
        ItemDefinition mf_untar = item("MF-UNTAR", "ARMOR", Material.GOLDEN_CHESTPLATE, "§f[MF-UNTAR] アーマー", 2, 0, 400, 7100, 5);
        // weight: 7.1kg
        // cost: 標準的な青色アーマー
        mf_untar.armorStats = armor(2, 3, null, null);
        items.add(mf_untar);

        ItemDefinition untar = item("UNTAR", "ARMOR", Material.GOLDEN_HELMET, "§f[UNTAR] ヘルメット", 2, 0, 350, 1800, 2);
        // weight: 1.8kg
        untar.armorStats = armor(2, 4, null, null);
        items.add(untar);

        // --- 医療品 ---
        ItemDefinition ai2 = item("ai2", "MED", Material.IRON_INGOT, "AI-2 Medical Kit", 2, 9, 50, 100, 1);
        // customModelData: 通常時のモデル
        // maxDurability: 最大耐久度
        // weight: 100g (軽量・コンパクト)
        // continuous: 長押しモードを有効化
        // healPerUse: 0.5秒ごとの回復量
        // durabilityPerUse: 0.5秒ごとの耐久消費
        // usingCustomModelData: 使用中のモデル
        ai2.medStats = continuousMed(2.0, 2, 10);
        items.add(ai2);

        ItemDefinition cat = item("CAT", "MED", Material.IRON_INGOT, "CAT", 2, 12, 0, 85, 1);
        // customModelData: 通常時のモデル
        // weight: 85g (止血帯単体としての標準重)
        // durationTicks: 効果時間 (45 ticks = 2.25秒)
        cat.medStats = oneTimeMed(45, true, true, true);
        items.add(cat);

        // --- バックパック ---
        ItemDefinition mbss = item("mbss", "BACKPACK", Material.IRON_INGOT, "MBSS Backpack", 3, 13, 0, 1800, 1);
        mbss.backpackStats = backpack(36, 0.4);
        items.add(mbss);

        ItemDefinition tactical_sling_bag = item("tactical_sling_bag", "BACKPACK", Material.IRON_INGOT, "Tactical Sling Bag", 2, 14, 0, 600, 1);
        tactical_sling_bag.backpackStats = backpack(9, 0.2);
        items.add(tactical_sling_bag);

        // --- リグ ---
        ItemDefinition micro_rig = item("micro_rig", "RIG", Material.IRON_INGOT, "Micro Rig", 2, 15, 0, 300, 1);
        micro_rig.rigStats = rig(6, 0.25);
        items.add(micro_rig);

        ItemDefinition d3rcx = item("d3rcx", "RIG", Material.IRON_INGOT, "Haley Strategic D3CRX", 3, 16, 0, 400, 1);
        d3rcx.rigStats = rig(12, 0.25);
        items.add(d3rcx);

        return items;
    }

    /**
     * @param rarity          レアリティ (1-5)
     * @param maxDurability   耐久値 (無ければ0)
     * @param weight          重さ (グラム)
     * @param cost            インベントリで占めるスロット数
     */
    private static ItemDefinition item(String id, String type, Material material, String displayName, int rarity,
                                       int customModelData, int maxDurability, int weight, int cost) {
        ItemDefinition def = new ItemDefinition();
        def.id = id;
        def.type = type;
        def.material = material.name();
        def.displayName = displayName;
        def.rarity = rarity;
        def.customModelData = customModelData;
        def.maxDurability = maxDurability;
        def.weight = weight;
        def.cost = cost;
        def.stackable = false;
        return def;
    }

    /**
     * @param slot          装備する部位 (HEAD など。null なら素材から決まる)
     * @param cameraOverlay かぶった時の画面のオーバーレイ (無ければnull)
     */
    private static ArmorStats armor(int armorClass, int customModelData, String slot, String cameraOverlay) {
        ArmorStats stats = new ArmorStats();
        stats.armorClass = armorClass;
        stats.customModelData = customModelData;
        stats.slot = slot;
        stats.cameraOverlay = cameraOverlay;
        return stats;
    }

    /** 長押しで使い続ける医療品 (0.5秒ごとに回復し、耐久を減らす) */
    private static MedStats continuousMed(double healPerUse, int durabilityPerUse, int usingCustomModelData) {
        MedStats stats = new MedStats();
        stats.continuous = true;
        stats.healPerUse = healPerUse;
        stats.durabilityPerUse = durabilityPerUse;
        stats.usingCustomModelData = usingCustomModelData;
        return stats;
    }

    /** 一回使い切りの医療品 (使い終わるまで durationTicks) */
    private static MedStats oneTimeMed(int durationTicks, boolean cureBleeding, boolean cureLegFracture, boolean cureArmFracture) {
        MedStats stats = new MedStats();
        stats.oneTime = true;
        stats.durationTicks = durationTicks;
        stats.cureBleeding = cureBleeding;
        stats.cureLegFracture = cureLegFracture;
        stats.cureArmFracture = cureArmFracture;
        return stats;
    }

    /** @param size スロット数 (9の倍数、最大54)、reduction 中身の重さの軽減率 */
    private static BackpackStats backpack(int size, double reduction) {
        BackpackStats stats = new BackpackStats();
        stats.size = size;
        stats.reduction = reduction;
        return stats;
    }

    /** @param size 解放するメインインベントリのスロット数 (最大27)、reduction 中身の重さの軽減率 */
    private static RigStats rig(int size, double reduction) {
        RigStats stats = new RigStats();
        stats.size = size;
        stats.reduction = reduction;
        return stats;
    }
}
