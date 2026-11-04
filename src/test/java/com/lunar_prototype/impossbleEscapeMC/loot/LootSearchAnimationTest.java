package com.lunar_prototype.impossbleEscapeMC.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** 実際の時間・色計算と、サーチ中の品物の差し替え判定を検証する。 */
public final class LootSearchAnimationTest {
    private static void check(boolean condition, String message) {if (!condition) throw new AssertionError(message);}
    private static final class Stack extends ItemStack {
        private final String identity;
        private final Material type;
        private final int amount;
        Stack(String identity, Material type, int amount) {super();this.identity=identity;this.type=type;this.amount=amount;}
        @Override public Material getType() {return type;}
        @Override public int getAmount() {return amount;}
        @Override public boolean isSimilar(ItemStack other) {
            return other instanceof Stack stack && stack.type==type && stack.identity.equals(identity);
        }
    }
    public static void main(String[] args) {
        int[] duration={10,12,15,20,26};
        for(int rarity=1;rarity<=5;rarity++) {
            check(LootSearchAnimation.durationTicks(rarity,0)==duration[rarity-1],"rarity duration");
            check(LootSearchAnimation.durationTicks(rarity,-2)==duration[rarity-1]-2,"both existing search skills apply additively");
            check(LootSearchAnimation.durationTicks(rarity,-1000)==1,"search duration never becomes zero");
            for(int adjustment:new int[]{0,-1,-2,-1000}) {
                int ticks=LootSearchAnimation.durationTicks(rarity,adjustment);
                int midpoint=ticks/2+ticks%2;
                for(int elapsed=0;elapsed<ticks;elapsed++) {
                    var frame=LootSearchAnimation.frame(rarity,elapsed,ticks);
                    check(frame.rotation()==elapsed%8,"rotation remains continuous during color transition");
                    boolean colored=rarity>=4&&elapsed>=midpoint;
                    check((frame.color()!=LootSearchAnimation.BASE_COLOR)==colored,"high rarity preview starts only at actual duration midpoint");
                    if(!colored)check(frame.highlight()==LootSearchAnimation.BASE_HIGHLIGHT,"early ring and all lower rarities retain original palette");
                    check((frame.color()&0xFF000000)==0&&(frame.highlight()&0xFF000000)==0,"valid RGB tints");
                }
                if(ticks>4&&rarity>=4) {
                    int target=rarity==4?LootSearchAnimation.EPIC_COLOR:LootSearchAnimation.LEGENDARY_COLOR;
                    var start=LootSearchAnimation.frame(rarity,midpoint,ticks);
                    var next=LootSearchAnimation.frame(rarity,midpoint+1,ticks);
                    var full=LootSearchAnimation.frame(rarity,midpoint+2,ticks);
                    check(start.color()!=target&&next.color()!=start.color()&&full.color()==target,"three tick transition reaches existing reveal color");
                }
            }
        }
        check(LootSearchAnimation.durationTicks(-1,0)==10&&LootSearchAnimation.durationTicks(99,0)==26,"catalog rarity clamps to valid range");
        check(LootSearchAnimation.durationTicks(5,Integer.MAX_VALUE)==Integer.MAX_VALUE,"adjustments do not overflow");
        check(LootSearchAnimation.frame(4,-100,20).rotation()==0,"negative elapsed cannot select an invalid asset");
        var expected=new Stack("legendary:a",Material.PAPER,5);
        check(SearchGUI.sameSearchTarget(expected,new Stack("legendary:a",Material.PAPER,1)),"another player removing part of the same stack does not restart it");
        check(!SearchGUI.sameSearchTarget(expected,new Stack("legendary:b",Material.PAPER,5)),"replacement metadata or identity cannot inherit progress");
        check(!SearchGUI.sameSearchTarget(expected,new Stack("legendary:a",Material.DIAMOND,5)),"replacement material cannot inherit progress");
        check(!SearchGUI.sameSearchTarget(expected,null)&&!SearchGUI.sameSearchTarget(expected,new Stack("legendary:a",Material.AIR,1)),"removed item cancels without revealing");
        check(!SearchGUI.sameSearchTarget(expected,new Stack("legendary:a",Material.PAPER,0)),"zero-amount stack cannot reveal");
        check(expected.getAmount()==5,"target comparison does not mutate live contents");
        System.out.println("LootSearchAnimationTest OK: rarity timings, skill-adjusted midpoint, three-tick palette transition, rotation and changed-target rejection");
    }
}
