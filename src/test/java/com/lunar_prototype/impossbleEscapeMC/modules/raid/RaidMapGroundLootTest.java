package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;

/**
 * 地面ルートの地点の保存: 古いマップの JSON (地点が無い) を読んでも空の一覧になる。保存した地点を読み戻せる。
 * サーバーは要らない
 */
public final class RaidMapGroundLootTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Gson gson = new Gson();
        RaidMap old = gson.fromJson("{\"mapId\":\"m\",\"worldName\":\"w\",\"lootContainers\":[]}", RaidMap.class);
        check(old.getGroundLoot() != null && old.getGroundLoot().isEmpty(), "old JSON gives an empty list");

        RaidMap loaded = gson.fromJson("{\"mapId\":\"m\",\"worldName\":\"w\",\"groundLoot\":["
                + "{\"coords\":[10.0,64.0,-3.0],\"crateId\":\"medical_case\"},"
                + "{\"coords\":[11.0,64.0,-3.0],\"crateId\":\"ammo_case\"}]}", RaidMap.class);
        check(loaded.getGroundLoot().size() == 2, "two points");
        check(loaded.getGroundLoot().get(0).getCrateId().equals("medical_case"), "crate id");
        String saved = gson.toJson(loaded);
        check(saved.contains("\"groundLoot\"") && saved.contains("\"ammo_case\""), "saved back: " + saved);
        System.out.println("RaidMapGroundLootTest OK");
    }
}
