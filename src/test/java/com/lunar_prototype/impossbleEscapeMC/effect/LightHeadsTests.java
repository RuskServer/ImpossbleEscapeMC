package com.lunar_prototype.impossbleEscapeMC.effect;

import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 光源ヘッドの色の引き当て。HEADS は不変の Map.of なので get(null) は NullPointerException になる。
 * テクスチャの無いスカルは textureOf が null を返すため、ここで null を弾けていないとチャンクの走査が途中で止まる
 */
class LightHeadsTests {
    private static final String ORANGE_EMERGENCY_LIGHT = "7e44937f03fd9f820475c3f6aaf96d1ee5ec0c9e43fbc9c4313adc7ae0d8c1f5";

    @Test
    void skullWithoutTextureIsNotALightAndDoesNotThrow() {
        assertDoesNotThrow(() -> assertNull(LightHeads.colorOf(null)));
    }

    @Test
    void unknownTextureIsNotALight() {
        assertNull(LightHeads.colorOf("0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void registeredTextureGivesItsLightColor() {
        assertEquals(Color.fromRGB(0xFF9A2E), LightHeads.colorOf(ORANGE_EMERGENCY_LIGHT));
    }
}
