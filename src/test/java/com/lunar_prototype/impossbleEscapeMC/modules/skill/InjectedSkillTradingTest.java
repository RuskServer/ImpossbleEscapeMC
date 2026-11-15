package com.lunar_prototype.impossbleEscapeMC.modules.skill;

import com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry;
import com.lunar_prototype.impossbleEscapeMC.modules.core.*;
import com.lunar_prototype.impossbleEscapeMC.modules.economy.EconomyModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.*;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** staticの初期化に頼らず、注入したスキルの倍率が購入・売却・修理へ届くことを確認する。 */
class InjectedSkillTradingTest {
    static class Players extends PlayerDataModule {
        PlayerData data;
        Players() { super(null, new CommandRegistry()); }
        @Override public PlayerData getPlayerData(UUID id) { return data; }
    }
    @Test void injectedStatsPreservePricingAndLimits() throws Exception {
        var server = MockBukkit.mock();
        try {
            var player = server.addPlayer();
            Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
            Players data = (Players) ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(Players.class);
            data.data = new PlayerData(player.getUniqueId());
            for (SkillNode node : SkillCatalog.all()) data.data.addSkill(node.id());
            var skills = new SkillModule(null, data, new EconomyModule(data), null);
            for (SkillStat stat : SkillStat.values())
                assertEquals(SkillModule.value(data.data, stat), skills.stat(player, stat), 1e-9, stat.name());
            var trader = new TraderModule(null, data, new EconomyModule(data), new CommandRegistry(), () -> null, () -> null, () -> skills);
            var item = new TraderItem("bolts", 1000, 10, 1);
            var definition = new TraderDefinition("kovacs", "Kovacs", TraderType.BUY, List.of(item), -1, true, true);
            double discount = SkillModule.value(data.data, SkillStat.BUY_ALL) + SkillModule.value(data.data, SkillStat.BUY_KOVACS);
            assertNotEquals(0, discount);
            assertEquals(Math.round(1000 * Math.max(0, 1 + discount)), trader.buyPrice(player.getUniqueId(), definition, item));
            assertEquals((int) Math.ceil(10 * (1 + SkillModule.value(data.data, SkillStat.DAILY_LIMIT)) - 1e-9), trader.dailyLimit(player.getUniqueId(), item));
            assertEquals(1 + SkillModule.value(data.data, SkillStat.SELL_BROKER), trader.sellMultiplier(player.getUniqueId()), 1e-9);
            assertEquals(Math.max(0, 1 + SkillModule.value(data.data, SkillStat.REPAIR_COST)), trader.repairMultiplier(player.getUniqueId()), 1e-9);
        } finally { MockBukkit.unmock(); }
    }
}
