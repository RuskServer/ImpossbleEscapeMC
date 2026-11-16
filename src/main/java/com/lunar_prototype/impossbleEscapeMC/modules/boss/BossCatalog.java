package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain.BrainLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ボスのJava定義。編成・装備・出現条件はここで管理し、配置座標はマップ側へ保存する。 */
final class BossCatalog {
    private BossCatalog() {}

    static Map<String, BossDefinition> create() {
        BossDefinition commander = spectreCommander();
        return Map.of(commander.id(), commander);
    }

    private static BossDefinition spectreCommander() {
        return new BossDefinition(BossDefinition.DEFAULT_ID, "Spectre Commander", true,
                Set.of("ノヴォザリエ薬品工場"), .125, List.of(
                new BossDefinition.Member("boss", true, "scar_h", "Altyn", "sp_v",
                        "pilgrim_tourist_backpack", BrainLevel.HIGH, 40, 8, "best"),
                new BossDefinition.Member("guard1", false, "m4a1", "Altyn", "sp_v",
                        "pilgrim_tourist_backpack", BrainLevel.MID, 40, 12, "best"),
                new BossDefinition.Member("guard2", false, "as_val", "Altyn", "sp_v",
                        "pilgrim_tourist_backpack", BrainLevel.MID, 40, 12, "best")));
    }
}
