package com.lunar_prototype.impossbleEscapeMC.core.di;

import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidReport;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResults;
import com.lunar_prototype.impossbleEscapeMC.modules.supply.SupplyLoadout;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.*;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** グローバルなプラグインを用意せず、注入した依存による保護判定を検証する。 */
class GuiOperationInjectionTest {
    private static final class Bags extends BackpackModule {
        final Inventory contents = Bukkit.createInventory(null, 9);
        Bags() { super(null, null, () -> null); }
        @Override public boolean isBackpackItem(ItemStack item) { return item != null && item.getType() == Material.PAPER; }
        @Override public Inventory loadBackpackInventory(ItemStack item) { return contents; }
    }
    private static final class InRaid extends RaidModule {
        InRaid() { super(null, null, null, null, null, () -> null, null, null, () -> null, () -> null, () -> null, null, null, null); }
        @Override public boolean isInRaid(Player player) { return true; }
    }
    @Test void filledBagAndRaidSupplyRemainProtectedUsingPassedDependencies() throws Exception {
        var server = MockBukkit.mock();
        try {
            var bags = new Bags();
            var trader = new TraderDefinition("test", "test", TraderType.SELL, List.of(), -1, false, false);
            var screen = new TraderGUI(null, trader, null, bags);
            ItemStack bag = new ItemStack(Material.PAPER);
            var meta = bag.getItemMeta(); meta.getPersistentDataContainer().set(PDCKeys.ITEM_ID, PDCKeys.STRING, "test_bag"); bag.setItemMeta(meta);
            var sellable = TraderGUI.class.getDeclaredMethod("sellableIdOf", ItemStack.class); sellable.setAccessible(true);
            bags.contents.setItem(0, new ItemStack(Material.DIAMOND));
            assertNull(sellable.invoke(screen, bag), "A filled bag cannot be sold with its contents");
            bags.contents.clear();
            assertEquals("test_bag", sellable.invoke(screen, bag), "An empty bag remains sellable");
            Player player = server.addPlayer();
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            // レイドのファイル初期化は行わず、実際の参加判定を渡す。
            var raids = (InRaid) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(InRaid.class);
            assertEquals("レイド中は受け取れません", SupplyLoadout.blockingReason(player, new PlayerData(player.getUniqueId()), raids));
        } finally { MockBukkit.unmock(); }
    }
    @Test void resultWaitersAreReplacedAndCancelledAtStop() throws Exception {
        var server = MockBukkit.mock();
        try (var messages = com.lunar_prototype.impossbleEscapeMC.api.message.MessageTestSupport.forwarding()) {
            var results = new RaidResults(MockBukkit.createMockPlugin(), null);
            results.start();
            try {
                Player player = server.addPlayer();
                var report = new RaidReport("test", true, "ok", null, 0, List.of(), 1, 0, false, List.of(), 0, List.of(), null);
                var field = RaidResults.class.getDeclaredField("pending"); field.setAccessible(true);
                @SuppressWarnings("unchecked") Map<UUID, BukkitRunnable> pending = (Map<UUID, BukkitRunnable>) field.get(results);
                RaidResults.show(player, report);
                BukkitRunnable first = pending.get(player.getUniqueId());
                assertNotNull(first);
                RaidResults.show(player, report);
                BukkitRunnable replacement = pending.get(player.getUniqueId());
                assertNotSame(first, replacement);
                assertTrue(first.isCancelled());
                results.stop();
                assertTrue(replacement.isCancelled());
                assertTrue(pending.isEmpty());
                assertNull(RaidResults.get(player.getUniqueId()));
            } finally { results.stop(); }
        } finally { MockBukkit.unmock(); }
    }
}
