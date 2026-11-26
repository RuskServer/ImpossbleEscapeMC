package com.lunar_prototype.impossbleEscapeMC.effect;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.*;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(BukkitState.class)
class DeathAdmissionTest {
 @Test void dyingSpectatorIsRejectedAtAdmissionAndQueueCollection() throws Exception {
  var server=MockBukkit.mock();Object old=get(ScreenEffectService.class,null,"instance");
  try(var messages=MessageTestSupport.forwarding()) {
   var plugin=allocate(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.class);
   set(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.class,plugin,"serviceContainer",new com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer());
   set(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC.class,null,"instance",plugin);
   var player=server.addPlayer();player.setGameMode(GameMode.SPECTATOR);
   var state=new ScreenEffectState();state.deathScene=new DeathScene(player.getLocation(),true);
   state.deathScene.enter(DeathScene.Phase.FIELD,0);
   var service=allocate(ScreenEffectService.class);
   set(ScreenEffectService.class,service,"states",Map.of(player.getUniqueId(),state));
   set(ScreenEffectService.class,null,"instance",service);
   assertTrue(ScreenEffectService.isDeathSceneActive(player));
   var hideout=allocate(com.lunar_prototype.impossbleEscapeMC.modules.hideout.HideoutModule.class);
   assertFalse(hideout.visit(player));
   var raids=allocate(RaidModule.class);var raid=allocate(RaidInstance.class);
   set(RaidInstance.class,raid,"players",new HashSet<>());
   Map<String,RaidMap> maps=new HashMap<>();maps.put("target",null);
   set(RaidModule.class,raids,"maps",maps);
   set(RaidModule.class,raids,"activeRaids",Map.of("target",raid));
   set(RaidModule.class,raids,"lastCycleStartTime",Map.of("target",0L));
   set(RaidModule.class,raids,"raidQueues",new HashMap<>());
   set(RaidModule.class,raids,"queueBossBars",new HashMap<>());
   set(RaidModule.class,raids,"mapTimers",Map.of("target",600));
   assertFalse(raids.joinQueue(List.of(player),"target"));
   set(RaidModule.class,raids,"raidQueues",new HashMap<>(Map.of("target",new HashSet<>(Set.of(player.getUniqueId())))));
   var collect=RaidModule.class.getDeclaredMethod("collectQueuedParticipants",String.class);collect.setAccessible(true);
   assertEquals(List.of(),collect.invoke(raids,"target"));
   assertTrue(state.deathScene.locksPlayer());
  } finally {set(ScreenEffectService.class,null,"instance",old);MockBukkit.unmock();}
 }
}
