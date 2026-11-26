package com.lunar_prototype.impossbleEscapeMC.review;

import com.google.gson.Gson;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.item.definition.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.registry.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.stats.BackpackStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class DataProtectionTest {
 @TempDir Path directory;
 private ImpossbleEscapeMC plugin() throws Exception {
  var plugin=allocate(ImpossbleEscapeMC.class);
  set(JavaPlugin.class,plugin,"logger",Logger.getLogger("review-repro"));
  set(JavaPlugin.class,plugin,"dataFolder",directory.toFile());return plugin;
 }
 @org.junit.jupiter.params.ParameterizedTest
 @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
 void unreadableBackpackCannotBeOpenedOrOverwritten(boolean binary) throws Exception {
  MockBukkit.mock();
  @SuppressWarnings("unchecked") var registry=(Map<String,ItemDefinition>)get(ItemRegistry.class,null,"ITEM_MAP");
  String id="review_corrupt_backpack";
  try {
   var definition=new ItemDefinition();definition.id=id;definition.type="BACKPACK";
   definition.backpackStats=new BackpackStats();definition.backpackStats.size=9;registry.put(id,definition);
   var item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
   byte[] corrupt={9,8,7};meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID,PDCKeys.STRING,id);
   if(binary) meta.getPersistentDataContainer().set(PDCKeys.BACKPACK_DATA,PDCKeys.BYTE_ARRAY,corrupt);
   else meta.getPersistentDataContainer().set(PDCKeys.BACKPACK_DATA,PDCKeys.STRING,"not-base64");
   item.setItemMeta(meta);
   var module=new BackpackModule(plugin(),null,()->null,null);
   var loaded=module.loadBackpackInventory(item);
   assertNull(loaded);
   module.saveBackpackInventory(item,org.bukkit.Bukkit.createInventory(null,9));
   if(binary) assertArrayEquals(corrupt,item.getItemMeta().getPersistentDataContainer().get(PDCKeys.BACKPACK_DATA,PDCKeys.BYTE_ARRAY));
   else assertEquals("not-base64",item.getItemMeta().getPersistentDataContainer().get(PDCKeys.BACKPACK_DATA,PDCKeys.STRING));
   try(var messages=com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport.forwarding()) {
    var player=((org.mockbukkit.mockbukkit.ServerMock)org.bukkit.Bukkit.getServer()).addPlayer();
    assertFalse(module.openBackpack(player,item));
    assertNull(player.getOpenInventory().getTopInventory());
   }
  } finally {registry.remove(id);MockBukkit.unmock();}
 }
 @Test void unreadableSellerRejectsCreditWithoutChangingSource() throws Exception {
  MockBukkit.mock();
  try {
   UUID id=UUID.randomUUID();var data=new PlayerData(id);data.setBalance(42);
   var module=new PlayerDataModule(plugin(),null);Path file=directory.resolve("userdata").resolve(id+".json");
   Files.writeString(file,"{broken-json");
   var economy=new EconomyModule(module);
   assertFalse(economy.canTransact(id));
   assertThrows(IllegalStateException.class,()->economy.deposit(id,1000));
   assertFalse(economy.withdraw(id,1));
   assertFalse(economy.has(id,1));
   @SuppressWarnings("unchecked") var failed=(Set<UUID>)get(PlayerDataModule.class,module,"loadFailed");
   assertTrue(failed.contains(id));assertEquals("{broken-json",Files.readString(file));
   var login=new AsyncPlayerPreLoginEvent("seller",java.net.InetAddress.getLoopbackAddress(),id);
   module.onAsyncPreLogin(login);
   assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,login.getLoginResult());
   Files.writeString(file,new Gson().toJson(data));
   assertEquals(42,economy.getBalance(id),"repaired data must retain its original balance");
  } finally {MockBukkit.unmock();}
 }
 @Test void marketRejectsUnreadableSellerBeforeChargingBuyerOrRemovingListing() throws Exception {
  var server=MockBukkit.mock();
  try(var messages=com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport.forwarding()) {
   var buyer=server.addPlayer();UUID seller=UUID.randomUUID();
   var data=new PlayerDataModule(plugin(),null);Files.writeString(directory.resolve("userdata").resolve(seller+".json"),"{broken-json");
   var economy=new EconomyModule(data);data.getPlayerData(buyer.getUniqueId()).setBalance(5000);
   var listing=new com.lunar_prototype.impossbleEscapeMC.modules.market.MarketListing(UUID.randomUUID(),seller,"seller","not-decoded",1000,123);
   var market=allocate(com.lunar_prototype.impossbleEscapeMC.modules.market.MarketModule.class);
   set(market.getClass(),market,"loaded",true);
   var raids=allocate(com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule.class);
   set(raids.getClass(),raids,"activeRaids",new HashMap<>());
   set(market.getClass(),market,"raids",raids);
   set(market.getClass(),market,"listings",new HashMap<>(Map.of(listing.getId(),listing)));
   var gui=new com.lunar_prototype.impossbleEscapeMC.modules.market.MarketMainGUI(buyer,market,MockBukkit.createMockPlugin(),economy);
   var buy=gui.getClass().getDeclaredMethod("handleListingInteraction",listing.getClass());buy.setAccessible(true);buy.invoke(gui,listing);
   assertEquals(5000,economy.getBalance(buyer.getUniqueId()));
   assertSame(listing,market.getListing(listing.getId()));
   assertTrue(buyer.getInventory().isEmpty());
   assertEquals("{broken-json",Files.readString(directory.resolve("userdata").resolve(seller+".json")));
  } finally {MockBukkit.unmock();}
 }

}
