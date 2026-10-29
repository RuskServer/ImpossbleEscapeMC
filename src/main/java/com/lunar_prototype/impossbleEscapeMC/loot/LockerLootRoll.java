package com.lunar_prototype.impossbleEscapeMC.loot;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** 装飾用ロッカーは補充世代ごとに25%で中身あり。読込だけでは再抽選しない。 */
public final class LockerLootRoll {
    public static final List<String> CRATES=List.of("standard_crate","medical_case","ammo_case");
    public static String select(RandomGenerator random) {
        return random.nextInt(4)==0?CRATES.get(random.nextInt(CRATES.size())):null;
    }
    public static String select(String id,long generation) {
        UUID seed=UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8));
        return select(new Random(seed.getMostSignificantBits()^Long.rotateLeft(seed.getLeastSignificantBits(),17)^generation*0x9E3779B97F4A7C15L));
    }
    private LockerLootRoll() {}
}
