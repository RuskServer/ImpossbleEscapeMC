package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;

public final class RaidRadioZoneTest {
    public static void main(String[] args) {
        var zone=new RaidRadioZone("office","factory","オフィス",-10,-34,5,11,-29,16);
        check(zone.contains(-10,-34,5,0)&&zone.contains(10.99,-29.01,15.99,0),"inclusive selected blocks with exclusive outer faces");
        check(!zone.contains(11,-32,10,0)&&!zone.contains(0,-29,10,0),"outside selected x/y faces");
        check(zone.contains(11.5,-32,10,2),"small horizontal boundary jitter retains current zone");
        check(!zone.contains(0,-28,10,2),"vertical hysteresis must not include another storey");
        Gson gson=new Gson();
        var map=gson.fromJson("{\"mapId\":\"factory\",\"worldName\":\"world\",\"spawnPoints\":[]}",RaidMap.class);
        check(map.highThreatZones().isEmpty(),"old maps require no migration or default zones");
        check(map.addHighThreatZone(zone)&&!map.addHighThreatZone(zone),"unique zone ids");
        map=gson.fromJson(gson.toJson(map),RaidMap.class);
        check(map.highThreatZones().equals(java.util.List.of(zone)),"WorldEdit bounds and label persist with map");
        check(map.removeHighThreatZone("office")&&map.highThreatZones().isEmpty(),"remove persisted zone");
        boolean invalid=false;try{new RaidRadioZone("bad","factory","bad",0,0,0,0,1,1);}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"invalid bounds rejected");
        System.out.println("RaidRadioZoneTest OK");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
