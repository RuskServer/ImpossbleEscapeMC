package com.lunar_prototype.impossbleEscapeMC.modules.raid.result;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Service;
import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Inject;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Startable;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.Stoppable;
import org.bukkit.plugin.java.JavaPlugin;
import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderDefinition;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderItem;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderModule;
import com.lunar_prototype.impossbleEscapeMC.modules.trader.TraderType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レイド結果を覚えておき、リザルトの画面 ({@link RaidResultDialog}) を開く。
 * 脱出・死亡・MIA の時に {@link #show} を呼ぶと、チャットに 1 行 (押すと開き直せる) を出し、
 * 死亡ならリスポーンして、テレポートの読み込みが落ち着いてから画面を開く
 */
@Service
public final class RaidResults implements Startable, Stoppable {

    /** 生きていてから画面を開くまで (tick)。テレポート・リスポーン直後の読み込みの画面と重ならないように */
    private static final int OPEN_DELAY_TICKS = 30;
    /** これ以上待っても開けなければあきらめる (チャットの行から開ける) */
    private static final int OPEN_TIMEOUT_TICKS = 20 * 60;

    private static final Map<UUID, RaidReport> REPORTS = new ConcurrentHashMap<>();

    private static RaidResults instance;
    private final JavaPlugin plugin;
    private final TraderModule traders;
    private final Map<UUID, BukkitRunnable> pending = new ConcurrentHashMap<>();
    @Inject public RaidResults(JavaPlugin plugin, TraderModule traders) { this.plugin = plugin; this.traders = traders; }
    @Override public void start() { instance = this; }
    @Override public void stop() {
        if (instance != this) return;
        pending.values().forEach(BukkitRunnable::cancel);
        pending.clear();
        REPORTS.clear();
        instance = null;
    }

    public static RaidReport get(UUID player) {
        return REPORTS.get(player);
    }

    /** 結果を覚えて、チャットに 1 行を出し、少し待ってから画面を開く */
    public static void show(Player player, RaidReport report) {
        UUID id = player.getUniqueId();
        REPORTS.put(id, report);
        Component open = MessageText.of("iem.raid.raidresults.view").color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.callback(audience -> {
                    if (audience instanceof Player p) open(p);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofHours(6)).build()));
        Messages.chat(player, "iem.raid.raidresults.show.notice_001", MessageText.of("iem.raid.raidresults.show.notice_001", MessageText.payload(MessageText.of("iem.raid.raidresults.show.notice_001.text_01").color(NamedTextColor.GRAY)
                .append(Component.text(report.outcome(), report.survived() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(MessageText.of("iem.raid.raidresults.show.notice_001.text_02", report.mapName(), report.rank()).color(NamedTextColor.GRAY))
                .append(open))));

        RaidResults owner = instance;
        BukkitRunnable previous = owner.pending.remove(id);
        if (previous != null) previous.cancel();
        BukkitRunnable task = new BukkitRunnable() {
            int waited;
            int alive;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (instance != owner || p == null || REPORTS.get(id) != report || (waited += 5) > OPEN_TIMEOUT_TICKS) {
                    owner.pending.remove(id, this);
                    cancel();
                    return;
                }
                alive = p.isDead() ? 0 : alive + 5;
                if (alive < OPEN_DELAY_TICKS) return;
                owner.pending.remove(id, this);
                cancel();
                open(p);
            }
        };
        task.runTaskTimer(owner.plugin, 5L, 5L);
        owner.pending.put(id, task);
    }

    /** 最後のレイドの結果の画面を開く (1 ページ目から) */
    public static void open(Player player) {
        RaidReport report = REPORTS.get(player.getUniqueId());
        if (report == null) {
            Messages.chat(player, "iem.raid.raidresults.open.notice_001", MessageText.of("iem.raid.raidresults.open.notice_001", MessageText.payload(MessageText.of("iem.raid.raidresults.open.notice_001.text_01").color(NamedTextColor.RED))));
            return;
        }
        RaidResultDialog.open(player, report);
    }

    /** 物の価値: 売るトレーダー (Broker など) の買い値 x 個数。売れない物は 0 */
    public static long valueOf(ItemStack item) {
        return valueOf(null, item);
    }

    /** プレイヤーの売却スキル・銃の耐久度を反映した実売却見込み額。 */
    public static long valueOf(UUID player, ItemStack item) {
        String id = QuestAutoHandIn.itemIdOf(item);
        if (id == null) return 0;
        TraderModule traders = instance.traders;
        if (traders == null) return 0;
        double best = 0;
        for (TraderDefinition trader : traders.getAllTraders()) {
            if (trader.type != TraderType.SELL || trader.items == null) continue;
            for (TraderItem ti : trader.items) {
                if (id.equals(ti.itemId)) best = Math.max(best, TraderModule.conditionAdjustedSellPrice(ti, item));
            }
        }
        double multiplier = player == null ? 1.0 : traders.sellMultiplier(player);
        return (long) Math.floor(best * item.getAmount() * multiplier);
    }
}
