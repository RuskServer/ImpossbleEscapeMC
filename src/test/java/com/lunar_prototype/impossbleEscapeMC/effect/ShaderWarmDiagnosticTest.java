package com.lunar_prototype.impossbleEscapeMC.effect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 停止時間の診断 ({@link ShaderWarmDiagnostic}) の計算: 返事の遅れから、最大・基準との差・最後に遅れた ping・返事なしを出す。
 * 測るエフェクトの一覧 (shaderwarm_effects.txt) が空でなく、重複がないことも確かめる
 */
public final class ShaderWarmDiagnosticTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + " (期待 " + expected + "、実際 " + actual + ")");
    }

    public static void main(String[] args) throws IOException {
        // 基準 (中央値) は、NaN を除いて数える
        near(ShaderWarmDiagnostic.median(new double[]{10, 12, 11}), 11, "中央値 (奇数)");
        near(ShaderWarmDiagnostic.median(new double[]{10, 20, Double.NaN, 14, 12}), 13, "中央値 (偶数・NaN を除く)");
        check(Double.isNaN(ShaderWarmDiagnostic.median(new double[]{Double.NaN})), "全部 NaN なら NaN");

        // 止まらなかったエフェクト: 基準 12 ms のまま
        ShaderWarmDiagnostic.Result calm = ShaderWarmDiagnostic.analyze(12, new double[]{12, 13, 11, 12, 14}, 30);
        near(calm.maxRttMs(), 14, "最大");
        near(calm.excessMs(), 2, "基準との差");
        check(calm.lastBadOffset() == -1, "遅れた ping は無い");
        check(calm.missing() == 0, "返事なしは無い");

        // 3 つ目の ping が 400 ms 遅れ、4 つ目も少し遅れた (基準 12 ms + 許容 30 ms を超える)
        ShaderWarmDiagnostic.Result stall = ShaderWarmDiagnostic.analyze(12, new double[]{12, 13, 412, 60, 14, 12}, 30);
        near(stall.maxRttMs(), 412, "最大 (停止)");
        near(stall.excessMs(), 400, "基準との差 (停止)");
        check(stall.lastBadOffset() == 3, "最後に遅れた ping は 4 つ目 (番号 3)");

        // 返事が来なかった ping は数える。最大には入れない
        ShaderWarmDiagnostic.Result lost = ShaderWarmDiagnostic.analyze(12, new double[]{12, Double.NaN, Double.NaN, 20}, 30);
        check(lost.missing() == 2, "返事なし 2 つ");
        near(lost.maxRttMs(), 20, "最大 (返事なしを除く)");

        // 1 つも返事が無い
        ShaderWarmDiagnostic.Result none = ShaderWarmDiagnostic.analyze(12, new double[]{Double.NaN, Double.NaN}, 30);
        check(Double.isNaN(none.maxRttMs()) && Double.isNaN(none.excessMs()), "返事が無ければ NaN");
        check(none.missing() == 2, "全部返事なし");

        // 基準より速い返事は、差を負にしない
        near(ShaderWarmDiagnostic.analyze(12, new double[]{5, 6}, 30).excessMs(), 0, "基準より速ければ差は 0");

        // エフェクトの一覧
        Set<String> names = new HashSet<>();
        try (InputStream in = ShaderWarmDiagnosticTest.class.getClassLoader().getResourceAsStream("shaderwarm_effects.txt")) {
            check(in != null, "shaderwarm_effects.txt が無い");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    check(line.matches("[a-z0-9_]+"), "エフェクト名は小文字・数字・_ だけ: " + line);
                    check(names.add(line), "重複: " + line);
                }
            }
        }
        check(names.size() >= 50, "エフェクトの数が少なすぎる: " + names.size());
        check(names.contains("grade") && names.contains("hit"), "grade と hit が一覧に無い");
        System.out.println("ShaderWarmDiagnosticTest OK (エフェクト " + names.size() + " 個)");
    }
}
