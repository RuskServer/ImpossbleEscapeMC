package com.lunar_prototype.impossbleEscapeMC.modules.quest;

import com.lunar_prototype.impossbleEscapeMC.modules.boss.BossIdentity;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.bukkit.Location;
import org.bukkit.entity.Zombie;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BossQuestClassificationTest {
    @Test void questUsesExplicitRoleEvenAfterControllerRemoval() {
        var server=MockBukkit.mock();
        try {
            var world=server.addSimpleWorld("test");var mob=world.spawn(new Location(world,0,10,0),Zombie.class);
            assertEquals("ZOMBIE",QuestListener.entityType(mob));
            new BossIdentity("boss",UUID.randomUUID(),"guard",false).apply(mob);
            assertEquals("SCAV",QuestListener.entityType(mob));
            new BossIdentity("boss",UUID.randomUUID(),"leader",true).apply(mob);
            assertEquals("BOSS",QuestListener.entityType(mob));assertEquals("PMC",QuestListener.entityType(server.addPlayer()));
        } finally {MockBukkit.unmock();}
    }
}
