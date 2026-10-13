package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutFacility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * スキルツリーのノードの一覧。中身は docs/SKILL_TREE.md の表と同じ (数値・費用は仮。変える時は両方)。
 * ツリーの線は「親の下の隙間で横に曲がり、子の列を下りる」ので、段 (row) と列 (col2) はノードを線が通らないように置いてある
 */
public final class SkillCatalog {
    /** ティアごとに要るプレイヤーレベル (ティア 1 から) */
    private static final int[] TIER_PLAYER_LEVEL = {0, 5, 10};

    private static final Map<String, SkillNode> BY_ID = new LinkedHashMap<>();

    static {
        // ---- 射撃 ----
        node(SkillCategory.SHOOTING, 1, "reload", 1, "素早い装填", "リロード時間 -8%",
                List.of(), false, 8000, List.of(cost("bolts", 2)),
                1, 2, 1, stats(SkillStat.RELOAD_TIME, -0.08));
        node(SkillCategory.SHOOTING, 2, "empty_reload", 1, "空マガジン交換", "空からのリロード -8%",
                List.of(), false, 8000, List.of(cost("bolts", 2)),
                1, 4, 2, stats(SkillStat.EMPTY_RELOAD_TIME, -0.08));
        node(SkillCategory.SHOOTING, 3, "swap", 1, "持ち替え", "武器の持ち替え -10%",
                List.of(), false, 6000, List.of(cost("screwdriver", 2)),
                1, 0, 0, stats(SkillStat.SWAP_TIME, -0.1));
        node(SkillCategory.SHOOTING, 4, "ads", 1, "構えの速さ", "ADS の時間 -8%",
                List.of(), false, 10000, List.of(cost("screwdriver", 1), cost("bolts", 1)),
                1, 6, 3, stats(SkillStat.ADS_TIME, -0.08));
        node(SkillCategory.SHOOTING, 5, "sprint_fire", 1, "走りからの射撃", "走りから撃てるまで -12%",
                List.of(), false, 8000, List.of(cost("dry_fuel", 1)),
                1, 8, 4, stats(SkillStat.SPRINT_TO_FIRE, -0.12));
        node(SkillCategory.SHOOTING, 6, "recoil_v", 1, "縦の反動", "縦の反動 -4%",
                List.of(), false, 12000, List.of(cost("wrench", 2)),
                2, 3, 5, stats(SkillStat.RECOIL_PITCH, -0.04));
        node(SkillCategory.SHOOTING, 7, "recoil_h", 1, "横の反動", "横の反動 -4%",
                List.of(), false, 12000, List.of(cost("wrench", 2)),
                2, 5, 6, stats(SkillStat.RECOIL_YAW, -0.04));
        node(SkillCategory.SHOOTING, 8, "hipfire", 1, "腰だめ", "腰だめの散らばり -8%",
                List.of(), false, 10000, List.of(cost("scraps", 4)),
                2, 7, 7, stats(SkillStat.HIPFIRE_SPREAD, -0.08));
        node(SkillCategory.SHOOTING, 9, "burst", 2, "連射の抑え", "撃ち続けた時の広がり -10%",
                List.of("shooting.recoil_v"), false, 30000, List.of(cost("tool_box", 1), cost("wrench", 2)),
                4, 3, 11, stats(SkillStat.BLOOM_INCREASE, -0.1));
        node(SkillCategory.SHOOTING, 10, "recover", 2, "収まりの速さ", "広がりの戻り +15%",
                List.of("shooting.recoil_h"), false, 30000, List.of(cost("tool_box", 1), cost("wrench", 2)),
                4, 5, 12, stats(SkillStat.BLOOM_DECAY, 0.15));
        node(SkillCategory.SHOOTING, 11, "steady", 2, "照準の安定", "照準の揺れ -10%",
                List.of("shooting.ads"), false, 30000, List.of(cost("mini_battery", 2), cost("screwdriver", 2)),
                3, 6, 10, stats(SkillStat.SWAY, -0.1));
        node(SkillCategory.SHOOTING, 12, "walk_fire", 2, "歩き撃ち", "動きながらの散らばり -15%",
                List.of("shooting.hipfire"), false, 25000, List.of(cost("tool_box", 1)),
                4, 7, 13, stats(SkillStat.MOVE_SPREAD, -0.15));
        node(SkillCategory.SHOOTING, 13, "reload_master", 2, "熟練の装填", "リロード・空リロードがさらに -6%",
                List.of("shooting.reload", "shooting.empty_reload"), true, 35000, List.of(cost("tool_box", 1), cost("bolts", 4)),
                3, 2, 9, stats(SkillStat.RELOAD_TIME, -0.06, SkillStat.EMPTY_RELOAD_TIME, -0.06));
        node(SkillCategory.SHOOTING, 14, "bolt", 2, "ボルト操作", "ボルトアクションの時間 -10%",
                List.of("shooting.swap"), false, 25000, List.of(cost("wrench", 3)),
                3, 0, 8, stats(SkillStat.BOLT_ACTION, -0.1));
        node(SkillCategory.SHOOTING, 15, "recoil_master", 3, "反動の掌握", "縦・横の反動がさらに -6%",
                List.of("shooting.burst", "shooting.recover"), false, 90000, List.of(cost("graphics_card", 1), cost("tool_box", 2)),
                5, 4, 14, stats(SkillStat.RECOIL_PITCH, -0.06, SkillStat.RECOIL_YAW, -0.06));
        node(SkillCategory.SHOOTING, 16, "still_aim", 3, "静かな照準", "構えの揺れ -25%",
                List.of("shooting.steady", "shooting.walk_fire"), false, 80000, List.of(cost("usb", 1), cost("mini_battery", 4)),
                5, 6, 15, stats(SkillStat.SHAKE, -0.25));
        node(SkillCategory.SHOOTING, 17, "quick_draw", 3, "早撃ち", "ADS・持ち替え・走りからの射撃がさらに -8%",
                List.of("shooting.reload_master", "shooting.bolt"), false, 80000, List.of(cost("usb", 1), cost("tool_box", 2)),
                5, 0, 16, stats(SkillStat.ADS_TIME, -0.08, SkillStat.SWAP_TIME, -0.08, SkillStat.SPRINT_TO_FIRE, -0.08));
        // ---- 体力 ----
        node(SkillCategory.BODY, 1, "endurance", 1, "持久力", "スタミナの上限 100 → 110",
                List.of(), false, 8000, List.of(cost("dry_fuel", 2)),
                1, 0, 17, stats(SkillStat.STAMINA_MAX, 10.0));
        node(SkillCategory.BODY, 2, "breathing", 1, "呼吸法", "スタミナの回復 0.6 → 0.66 /tick",
                List.of(), false, 8000, List.of(cost("dry_fuel", 2)),
                1, 2, 18, stats(SkillStat.STAMINA_RECOVERY, 0.1));
        node(SkillCategory.BODY, 3, "rebound", 1, "立ち直り", "回復が始まるまで 1000 → 800ms",
                List.of(), false, 6000, List.of(cost("toilet_paper", 4)),
                1, 8, 19, stats(SkillStat.STAMINA_DELAY_MS, -200.0));
        node(SkillCategory.BODY, 4, "light_jump", 1, "軽い跳躍", "ジャンプのスタミナ 2 → 1.5",
                List.of(), false, 6000, List.of(cost("scraps", 3)),
                2, 1, 20, stats(SkillStat.JUMP_COST, -0.5));
        node(SkillCategory.BODY, 5, "fatigue", 1, "疲労耐性", "切らした時の回復待ち 2000 → 1500ms",
                List.of(), false, 8000, List.of(cost("dry_fuel", 1), cost("toilet_paper", 2)),
                1, 6, 21, stats(SkillStat.EXHAUSTION_MS, -500.0));
        node(SkillCategory.BODY, 6, "nimble", 1, "身軽", "「軽い」の上限 8kg → 9.5kg",
                List.of(), false, 10000, List.of(cost("scraps", 4)),
                2, 5, 22, stats(SkillStat.LIGHT_LIMIT_G, 1500.0));
        node(SkillCategory.BODY, 7, "spread_load", 1, "荷重分散", "「重い」の負担が増え始める 20kg → 23kg",
                List.of(), false, 10000, List.of(cost("bolts", 2)),
                1, 4, 23, stats(SkillStat.HEAVY_RAMP_START_G, 3000.0));
        node(SkillCategory.BODY, 8, "well_fed", 1, "腹持ち", "空腹での回復の下限 0.3 → 0.45",
                List.of(), false, 6000, List.of(cost("water_filter", 1)),
                2, 3, 24, stats(SkillStat.FOOD_RECOVERY_FLOOR, 0.15));
        node(SkillCategory.BODY, 9, "long_run", 2, "長距離走", "走りのスタミナ消費 -10%",
                List.of("body.endurance"), false, 30000, List.of(cost("water_filter", 2), cost("dry_fuel", 3)),
                3, 0, 25, stats(SkillStat.SPRINT_COST, -0.1));
        node(SkillCategory.BODY, 10, "crouch_rest", 2, "屈んで休む", "屈んでいる時の回復 ×1.2 → ×1.4",
                List.of("body.breathing"), false, 25000, List.of(cost("water_filter", 2)),
                3, 2, 26, stats(SkillStat.CROUCH_RECOVERY, 0.2));
        node(SkillCategory.BODY, 11, "heavy_load", 2, "重荷に慣れる", "走れなくなる重さ 35kg → 38kg",
                List.of("body.spread_load"), false, 35000, List.of(cost("tool_box", 1), cost("bolts", 3)),
                3, 4, 27, stats(SkillStat.SPRINT_BLOCK_G, 3000.0));
        node(SkillCategory.BODY, 12, "steady_steps", 2, "足取り", "切らした時の遅さ -0.2 → -0.12",
                List.of("body.fatigue"), false, 25000, List.of(cost("dry_fuel", 3)),
                3, 6, 28, stats(SkillStat.EXHAUSTED_SLOW, 0.08));
        node(SkillCategory.BODY, 13, "porter", 3, "担ぎ手", "遅くなり始める重さ 40kg → 45kg",
                List.of("body.heavy_load", "body.steady_steps"), false, 90000, List.of(cost("mechanical_golden_watch", 1)),
                5, 6, 29, stats(SkillStat.SLOW_START_G, 5000.0));
        node(SkillCategory.BODY, 14, "conditioned", 3, "鍛えた体", "「とても重い」の消費倍率 1.5 → 1.3、スタミナの上限さらに +10",
                List.of("body.long_run", "body.crouch_rest"), false, 80000, List.of(cost("usb", 1), cost("water_filter", 3)),
                5, 0, 30, stats(SkillStat.CRITICAL_COST, -0.2, SkillStat.STAMINA_MAX, 10.0));
        // ---- 医療 ----
        node(SkillCategory.MEDICAL, 1, "tourniquet", 1, "止血帯の扱い", "CAT の使用時間 45 → 36 tick",
                List.of(), false, 8000, List.of(cost("CAT", 2)),
                1, 0, 31, stats(SkillStat.CAT_TICKS, -9.0));
        node(SkillCategory.MEDICAL, 2, "first_aid", 1, "応急手当", "AI-2 の 1 回の回復 2.0 → 2.4 HP",
                List.of(), false, 10000, List.of(cost("ai2", 1)),
                1, 2, 32, stats(SkillStat.AI2_HEAL, 0.4));
        node(SkillCategory.MEDICAL, 3, "bleed_control", 1, "出血の抑え", "出血のダメージ -15%",
                List.of(), false, 8000, List.of(cost("CAT", 2)),
                1, 6, 33, stats(SkillStat.BLEED_DAMAGE, -0.15));
        node(SkillCategory.MEDICAL, 4, "sturdy_legs", 1, "丈夫な脚", "脚に当たった時の骨折 25% → 21%",
                List.of(), false, 8000, List.of(cost("scraps", 3)),
                2, 3, 34, stats(SkillStat.LEG_FRACTURE_CHANCE, -0.04));
        node(SkillCategory.MEDICAL, 5, "shallow", 1, "浅い傷", "当たった時の出血 10% → 8.5%",
                List.of(), false, 8000, List.of(cost("toilet_paper", 4)),
                2, 5, 35, stats(SkillStat.BLEED_CHANCE, -0.015));
        node(SkillCategory.MEDICAL, 6, "limp", 1, "骨折の歩き方", "脚を折って走った時のダメージ 0.5 → 0.35 /秒",
                List.of(), false, 6000, List.of(cost("bolts", 2)),
                1, 8, 36, stats(SkillStat.FRACTURE_SPRINT_DAMAGE, -0.15));
        node(SkillCategory.MEDICAL, 7, "steady_hands", 1, "手慣れ", "使用が途切れたとみなすまで 250 → 400ms (うっかり中断を減らす)",
                List.of(), false, 6000, List.of(cost("CAT", 1)),
                1, 4, 37, stats(SkillStat.USE_GRACE_MS, 150.0));
        node(SkillCategory.MEDICAL, 8, "adrenaline", 2, "アドレナリン", "自動の痛み止めの間隔 5 分 → 4 分",
                List.of("medical.bleed_control"), false, 30000, List.of(cost("ai2", 2), cost("water_filter", 1)),
                3, 6, 38, stats(SkillStat.ADRENALINE_COOLDOWN_MS, -60000.0));
        node(SkillCategory.MEDICAL, 9, "long_rush", 2, "長い興奮", "自動の痛み止め 5 秒 → 8 秒",
                List.of("medical.limp"), false, 25000, List.of(cost("ai2", 2)),
                3, 8, 39, stats(SkillStat.ADRENALINE_DURATION_MS, 3000.0));
        node(SkillCategory.MEDICAL, 10, "frugal", 2, "節約", "AI-2 の耐久を 20% の確率で減らさない",
                List.of("medical.first_aid"), false, 30000, List.of(cost("ai2", 3)),
                3, 2, 40, stats(SkillStat.AI2_SAVE_CHANCE, 0.2));
        node(SkillCategory.MEDICAL, 11, "quick_treat", 2, "手早い処置", "CAT の使用時間さらに 36 → 30 tick",
                List.of("medical.tourniquet"), false, 25000, List.of(cost("CAT", 4)),
                3, 0, 41, stats(SkillStat.CAT_TICKS, -6.0));
        node(SkillCategory.MEDICAL, 12, "iron_body", 3, "鉄の体", "骨折・出血の確率がさらに -20%",
                List.of("medical.sturdy_legs", "medical.shallow"), false, 90000, List.of(cost("graphics_card", 1), cost("CAT", 4)),
                5, 4, 42, stats(SkillStat.INJURY_CHANCE, -0.2));
        node(SkillCategory.MEDICAL, 13, "field_medic", 3, "野戦衛生兵", "AI-2 の回復の間隔 10 → 8 tick",
                List.of("medical.frugal", "medical.quick_treat"), false, 80000, List.of(cost("usb", 1), cost("ai2", 4)),
                5, 0, 43, stats(SkillStat.AI2_INTERVAL_TICKS, -2.0));
        // ---- 隠密・探索 ----
        node(SkillCategory.STEALTH, 1, "quick_search", 1, "手早い探索", "1 マスの探索 0.5 → 0.45 秒",
                List.of(), false, 12000, List.of(cost("flashlight", 1), cost("scraps", 3)),
                1, 0, 44, stats(SkillStat.SEARCH_TICKS, -1.0));
        node(SkillCategory.STEALTH, 2, "quiet_feet", 1, "静かな足", "歩きの足音の大きさ 0.8 → 0.7",
                List.of(), false, 8000, List.of(cost("toilet_paper", 4)),
                1, 2, 45, stats(SkillStat.WALK_STEP_VOLUME, -0.1));
        node(SkillCategory.STEALTH, 3, "sneak", 1, "忍び足", "歩きで SCAV に気づかれるまで 40 → 50 tick",
                List.of(), false, 8000, List.of(cost("cigarettes", 3)),
                1, 4, 46, stats(SkillStat.WALK_ALERT_TICKS, 10.0));
        node(SkillCategory.STEALTH, 4, "low_profile", 1, "低い姿勢", "屈んだ時の見つかりやすさ ×0.6 → ×0.5",
                List.of(), false, 10000, List.of(cost("scraps", 4)),
                2, 5, 26, stats(SkillStat.CROUCH_VISIBILITY, -0.1));
        node(SkillCategory.STEALTH, 5, "quick_extract", 1, "素早い脱出", "脱出にかかる時間 10 → 9 秒",
                List.of(), false, 12000, List.of(cost("mini_battery", 1)),
                1, 8, 47, stats(SkillStat.EXTRACT_SECONDS, -1.0));
        node(SkillCategory.STEALTH, 6, "reach", 1, "拾う手", "落ちている物を拾える距離 3.5 → 4.0",
                List.of(), false, 6000, List.of(cost("screwdriver", 1)),
                1, 6, 37, stats(SkillStat.PICKUP_RANGE, 0.5));
        node(SkillCategory.STEALTH, 7, "grass", 1, "草むら", "草をかき分ける音の間隔 1.2 → 1.5",
                List.of(), false, 6000, List.of(cost("scraps", 2)),
                2, 1, 48, stats(SkillStat.RUSTLE_DISTANCE, 0.3));
        node(SkillCategory.STEALTH, 8, "search_master", 2, "熟練の探索", "1 マスの探索さらに 0.45 → 0.4 秒",
                List.of("stealth.quick_search"), false, 40000, List.of(cost("mini_battery", 2), cost("flashlight", 1)),
                3, 0, 49, stats(SkillStat.SEARCH_TICKS, -1.0));
        node(SkillCategory.STEALTH, 9, "silent", 2, "気配消し", "足音で SCAV を呼ぶ範囲 32 → 28",
                List.of("stealth.quiet_feet"), false, 30000, List.of(cost("mini_battery", 2)),
                3, 2, 50, stats(SkillStat.FOOTSTEP_ALERT_RADIUS, -4.0));
        node(SkillCategory.STEALTH, 10, "quiet_run", 2, "走りの静けさ", "走りで気づかれるまで 10 → 14 tick",
                List.of("stealth.sneak"), false, 30000, List.of(cost("tool_box", 1)),
                3, 4, 51, stats(SkillStat.SPRINT_ALERT_TICKS, 4.0));
        node(SkillCategory.STEALTH, 11, "shortcut", 2, "抜け道", "脱出にかかる時間さらに 9 → 8 秒",
                List.of("stealth.quick_extract"), false, 40000, List.of(cost("mini_battery", 3)),
                3, 8, 52, stats(SkillStat.EXTRACT_SECONDS, -1.0));
        node(SkillCategory.STEALTH, 12, "shadow", 3, "影に溶ける", "暗い所での見つかりやすさ -15%",
                List.of("stealth.low_profile", "stealth.quiet_run"), false, 90000, List.of(cost("usb", 1), cost("mini_battery", 3)),
                5, 6, 53, stats(SkillStat.DARK_VISIBILITY, -0.15));
        node(SkillCategory.STEALTH, 13, "faint", 3, "気配の薄さ", "動いていると気づかれる距離 8 → 6",
                List.of("stealth.silent", "stealth.quiet_run"), false, 90000, List.of(cost("graphics_card", 1)),
                5, 2, 54, stats(SkillStat.MOVING_AWARENESS, -2.0));
        // ---- 交易 ----
        node(SkillCategory.TRADE, 1, "kovacs", 1, "Kovacs と顔なじみ", "Kovacs の買値 -3%",
                List.of(), false, 12000, List.of(cost("cigarettes", 3)),
                1, 0, 55, stats(SkillStat.BUY_KOVACS, -0.03));
        node(SkillCategory.TRADE, 2, "pharmakon", 1, "Pharmakon と顔なじみ", "Pharmakon の買値 -3%",
                List.of(), false, 12000, List.of(cost("cigarettes", 3)),
                1, 2, 56, stats(SkillStat.BUY_PHARMAKON, -0.03));
        node(SkillCategory.TRADE, 3, "bastion", 1, "Bastion と顔なじみ", "Bastion の買値 -3%",
                List.of(), false, 12000, List.of(cost("cigarettes", 3)),
                1, 4, 57, stats(SkillStat.BUY_BASTION, -0.03));
        node(SkillCategory.TRADE, 4, "pitch", 1, "売り込み", "ブローカーの買取 +3%",
                List.of(), false, 12000, List.of(cost("cigarettes", 2), cost("toilet_paper", 2)),
                1, 6, 58, stats(SkillStat.SELL_BROKER, 0.03));
        node(SkillCategory.TRADE, 5, "repair", 1, "修理の伝手", "修理代 -10%",
                List.of(), false, 10000, List.of(cost("wrench", 2)),
                2, 1, 59, stats(SkillStat.REPAIR_COST, -0.1));
        node(SkillCategory.TRADE, 6, "supply", 1, "配給係と顔なじみ", "支給装備の待ち時間 15 → 12 分",
                List.of(), false, 8000, List.of(cost("cigarettes", 2)),
                1, 8, 60, stats(SkillStat.SUPPLY_COOLDOWN_MIN, -3.0));
        node(SkillCategory.TRADE, 7, "records", 1, "記録係", "レイドの経験値 +5%",
                List.of(), false, 15000, List.of(cost("mini_battery", 1)),
                2, 5, 61, stats(SkillStat.RAID_EXP, 0.05));
        node(SkillCategory.TRADE, 8, "bulk", 2, "大口の客", "トレーダーの 1 日の上限 +25% (端数切り上げ)",
                List.of("trade.kovacs", "trade.pharmakon", "trade.bastion"), true, 40000, List.of(cost("usb", 1)),
                3, 2, 62, stats(SkillStat.DAILY_LIMIT, 0.25));
        node(SkillCategory.TRADE, 9, "negotiate", 2, "交渉術", "ブローカーの買取さらに +3%",
                List.of("trade.pitch"), false, 35000, List.of(cost("cigarettes", 5)),
                3, 6, 63, stats(SkillStat.SELL_BROKER, 0.03));
        node(SkillCategory.TRADE, 10, "supply_extra", 2, "支給の上乗せ", "支給の AI-2 の残り 20 → 30",
                List.of("trade.supply"), false, 25000, List.of(cost("ai2", 2)),
                3, 8, 64, stats(SkillStat.SUPPLY_AI2, 10.0));
        node(SkillCategory.TRADE, 11, "regular", 3, "上客", "すべてのトレーダーの買値さらに -4%",
                List.of("trade.bulk"), false, 100000, List.of(cost("mechanical_golden_watch", 1)),
                5, 2, 65, stats(SkillStat.BUY_ALL, -0.04));
        node(SkillCategory.TRADE, 12, "appraiser", 3, "目利き", "ブローカーの買取さらに +4%",
                List.of("trade.negotiate", "trade.records"), false, 100000, List.of(cost("graphics_card", 1)),
                5, 4, 66, stats(SkillStat.SELL_BROKER, 0.04));

    }

    private SkillCatalog() {
    }

    private static void node(SkillCategory category, int number, String id, int tier, String name, String effect,
                             List<String> requires, boolean anyOf, long money, List<HideoutFacility.ItemCost> materials,
                             int row, int col2, int icon, Map<SkillStat, Double> stats) {
        SkillNode node = new SkillNode(category.id + "." + id, category, number, tier, name, effect, requires, anyOf, money,
                materials, row, col2, icon, stats);
        if (BY_ID.put(node.id(), node) != null) throw new IllegalStateException("duplicate skill " + node.id());
    }

    private static HideoutFacility.ItemCost cost(String itemId, int amount) {
        return new HideoutFacility.ItemCost(itemId, amount);
    }

    private static Map<SkillStat, Double> stats(Object... pairs) {
        Map<SkillStat, Double> map = new EnumMap<>(SkillStat.class);
        for (int i = 0; i < pairs.length; i += 2) map.put((SkillStat) pairs[i], (Double) pairs[i + 1]);
        return Collections.unmodifiableMap(map);
    }

    public static SkillNode get(String id) {
        return BY_ID.get(id);
    }

    public static List<SkillNode> all() {
        return List.copyOf(BY_ID.values());
    }

    /** 系統のノード (設計書の番号順) */
    public static List<SkillNode> of(SkillCategory category) {
        List<SkillNode> out = new ArrayList<>();
        for (SkillNode node : BY_ID.values()) {
            if (node.category() == category) out.add(node);
        }
        return out;
    }

    /** ティアを開くのに要る、隠れ家のスキルツリー設備のレベル */
    public static int facilityLevel(int tier) {
        return tier;
    }

    /** ティアのノードを習得するのに要るプレイヤーレベル */
    public static int playerLevel(int tier) {
        return TIER_PLAYER_LEVEL[tier - 1];
    }
}
