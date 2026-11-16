package com.lunar_prototype.impossbleEscapeMC.modules.boss;

import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBossPost;
import java.util.*;

public final class BossPolicyTest {
    @Test
    @org.junit.jupiter.api.DisplayName("unsafe/absent players do not reroll or consume")
    void unsafeAbsentPlayersDoNotRerollOrConsume() throws Exception {
        var selected=new BossRoll(.125,.124999,true,false);
        for(int i=0;i<500;i++)check(selected.waiting(),"unsafe/absent players do not reroll or consume");
        check(selected.consume()&&!selected.waiting()&&!selected.consume(),"one attempt only after spawn, death or failure");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("upper chance bound excluded")
    void upperChanceBoundExcluded() throws Exception {
        check(!new BossRoll(.125,.125,true,false).waiting(),"upper chance bound excluded");
        check(!new BossRoll(1,0,true,true).waiting(),"restored raids cannot reroll or duplicate gear");
        check(!new BossRoll(1,0,false,false).waiting(),"disabled map or incomplete registration");
        check(!new BossRoll(0,0,true,false).waiting(),"zero chance");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("invalid chance disables spawning")
    void invalidChanceDisablesSpawning() throws Exception {
        for(double chance:new double[]{-1,2,Double.NaN,Double.POSITIVE_INFINITY})check(!new BossRoll(chance,0,true,false).waiting(),"invalid chance disables spawning");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("12.5 percent threshold")
    void percentThreshold() throws Exception {
        int count=0;
        for(int i=0;i<800;i++)if(new BossRoll(.125,i/800.0,true,false).waiting())count++;
        check(count==100,"12.5 percent threshold");
    }

    @Test
    @org.junit.jupiter.api.DisplayName("rollback every previously spawned member")
    void rollbackEveryPreviouslySpawnedMember() throws Exception {
        for(int fail=0;fail<3;fail++) {
            final int failure=fail;List<UUID> made=new ArrayList<>(),removed=new ArrayList<>();
            try {
                BossEncounter.spawnAll(3,i->{if(i==failure)return null;UUID id=UUID.randomUUID();made.add(id);return id;},removed::add);
                throw new AssertionError("missing equipment must fail the full group");
            }catch(IllegalStateException expected){check(made.equals(removed),"rollback every previously spawned member");}
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("one boss and two guards")
    void oneBossAndTwoGuards() throws Exception {
        var all=BossEncounter.spawnAll(3,i->new UUID(0,i+1),id->{throw new AssertionError("success must not rollback");});
        check(all.size()==3&&new HashSet<>(all).size()==3,"one boss and two guards");
    }
    private static void check(boolean value,String reason){ org.junit.jupiter.api.Assertions.assertTrue(value, reason); }
}
