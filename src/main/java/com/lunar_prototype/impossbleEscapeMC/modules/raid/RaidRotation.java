package com.lunar_prototype.impossbleEscapeMC.modules.raid;

/**
 * マップの周期の並び (サーバーなしで確かめられる。RaidRotationTest)。
 * <p>
 * 周期 (cycle 秒) をマップの数で等分し、並びの index 番目のマップは「回転の時刻」(0..cycle-1 を 1 秒ずつ回る) が
 * index × 間隔 の時に始まる枠を持つ。等分してずらしておけば、途中参加の枠 (周期の始めの何分か) が間隔より長い限り、
 * いつ来てもどこかのマップに入れる。
 * <p>
 * 周期が自然に終わった時は、次の周期の長さを最大 {@link #MAX_ADJUST} 秒だけ伸び縮みさせて枠に寄せる
 * (レイドの長さを大きく変えずに、手で始め直した・再起動したなどでずれた並びを数周で戻す)
 */
final class RaidRotation {

    /** 1 周期で寄せる量の上限 (秒) */
    static final int MAX_ADJUST = 120;

    private RaidRotation() {
    }

    /** マップの開始の間隔 (秒) */
    static int spacing(int cycle, int maps) {
        return cycle / Math.max(1, maps);
    }

    /** index 番目のマップの枠の始まる回転の時刻 */
    static int slot(int cycle, int maps, int index) {
        return Math.floorMod(index * spacing(cycle, maps), cycle);
    }

    /**
     * 回転の時刻が elapsed の時に、枠どおりに並べた index 番目のマップの残り時間 (次に始まるまで。1..cycle。
     * cycle は「今始まった」)
     */
    static int timerOnSlot(int cycle, int maps, int index, int elapsed) {
        int until = Math.floorMod(slot(cycle, maps, index) - elapsed, cycle);
        return until == 0 ? cycle : until;
    }

    /**
     * index 番目のマップの周期が今 (回転の時刻 elapsed) 自然に終わって次が始まる時の、次の周期の長さ。
     * 枠より早く始まったら伸ばし、遅れたら縮める (どちらも {@link #MAX_ADJUST} まで)
     */
    static int nextLength(int cycle, int maps, int index, int elapsed) {
        int until = Math.floorMod(slot(cycle, maps, index) - elapsed, cycle);
        // until が小さい = 枠はこの後 = 早く始まった (伸ばす)。cycle に近い = 枠はもう過ぎた = 遅れた (縮める)
        int early = until <= cycle / 2 ? until : until - cycle;
        return cycle + Math.max(-MAX_ADJUST, Math.min(MAX_ADJUST, early));
    }
}
