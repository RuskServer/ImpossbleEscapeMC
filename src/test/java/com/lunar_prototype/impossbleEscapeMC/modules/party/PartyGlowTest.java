package com.lunar_prototype.impossbleEscapeMC.modules.party;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;

import java.util.List;

/**
 * 味方の光らせ方: サーバーが送る状態のビット (0 番) に光るビットを足し、ほかのビット・ほかのデータは変えない
 */
public final class PartyGlowTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // 型の表は PacketEvents の起動後でないと読めないので、ここでは型を null にする (書き換えは元の型をそのまま使う)
    public static void main(String[] args) {
        EntityData<?> health = new EntityData<>(9, null, 20f);
        List<EntityData<?>> sneaking = List.of(health, new EntityData<>(0, null, (byte) 0x02));
        List<EntityData<?>> out = PartyGlow.withGlow(sneaking);
        check(out != null && out.size() == 2 && out.get(0) == health, "other data kept");
        check((Byte) out.get(1).getValue() == (byte) 0x42, "glow added, sneak kept: " + out.get(1).getValue());
        check(sneaking.get(1).getValue().equals((byte) 0x02), "input not changed");

        check(PartyGlow.withGlow(List.of(health)) == null, "no flags, no rewrite");
        check(PartyGlow.withGlow(List.of(new EntityData<>(0, null, (byte) 0x40))) == null, "already glowing");
        List<EntityData<?>> gliding = PartyGlow.withGlow(List.of(new EntityData<>(0, null, (byte) 0x80)));
        check(gliding != null && (Byte) gliding.get(0).getValue() == (byte) 0xC0, "high bit kept");
        System.out.println("PartyGlowTest OK");
    }
}
