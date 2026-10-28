package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import java.util.List;

public final class SpawnSiteAssessmentTest {
    private static RaidMap.SpawnEntry point(int id,double y){return new RaidMap.SpawnEntry(id,0,y,0,0,0);}
    public static void main(String[] args) {
        var site=point(1,-34);var upper=point(2,1);var nearer=point(3,-30);
        var all=List.of(site,upper,nearer);
        check(SpawnSiteAssessment.upperReference(site,all,0,false,false,3)==nearer,"select nearest qualifying upper level");
        check(SpawnSiteAssessment.upperReference(site,List.of(site,point(4,-31)),0,false,false,3)!=null,"inclusive height boundary");
        check(SpawnSiteAssessment.upperReference(site,List.of(site,point(4,-31.01)),0,false,false,3)==null,"small height change is not underground");
        check(SpawnSiteAssessment.upperReference(site,all,1,false,false,3)==null,"requires completely zero exits");
        check(SpawnSiteAssessment.upperReference(site,all,0,true,false,3)==null,"unknown terrain cannot justify classification");
        check(SpawnSiteAssessment.upperReference(site,all,0,false,true,3)==null,"unsafe footing must not be exempted");
        check(SpawnSiteAssessment.upperReference(site,List.of(site,point(4,-34)),0,false,false,3)==null,"same height is constrained, not underground");
        check(SpawnSiteAssessment.upperReference(site,List.of(site),0,false,false,3)==null,"needs another spawn for comparison");
        check(SpawnSiteAssessment.upperReference(upper,all,0,false,false,3)==null,"highest site is not a lower-level candidate");
        System.out.println("SpawnSiteAssessmentTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
