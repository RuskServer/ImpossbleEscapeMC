package com.lunar_prototype.impossbleEscapeMC.item;

import java.util.Arrays;

/** 占有スロットの割り当てと、容量のゲージの目盛りを確かめる。サーバーは要らない */
public final class CostLayoutTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** costs: 0 は空き */
    private static int[] layout(int firstMain, int... costs) {
        boolean[] free = new boolean[costs.length];
        for (int i = 0; i < costs.length; i++) free[i] = costs[i] == 0;
        return CostSlotManager.placeholderOwners(costs, free, firstMain);
    }

    public static void main(String[] args) {
        // メインの段の物の分は、すぐ後ろに置く
        int[] owners = layout(2, 1, 0, 3, 0, 0, 0, 0);
        check(Arrays.equals(owners, new int[]{-1, -1, -1, 2, 2, -1, -1}), "right after " + Arrays.toString(owners));
        // 後ろが埋まっていれば、すぐ前 (ホットバーには行かない)
        owners = layout(2, 0, 0, 0, 2, 1, 1);
        check(Arrays.equals(owners, new int[]{-1, -1, 3, -1, -1, -1}), "just before " + Arrays.toString(owners));
        // 近い方から、同じ距離なら後ろ
        owners = layout(0, 0, 0, 3, 0, 1, 0);
        check(Arrays.equals(owners, new int[]{-1, 2, -1, 2, -1, -1}), "nearest " + Arrays.toString(owners));
        // ホットバーの物の分は右下 (末尾) から
        owners = layout(3, 2, 1, 0, 0, 1, 0, 0);
        check(Arrays.equals(owners, new int[]{-1, -1, -1, -1, -1, -1, 0}), "hotbar owner at the end " + Arrays.toString(owners));
        // メインの段に空きが足りなければ、残りは末尾から (ホットバーの空きも使う)
        owners = layout(2, 0, 1, 3, 0);
        check(Arrays.equals(owners, new int[]{2, -1, -1, 2}), "overflow to hotbar " + Arrays.toString(owners));
        // 先にある物から近い空きを取る (並んだ 2 つの大きい物は、その後ろに同じ順で並ぶ)
        owners = layout(0, 2, 2, 0, 0);
        check(Arrays.equals(owners, new int[]{-1, -1, 0, 1}), "first item first " + Arrays.toString(owners));
        // 今の占有スロットも空きとして扱う (物の場所だけで決まる)
        boolean[] free = {false, true, true};
        check(Arrays.equals(CostSlotManager.placeholderOwners(new int[]{2, 0, 0}, free, 0), new int[]{-1, 0, -1}), "stable");

        // ゲージ: 空は 0、少しでも使えば 1 以上、満杯の時だけ最大
        check(CapacityGauge.level(0, 17) == 0, "empty");
        check(CapacityGauge.level(1, 40) == 1, "a little");
        check(CapacityGauge.level(16, 17) == CapacityGauge.LEVELS - 1, "almost full");
        check(CapacityGauge.level(17, 17) == CapacityGauge.LEVELS, "full");
        check(CapacityGauge.level(20, 17) == CapacityGauge.LEVELS, "over");
        check(CapacityGauge.level(3, 0) == 0, "no capacity");
        System.out.println("CostLayoutTest: placeholder layout and gauge levels passed");
    }
}
