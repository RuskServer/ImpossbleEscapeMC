package com.lunar_prototype.impossbleEscapeMC.effect;

import java.io.ByteArrayOutputStream;

/** 検証用の4×4×4区画パレット。索引・本体を連続した14bit wordへ詰める。 */
final class LightGridCompression {
    static final int FORMAT = 0x14C1;
    private LightGridCompression() {}

    static int[] encode(int[] light, int w, int h, int d) {
        if (w <= 0 || h <= 0 || d <= 0 || w % 4 != 0 || h % 4 != 0 || d % 4 != 0
                || (long) w * h * d > 1_048_576 || light.length != 2 * ((w * h * d + 6) / 7))
            throw new IllegalArgumentException("invalid light grid");
        int tiles = w * h * d / 64;
        byte[] index = new byte[tiles * 2];
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        int tile = 0;
        for (int y = 0; y < h; y += 4) for (int z = 0; z < d; z += 4) for (int x = 0; x < w; x += 4) {
            if (body.size() > 65535) throw new IllegalArgumentException("tile offset overflow");
            index[tile * 2] = (byte) body.size();
            index[tile * 2 + 1] = (byte) (body.size() >> 8);
            tile++;
            int[] block = new int[64];
            boolean[] used = new boolean[16];
            for (int i = 0; i < 64; i++) {
                int cell = x + i % 4 + w * (z + i / 4 % 4 + d * (y + i / 16));
                int value = ((light[2 * (cell / 7)] | light[2 * (cell / 7) + 1] << 14) >>> (cell % 7 * 4)) & 15;
                block[i] = value;
                used[value] = true;
            }
            int n = 0;
            int[] palette = new int[16], ids = new int[16];
            for (int v = 0; v < 16; v++) if (used[v]) { ids[v] = n; palette[n++] = v; }
            int mode = n == 1 ? 0 : n == 2 ? 1 : n <= 4 ? 2 : 3;
            body.write(mode);
            if (mode == 0) { body.write(palette[0]); continue; }
            if (mode != 3) {
                body.write(n);
                for (int i = 0; i < n; i++) body.write(palette[i]);
            }
            int bits = mode == 3 ? 4 : mode;
            for (int i = 0; i < 64; i += 8 / bits) {
                int packed = 0;
                for (int j = 0; j < 8 / bits; j++) packed |= (mode == 3 ? block[i + j] : ids[block[i + j]]) << (j * bits);
                body.write(packed);
            }
        }
        byte[] bytes = new byte[index.length + body.size()];
        System.arraycopy(index, 0, bytes, 0, index.length);
        System.arraycopy(body.toByteArray(), 0, bytes, index.length, body.size());
        int[] words = new int[(bytes.length * 8 + 13) / 14];
        for (int i = 0; i < bytes.length; i++) {
            int bit = i * 8, at = bit / 14, shift = bit % 14, value = bytes[i] & 255;
            words[at] |= value << shift & 16383;
            if (shift + 8 > 14) words[at + 1] |= value >>> (14 - shift);
        }
        return words;
    }
}
