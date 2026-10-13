package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 1 人分のレイド結果 (リザルトの画面に出す物をまとめた写し)。レイドが終わった時に {@code RaidInstance} が作る
 *
 * @param mapName     マップの名前
 * @param survived    脱出できたか
 * @param outcome     結果の名前 (脱出成功・死亡・MIA・離脱)
 * @param extractName 脱出した地点 (脱出できなかったら null)
 * @param raidMillis  レイドにいた時間
 * @param exp         獲得した経験値の内訳
 * @param levelBefore このレイドの経験値を足す前のレベル
 * @param expBefore   このレイドの経験値を足す前の経験値
 * @param pending     経験値はまだ足されていない (レイド全体の終了時に足される)
 * @param loot        持ち帰った物 (FIR、価値の高い順)
 * @param lostValue   失った物 (持ち込んで、死亡・MIA で失った物) の価値
 * @param kills       倒した相手 (時刻順)
 * @param death       自分を倒した相手 (死亡していなければ null)
 */
public record RaidReport(String mapName, boolean survived, String outcome, String extractName, long raidMillis,
                         List<ExpLine> exp, int levelBefore, long expBefore, boolean pending,
                         List<LootStack> loot, long lostValue, List<Kill> kills, Kill death) {

    /** 経験値の内訳の 1 行 */
    public record ExpLine(UiIcon icon, String label, String note, long amount) {
    }

    /** 持ち帰った物 1 つ (欄 1 つ分) と、その価値 (売値 x 個数)、クエストに使えるか */
    public record LootStack(ItemStack item, long value, boolean questUsable) {
    }

    public enum Kind { SCAV, PMC, BOSS }

    /**
     * 倒した (倒された) 記録
     *
     * @param atMillis レイドが始まってからの時間
     * @param weapon   撃った武器 (見本。分からなければ null)
     * @param part     当たった部位 (頭・胴・腕・脚。分からなければ null)
     * @param distance 距離 (メートル。分からなければ -1)
     */
    public record Kill(long atMillis, String name, Kind kind, ItemStack weapon, String weaponName, String ammoName,
                       int distance, String part) {
        public boolean headshot() {
            return "頭".equals(part);
        }
    }

    public long expTotal() {
        return exp.stream().mapToLong(ExpLine::amount).sum();
    }

    public long foundValue() {
        return loot.stream().mapToLong(LootStack::value).sum();
    }

    public long profit() {
        return foundValue() - lostValue;
    }

    /**
     * 評価ランク: 脱出できなければ D。脱出したら 総利益 (千₽ ごとに 1 点)・倒した数 (SCAV 5 点、PMC 20 点、BOSS 30 点) の合計で
     * S (250 点以上)・A (120)・B (50)・C
     */
    public String rank() {
        if (!survived) return "D";
        return rankOf(score());
    }

    public long score() {
        long points = Math.max(0, profit()) / 1000;
        for (Kill k : kills) {
            points += switch (k.kind()) {
                case SCAV -> 5;
                case PMC -> 20;
                case BOSS -> 30;
            };
        }
        return points;
    }

    static String rankOf(long score) {
        return score >= 250 ? "S" : score >= 120 ? "A" : score >= 50 ? "B" : "C";
    }

    /** 次のランクまでの点 (S・D なら -1) */
    public long pointsToNextRank() {
        if (!survived) return -1;
        long s = score();
        return s >= 250 ? -1 : s >= 120 ? 250 - s : s >= 50 ? 120 - s : 50 - s;
    }
}
