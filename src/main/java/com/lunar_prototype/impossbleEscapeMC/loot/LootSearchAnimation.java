package com.lunar_prototype.impossbleEscapeMC.loot;

/** レアリティ別の検索時間とリング色。背景・アイテム本体の見た目は扱わない。 */
final class LootSearchAnimation {
    static final int BASE_COLOR = 0xDEAC4E;
    static final int BASE_HIGHLIGHT = 0xF6D482;
    static final int EPIC_COLOR = 0xB954FF;
    static final int LEGENDARY_COLOR = 0xFFAA00;
    static final int COLOR_FADE_TICKS = 3;
    private LootSearchAnimation() { }

    record Frame(int rotation, int color, int highlight) { }

    static int durationTicks(int rarity, int skillAdjustment) {
        int base = switch (Math.clamp(rarity, 1, 5)) {
            case 2 -> 12; case 3 -> 15; case 4 -> 20; case 5 -> 26; default -> 10;
        };
        return (int) Math.clamp((long) base + skillAdjustment, 1, Integer.MAX_VALUE);
    }

    static Frame frame(int rarity, int elapsedTicks, int durationTicks) {
        int elapsed = Math.max(0, elapsedTicks);
        int duration = Math.max(1, durationTicks);
        int midpoint = duration / 2 + duration % 2;
        double progress = Math.clamp((elapsed - (double) midpoint + 1) / COLOR_FADE_TICKS, 0, 1);
        int target = rarity == 4 ? EPIC_COLOR : rarity == 5 ? LEGENDARY_COLOR : BASE_COLOR;
        if (rarity != 4 && rarity != 5) progress = 0;
        int highlight = mix(target, 0xFFFFFF, 0.35);
        return new Frame(elapsed % 8, mix(BASE_COLOR, target, progress), mix(BASE_HIGHLIGHT, highlight, progress));
    }

    private static int mix(int from, int to, double amount) {
        int out = 0;
        for (int shift : new int[]{16, 8, 0}) {
            int a = (from >> shift) & 255, b = (to >> shift) & 255;
            out |= ((int) Math.round(a + (b - a) * amount)) << shift;
        }
        return out;
    }
}
