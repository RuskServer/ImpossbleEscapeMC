package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RaidModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, RaidMap> maps = new HashMap<>();
    private final Map<String, RaidInstance> activeRaids = new HashMap<>();
    private final Map<String, Set<UUID>> raidQueues = new HashMap<>();
    private final File mapsFolder;
    private final File stateFile;

    public static final int CYCLE_DURATION = 600; // 10 minutes (600 seconds)
    public static final int LATE_JOIN_WINDOW = 240; // 4 minutes (240 seconds)
    private final Map<String, Integer> mapTimers = new HashMap<>();
    private final Map<String, Long> lastCycleStartTime = new HashMap<>();
    /**
     * 回転の時刻 (0..CYCLE_DURATION-1。1 秒ずつ回る)。マップは名前の順に、周期を等分した枠でこの時刻に始まる ({@link RaidRotation})。
     * 等分にずらしておけば、いつ来てもどこかのマップに途中参加しやすい
     */
    private int rotationElapsed;
    private BukkitRunnable globalTimerTask;
    private final Map<String, BossBar> queueBossBars = new HashMap<>();
    /** マップごとの、今の周期と次の周期の時間帯・天候 ({@link RaidConditions})。周期が切り替わると次が今になる */
    private final Map<String, RaidConditions> currentConditions = new HashMap<>();
    private final Map<String, RaidConditions> nextConditions = new HashMap<>();
    private final java.util.Random conditionRandom = new java.util.Random();
    /** マップごとの、今の周期で死亡・脱出した人 (同じ周期に途中参加し直させない。周期が替わると消す) */
    private final Map<String, Set<UUID>> finishedThisCycle = new HashMap<>();
    /** レイドの途中で切断した人: どのマップの、どの周期 (始まった時刻) か。戻った時に、同じ周期なら続け、終わっていれば装備を失う */
    private record Disconnect(String mapId, long cycleStart) {
    }
    private final Map<UUID, Disconnect> disconnected = new HashMap<>();

    public RaidModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.mapsFolder = new File(plugin.getDataFolder(), "raids");
        this.stateFile = new File(plugin.getDataFolder(), "raid_state.json");
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs();
        }
    }

    @Override
    public void onEnable(ServiceContainer container) {
        loadMaps();
        container.get(com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry.class).register(new RaidCommand(this));
        // 出撃時の PDA の演出の間の操作の停止
        Bukkit.getPluginManager().registerEvents(new PdaIntro(), plugin);
        // レイド結果のキルログ: 弾が当たった時の様子 (武器・弾・距離・部位) を覚えておく
        Bukkit.getPluginManager().registerEvents(new com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidKillTracker(), plugin);
        
        // Initialize BossBars and Timers for each map
        for (String mapId : maps.keySet()) {
            mapTimers.put(mapId, CYCLE_DURATION);
            lastCycleStartTime.put(mapId, System.currentTimeMillis());
            queueBossBars.put(mapId, BossBar.bossBar(
                    Component.text("出撃準備中 (" + mapId + ")...", NamedTextColor.YELLOW),
                    1.0f,
                    BossBar.Color.YELLOW,
                    BossBar.Overlay.PROGRESS
            ));
            currentConditions.put(mapId, RaidConditions.roll(conditionRandom));
            nextConditions.put(mapId, RaidConditions.roll(conditionRandom));
            applyConditions(mapId);
        }

        // マップの開始を、周期をマップの数で等分した枠にずらして並べる
        applyRotationOffsets();

        loadState(); // 状態の復元
        startGlobalTimer();
        
        // 1分ごとのオートセーブ (参加者の一覧はメインのスレッドで変わるので、読むのもメインのスレッドで)
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveState, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        saveState(); // 終了時に保存
        suspendAllRaids();   // 結果を出さずに止める (再起動の後に参加者を戻して続ける)
        
        // Cleanup BossBars
        for (BossBar bar : queueBossBars.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        queueBossBars.clear();

        if (globalTimerTask != null) {
            globalTimerTask.cancel();
        }
    }

    /** 周期の並びの順 (名前の順。再起動しても同じ) */
    private List<String> rotationOrder() {
        List<String> order = new ArrayList<>(maps.keySet());
        java.util.Collections.sort(order);
        return order;
    }

    /**
     * どのマップも、枠どおりの所にいる扱いにする (回転の時刻を 0 から)。始まった時刻も残り時間に合わせる
     * (枠の始まりから途中参加の時間を過ぎたマップは、今は途中参加できない)
     */
    private void applyRotationOffsets() {
        rotationElapsed = 0;
        List<String> order = rotationOrder();
        for (int i = 0; i < order.size(); i++) placeOnSlot(order.get(i), order.size(), i);
        if (order.size() >= 2) {
            plugin.getLogger().info("Raid rotation: " + order.size() + " maps, one every "
                    + formatTime(RaidRotation.spacing(CYCLE_DURATION, order.size())) + " " + order);
        }
    }

    /** そのマップの残り時間と始まった時刻を、今の回転の時刻での枠の位置にする */
    private void placeOnSlot(String mapId, int mapCount, int index) {
        int timer = RaidRotation.timerOnSlot(CYCLE_DURATION, mapCount, index, rotationElapsed);
        mapTimers.put(mapId, timer);
        lastCycleStartTime.put(mapId, System.currentTimeMillis() - (CYCLE_DURATION - timer) * 1000L);
    }

    /** レイド中の人か、出撃を待っている人がいるマップ (残り時間を急に動かさない) */
    private boolean isBusy(String mapId) {
        Set<UUID> queue = raidQueues.get(mapId);
        return activeRaids.containsKey(mapId) || (queue != null && !queue.isEmpty());
    }

    /** 新しいレイドを始める前に、そのマップの状態をまっさらにする (セッションの区切り) */
    private void resetMapForRaid(String mapId) {
        finishedThisCycle.remove(mapId);
        RaidMap map = maps.get(mapId);
        if (map == null) return;
        if (plugin.getLootManager() != null) {
            plugin.getLootManager().refillContainers(map);
        }
        if (plugin.getCorpseManager() != null) {
            org.bukkit.World world = Bukkit.getWorld(map.getWorldName());
            if (world != null) {
                plugin.getCorpseManager().cleanup(world);
            }
        }
    }

    /** このマップのインスタンスがまだ無ければ作る (二重生成しない) */
    private RaidInstance createRaid(String mapId, List<Player> participants) {
        RaidInstance existing = activeRaids.get(mapId);
        if (existing != null) return existing;
        RaidMap map = maps.get(mapId);
        if (map == null) return null;
        RaidInstance raid = new RaidInstance(plugin, map, participants);
        activeRaids.put(mapId, raid);
        return raid;
    }

    /** 待機列にいる人を出撃させる (ボスバーを片付けて、出撃できる人だけ) */
    private List<Player> collectQueuedParticipants(String mapId) {
        List<Player> participants = new ArrayList<>();
        Set<UUID> playerUuids = raidQueues.get(mapId);
        if (playerUuids == null) return participants;
        BossBar bar = queueBossBars.get(mapId);
        for (UUID uuid : playerUuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            if (bar != null) p.hideBossBar(bar);
            if (isInRaid(p)) continue;
            // 待機列に入った後で装備を増やせるため、実際の出撃直前にも容量を確認する。
            if (com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.isOverCapacity(p)) {
                sendCapacityWarning(p);
                continue;
            }
            participants.add(p);
        }
        raidQueues.remove(mapId);
        return participants;
    }

    /**
     * 空いているマップで今すぐレイドを始める (物資・死体をリセットし、待機列も取り込む)。
     * 周期の残り時間を待たずに始めるので、マップが空いた時の虚無時間が無くなる。二重生成しない。
     */
    private void startRaidOnDemand(String mapId, List<Player> group) {
        if (activeRaids.containsKey(mapId)) return;
        lastCycleStartTime.put(mapId, System.currentTimeMillis());
        mapTimers.put(mapId, CYCLE_DURATION);
        resetMapForRaid(mapId);
        applyConditions(mapId);

        List<Player> participants = new ArrayList<>(collectQueuedParticipants(mapId));
        for (Player p : group) {
            if (participants.contains(p) || isInRaid(p)) continue;
            if (com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.isOverCapacity(p)) {
                sendCapacityWarning(p);
                continue;
            }
            participants.add(p);
        }
        group.forEach(this::leaveQueue);
        createRaid(mapId, participants);
    }

    private static void sendCapacityWarning(Player player) {
        int used = com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.usedCost(player, player.getInventory());
        int capacity = com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.capacity(player, player.getInventory());
        player.sendMessage(Component.text("持ち物が容量を超えています (" + used + " / " + capacity + " マス、あと "
                + Math.max(0, used - capacity) + " マス整理が必要です)。整理してから再度出撃してください。", NamedTextColor.RED));
    }

    private void saveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("mapTimers", mapTimers);
        
        Map<String, List<String>> raidPlayers = new HashMap<>();
        for (Map.Entry<String, RaidInstance> entry : activeRaids.entrySet()) {
            List<String> uuids = new ArrayList<>();
            for (UUID uuid : entry.getValue().getParticipantUuids()) {
                uuids.add(uuid.toString());
            }
            raidPlayers.put(entry.getKey(), uuids);
        }
        state.put("activeRaids", raidPlayers);
        state.put("cycleStarts", new HashMap<>(lastCycleStartTime));
        state.put("rotationElapsed", rotationElapsed);
        Map<String, Map<String, Object>> left = new HashMap<>();
        disconnected.forEach((id, d) -> left.put(id.toString(), Map.of("mapId", d.mapId(), "cycleStart", d.cycleStart())));
        state.put("disconnected", left);

        try (FileWriter writer = new FileWriter(stateFile)) {
            gson.toJson(state, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save raid state.");
        }
    }

    private void loadState() {
        if (!stateFile.exists()) return;
        try (FileReader reader = new FileReader(stateFile)) {
            Map<String, Object> state = gson.fromJson(reader, Map.class);
            if (state == null) return;

            if (state.containsKey("mapTimers")) {
                Map<String, Double> loadedTimers = (Map<String, Double>) state.get("mapTimers");
                for (Map.Entry<String, Double> entry : loadedTimers.entrySet()) {
                    if (mapTimers.containsKey(entry.getKey())) {
                        mapTimers.put(entry.getKey(), entry.getValue().intValue());
                    }
                }
            }

            if (state.get("rotationElapsed") instanceof Number elapsed) {
                rotationElapsed = Math.floorMod(elapsed.intValue(), CYCLE_DURATION);
            }
            if (state.get("cycleStarts") instanceof Map<?, ?> starts) {
                starts.forEach((id, start) -> {
                    if (maps.containsKey(String.valueOf(id)) && start instanceof Number n) lastCycleStartTime.put(String.valueOf(id), n.longValue());
                });
            }
            if (state.get("disconnected") instanceof Map<?, ?> left) {
                left.forEach((id, d) -> {
                    if (d instanceof Map<?, ?> m && m.get("mapId") != null && m.get("cycleStart") instanceof Number n) {
                        disconnected.put(UUID.fromString(String.valueOf(id)), new Disconnect(String.valueOf(m.get("mapId")), n.longValue()));
                    }
                });
            }

            if (state.containsKey("activeRaids")) {
                Map<String, List<String>> raidPlayers = (Map<String, List<String>>) state.get("activeRaids");
                for (Map.Entry<String, List<String>> entry : raidPlayers.entrySet()) {
                    String mapId = entry.getKey();
                    RaidMap map = maps.get(mapId);
                    if (map == null) continue;

                    Set<UUID> uuids = new HashSet<>();
                    for (String uuidStr : entry.getValue()) {
                        uuids.add(UUID.fromString(uuidStr));
                    }

                    if (!uuids.isEmpty()) {
                        RaidInstance raid = new RaidInstance(plugin, map, new ArrayList<>());
                        raid.restoreParticipants(uuids); // 復元用メソッド
                        activeRaids.put(mapId, raid);
                    }
                }
            }
            stateFile.delete(); // 読み込み後は削除
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load raid state.");
        }
    }

    private void startGlobalTimer() {
        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                rotationElapsed = (rotationElapsed + 1) % CYCLE_DURATION;
                List<String> order = rotationOrder();
                for (String mapId : order) {
                    int timeLeft = mapTimers.getOrDefault(mapId, CYCLE_DURATION);
                    
                    if (timeLeft <= 0) {
                        onMapCycleEnd(mapId);
                        // 次の周期の長さで、枠からのずれを少しずつ戻す
                        mapTimers.put(mapId, RaidRotation.nextLength(CYCLE_DURATION, order.size(), order.indexOf(mapId), rotationElapsed));
                    } else {
                        mapTimers.put(mapId, timeLeft - 1);
                    }
                    
                    updateMapBossBar(mapId);
                    notifyQueuedPlayers(mapId);
                }
            }
        };
        globalTimerTask.runTaskTimer(plugin, 0, 20);
    }

    private void updateMapBossBar(String mapId) {
        BossBar bar = queueBossBars.get(mapId);
        if (bar == null) return;

        int timeLeft = mapTimers.getOrDefault(mapId, 0);
        float progress = (float) timeLeft / (float) CYCLE_DURATION;
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        
        bar.progress(progress);
        RaidConditions next = getNextConditions(mapId);
        bar.name(Component.text("出撃まであと " + formatTime(timeLeft) + " (" + mapId
                + (next != null ? " / " + next.describe() : "") + ")", NamedTextColor.YELLOW));
    }

    private void notifyQueuedPlayers(String mapId) {
        int timeLeft = mapTimers.getOrDefault(mapId, 0);
        if (timeLeft == 60 || (timeLeft <= 10 && timeLeft > 0)) {
            Set<UUID> queue = raidQueues.get(mapId);
            if (queue == null) return;

            String message = "§e[RAID] §f" + mapId + " 出撃まであと §b" + timeLeft + "秒";
            for (UUID uuid : queue) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(message);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }
            }
        }
    }

    private void onMapCycleEnd(String mapId) {
        plugin.getLogger().info("Raid cycle ended for " + mapId + ". Starting new raids and MIA processing...");
        lastCycleStartTime.put(mapId, System.currentTimeMillis());

        // 1. 既存レイドの終了処理 (MIA) - 対象マップのみ
        RaidInstance activeRaid = activeRaids.get(mapId);
        if (activeRaid != null) {
            activeRaid.handleMIA();
            activeRaid.stop();
        }

        // 1.5 時間帯・天候: 次の周期の分を今の周期にして、ワールドへ当てはめる (待機中に見せていた条件で出撃する)
        RaidConditions upcoming = nextConditions.remove(mapId);
        currentConditions.put(mapId, upcoming != null ? upcoming : RaidConditions.roll(conditionRandom));
        nextConditions.put(mapId, RaidConditions.roll(conditionRandom));
        applyConditions(mapId);

        // 2,3 物資リセット・死体消去
        resetMapForRaid(mapId);

        // 4. 一斉スタート (キューの掃き出し) - 対象マップのみ
        List<Player> participants = collectQueuedParticipants(mapId);
        if (!participants.isEmpty()) {
            createRaid(mapId, participants);
        }

        Bukkit.broadcast(Component.text(mapId + " の新たなレイドサイクルが開始されました。", NamedTextColor.AQUA));
    }

    /**
     * キューに参加
     */
    public boolean joinQueue(Player player, String mapId) {
        return joinQueue(List.of(player), mapId);
    }

    /**
     * 出撃の待機列に入れる (パーティーはメンバーをまとめて)。1 人でも出撃できない人がいれば、誰も入れない
     * (理由はその人のチャットに出す)。途中参加の時間内なら、まとめてレイドに入れる (パーティーは同じ所に出る)
     */
    public boolean joinQueue(List<Player> group, String mapId) {
        if (!maps.containsKey(mapId) || group.isEmpty()) return false;
        RaidInstance active = activeRaids.get(mapId);
        long age = (System.currentTimeMillis() - lastCycleStartTime.getOrDefault(mapId, 0L)) / 1000;
        boolean lateJoin = age < LATE_JOIN_WINDOW;

        for (Player player : group) {
            // すでにどこかのレイドに参加中かチェック
            for (RaidInstance raid : activeRaids.values()) {
                if (raid.isParticipant(player.getUniqueId())) {
                    player.sendMessage(Component.text("すでにレイドに参加中です。", NamedTextColor.RED));
                    return false;
                }
            }
            // 持ち物が容量を超えたままでは出撃させない (レイド中にあふれると足元に落とすことになるため、ロビーで整理してもらう)
            if (com.lunar_prototype.impossbleEscapeMC.item.CostSlotManager.isOverCapacity(player)) {
                sendCapacityWarning(player);
                return false;
            }
            // 進行中のレイドに、この周期ですでに死亡・脱出した人は途中参加できない
            // (マップが空いていれば新しいセッションを始めるので制限しない)
            if (active != null && lateJoin && finishedThisCycle(mapId, player)) {
                player.sendMessage(Component.text("このレイドセッションではすでに死亡・脱出しているため、再参加はできません。", NamedTextColor.RED));
                return false;
            }
        }

        // マップが空いていれば、周期を待たずに今すぐ開始する (虚無時間の解消)。中でリセットも行う。
        if (active == null) {
            startRaidOnDemand(mapId, group);
            group.forEach(p -> p.sendMessage(Component.text(mapId + " のレイドを開始しました。", NamedTextColor.GREEN)));
            return true;
        }

        // 途中参加 (周期の始まりから LATE_JOIN_WINDOW 以内)
        if (lateJoin) {
            group.forEach(this::leaveQueue);
            active.joinInProgress(group);
            group.forEach(p -> p.sendMessage(Component.text(mapId + " のレイドに途中参加しました。", NamedTextColor.GREEN)));
            return true;
        }

        int timeLeft = mapTimers.getOrDefault(mapId, CYCLE_DURATION);
        BossBar bar = queueBossBars.get(mapId);
        for (Player player : group) {
            // すでに別のキューにいる場合は削除
            leaveQueue(player);
            raidQueues.computeIfAbsent(mapId, k -> new HashSet<>()).add(player.getUniqueId());
            player.sendMessage(Component.text(mapId + " の出撃待機列に参加しました。出撃まであと " + formatTime(timeLeft), NamedTextColor.GREEN));
            if (bar != null) player.showBossBar(bar);
        }
        return true;
    }

    /**
     * オートマッチング (最適なマップを自動選択)
     */
    public void autoMatch(Player player) {
        // すでにレイド中なら中断
        if (isInRaid(player)) {
            player.sendMessage(Component.text("すでにレイドに参加中です。", NamedTextColor.RED));
            return;
        }

        com.lunar_prototype.impossbleEscapeMC.modules.party.Party party = com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule.get().getParty(player.getUniqueId());
        if (party != null && !party.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("パーティリーダーのみがマッチングを開始できます。", NamedTextColor.RED));
            return;
        }

        List<String> mapIds = getMapIds();
        if (mapIds.isEmpty()) return;

        // 1. 途中参加可能なレイドを優先
        String bestMapId = null;

        // 1a. すでにレイドインスタンス（タスク）が動いているものを最優先
        for (String id : mapIds) {
            long age = (System.currentTimeMillis() - lastCycleStartTime.getOrDefault(id, 0L)) / 1000;
            RaidInstance active = activeRaids.get(id);
            if (age < LATE_JOIN_WINDOW && active != null && !finishedThisCycle(id, player)) {
                bestMapId = id;
                break;
            }
        }

        // 1b. 空いているマップは周期を待たずに今すぐ始まる (startRaidOnDemand) ので次に優先
        if (bestMapId == null) {
            for (String id : mapIds) {
                if (!activeRaids.containsKey(id)) {
                    bestMapId = id;
                    break;
                }
            }
        }

        // 2. なければ待機時間の最も短い (mapTimerが小さい) マップを選択
        if (bestMapId == null) {
            int shortestWait = Integer.MAX_VALUE;
            for (String id : mapIds) {
                int timeLeft = mapTimers.getOrDefault(id, CYCLE_DURATION);
                if (timeLeft < shortestWait) {
                    shortestWait = timeLeft;
                    bestMapId = id;
                }
            }
        }

        if (bestMapId != null) {
            final String finalMapId = bestMapId;
            if (party != null) {
                List<Player> members = new ArrayList<>();
                for (UUID member : party.getMembers()) {
                    Player p = Bukkit.getPlayer(member);
                    if (p != null) members.add(p);
                }
                joinQueue(members, finalMapId);
            } else {
                joinQueue(player, bestMapId);
            }
        }
    }

    /**
     * キューから離脱
     */
    public void leaveQueue(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : raidQueues.entrySet()) {
            if (entry.getValue().remove(player.getUniqueId())) {
                BossBar bar = queueBossBars.get(entry.getKey());
                if (bar != null) player.hideBossBar(bar);
            }
        }
    }

    public boolean isInQueue(Player player) {
        for (Set<UUID> queue : raidQueues.values()) {
            if (queue.contains(player.getUniqueId())) return true;
        }
        return false;
    }

    public boolean isInRaid(Player player) {
        return getRaidOf(player) != null;
    }

    /** プレイヤーが参加中のレイド。参加していなければ null */
    public RaidInstance getRaidOf(Player player) {
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) return raid;
        }
        return null;
    }

    public String getQueuedMap(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : raidQueues.entrySet()) {
            if (entry.getValue().contains(player.getUniqueId())) return entry.getKey();
        }
        return null;
    }

    public int getQueueCount(String mapId) {
        Set<UUID> queue = raidQueues.get(mapId);
        return queue != null ? queue.size() : 0;
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public int getGlobalTimeLeft() {
        // Fallback: 最初のマップの時間を返す
        if (mapTimers.isEmpty()) return CYCLE_DURATION;
        return mapTimers.values().iterator().next();
    }

    /** 今の周期 (進行中・途中参加) の時間帯・天候 */
    public RaidConditions getConditions(String mapId) {
        return currentConditions.get(mapId);
    }

    /** 次の周期 (今から待機列に入ると出撃する) の時間帯・天候 */
    public RaidConditions getNextConditions(String mapId) {
        return nextConditions.get(mapId);
    }

    /** 今の周期の時間帯・天候をレイドのワールドへ当てはめる (ワールドがまだ無ければ、レイドが始まる時に当てはめる) */
    void applyConditions(String mapId) {
        RaidConditions conditions = currentConditions.get(mapId);
        RaidMap map = maps.get(mapId);
        if (conditions != null && map != null) conditions.applyTo(Bukkit.getWorld(map.getWorldName()));
    }

    public int getMapTimeLeft(String mapId) {
        return mapTimers.getOrDefault(mapId, CYCLE_DURATION);
    }

    public long getMapLastStartTime(String mapId) {
        return lastCycleStartTime.getOrDefault(mapId, 0L);
    }

    public void forceStartCycle() {
        forceStartCycle(null);
    }

    /**
     * 周期を今すぐ始め直す。マップを指定した時は、そのマップを基準にほかのマップを等間隔に並べ直す
     * (レイド中・出撃待ちの人がいるマップは急に動かさず、周期の終わりごとに少しずつ寄せる)。
     * 指定しない時は全部を始め直し、枠の後ろのマップほど最初の周期を長くして等間隔にする (レイドを短くしない)
     */
    public void forceStartCycle(String targetMapId) {
        List<String> order = rotationOrder();
        if (targetMapId != null) {
            if (!maps.containsKey(targetMapId)) return;
            onMapCycleEnd(targetMapId);
            mapTimers.put(targetMapId, CYCLE_DURATION);
            // 始め直したマップが、ちょうど自分の枠の始まりにいることにする
            rotationElapsed = RaidRotation.slot(CYCLE_DURATION, order.size(), order.indexOf(targetMapId));
            for (int i = 0; i < order.size(); i++) {
                String mapId = order.get(i);
                if (!mapId.equals(targetMapId) && !isBusy(mapId)) placeOnSlot(mapId, order.size(), i);
            }
            return;
        }

        rotationElapsed = 0;
        for (int i = 0; i < order.size(); i++) {
            String mapId = order.get(i);
            onMapCycleEnd(mapId);
            mapTimers.put(mapId, CYCLE_DURATION + RaidRotation.slot(CYCLE_DURATION, order.size(), i));
        }
    }

    public RaidInstance getActiveRaid(String mapId) {
        return activeRaids.get(mapId);
    }

    /** サーバーを止める時: 結果を出さずに止める (参加者は saveState が覚え、再起動の後に戻す) */
    public void suspendAllRaids() {
        for (RaidInstance raid : new ArrayList<>(activeRaids.values())) {
            raid.suspend();
        }
        activeRaids.clear();
    }

    public void removeRaid(String mapId) {
        activeRaids.remove(mapId);
    }

    /**
     * まだ登録されているのが同じインスタンスの時だけ外す。
     * 終了処理が遅れて走った古いインスタンスが、新しく始まったインスタンスを消してしまわないようにする。
     */
    public void removeRaid(String mapId, RaidInstance raid) {
        activeRaids.remove(mapId, raid);
    }

    public void onPlayerDeath(Player player) {
        for (RaidInstance raid : activeRaids.values()) {
            if (raid.isParticipant(player.getUniqueId())) {
                raid.onPlayerDeath(player);
                // Mark for failure effect on respawn
                player.setMetadata("raid_death_failure", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                return;
            }
        }
    }

    public void applyFailureEffect(Player player) {
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 100, 0, false, false, false));
        player.playSound(player.getLocation(), "minecraft:custom.death", org.bukkit.SoundCategory.MASTER, 2.0f, 1.0f);
    }

    public void onPlayerQuit(Player player) {
        leaveQueue(player);
        for (Map.Entry<String, RaidInstance> entry : new ArrayList<>(activeRaids.entrySet())) {
            if (entry.getValue().isParticipant(player.getUniqueId())) {
                disconnected.put(player.getUniqueId(), new Disconnect(entry.getKey(), lastCycleStartTime.getOrDefault(entry.getKey(), 0L)));
                entry.getValue().onPlayerQuit(player);
                return;
            }
        }
    }

    private boolean finishedThisCycle(String mapId, Player player) {
        return finishedThisCycle.getOrDefault(mapId, Set.of()).contains(player.getUniqueId());
    }

    /** この周期で死亡・脱出した (同じ周期に途中参加し直させない) */
    public void markFinished(String mapId, UUID player) {
        finishedThisCycle.computeIfAbsent(mapId, k -> new HashSet<>()).add(player);
    }

    /**
     * 参加した時: レイドの参加者 (再起動で戻した人) なら表示を戻す。レイドの途中で切断した人は、同じ周期のうちならレイドに戻し、
     * 周期が終わっていれば (MIA と同じく) 装備を失ってロビーへ。覚えていない人がレイドのワールドに残っていた時も同じ
     * (切断したまま周期が終わった人・再起動の前に切断した人)
     */
    public void onPlayerJoin(Player player) {
        UUID id = player.getUniqueId();
        RaidInstance current = getRaidOf(player);
        if (current != null) {
            disconnected.remove(id);
            current.onParticipantJoin(player);
            return;
        }
        Disconnect d = disconnected.remove(id);
        RaidMap map = d != null ? maps.get(d.mapId()) : null;
        if (map != null && player.getWorld().getName().equals(map.getWorldName())
                && lastCycleStartTime.getOrDefault(d.mapId(), 0L) == d.cycleStart()) {
            RaidInstance raid = activeRaids.get(d.mapId());
            if (raid == null) {
                raid = createRaid(d.mapId(), new ArrayList<>());
            }
            if (raid != null && raid.rejoin(player)) return;
        }
        boolean inRaidWorld = maps.values().stream().anyMatch(m -> player.getWorld().getName().equals(m.getWorldName()));
        if (!inRaidWorld) return;
        // 覚えていない人は、遊んでいる人 (サバイバル・アドベンチャー) だけ (マップを直している運営は除く)
        GameMode mode = player.getGameMode();
        if (d == null && mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return;
        forfeitAfterDisconnect(player);
    }

    /** 切断したまま周期が終わった: MIA と同じく装備をすべて失い、ロビーへ */
    private void forfeitAfterDisconnect(Player player) {
        player.closeInventory();
        player.getInventory().clear();
        player.setItemOnCursor(null);
        player.setGameMode(GameMode.ADVENTURE);
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        player.sendMessage(Component.text("MIA: レイド中に切断したまま時間切れになったため、装備をすべて失いました", NamedTextColor.RED));
        applyFailureEffect(player);
        plugin.getRaidMapManager().updateMapSlot(player);
    }

    /**
     * 全てのアクティブなレイドインスタンスに対して、指定したスカブ（NPC）の死亡を通知する。
     *
     * @param uuid 通知対象のスカブの UUID
     */
    public void onScavDeath(UUID uuid) {
        for (RaidInstance raid : activeRaids.values()) {
            raid.onScavDeath(uuid);
        }
    }

    /**
     * 指定したマップのアクティブなレイドに対して、プレイヤーによるスカブの撃破を通知する。
     *
     * 指定した `mapId` に対応するアクティブなレイドが存在する場合、そのレイドの
     * `onScavKilledByPlayer` を呼び出して撃破イベントを委譲する。該当するレイドがなければ何もしない。
     *
     * @param mapId     対象のマップ識別子
     * @param scavUuid  撃破されたスカブの UUID
     * @param killerUuid 撃破を行ったプレイヤーの UUID
     */
    public void onScavKilledByPlayer(String mapId, UUID scavUuid, UUID killerUuid, ScavBrain.BrainLevel brainLevel) {
        RaidInstance raid = activeRaids.get(mapId);
        if (raid == null) return;
        raid.onScavKilledByPlayer(scavUuid, killerUuid, brainLevel);
    }

    /**
     * 指定した識別子で新しいレイドマップを作成して永続化する。
     * 既に同じ識別子のマップが存在する場合は何もしない。
     *
     * @param mapId 作成するマップの一意の識別子（マップファイル名およびマップ集合のキーとして用いる）
     */
    public void createMap(String mapId) {
        if (maps.containsKey(mapId)) return;
        RaidMap map = new RaidMap(mapId);
        maps.put(mapId, map);
        saveMap(map);
    }

    public void deleteMap(String mapId) {
        maps.remove(mapId);
        File file = new File(mapsFolder, mapId + ".json");
        if (file.exists()) {
            file.delete();
        }
    }

    public RaidMap getMap(String mapId) {
        return maps.get(mapId);
    }

    public List<String> getMapIds() {
        return new ArrayList<>(maps.keySet());
    }

    public Map<String, RaidMap> getMaps() {
        return maps;
    }

    public Collection<RaidInstance> getActiveRaids() {
        return activeRaids.values();
    }

    public void saveMap(RaidMap map) {
        File file = new File(mapsFolder, map.getMapId() + ".json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(map, writer);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            plugin.getLogger().warning("Failed to save raid map: " + map.getMapId());
            e.printStackTrace();
        }
    }

    private void loadMaps() {
        File[] files = mapsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                RaidMap map = gson.fromJson(reader, RaidMap.class);
                if (map != null) {
                    maps.put(map.getMapId(), map);
                }
            } catch (IOException | com.google.gson.JsonSyntaxException e) {
                plugin.getLogger().warning("Failed to load raid map from: " + file.getName());
                e.printStackTrace();
            }
        }
        plugin.getLogger().info("Loaded " + maps.size() + " raid maps.");
    }
}
