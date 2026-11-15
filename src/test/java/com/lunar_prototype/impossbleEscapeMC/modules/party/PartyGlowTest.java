package com.lunar_prototype.impossbleEscapeMC.modules.party;

import org.junit.jupiter.api.Test;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;

import java.util.List;

/**
 * 味方の光らせ方: サーバーが送る状態のビット (0 番) に光るビットを足し、ほかのビット・ほかのデータは変えない
 */
public final class PartyGlowTest {
    private static void check(boolean condition, String message) { org.junit.jupiter.api.Assertions.assertTrue(condition, message); }

    // 型の表は PacketEvents の起動後でないと読めないので、ここでは型を null にする (書き換えは元の型をそのまま使う)
    @Test
    @org.junit.jupiter.api.DisplayName("other data kept")
    void otherDataKept() throws Exception {
        EntityData<?> health = new EntityData<>(9, null, 20f);
        List<EntityData<?>> sneaking = List.of(health, new EntityData<>(0, null, (byte) 0x02));
        List<EntityData<?>> out = PartyGlow.withGlow(sneaking);
        check(out != null && out.size() == 2 && out.get(0) == health, "other data kept");
        check((Byte) out.get(1).getValue() == (byte) 0x42, "glow added, sneak kept: " + out.get(1).getValue());
        check(sneaking.get(1).getValue().equals((byte) 0x02), "input not changed");
        check(PartyGlow.withGlow(List.of(health)) == null, "no flags, no rewrite");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("already glowing")
    void alreadyGlowing() throws Exception {
        check(PartyGlow.withGlow(List.of(new EntityData<>(0, null, (byte) 0x40))) == null, "already glowing");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("high bit kept")
    void highBitKept() throws Exception {
        List<EntityData<?>> gliding = PartyGlow.withGlow(List.of(new EntityData<>(0, null, (byte) 0x80)));
        check(gliding != null && (Byte) gliding.get(0).getValue() == (byte) 0xC0, "high bit kept");
    }
}
