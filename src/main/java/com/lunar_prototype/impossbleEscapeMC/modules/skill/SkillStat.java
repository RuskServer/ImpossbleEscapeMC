package com.lunar_prototype.impossbleEscapeMC.modules.skill;

/**
 * スキルが変える値。習得したノードの値を足し合わせた物を {@link SkillModule#value} で聞く (習得していなければ 0)。
 * 「割合」は 1 + 値 を掛ける (-0.08 = 8% 減らす)。それ以外は書いてある単位で、元の値に足す
 */
public enum SkillStat {
    // ---- 射撃 (データパックの銃の数値。toisarm.state.*、割合) ----
    RELOAD_TIME, EMPTY_RELOAD_TIME, SWAP_TIME, ADS_TIME, SPRINT_TO_FIRE, BOLT_ACTION,
    RECOIL_PITCH, RECOIL_YAW, HIPFIRE_SPREAD, MOVE_SPREAD, BLOOM_INCREASE, BLOOM_DECAY, SWAY, SHAKE,

    // ---- 体力 ----
    /** スタミナの上限 (足す) */
    STAMINA_MAX,
    /** スタミナの回復 (割合) */
    STAMINA_RECOVERY,
    /** 回復が始まるまで (ミリ秒) */
    STAMINA_DELAY_MS,
    /** ジャンプのスタミナ (足す) */
    JUMP_COST,
    /** 切らした時の回復待ち (ミリ秒) */
    EXHAUSTION_MS,
    /** 「軽い」の上限 (グラム) */
    LIGHT_LIMIT_G,
    /** 「重い」の負担が増え始める重さ (グラム) */
    HEAVY_RAMP_START_G,
    /** 空腹での回復の下限 (足す) */
    FOOD_RECOVERY_FLOOR,
    /** 走りのスタミナ消費 (割合) */
    SPRINT_COST,
    /** 屈んでいる時の回復の倍率 (足す) */
    CROUCH_RECOVERY,
    /** 走れなくなる重さ (グラム) */
    SPRINT_BLOCK_G,
    /** 切らした時の遅さ (足す。-0.2 に足す) */
    EXHAUSTED_SLOW,
    /** 遅くなり始める重さ (グラム) */
    SLOW_START_G,
    /** 「とても重い」の消費倍率 (足す) */
    CRITICAL_COST,

    // ---- 医療 ----
    /** CAT の使用時間 (tick) */
    CAT_TICKS,
    /** AI-2 の 1 回の回復 (HP) */
    AI2_HEAL,
    /** 出血のダメージ (割合) */
    BLEED_DAMAGE,
    /** 脚に当たった時の骨折の確率 (足す) */
    LEG_FRACTURE_CHANCE,
    /** 当たった時の出血の確率 (足す) */
    BLEED_CHANCE,
    /** 脚を折って走った時のダメージ (足す、1 秒あたり) */
    FRACTURE_SPRINT_DAMAGE,
    /** 使用が途切れたとみなすまで (ミリ秒) */
    USE_GRACE_MS,
    /** 自動の痛み止めの間隔 (ミリ秒) */
    ADRENALINE_COOLDOWN_MS,
    /** 自動の痛み止めの長さ (ミリ秒) */
    ADRENALINE_DURATION_MS,
    /** AI-2 の耐久を減らさない確率 */
    AI2_SAVE_CHANCE,
    /** 骨折・出血の確率 (割合) */
    INJURY_CHANCE,
    /** AI-2 の回復の間隔 (tick) */
    AI2_INTERVAL_TICKS,

    // ---- 隠密・探索 ----
    /** 1 マスの探索 (tick) */
    SEARCH_TICKS,
    /** 歩きの足音の大きさ (足す) */
    WALK_STEP_VOLUME,
    /** 歩きで気づかれるまで (tick) */
    WALK_ALERT_TICKS,
    /** 走りで気づかれるまで (tick) */
    SPRINT_ALERT_TICKS,
    /** 屈んだ時の見つかりやすさの倍率 (足す) */
    CROUCH_VISIBILITY,
    /** 脱出にかかる時間 (秒) */
    EXTRACT_SECONDS,
    /** 落ちている物を拾える距離 (ブロック) */
    PICKUP_RANGE,
    /** 草をかき分ける音の間隔 (ブロック) */
    RUSTLE_DISTANCE,
    /** 足音で SCAV を呼ぶ範囲 (ブロック) */
    FOOTSTEP_ALERT_RADIUS,
    /** 暗い所での見つかりやすさ (割合) */
    DARK_VISIBILITY,
    /** 動いていると気づかれる距離 (ブロック) */
    MOVING_AWARENESS,

    // ---- 交易 ----
    /** トレーダーの買値 (割合) */
    BUY_KOVACS, BUY_PHARMAKON, BUY_BASTION, BUY_ALL,
    /** ブローカーの買取 (割合) */
    SELL_BROKER,
    /** 修理代 (割合) */
    REPAIR_COST,
    /** 支給装備の待ち時間 (分) */
    SUPPLY_COOLDOWN_MIN,
    /** 支給の AI-2 の残り (足す) */
    SUPPLY_AI2,
    /** トレーダーの 1 日の上限 (割合、切り上げ) */
    DAILY_LIMIT,
    /** レイドの経験値 (割合) */
    RAID_EXP
}
