package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossIdentity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.*;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BossKillClassificationTest {
    @Test void onlyExplicitLeaderIsBossEvenWithoutController() {
        var server=MockBukkit.mock();
        try {
            var world=server.addSimpleWorld("test");
            var mob=world.spawn(new Location(world,0,10,0),Zombie.class);
            assertEquals(RaidReport.Kind.SCAV,RaidKillTracker.kindOf(mob));
            var guard=new BossIdentity("commander",UUID.randomUUID(),"guard",false);
            guard.apply(mob);assertEquals(guard,BossIdentity.read(mob));
            assertEquals(RaidReport.Kind.SCAV,RaidKillTracker.kindOf(mob));
            var leader=new BossIdentity("commander",guard.encounterId(),"leader",true);
            leader.apply(mob);assertEquals(RaidReport.Kind.BOSS,RaidKillTracker.kindOf(mob));
            assertEquals(RaidReport.Kind.PMC,RaidKillTracker.kindOf(server.addPlayer()));
            mob.getPersistentDataContainer().set(new NamespacedKey("iem","boss_encounter"),PersistentDataType.STRING,"corrupt");
            assertNull(BossIdentity.read(mob));assertEquals(RaidReport.Kind.SCAV,RaidKillTracker.kindOf(mob));
        } finally {MockBukkit.unmock();}
    }
}
