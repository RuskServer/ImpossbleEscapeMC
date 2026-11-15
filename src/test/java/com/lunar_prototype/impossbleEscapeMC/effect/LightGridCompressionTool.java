package com.lunar_prototype.impossbleEscapeMC.effect;

/** 取得済みの光データを測定用に圧縮する補助CLI。検証はJUnitで実行する。 */
public final class LightGridCompressionTool {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected an input payload file");

            byte[] payload = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(args[0]));
            int[] light = new int[2 * ((48 * 16 * 48 + 6) / 7)];
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(payload);
            for (int i = 0; i < light.length; i++) light[i] = bytes.getShort(46 + i * 2) & 65535;
            int[] words = LightGridCompression.encode(light, 48, 16, 48);
            java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(words.length * 2);
            for (int word : words) out.putShort((short) word);
            java.nio.file.Files.write(java.nio.file.Path.of(args[0] + ".palette"), out.array());

    }
}
