package com.lunar_prototype.impossbleEscapeMC.modules.hideout;
import java.util.List;
import java.util.Map;
public final class HideoutMaterialNeedsTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        var uses=Map.of("bolts",List.of(new HideoutMaterialNeeds.Use("ワークベンチ",2,4,false),new HideoutMaterialNeeds.Use("医療設備",1,2,true)));
        var need=HideoutMaterialNeeds.withStock(uses,Map.of("bolts",2)).get("bolts");
        check(need.required()==6&&need.have()==2&&need.shortage()==4&&need.missing(),"shared stock is counted once against combined demand");
        check(!HideoutMaterialNeeds.withStock(uses,Map.of("bolts",6)).get("bolts").missing(),"exact stock secured");
        check(!HideoutMaterialNeeds.withStock(uses,Map.of("bolts",99)).get("bolts").missing(),"excess stock secured");
        var next=HideoutMaterialNeeds.demands(f->f==HideoutFacility.STASH?1:0,f->f==HideoutFacility.STASH?4:0);
        check(next.get("bolts").getFirst().level()==2&&next.get("bolts").getFirst().amount()==8,"only next stash upgrade");
        check(!next.containsKey("wrench"),"future level materials excluded");
        check(HideoutMaterialNeeds.demands(f->4,f->4).isEmpty(),"maxed facilities excluded");
        check(HideoutMaterialNeeds.prerequisiteMissing(HideoutFacility.MEDICAL,1,f->0),"unmet prerequisite recognized");
        check(!HideoutMaterialNeeds.prerequisiteMissing(HideoutFacility.MEDICAL,1,f->f==HideoutFacility.WORKBENCH?1:0),"prerequisite resolved");
        check(HideoutMaterialNeeds.demands(f->0,f->f==HideoutFacility.MEDICAL?4:0).isEmpty(),"prerequisite-only facility must not invent material requirements");
        try{need.uses().clear();throw new AssertionError("mutable snapshot");}catch(UnsupportedOperationException expected){}
        System.out.println("PASS: next upgrade aggregation, shared counts, secured stock, prerequisites and immutable snapshots");
    }
}
