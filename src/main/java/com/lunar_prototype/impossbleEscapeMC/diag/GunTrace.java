package com.lunar_prototype.impossbleEscapeMC.diag;

import com.lunar_prototype.impossbleEscapeMC.ai.DatapackGunnerManager;
import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunCatalog;
import com.lunar_prototype.impossbleEscapeMC.listener.DatapackWorkEntityGuard;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * 銃のアニメーションが止まる・モデルが消える、を後から調べるための記録。読み取り専用 (銃もスコアも書き換えない)。
 * <p>
 * 銃 (データパック銃) を持っているプレイヤーの状態を毎 tick 読んで、直近 {@link #HISTORY} tick を覚える。
 * {@link GunTraceRules} が異常を見つけた時だけ、履歴ごと {@code plugins/<プラグイン>/gun-trace/日付.log} へ書く。普段は何も書かない。
 * プラグインのタスクはデータパックの tick より先に動くので、読む値は「前の tick の終わりの状態」。
 * <p>
 * 負荷を抑える: 銃を持っていないプレイヤーは見ない / 履歴は固定長 / 同じ異常は {@link #COOLDOWN} tick まとめる /
 * 1 日のファイルは {@link #DAILY_LIMIT} バイトで頭打ち / {@link #KEEP_DAYS} 日より古いファイルは起動時に消す。
 */
public final class GunTrace implements Runnable {
    static final int HISTORY = 60;
    static final int COOLDOWN = 200;
    static final long DAILY_LIMIT = 2L * 1024 * 1024;
    static final int KEEP_DAYS = 14;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** データパックのスコアへ書いた物を、次の tick の記録へ添えるための窓口 (プラグイン側の書き込みとの相関を見る) */
    private static volatile GunTrace active;

    private final JavaPlugin plugin;
    private final Path folder;
    private final Map<UUID, Deque<GunTraceSample>> history = new HashMap<>();
    private final Map<UUID, GunTraceRules.State> states = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastReported = new HashMap<>();
    private final Map<UUID, List<String>> pendingWrites = new HashMap<>();
    private BukkitTask task;
    private long tick;
    private LocalDate fileDay;
    private long fileBytes;

    private GunTrace(JavaPlugin plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("gun-trace");
    }

    /** 起動時に呼ぶ。設定 diagnostics.gun-trace (既定 true) が false なら何もしない */
    public static void start(JavaPlugin plugin) {
        if (active != null || !plugin.getConfig().getBoolean("diagnostics.gun-trace", true)) return;
        GunTrace trace = new GunTrace(plugin);
        active = trace;
        trace.task = Bukkit.getScheduler().runTaskTimer(plugin, trace, 1L, 1L);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, trace::deleteOldFiles);
        plugin.getLogger().info("[gun-trace] 銃のアニメーションの記録を有効にしました (異常の時だけ " + trace.folder.getFileName() + "/ へ書きます)");
    }

    public static void stop() {
        GunTrace trace = active;
        active = null;
        if (trace != null && trace.task != null) trace.task.cancel();
    }

    /** プラグインがデータパックのスコアを書いた時に呼ぶ。次の tick の記録に添える。記録が無効なら何もしない */
    public static void noteWrite(Player player, String objective, int value) {
        GunTrace trace = active;
        if (trace == null) return;
        String name = objective.startsWith("toisarm.") ? objective.substring("toisarm.".length()) : objective;
        trace.pendingWrites.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(name + "=" + value);
    }

    @Override
    public void run() {
        tick++;
        Entity work = Bukkit.getEntity(DatapackWorkEntityGuard.WORK_UUID);
        String workWorld = work != null && work.isValid() ? work.getWorld().getName() : null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (DatapackGunnerManager.isGunner(player)) continue;   // SCAV の銃は対象外
            ItemStack gun = player.getInventory().getItemInMainHand();
            String gunId = DatapackGunCatalog.gunIdOf(gun);
            if (gunId == null) {
                // 銃を離したら履歴を捨てる (持ち替え直しは別の記録)
                history.remove(id);
                states.remove(id);
                pendingWrites.remove(id);
                continue;
            }
            GunTraceSample sample = read(player, gun, gunId, workWorld);
            Deque<GunTraceSample> buffer = history.computeIfAbsent(id, k -> new ArrayDeque<>(HISTORY + 1));
            buffer.addLast(sample);
            while (buffer.size() > HISTORY) buffer.removeFirst();
            List<String> anomalies = states.computeIfAbsent(id, k -> new GunTraceRules.State()).accept(sample);
            if (!anomalies.isEmpty()) report(player, gunId, anomalies, buffer);
        }
        history.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        lastReported.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        pendingWrites.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private GunTraceSample read(Player player, ItemStack gun, String gunId, String workWorld) {
        Key model = gun.getData(DataComponentTypes.ITEM_MODEL);
        CustomModelData cmd = gun.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        float frame = cmd != null && !cmd.floats().isEmpty() ? cmd.floats().get(0) : Float.NaN;
        int strings = cmd == null ? 0 : cmd.strings().size();
        int[] timers = new int[GunTraceSample.TIMER_NAMES.length];
        for (int i = 0; i < timers.length; i++) timers[i] = score(player, "toisarm.timer." + GunTraceSample.TIMER_NAMES[i], -9);
        List<String> writes = pendingWrites.remove(player.getUniqueId());
        return new GunTraceSample(tick, gunId, score(player, "toisarm.animation_id", -9), score(player, "toisarm.animation_changed", -9),
                frame, strings, model == null ? null : model.asString(), timers, workWorld,
                player.getInventory().getHeldItemSlot(), writes == null ? null : String.join(";", writes));
    }

    private static int score(Player player, String objectiveName, int fallback) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
        if (objective == null) return fallback;
        Score score = objective.getScore(player.getName());
        return score.isScoreSet() ? score.getScore() : fallback;
    }

    private void report(Player player, String gunId, List<String> anomalies, Deque<GunTraceSample> buffer) {
        Map<String, Long> reported = lastReported.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        List<String> fresh = new ArrayList<>();
        for (String anomaly : anomalies) {
            String kind = anomaly.contains("(") ? anomaly.substring(0, anomaly.indexOf('(')) : anomaly;
            Long last = reported.get(kind);
            if (last != null && tick - last < COOLDOWN) continue;
            reported.put(kind, tick);
            fresh.add(anomaly);
        }
        if (fresh.isEmpty()) return;

        StringBuilder text = new StringBuilder();
        text.append("==== ").append(LocalDateTime.now().format(TIME)).append("  player=").append(player.getName())
                .append("  gun=").append(gunId).append("  tick=").append(tick).append('\n');
        for (String anomaly : fresh) text.append("  anomaly: ").append(anomaly).append('\n');
        text.append("  history (古い → 新しい。t+0 が今の tick の記録):\n");
        for (GunTraceSample sample : buffer) text.append("    ").append(sample.line(tick)).append('\n');
        text.append('\n');
        write(text.toString());
        plugin.getLogger().warning("[gun-trace] 異常: " + String.join(", ", fresh) + " player=" + player.getName() + " gun=" + gunId
                + " (詳細: " + folder.getFileName() + "/" + LocalDate.now().format(DAY) + ".log)");
    }

    /** 異常の記録は稀なので、書き込みだけ別スレッドへ渡す (本文はもう作ってある) */
    private void write(String text) {
        LocalDate today = LocalDate.now();
        if (!today.equals(fileDay)) {
            fileDay = today;
            Path file = folder.resolve(today.format(DAY) + ".log");
            try {
                fileBytes = Files.exists(file) ? Files.size(file) : 0;
            } catch (IOException e) {
                fileBytes = 0;
            }
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (fileBytes + bytes.length > DAILY_LIMIT) return;
        fileBytes += bytes.length;
        Path file = folder.resolve(today.format(DAY) + ".log");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Files.createDirectories(folder);
                Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "[gun-trace] 記録を書けませんでした", e);
            }
        });
    }

    private void deleteOldFiles() {
        if (!Files.isDirectory(folder)) return;
        LocalDate limit = LocalDate.now().minusDays(KEEP_DAYS);
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().endsWith(".log")).forEach(p -> {
                try {
                    LocalDate day = LocalDate.parse(p.getFileName().toString().replace(".log", ""), DAY);
                    if (day.isBefore(limit)) Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 日付でない名前のファイルは触らない
                }
            });
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[gun-trace] 古い記録を消せませんでした", e);
        }
    }
}
