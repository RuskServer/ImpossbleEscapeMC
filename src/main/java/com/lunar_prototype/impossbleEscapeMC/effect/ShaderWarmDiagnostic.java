package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.api.message.Messages;
import com.lunar_prototype.impossbleEscapeMC.api.message.MessageText;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ポストエフェクトを初めて重ねた時の、クライアントの停止時間を測る診断 ({@code /shaderdebug warm})。
 * <p>
 * ポストエフェクトのシェーダーは、そのエフェクトが初めて使われるフレームで、クライアントの描画のスレッドを止めて組み立てられる。
 * 止まっている間、クライアントはサーバーからの ping (ClientboundPing) に返事 (ServerboundPong) を返せない。
 * そこで、エフェクトを重ねたあと、毎 tick ping を送り、返事が遅れた分を停止時間として測る。
 * 返事の遅れが元に戻れば、組み立てが終わった印になる (クライアントからの通知は無い。この方法ならクライアントの mod が要らない)。
 * <p>
 * 注意:
 * <ul>
 *   <li>ping はエフェクトを重ねた<b>次の tick</b>から送る (同じ tick の ping はエフェクトと一緒に処理され、止まる前に返事が返る)</li>
 *   <li>ping の間隔は 1 tick (50 ms)。それより短い停止は見えない (見えない = ほぼカクつきとして見えない)</li>
 *   <li>1 回目 (冷えた状態) と 2 回目 (温まった状態) を比べると、手法が合っているか分かる。2 回目がほぼ 0 なら、1 回目の遅れはコンパイルのもの</li>
 *   <li>測っている間、エフェクトが次々に画面に出る (点滅する)。測る人の画面だけ</li>
 * </ul>
 * エフェクトの一覧は、リソースの shaderwarm_effects.txt (リソースパックの assets/iem/post_effect/ のファイル名)。
 */
public final class ShaderWarmDiagnostic {

    private static final String NAMESPACE = "iem";
    /** ping の id の範囲 (他のパケットと混ざらないよう、決まった範囲を使う) */
    private static final int PING_ID_BASE = 0x5E7A0000;
    private static final int PING_ID_RANGE = 0x10000;
    /** 基準の返事の遅れを測る tick と、その後に遅れた返事を待つ tick */
    private static final int BASELINE_TICKS = 40;
    private static final int BASELINE_WAIT_TICKS = 10;
    /** エフェクトを外すまでの待ち (最後の ping の返事を待つ) と、次のエフェクトまでの間 */
    private static final int SETTLE_TICKS = 8;
    private static final int GAP_TICKS = 6;
    /** この ms を超えて基準より遅れた返事を、「止まった」とみなす */
    static final double TOLERANCE_MS = 30.0;
    /** カクつきとして見える目安の停止時間 */
    static final double VISIBLE_MS = 50.0;

    private static final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    private static final Map<Integer, PingInfo> pings = new ConcurrentHashMap<>();
    private static int nextPingId = 0;
    private static PongListener listener;

    private record PingInfo(Run run, int slot, int offset, long sentNanos) {
    }

    private ShaderWarmDiagnostic() {
    }

    /** 1 つのエフェクトの測定結果。maxRttMs は最大の返事の遅れ、excessMs は基準との差、lastBadOffset は最後に基準より遅れた ping の番号 (無ければ -1)、missing は返事が来なかった ping の数 */
    record Result(double maxRttMs, double excessMs, int lastBadOffset, int missing) {
    }

    /** 基準の返事の遅れ (中央値) と、1 つのエフェクトの返事の遅れの並び (NaN = 返事なし) から、結果を出す */
    static Result analyze(double baselineMedianMs, double[] rttMs, double toleranceMs) {
        double max = Double.NEGATIVE_INFINITY;
        int lastBad = -1;
        int missing = 0;
        for (int i = 0; i < rttMs.length; i++) {
            double v = rttMs[i];
            if (Double.isNaN(v)) {
                missing++;
                continue;
            }
            max = Math.max(max, v);
            if (v - baselineMedianMs > toleranceMs) lastBad = i;
        }
        if (max == Double.NEGATIVE_INFINITY) return new Result(Double.NaN, Double.NaN, lastBad, missing);
        return new Result(max, Math.max(0.0, max - baselineMedianMs), lastBad, missing);
    }

    static double median(double[] values) {
        double[] v = Arrays.stream(values).filter(x -> !Double.isNaN(x)).sorted().toArray();
        if (v.length == 0) return Double.NaN;
        return v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2.0;
    }

    /** 測定を始める。passes = 全エフェクトを通す回数、window = エフェクトを重ねたあとに ping を送る tick 数。始められなければ理由を返す (成功は null) */
    public static String start(ImpossbleEscapeMC plugin, Player player, int passes, int window, List<String> only) {
        if (runs.containsKey(player.getUniqueId())) return "すでに測定中です (/shaderdebug warm stop で止められます)";
        List<Key> effects = new ArrayList<>();
        for (String name : loadEffectNames(plugin)) {
            if (!only.isEmpty() && !only.contains(name)) continue;
            effects.add(Key.key(NAMESPACE, name));
        }
        if (effects.isEmpty()) return "測るエフェクトがありません";
        Run run = new Run(plugin, player, effects, Math.max(1, passes), Math.max(10, window));
        runs.put(player.getUniqueId(), run);
        ensureListener();
        run.task = Bukkit.getScheduler().runTaskTimer(plugin, run::tick, 1L, 1L);
        return null;
    }

    /** 測定を止める。止めたら true */
    public static boolean stop(Player player) {
        Run run = runs.get(player.getUniqueId());
        if (run == null) return false;
        run.finish(false);
        return true;
    }

    /** プラグインを止める時 */
    public static void shutdown() {
        for (Run run : new ArrayList<>(runs.values())) run.finish(false);
        removeListener();
    }

    private static List<String> loadEffectNames(ImpossbleEscapeMC plugin) {
        List<String> names = new ArrayList<>();
        try (InputStream in = plugin.getResource("shaderwarm_effects.txt")) {
            if (in == null) return names;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) names.add(line);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("shaderwarm_effects.txt を読めません: " + e.getMessage());
        }
        return names;
    }

    private static synchronized int newPingId() {
        nextPingId = (nextPingId + 1) % PING_ID_RANGE;
        return PING_ID_BASE + nextPingId;
    }

    private static synchronized void ensureListener() {
        if (listener != null) return;
        listener = new PongListener();
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    private static synchronized void removeListener() {
        if (listener == null) return;
        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        listener = null;
    }

    /** クライアントの返事 (Pong) を見る。受け取った時刻を、パケットを読む前に取る */
    private static final class PongListener extends PacketListenerAbstract {
        private PongListener() {
            super(PacketListenerPriority.MONITOR);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            long now = System.nanoTime();
            if (event.getPacketType() != PacketType.Play.Client.PONG) return;
            int id = new WrapperPlayClientPong(event).getId();
            PingInfo info = pings.remove(id);
            if (info == null) return;
            info.run().record(info.slot(), info.offset(), (now - info.sentNanos()) / 1_000_000.0);
        }
    }

    private static final class Run {
        private final ImpossbleEscapeMC plugin;
        private final UUID playerId;
        private final String playerName;
        private final List<Key> effects;
        private final int passes;
        private final int window;
        private final int slotTicks;
        private final int slots;
        /** [基準] と [エフェクトの回] ごとの返事の遅れ (ms)。NaN = まだ・返事なし */
        private final double[] baseline = new double[BASELINE_TICKS];
        private final double[][] rtts;
        private final long startedAt = System.currentTimeMillis();
        private BukkitTask task;
        private int tick = 0;
        private double baselineMedian = Double.NaN;
        private boolean probing = false;
        private boolean done = false;

        private Run(ImpossbleEscapeMC plugin, Player player, List<Key> effects, int passes, int window) {
            this.plugin = plugin;
            this.playerId = player.getUniqueId();
            this.playerName = player.getName();
            this.effects = effects;
            this.passes = passes;
            this.window = window;
            this.slotTicks = 1 + window + SETTLE_TICKS + GAP_TICKS;
            this.slots = effects.size() * passes;
            Arrays.fill(baseline, Double.NaN);
            this.rtts = new double[slots][window];
            for (double[] row : rtts) Arrays.fill(row, Double.NaN);
        }

        synchronized void record(int slot, int offset, double rttMs) {
            if (slot < 0) baseline[offset] = rttMs;
            else if (slot < slots) rtts[slot][offset] = rttMs;
        }

        private void sendPing(Player player, int slot, int offset) {
            int id = newPingId();
            pings.put(id, new PingInfo(this, slot, offset, System.nanoTime()));
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(id));
        }

        void tick() {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                finish(false);
                return;
            }
            int t = tick++;
            int effectsStart = BASELINE_TICKS + BASELINE_WAIT_TICKS;
            if (t == 0) {
                Messages.chat(player, "iem.system.shaderwarmdiagnostic.tick.notice_001", MessageText.of("iem.system.shaderwarmdiagnostic.tick.notice_001", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.tick.notice_001.text_01", effects.size(), passes, (effectsStart + slots * slotTicks) / 20).color(NamedTextColor.AQUA))));
            }
            if (t < BASELINE_TICKS) {
                sendPing(player, -1, t);
                return;
            }
            if (t < effectsStart) return;
            if (Double.isNaN(baselineMedian)) {
                synchronized (this) {
                    baselineMedian = median(baseline);
                }
                if (Double.isNaN(baselineMedian)) {
                    Messages.chat(player, "iem.system.shaderwarmdiagnostic.tick.notice_002", MessageText.of("iem.system.shaderwarmdiagnostic.tick.notice_002", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.tick.notice_002.text_01").color(NamedTextColor.RED))));
                    finish(false);
                    return;
                }
            }
            int rel = t - effectsStart;
            int slot = rel / slotTicks;
            int in = rel % slotTicks;
            if (slot >= slots) {
                finish(true);
                return;
            }
            Key effect = effects.get(slot % effects.size());
            if (in == 0) {
                ScreenEffectService.setProbe(player, effect);
                probing = true;
            } else if (in <= window) {
                sendPing(player, slot, in - 1);
            } else if (in == window + SETTLE_TICKS && probing) {
                ScreenEffectService.setProbe(player, null);
                probing = false;
            }
        }

        void finish(boolean completed) {
            if (done) return;
            done = true;
            if (task != null) task.cancel();
            runs.remove(playerId);
            pings.values().removeIf(info -> info.run() == this);
            if (runs.isEmpty()) removeListener();
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) ScreenEffectService.setProbe(player, null);
            if (player == null) return;
            if (!completed) {
                Messages.chat(player, "iem.system.shaderwarmdiagnostic.finish.notice_001", MessageText.of("iem.system.shaderwarmdiagnostic.finish.notice_001", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.finish.notice_001.text_01").color(NamedTextColor.YELLOW))));
                return;
            }
            report(player);
        }

        private void report(Player player) {
            Result[] results = new Result[slots];
            synchronized (this) {
                for (int s = 0; s < slots; s++) results[s] = analyze(baselineMedian, rtts[s], TOLERANCE_MS);
            }
            int n = effects.size();
            List<String> csv = new ArrayList<>();
            csv.add("pass,effect,max_rtt_ms,excess_ms,last_bad_tick,missing_pongs");
            for (int s = 0; s < slots; s++) {
                Result r = results[s];
                csv.add((s / n + 1) + "," + effects.get(s % n).value() + "," + fmt(r.maxRttMs()) + "," + fmt(r.excessMs()) + "," + (r.lastBadOffset() + 1) + "," + r.missing());
            }
            String file = writeCsv(csv);
            Messages.chat(player, "iem.system.shaderwarmdiagnostic.report.notice_001", MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_001", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_001.text_01", fmt(baselineMedian)).color(NamedTextColor.GREEN))));
            for (int p = 0; p < passes; p++) {
                double sum = 0;
                int visible = 0;
                for (int i = 0; i < n; i++) {
                    double ex = results[p * n + i].excessMs();
                    if (Double.isNaN(ex)) continue;
                    sum += ex;
                    if (ex >= VISIBLE_MS) visible++;
                }
                Messages.chat(player, "iem.system.shaderwarmdiagnostic.report.notice_002", MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_002", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_002.text_01", (p + 1), (p == 0 ? MessageText.of("iem.system.shaderwarmdiagnostic.first_pass") : p == 1 ? MessageText.of("iem.system.shaderwarmdiagnostic.second_pass") : ""), fmt(sum), (int) VISIBLE_MS, visible).color(NamedTextColor.GREEN))));
            }
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -nz(results[i].excessMs())));
            Messages.chat(player, "iem.system.shaderwarmdiagnostic.report.notice_003", MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_003", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_003.text_01").color(NamedTextColor.GRAY))));
            for (int k = 0; k < Math.min(12, n); k++) {
                int i = order[k];
                String second = passes > 1 ? " → " + fmt(results[n + i].excessMs()) + " ms" : "";
                Messages.chat(player, "iem.system.shaderwarmdiagnostic.report.notice_004", MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_004", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_004.text_01", effects.get(i).value(), fmt(results[i].excessMs()), second, (results[i].missing() > 0 ? MessageText.of("iem.system.shaderwarmdiagnostic.no_reply", results[i].missing()) : "")).color(NamedTextColor.WHITE))));
            }
            if (file != null) Messages.chat(player, "iem.system.shaderwarmdiagnostic.report.notice_005", MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_005", MessageText.payload(MessageText.of("iem.system.shaderwarmdiagnostic.report.notice_005.text_01", file).color(NamedTextColor.GRAY))));
        }

        private String writeCsv(List<String> lines) {
            try {
                Path dir = plugin.getDataFolder().toPath().resolve("shaderwarm");
                Files.createDirectories(dir);
                Path out = dir.resolve("shaderwarm-" + playerName + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
                Files.write(out, lines, StandardCharsets.UTF_8);
                return plugin.getDataFolder().getParentFile().toPath().relativize(out).toString().replace('\\', '/');
            } catch (IOException e) {
                plugin.getLogger().warning("結果を書けません: " + e.getMessage());
                return null;
            }
        }
    }

    private static double nz(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "-" : String.format(java.util.Locale.ROOT, "%.0f", v);
    }
}
