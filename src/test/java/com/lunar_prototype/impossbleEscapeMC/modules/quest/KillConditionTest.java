package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.component.impl.KillEntityObjective;
import com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger;

import java.util.HashMap;
import java.util.Map;

/**
 * 討伐の条件 (マップ・ヘッドショット・銃の種類・銃・距離) で、キルを数える・数えないが正しいか。銃の種類の決め方。
 * サーバーは要らない
 */
public final class KillConditionTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String, Object> kill(String type, String map, boolean headshot, String gunId, double distance) {
        Map<String, Object> params = new HashMap<>();
        params.put("entityType", type);
        if (map != null) params.put("mapId", map);
        params.put("headshot", headshot);
        if (gunId != null) {
            params.put("weaponId", gunId);
            params.put("weaponClass", WeaponClass.of(gunId).name());
        }
        params.put("killDistance", distance);
        return params;
    }

    /** そのキルで進んだか (新しい進捗に 1 回だけ当てる) */
    private static boolean counts(KillEntityObjective objective, Map<String, Object> params) {
        return objective.updateProgress(null, null, new ActiveQuest("test"), 0, QuestTrigger.KILL_ENTITY, params);
    }

    public static void main(String[] args) {
        String factory = "ノヴォザリエ薬品工場", coast = "ノヴォザリエ沿岸工業地区";

        // 条件なしは今まで通り (種類だけ)
        KillEntityObjective plain = new KillEntityObjective("SCAV", 3);
        check(counts(plain, kill("SCAV", null, false, null, 10)), "plain counts outside a raid");
        check(!counts(plain, kill("PMC", factory, true, "ak74", 10)), "other type rejected");

        KillEntityObjective onMap = new KillEntityObjective("SCAV", 3).onMap(factory);
        check(counts(onMap, kill("SCAV", factory, false, null, 10)), "map matches");
        check(!counts(onMap, kill("SCAV", coast, false, null, 10)), "other map rejected");
        check(!counts(onMap, kill("SCAV", null, false, null, 10)), "outside a raid rejected");

        KillEntityObjective headshot = new KillEntityObjective("SCAV", 3).headshot();
        check(counts(headshot, kill("SCAV", factory, true, "ak74", 10)), "headshot counts");
        check(!counts(headshot, kill("SCAV", factory, false, "ak74", 10)), "body shot rejected");
        Map<String, Object> unknownHit = kill("SCAV", factory, false, null, 10);
        unknownHit.remove("headshot");
        check(!counts(headshot, unknownHit), "unknown hit rejected");

        KillEntityObjective shotgun = new KillEntityObjective("SCAV", 3).with(WeaponClass.SHOTGUN);
        check(counts(shotgun, kill("SCAV", coast, false, "mossberg_590", 5)), "shotgun counts");
        check(!counts(shotgun, kill("SCAV", coast, false, "ak74", 5)), "rifle rejected for shotgun");
        check(!counts(shotgun, kill("SCAV", coast, false, null, 5)), "no weapon rejected");

        KillEntityObjective ak = new KillEntityObjective("SCAV", 3).withWeapon("ak74", "AK-74M");
        check(counts(ak, kill("SCAV", coast, false, "ak74", 20)), "AK counts");
        check(!counts(ak, kill("SCAV", coast, false, "m4a1", 20)), "other rifle rejected");

        // 組み合わせ: 沿岸で、狙撃銃で、40m 以上
        KillEntityObjective marksman = new KillEntityObjective("SCAV", 4, 40.0, null).onMap(coast).with(WeaponClass.MARKSMAN);
        check(counts(marksman, kill("SCAV", coast, false, "m700", 55)), "all conditions met");
        check(!counts(marksman, kill("SCAV", coast, false, "m700", 30)), "too close rejected");
        check(!counts(marksman, kill("SCAV", factory, false, "svd", 55)), "wrong map rejected");
        check(!counts(marksman, kill("SCAV", coast, false, "ak74", 55)), "wrong class rejected");

        // 説明文と一覧の短い条件
        check(marksman.getDescription().equals("ノヴォザリエ沿岸工業地区 で SCAV を 4 体排除する (狙撃銃・距離: 40m 以上)"),
                "description: " + marksman.getDescription());
        check(marksman.shortConditions().equals("沿岸工業地区・狙撃銃・40m 以上"), "short: " + marksman.shortConditions());
        check(new KillEntityObjective("SCAV", 5, null, 15.0).getDescription().equals("SCAV を 5 体排除する (距離: 15m 以下)"),
                "distance format");
        check(plain.shortConditions().isEmpty(), "no conditions, no short text");

        // 銃の種類: 今ある銃は表、知らない銃は性能から
        check(WeaponClass.of("usp45") == WeaponClass.PISTOL && WeaponClass.of("uzi") == WeaponClass.SMG
                && WeaponClass.of("as_val") == WeaponClass.ASSAULT_RIFLE && WeaponClass.of("svd") == WeaponClass.MARKSMAN
                && WeaponClass.of("mossberg_590") == WeaponClass.SHOTGUN, "known guns");
        check(WeaponClass.KNOWN.size() == 11, "all eleven datapack guns classified");
        check(WeaponClass.of(new DatapackGunProfile("x", "X", 500, false, false, 6, 40, 60, 80, 8)) == WeaponClass.SHOTGUN, "pellets");
        check(WeaponClass.of(new DatapackGunProfile("x", "X", 60, false, true, 5, 40, 60, 400, 1)) == WeaponClass.MARKSMAN, "long range");
        check(WeaponClass.of(new DatapackGunProfile("x", "X", 900, true, false, 30, 40, 60, 90, 1)) == WeaponClass.SMG, "short auto");
        check(WeaponClass.of(new DatapackGunProfile("x", "X", 400, false, false, 12, 40, 60, 80, 1)) == WeaponClass.PISTOL, "short semi");
        check(WeaponClass.of(new DatapackGunProfile("x", "X", 700, true, false, 30, 40, 60, 125, 1)) == WeaponClass.ASSAULT_RIFLE, "mid range");
        System.out.println("KillConditionTest OK");
    }
}
