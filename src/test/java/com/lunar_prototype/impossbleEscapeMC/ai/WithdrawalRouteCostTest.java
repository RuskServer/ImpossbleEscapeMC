package com.lunar_prototype.impossbleEscapeMC.ai;

import java.util.List;
import static com.lunar_prototype.impossbleEscapeMC.ai.WithdrawalRouteCost.Sample;

public final class WithdrawalRouteCostTest {
    public static void main(String[] args) {
        var quickCover=List.of(s(0,true),s(1,true),s(2,false),s(4,false),s(8,false));
        var exposedShortcut=List.of(s(0,true),s(1,true),s(2,true),s(3,true),s(4,false));
        check(cost(quickCover)>cost(exposedShortcut),"early LOS break must beat a short exposed route");
        var sheltered=List.of(s(0,false),s(2,false),s(4,false),s(6,false));
        var reopen=List.of(s(0,false),s(2,false),s(4,true),s(6,false));
        check(cost(sheltered)>cost(reopen),"re-exposure after reaching cover is penalized");
        var danger=List.of(new Sample(0,true,3,20),new Sample(1,true,3,20),new Sample(2,false,3,20),new Sample(4,false,3,20),new Sample(8,false,3,20));
        check(cost(quickCover)>cost(danger),"dangerous route is penalized");
        var closer=List.of(new Sample(0,true,0,20),new Sample(1,true,0,18),new Sample(2,false,0,15),new Sample(4,false,0,16),new Sample(8,false,0,20));
        check(cost(quickCover)>cost(closer),"running toward threat before hiding is penalized");
        check(WithdrawalRouteCost.score(quickCover,20,3)>WithdrawalRouteCost.score(quickCover,20,1),"dead end loses to a route with exits");
        var progress=new WithdrawalRouteCost.Progress(100);
        check(!progress.stuck(100,16),"initial plan");
        check(!progress.stuck(121,12),"path progress remains valid even when detouring");
        check(!progress.stuck(143,8),"committed route can continue beyond a short withdraw timer");
        check(progress.stuck(167,8),"no progress for 24 ticks triggers replan");
        progress=new WithdrawalRouteCost.Progress(0);
        for(int tick=0;tick<160;tick+=3)check(!progress.stuck(tick,100-tick*.5),"ongoing progress");
        check(progress.stuck(160,10),"hard route lifetime prevents endless commitment");
        System.out.println("WithdrawalRouteCostTest OK");
    }
    private static Sample s(double d,boolean exposed){return new Sample(d,exposed,0,20);}
    private static double cost(List<Sample> s){return WithdrawalRouteCost.score(s,20,3);}
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
