package com.lunar_prototype.impossbleEscapeMC.effect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * シェーダーの先読み ({@link ShaderWarmup}) の、サーバーなしで確かめられる部分: 一覧の読み込み、棒の段、
 * 先読みの一覧 (shaderwarm_warmup.txt) が測定の一覧 (shaderwarm_effects.txt) の中にあること
 */
public final class ShaderWarmupTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static List<String> resource(String name) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = ShaderWarmupTest.class.getClassLoader().getResourceAsStream(name)) {
            check(in != null, name + " が無い");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) lines.add(line);
            }
        }
        return lines;
    }

    public static void main(String[] args) throws IOException {
        // 一覧の読み込み: コメントと空行は飛ばす
        check(ShaderWarmup.parseEffectNames(List.of("# 説明", "", "  grade_visor ", "hit")).equals(List.of("grade_visor", "hit")), "コメントと空行を飛ばす");
        boolean rejected = false;
        try {
            ShaderWarmup.parseEffectNames(List.of("Grade"));
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "大文字は受け付けない");
        rejected = false;
        try {
            ShaderWarmup.parseEffectNames(List.of("hit", "hit"));
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "重複は受け付けない");

        // 棒の目盛り
        check(ShaderWarmup.litSegments(0, 16) == 0, "始め");
        check(ShaderWarmup.litSegments(8, 16) == 17, "半分 (34 の半分)");
        check(ShaderWarmup.litSegments(16, 16) == 34, "終わり");
        check(ShaderWarmup.litSegments(99, 16) == 34, "超えても上限");
        check(ShaderWarmup.litSegments(0, 0) == 34, "全体が 0 なら終わり扱い");

        // 先読みの一覧: 測定の一覧にあるものだけ。control_debug (確認用) は入れない
        List<String> warm = ShaderWarmup.parseEffectNames(resource("shaderwarm_warmup.txt"));
        Set<String> all = new HashSet<>(ShaderWarmup.parseEffectNames(resource("shaderwarm_effects.txt")));
        check(warm.size() >= 10, "先読みの数が少なすぎる: " + warm.size());
        for (String name : warm) check(all.contains(name), "先読みに、パックに無いエフェクト: " + name);
        check(!warm.contains("control_debug"), "確認用は入れない");
        System.out.println("ShaderWarmupTest OK (先読み " + warm.size() + " 個)");
    }
}
