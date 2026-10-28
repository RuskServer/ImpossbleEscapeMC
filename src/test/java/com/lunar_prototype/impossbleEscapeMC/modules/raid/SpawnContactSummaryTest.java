package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.HashSet;
import java.util.List;
import static com.lunar_prototype.impossbleEscapeMC.modules.raid.SpawnContactSummary.*;

public final class SpawnContactSummaryTest {
    public static void main(String[] args) {
        for (int count=0;count<=64;count++) {
            var order=comparisons(count);
            check(order.size()==count*(count-1)/2,"all unordered pairs included for "+count);
            check(new HashSet<>(order).size()==order.size(),"no duplicate comparison");
            for(var c:order)check(c.a()>=0&&c.a()<c.b()&&c.b()<count,"valid pair indices");
            if(count>=2) {
                var seen=new HashSet<Integer>();
                // 偶数なら最初の1ラウンド、奇数なら2ラウンドで全地点を検査。
                int prefix=count%2==0?count/2:count-1;
                for(var c:order.subList(0,Math.min(prefix,order.size()))){seen.add(c.a());seen.add(c.b());}
                check(seen.size()==count,"every spawn participates before later rounds");
            }
        }
        check(comparisons(64).size()==2016,"64-spawn maps must not be cut off at old 256-pair limit");
        var pairs=List.of(new Pair(4,15,100,35.0,""),new Pair(4,90,200,12.0,""),
                new Pair(15,90,100,null,"経路探索の上限"));
        var a=site(4,4,pairs);
        check(a.earliest().seconds()==12&&a.opponent()==90,"fastest time need not be nearest spawn");
        check(a.unknown()==1,"missing pairs count as unknown even when other pairs exist");
        var b=site(90,4,pairs);
        check(b.opponent()==4&&b.unknown()==2,"symmetric result includes explicit and missing failures");
        var missing=site(200,4,pairs);
        check(missing.earliest()==null&&missing.unknown()==3,"late unprocessed spawn cannot appear safe");
        var partial=site(4,2,List.of(new Pair(4,90,10,45.0,"探索範囲に読込失敗あり")));
        check(partial.earliest().seconds()==45&&partial.unknown()==1,"partial timing remains provisional");
        var direct=site(4,2,List.of(new Pair(4,90,100,0.0,"")));
        check(direct.direct()==1&&direct.unknown()==0&&direct.earliest().seconds()==0,"initial LOS is zero seconds");
        check(site(4,1,List.of()).unknown()==0,"single spawn has no missing opponent");
        var tie=site(4,3,List.of(new Pair(4,90,50,20.0,""),new Pair(4,15,50,20.0,"")));
        check(tie.opponent()==15,"stable target when times tie");
        var reasons=unknownReasons(4,pairs);
        check(reasons.get("経路探索の上限")==1,"explicit failure reason visible outside hover");
        check(reasons.values().stream().mapToInt(Integer::intValue).sum()==4,"reason counts include three unprocessed pairs");
        check(unknownReasons(2,List.of(new Pair(4,90,10,45.0,"探索範囲に読込失敗あり")))
                .containsKey("探索範囲に読込失敗あり"),"provisional timing retains failure reason");
        check(unknownReasons(2,List.of(new Pair(4,90,10,9.0,""))).isEmpty(),"confirmed pair has no unknown reason");
        System.out.println("SpawnContactSummaryTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
