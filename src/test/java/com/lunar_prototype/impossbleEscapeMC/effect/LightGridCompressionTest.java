package com.lunar_prototype.impossbleEscapeMC.effect;

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
    public static void main(String[] args) throws Exception {
        Random rng = new Random(8351);
        for (int w : new int[]{4, 48, 80}) for (int n : new int[]{1, 2, 4, 16}) {
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
            for (int word : words) if (word < 0 || word > 16383) throw new AssertionError("word range");
            for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) for (int x = 0; x < w; x++)
                if (code(words, w, h, d, x, y, z) != values[x + w * (z + d * y)]) throw new AssertionError("round trip");
        }
        try { LightGridCompression.encode(new int[1], 5, 4, 4); throw new AssertionError("invalid dimensions"); }
        catch (IllegalArgumentException expected) { }
        if (args.length == 1) {
            byte[] payload = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));
            int[] light = new int[2 * ((48 * 16 * 48 + 6) / 7)];
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(payload);
            for (int i = 0; i < light.length; i++) light[i] = bytes.getShort(46 + i * 2) & 65535;
            int[] words = LightGridCompression.encode(light, 48, 16, 48);
            java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(words.length * 2);
            for (int word : words) out.putShort((short) word);
            java.nio.file.Files.write(java.nio.file.Path.of(args[0] + ".palette"), out.array());
        }
        System.out.println("PASS: Java palette codec, 12 full-grid round trips, 14bit bounds, invalid dimensions");
    }
}
