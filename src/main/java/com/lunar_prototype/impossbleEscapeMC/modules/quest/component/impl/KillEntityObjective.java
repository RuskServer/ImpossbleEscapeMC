package com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.ActiveQuest;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.WeaponClass;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.AbstractQuestObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 特定のエンティティを一定数殺害する目標。
 * 条件 (任意・組み合わせ可): 距離、マップ、ヘッドショット (頭・顔)、銃の種類、銃
 * <p>
 * キルのトリガーの params: entityType, killDistance (m), mapId (倒した人が参加中のレイドのマップ),
 * headshot (Boolean), weaponId (当てた銃のデータパックの ID), weaponClass ({@link WeaponClass} の名前)
 */
public class KillEntityObjective extends AbstractQuestObjective {
    private final String entityType; // "SCAV", "PMC", "BOSS"
    private final int targetAmount;
    private final Double minDistance;
    private final Double maxDistance;
    private final String mapId;
    private final boolean headshot;
    private final WeaponClass weaponClass;
    private final String weaponId;
    /** 銃の条件の表示名 (weaponId の時) */
    private final String weaponName;

    public KillEntityObjective(String entityType, int targetAmount) {
        this(entityType, targetAmount, null, null);
    }

    public KillEntityObjective(String entityType, int targetAmount, Double minDistance, Double maxDistance) {
        this(entityType, targetAmount, minDistance, maxDistance, null, false, null, null, null);
    }

    private KillEntityObjective(String entityType, int targetAmount, Double minDistance, Double maxDistance,
                                String mapId, boolean headshot, WeaponClass weaponClass, String weaponId, String weaponName) {
        this.entityType = entityType;
        this.targetAmount = targetAmount;
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
        this.mapId = mapId;
        this.headshot = headshot;
        this.weaponClass = weaponClass;
        this.weaponId = weaponId;
        this.weaponName = weaponName;
    }

    /** このマップ (脱出の目標と同じマップ名) で倒した時だけ数える */
    public KillEntityObjective onMap(String mapId) {
        return new KillEntityObjective(entityType, targetAmount, minDistance, maxDistance, mapId, headshot, weaponClass, weaponId, weaponName);
    }

    /** ヘッドショット (頭・顔に当てて倒した) だけ数える */
    public KillEntityObjective headshot() {
        return new KillEntityObjective(entityType, targetAmount, minDistance, maxDistance, mapId, true, weaponClass, weaponId, weaponName);
    }

    /** この種類の銃で倒した時だけ数える */
    public KillEntityObjective with(WeaponClass weaponClass) {
        return new KillEntityObjective(entityType, targetAmount, minDistance, maxDistance, mapId, headshot, weaponClass, weaponId, weaponName);
    }

    /** この銃 (データパックの銃 ID) で倒した時だけ数える。name は表示名 */
    public KillEntityObjective withWeapon(String weaponId, String name) {
        return new KillEntityObjective(entityType, targetAmount, minDistance, maxDistance, mapId, headshot, weaponClass, weaponId, name);
    }

    @Override
    public boolean updateProgress(Player player, PlayerData data, ActiveQuest activeQuest, int index, QuestTrigger trigger, Map<String, Object> params) {
        if (trigger != QuestTrigger.KILL_ENTITY) return false;
        if (!matches(params)) return false;

        int current = activeQuest.getProgress(index);
        if (current < targetAmount) {
            activeQuest.setProgress(index, current + 1);
            return true;
        }
        return false;
    }

    /** このキルが条件に合うか */
    boolean matches(Map<String, Object> params) {
        String killedType = (String) params.get("entityType");
        if (killedType == null || !killedType.equalsIgnoreCase(entityType)) return false;
        if (!isDistanceInRange(params)) return false;
        if (mapId != null && !(params.get("mapId") instanceof String map && map.equalsIgnoreCase(mapId))) return false;
        if (headshot && !Boolean.TRUE.equals(params.get("headshot"))) return false;
        if (weaponClass != null && !weaponClass.name().equals(params.get("weaponClass"))) return false;
        return weaponId == null || weaponId.equals(params.get("weaponId"));
    }

    @Override
    public boolean isCompleted(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) >= targetAmount;
    }

    @Override
    public String getProgressText(ActiveQuest activeQuest, int index) {
        return activeQuest.getProgress(index) + " / " + targetAmount;
    }

    @Override
    protected String defaultDescription() {
        StringBuilder sb = new StringBuilder();
        if (mapId != null) sb.append(mapId).append(" で ");
        sb.append(entityType).append(" を ").append(targetAmount).append(" 体排除する");
        List<String> conditions = new ArrayList<>();
        if (weaponId != null) conditions.add(weaponName != null ? weaponName : weaponId);
        if (weaponClass != null) conditions.add(weaponClass.displayName());
        if (headshot) conditions.add("ヘッドショット");
        if (minDistance != null || maxDistance != null) conditions.add("距離: " + formatDistanceRange());
        if (!conditions.isEmpty()) sb.append(" (").append(String.join("・", conditions)).append(")");
        return sb.toString();
    }

    /** 一覧に出す短い条件 (例: 「薬品工場・HS」)。条件が無ければ空 */
    public String shortConditions() {
        List<String> out = new ArrayList<>();
        if (mapId != null) out.add(mapId.startsWith("ノヴォザリエ") ? mapId.substring("ノヴォザリエ".length()) : mapId);
        if (weaponId != null) out.add(weaponName != null ? weaponName : weaponId);
        if (weaponClass != null) out.add(weaponClass.displayName());
        if (headshot) out.add("HS");
        if (minDistance != null || maxDistance != null) out.add(formatDistanceRange());
        return String.join("・", out);
    }

    private boolean isDistanceInRange(Map<String, Object> params) {
        if (minDistance == null && maxDistance == null) return true;

        Object rawDistance = params.get("killDistance");
        if (!(rawDistance instanceof Number)) return false;

        double distance = ((Number) rawDistance).doubleValue();
        if (minDistance != null && distance < minDistance) return false;
        if (maxDistance != null && distance > maxDistance) return false;
        return true;
    }

    private String formatDistanceRange() {
        if (minDistance != null && maxDistance != null) {
            return meters(minDistance) + "m - " + meters(maxDistance) + "m";
        }
        if (minDistance != null) {
            return meters(minDistance) + "m 以上";
        }
        return meters(maxDistance) + "m 以下";
    }

    /** 整数の距離は小数点を付けない (40.0 → 40) */
    private static String meters(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    public String getEntityType() { return entityType; }
    public int getTargetAmount() { return targetAmount; }
    /** マップの条件 (null は任意のマップ) */
    public String getMapId() { return mapId; }
}
