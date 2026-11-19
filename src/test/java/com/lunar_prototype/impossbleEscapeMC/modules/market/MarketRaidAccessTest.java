package com.lunar_prototype.impossbleEscapeMC.modules.market;

import com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport;
import com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.testing.BukkitState;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static com.lunar_prototype.impossbleEscapeMC.testing.FixtureObjects.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(BukkitState.class)
class MarketRaidAccessTest {
    static class Raids extends RaidModule {
        boolean active;
        Raids() { super(null,null,null,null,null,()->null,null,null,()->null,()->null,()->null,null,null,null); }
        @Override public boolean isInRaid(Player player) { return active; }
    }

    @Test void raidBlocksCommandAndBothTransactionCallbacksAfterOpening() throws Exception {
        var server = MockBukkit.mock();
        try (var messages = MessageTestSupport.forwarding()) {
            var player = server.addPlayer(); var plugin = MockBukkit.createMockPlugin();
            var raids = allocate(Raids.class);
            var market = allocate(MarketModule.class);
            set(MarketModule.class, market, "raids", raids);
            set(MarketModule.class, market, "loaded", true);
            assertTrue(market.requireAccess(player));
            var item = new ItemStack(Material.DIAMOND, 3); player.getInventory().setItem(0, item);
            var price = new MarketPriceGUI(player, market, plugin, null, item.clone(), 0);
            var main = new MarketMainGUI(player, market, plugin, null);
            raids.active = true;
            var stack = (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                    new Class<?>[]{CommandSourceStack.class}, (p,m,a) -> {
                        if (m.getName().equals("getSender")) return player;
                        throw new AssertionError(m.getName());
                    });
            var levels = new LevelModule(null) {
                @Override public int getLevel(UUID id) { fail("レイド中はレベル判定より先に拒否する"); return 10; }
            };
            new MarketCommand(market, levels, plugin, null).execute(stack, new String[0]);
            assertNull(player.getOpenInventory().getTopInventory());
            var confirm = MarketPriceGUI.class.getDeclaredMethod("handleConfirm"); confirm.setAccessible(true);
            confirm.invoke(price);
            var buy = MarketMainGUI.class.getDeclaredMethod("handleListingInteraction", MarketListing.class); buy.setAccessible(true);
            buy.invoke(main, new MarketListing(UUID.randomUUID(), UUID.randomUUID(), "seller", "invalid", 1000, 123));
            assertEquals(3, player.getInventory().getItem(0).getAmount());
            assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
        } finally { MockBukkit.unmock(); }
    }
}
