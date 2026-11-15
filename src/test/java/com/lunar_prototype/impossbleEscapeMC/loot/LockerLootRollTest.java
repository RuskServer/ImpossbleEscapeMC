package com.lunar_prototype.impossbleEscapeMC.loot;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidMap;
import java.util.HashMap;
import java.util.Objects;

public final class LockerLootRollTest {
    @Test
    @org.junit.jupiter.api.DisplayName("only requested crate types")
    void onlyRequestedCrateTypes() throws Exception {
        var count=new HashMap<String,Integer>();
        int empty=0;
        for(int i=0;i<120000;i++) {
            String result=LockerLootRoll.select("locker-"+i,7);
            if(result==null)empty++;else {
                check(LockerLootRoll.CRATES.contains(result),"only requested crate types");count.merge(result,1,Integer::sum);
            }
        }
        check(Math.abs(empty/120000.0-.75)<.01,"approximately three quarters empty");
        for(String crate:LockerLootRoll.CRATES)check(Math.abs(count.getOrDefault(crate,0)/120000.0-1.0/12)<.005,"equal crate probabilities: "+crate);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("reload retains roll, including empty results")
    void reloadRetainsRollIncludingEmptyResults() throws Exception {
        var spec=new RaidMap.LootLocker("stable-locker",-17,-34,208,2);
        spec.generation=12;
        String first=LockerLootRoll.select(spec.id,spec.generation);
        var saved=new Gson().fromJson(new Gson().toJson(spec),RaidMap.LootLocker.class);
        check(Objects.equals(first,LockerLootRoll.select(saved.id,saved.generation)),"reload retains roll, including empty results");
        var outcomes=new java.util.HashSet<String>();
        for(int generation=0;generation<100;generation++)outcomes.add(LockerLootRoll.select(spec.id,generation));
        check(outcomes.size()==4,"refill generations can choose empty or each crate");
    }
    private static void check(boolean value,String reason){ org.junit.jupiter.api.Assertions.assertTrue(value, reason); }
}
