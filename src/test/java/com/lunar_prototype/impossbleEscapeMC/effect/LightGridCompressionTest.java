package com.lunar_prototype.impossbleEscapeMC.effect;

import org.junit.jupiter.api.Test;

import java.util.Random;

public final class LightGridCompressionTest {
    static int bits(int[] words, int bit, int count) {
        int value = words[bit / 14] >>> (bit % 14);
        if (bit % 14 + count > 14) value |= words[bit / 14 + 1] << (14 - bit % 14);
        return value & ((1 << count) - 1);
    }
    static int code(int[] words, int w, int h, int d, int x, int y, int z) {
        int tile = x / 4 + w / 4 * (z / 4 + d / 4 * (y / 4));
        int at = (w * h * d / 64 * 2 + bits(words, tile * 16, 16)) * 8;
        int mode = bits(words, at, 8), i = x % 4 + 4 * (z % 4 + 4 * (y % 4));
        if (mode == 0) return bits(words, at + 8, 8);
        if (mode == 3) return bits(words, at + 8 + i * 4, 4);
        int n = bits(words, at + 8, 8);
        int id = bits(words, at + (2 + n) * 8 + i * mode, mode);
        return bits(words, at + (2 + id) * 8, 8);
    }
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> grids() {
        return java.util.stream.IntStream.of(4, 48, 64, 80).boxed().flatMap(w ->
                java.util.stream.IntStream.of(1, 2, 4, 16).mapToObj(n -> org.junit.jupiter.params.provider.Arguments.of(w, n)));
    }
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}x16x{0}, palette={1}")
    @org.junit.jupiter.params.provider.MethodSource("grids")
    void paletteRoundTripsWithoutExceedingFourteenBits(int w, int n) {
        Random rng = new Random(8351L + w * 31L + n);
        int h = 16, d = w, count = w * h * d;
        int[] values = new int[count], light = new int[2 * ((count + 6) / 7)];
        for (int i = 0; i < count; i++) {
            values[i] = rng.nextInt(n);
            int bit = (i % 7) * 4, at = 2 * (i / 7);
            light[at] |= values[i] << bit & 16383;
            if (bit >= 14) light[at + 1] |= values[i] << (bit - 14);
            else if (bit + 4 > 14) light[at + 1] |= values[i] >>> (14 - bit);
        }
        int[] words = LightGridCompression.encode(light, w, h, d);
        for (int word : words) org.junit.jupiter.api.Assertions.assertTrue(word >= 0 && word <= 16383, "word range");
        for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) for (int x = 0; x < w; x++)
            org.junit.jupiter.api.Assertions.assertEquals(values[x + w * (z + d * y)], code(words, w, h, d, x, y, z), "round trip");
    }
    @Test void rejectsInvalidDimensions() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> LightGridCompression.encode(new int[1], 5, 4, 4));
    }
}
