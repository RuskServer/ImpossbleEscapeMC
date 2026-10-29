package com.lunar_prototype.impossbleEscapeMC.modules.inventory;

/** スタックへの追加入力と、新しいスタックのコストを分けて、受け取れる個数だけ計画する。 */
public final class CostInsertion {
    private CostInsertion() {}
    public static int[] plan(int capacity, int used, int cost, int amount, int maxStack, int[] rooms, boolean[] empty) {
        if (rooms.length != empty.length || maxStack <= 0 || cost < 0 || amount < 0) throw new IllegalArgumentException();
        int[] moves = new int[rooms.length];
        for (int i = 0; i < rooms.length && amount > 0; i++) {
            if (empty[i]) continue;
            moves[i] = Math.min(amount, Math.max(0, rooms[i]));
            amount -= moves[i];
        }
        for (int i = 0; i < rooms.length && amount > 0; i++) {
            if (!empty[i] || (long) used + cost > capacity) continue;
            moves[i] = Math.min(amount, maxStack);
            amount -= moves[i];
            used += cost;
        }
        return moves;
    }
}
