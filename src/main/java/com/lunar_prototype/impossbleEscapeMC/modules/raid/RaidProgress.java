package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 再起動をまたぐ未精算の経験値。表示用ItemStackをGsonへ渡さない。 */
record RaidProgress(String sessionId, long startTime, Set<UUID> participants, Map<UUID, Reward> members) {
    record Reward(String outcome, long experience, int scavKills, int pmcKills, int bossKills,
                  boolean extractionCountIncremented, String extractName, long endMillis,
                  boolean granted, int levelBefore, long expBefore, Map<String, long[]> breakdown) { }
}
