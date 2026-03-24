package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RaidInstance {
    private final ImpossbleEscapeMC plugin;
    private final RaidMap map;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> raidMembers = new HashSet<>();
    private final List<RaidMap.ExtractionPoint> activeExtractions = new ArrayList<>();
    private final Map<UUID, Integer> extractionTimer = new HashMap<>();
    private final List<VirtualScav> virtualScavs = new ArrayList<>();
    private final Map<UUID, RaidResult> raidResults = new HashMap<>();

    private final long startTime;
    private final String raidSessionId;
    private BukkitRunnable task;
    private BossBar bossBar;
    private int respawnTicks = 0;
    private String endReason = "raid_end";
    private boolean ended = false;

    private enum RaidOutcome {
        SURVIVED("生還"),
        DEAD("死亡"),
        MIA("MIA"),
        LEFT("離脱"),
        ACTIVE("進行中");

        private final String displayName;

        /**
         * 列挙定数に表示用の名前を設定するコンストラクタ。
         *
         * @param displayName ユーザー向けに表示する名前（例: 日本語の表示名）
         */
        RaidOutcome(String displayName) {
            this.displayName = displayName;
        }
    }

    private static class RaidResult {
        private RaidOutcome outcome = RaidOutcome.ACTIVE;
        private long experienceToGrant = 0L;
        private int scavKills = 0;
        private boolean extractionCountIncremented = false;
    }

    /**
     * 新しいレイドセッションを初期化し、仮想スカブの作成、抽出ポイントの選定、定期更新タスクの開始、および参加プレイヤーの参加処理を行う。
     *
     * 起動時にAIレイドロガーが利用可能であればレイドセッションを開始し、マップ定義に基づいて仮想スカブを登録します。指定された参加者リストが空でない場合はその参加者をレイドへ参加させます。
     *
     * @param plugin      プラグインのエントリポイント（モジュールやサービス取得に使用）
     * @param map         このインスタンスが管理するレイド用マップ定義
     * @param participants 初期参加者のリスト（空の場合は参加処理を行わない）
     */
    public RaidInstance(ImpossbleEscapeMC plugin, RaidMap map, List<Player> participants) {
        this.plugin = plugin;
        this.map = map;
        this.startTime = System.currentTimeMillis();
        this.raidSessionId = map.getMapId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        if (plugin.getAiRaidLogger() != null) {
            plugin.getAiRaidLogger().startRaidSession(
                    raidSessionId,
                    map.getMapId(),
                    map.getWorldName(),
                    Bukkit.getCurrentTick(),
                    startTime
            );
        }

        for (RaidMap.ScavSpawnPoint sp : map.getScavSpawnPoints()) {
            virtualScavs.add(new VirtualScav(sp.getLocation(map.getWorldName()), sp.isPermanent()));
        }

        assignExtractions();
        startTask();

        if (!participants.isEmpty()) {
            joinPlayers(participants);
        }
    }

    private void assignExtractions() {
        List<RaidMap.ExtractionPoint> all = new ArrayList<>(map.getExtractionPoints());
        if (all.isEmpty()) {
            throw new IllegalStateException("Raid map '" + map.getMapId() + "' has no extraction points");
        }
        Collections.shuffle(all);
        int count = Math.max(1, all.size() / 2);
        for (int i = 0; i < count; i++) {
            activeExtractions.add(all.get(i));
        }
    }

    private Location findSafeSpawn(List<Location> spawns, Set<Location> usedThisWave) {
        if (spawns.isEmpty()) return null;

        List<Location> safeSpawns = new ArrayList<>();
        Location bestFallback = null;
        double maxMinDist = -1.0;

        for (Location spawn : spawns) {
            double minDistToPlayer = Double.MAX_VALUE;
            boolean someoneNear = false;

            // 既存のレイド内プレイヤーとの距離
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.getWorld().equals(spawn.getWorld())) continue;

                double dist = p.getLocation().distance(spawn);
                if (dist < 50.0) someoneNear = true;
                if (dist < minDistToPlayer) minDistToPlayer = dist;
            }

            // この出撃ウェーブですでに割り当てられた地点との距離
            for (Location used : usedThisWave) {
                if (!used.getWorld().equals(spawn.getWorld())) continue;
                double dist = used.distance(spawn);
                if (dist < 50.0) someoneNear = true;
                if (dist < minDistToPlayer) minDistToPlayer = dist;
            }

            if (!someoneNear && !usedThisWave.contains(spawn)) {
                safeSpawns.add(spawn);
            }

            if (minDistToPlayer > maxMinDist) {
                maxMinDist = minDistToPlayer;
                bestFallback = spawn;
            }
        }

        if (!safeSpawns.isEmpty()) {
            return safeSpawns.get(new Random().nextInt(safeSpawns.size()));
        }
        return bestFallback != null ? bestFallback : spawns.get(0);
    }

    private void playStartEffect(Player p) {
        p.sendMessage(Component.text("レイド開始: " + map.getMapId(), NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD));
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

        // タイトル表示
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("RAID START", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD),
                Component.text(map.getMapId() + " へ出撃しました", NamedTextColor.YELLOW),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(500))
        ));
    }

    private void applySpawnProtection(Player p) {
        p.setInvulnerable(true);
        p.sendMessage(Component.text("スポーン保護が3秒間有効です。", NamedTextColor.AQUA));
        new BukkitRunnable() {
            @Override
            public void run() {
                if (p.isOnline()) {
                    p.setInvulnerable(false);
                    p.sendMessage(Component.text("スポーン保護が終了しました。", NamedTextColor.RED));
                }
            }
        }.runTaskLater(plugin, 60); // 3 seconds (20 ticks * 3)
    }

    private void showExtractions(Player p) {
        p.sendMessage(Component.text("利用可能な脱出地点:", NamedTextColor.YELLOW));
        for (RaidMap.ExtractionPoint ep : activeExtractions) {
            p.sendMessage(Component.text("- " + ep.getName(), NamedTextColor.WHITE));
        }
    }

    /**
     * レイドのUI（ボスバー）を初期化し、1秒間隔で実行されるレイドの定期更新タスクを開始する。
     *
     * 定期タスクはボスバーの更新、参加者のゲームモード強制、抽出判定、仮想スカブのスポーン管理、プレイヤースナップショットのログ記録を行い、
     * 定期的に死んだスカブの復活チェックを実行します（内部での周期管理あり）。
     */
    private void startTask() {
        bossBar = BossBar.bossBar(
                Component.text("残りレイド時間: 計算中..."),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showBossBar(bossBar);
            }
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                updateBossBar();
                enforceRaidGamemode();
                checkExtractions();
                updateScavs();
                logPlayerSnapshots();

                respawnTicks++;
                if (respawnTicks >= 3600) { // 180 seconds = 3 mins
                    respawnDeadScavs();
                    respawnTicks = 0;
                }
            }
        };
        task.runTaskTimer(plugin, 0, 20);
    }

    /**
     * 現在このインスタンスに参加しているオンラインプレイヤーのゲームモードをすべてAdventureに設定する。
     *
     * オフラインのUUIDは無視される。
     */
    private void enforceRaidGamemode() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }
    }

    /**
     * 現在のレイド参加者について、利用可能なAIレイドロガーへプレイヤーのスナップショットを記録する。
     *
     * AIレイドロガーが存在し有効な場合にのみ、現在の参加者集合を走査してオンラインの各プレイヤーに対し
     * `plugin.getAiRaidLogger().logPlayerSnapshot(raidSessionId, p)` を呼び出す。
     */
    private void logPlayerSnapshots() {
        if (plugin.getAiRaidLogger() == null || !plugin.getAiRaidLogger().isEnabled()) return;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                plugin.getAiRaidLogger().logPlayerSnapshot(raidSessionId, p);
            }
        }
    }

    private void respawnDeadScavs() {
        for (VirtualScav vs : virtualScavs) {
            if (vs.isDead()) {
                // Check if players are far away (64 blocks)
                boolean playerNearby = false;
                Location loc = vs.getSpawnLocation();
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.getWorld().equals(loc.getWorld()) && p.getLocation().distance(loc) < 64) {
                        playerNearby = true;
                        break;
                    }
                }

                if (!playerNearby) {
                    vs.setDead(false);
                }
            }
        }
    }

    private void updateBossBar() {
        int timeLeft = plugin.getRaidModule().getGlobalTimeLeft();
        String timeStr = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        bossBar.name(Component.text("残りレイド時間: " + timeStr, NamedTextColor.WHITE));
        bossBar.progress((float) timeLeft / (float) RaidModule.CYCLE_DURATION);
    }

    /**
     * サイクル終了時に現在参加中の全プレイヤーを「MIA」として処理し、関連状態を更新する。
     *
     * 詳細: endReason を "mia_cycle_end" に設定し、各参加者について抽出タイマーを削除して
     * RaidResult を `MIA` に設定する。オンラインのプレイヤーには失敗メッセージを送信し、
     * ゲームモードを Adventure に変更してメインワールドのスポーンへテレポートし、ボスバーを非表示にし、
     * 失敗エフェクトを適用してマップスロットを更新する。最後に参加者集合をクリアする。
     */
    public void handleMIA() {
        endReason = "mia_cycle_end";
        for (UUID uuid : new HashSet<>(players)) {
            extractionTimer.remove(uuid);
            RaidResult result = getOrCreateRaidResult(uuid);
            result.outcome = RaidOutcome.MIA;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text("脱出に失敗しました (MIA)。", NamedTextColor.RED));
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                p.hideBossBar(bossBar);
                plugin.getRaidModule().applyFailureEffect(p);
                plugin.getRaidMapManager().updateMapSlot(p);
            }
        }
        players.clear();
    }

    public void resetCycle() {
        activeExtractions.clear();
        assignExtractions();
        // Cleanup SCAVs for the new cycle
        for (VirtualScav vs : virtualScavs) {
            if (vs.isSpawned() && vs.getEntityId() != null) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }
    }

    private void updateScavs() {
        for (VirtualScav vs : virtualScavs) {
            boolean playerNearby = false;
            Location spawnLoc = vs.getSpawnLocation();

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.getWorld().equals(spawnLoc.getWorld()) && p.getLocation().distance(spawnLoc) < 64) {
                    playerNearby = true;
                    break;
                }
            }

            if (playerNearby && !vs.isSpawned() && !vs.isDead()) {
                UUID uuid = plugin.getScavSpawner().spawnScav(spawnLoc, raidSessionId, map.getMapId());
                vs.setEntityId(uuid);
                vs.setSpawned(true);
            } else if (!playerNearby && vs.isSpawned() && !vs.isPermanent()) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }
    }

    /**
     * 与えられたエンティティIDに対応する仮想SCAVを死亡状態にして、スポーン状態とエンティティ参照をクリアする。
     *
     * @param entityId 死亡したエンティティのUUID。該当する仮想SCAVが存在しない場合は何もしない。
     */
    public void onScavDeath(UUID entityId) {
        for (VirtualScav vs : virtualScavs) {
            if (entityId.equals(vs.getEntityId())) {
                vs.setDead(true);
                vs.setSpawned(false);
                vs.setEntityId(null);
                break;
            }
        }
    }

    /**
     * プレイヤーによるSCAVの撃破をこのレイドの集計に反映する。
     *
     * 指定したKillerがこのレイドの参加者（raidMembers）であり、指定したSCAV実体IDがこのレイドに属する場合に、
     * そのプレイヤーの `RaidResult.scavKills` を1増やし `RaidResult.experienceToGrant` に50を加算する。
     *
     * @param scavEntityId 撃破されたSCAVのエンティティUUID
     * @param killerUuid   撃破を行ったプレイヤーのUUID
     */
    public void onScavKilledByPlayer(UUID scavEntityId, UUID killerUuid) {
        if (!raidMembers.contains(killerUuid)) return;

        boolean scavBelongsToThisRaid = false;
        for (VirtualScav vs : virtualScavs) {
            if (scavEntityId.equals(vs.getEntityId())) {
                scavBelongsToThisRaid = true;
                break;
            }
        }
        if (!scavBelongsToThisRaid) return;

        RaidResult result = getOrCreateRaidResult(killerUuid);
        result.scavKills += 1;
        result.experienceToGrant += 50L;
    }

    /**
     * 現在参加中の各プレイヤーを確認し、アクティブな抽出ポイントにいる場合は抽出処理を開始し、
     * どの抽出ゾーンにもいない場合はそのプレイヤーの抽出進捗をクリアする。
     *
     * <p>オンラインでないプレイヤーは無視される。各プレイヤーは最初に見つかった抽出ポイントのみで処理される。</p>
     */
    private void checkExtractions() {
        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            boolean inZone = false;
            for (RaidMap.ExtractionPoint ep : activeExtractions) {
                Location epLoc = ep.getLocation(map.getWorldName());
                if (epLoc != null
                        && p.getWorld().equals(epLoc.getWorld())
                        && p.getLocation().distance(epLoc) <= ep.getRadius()) {
                    inZone = true;
                    handleExtraction(p, ep);
                    break;
                }
            }

            if (!inZone) {
                extractionTimer.remove(uuid);
            }
        }
    }

    /**
     * プレイヤーの抽出進行を更新し、進行中は残り時間表示と効果音を送信、完了時に脱出成功処理を行う。
     *
     * カウントをインクリメントして残り時間がある間はアクションバー表示とクリック音を再生する。カウントが完了した場合は
     * 当該プレイヤーのリザルトを `SURVIVED` に設定して経験値を蓄積し（経験値はレイド終了時に付与される）、永続的な
     * 脱出回数を一度だけインクリメントする。その後プレイヤーをアクティブ参加者から除外し、ボスバーを非表示にして
     * アドベンチャーモードへ戻し、メインワールドのスポーンへテレポートしてマップスロットを更新する。参加者が0人になればレイドを終了する。
     *
     * @param p 進行を更新するプレイヤー
     * @param ep 対象の抽出ポイント（表示名と半径を含む）
     */
    private void handleExtraction(Player p, RaidMap.ExtractionPoint ep) {
        int count = extractionTimer.getOrDefault(p.getUniqueId(), 0);
        count++;
        extractionTimer.put(p.getUniqueId(), count);

        int remaining = 10 - count;
        if (remaining > 0) {
            p.sendActionBar(Component.text("脱出中... " + remaining + "s (" + ep.getName() + ")", NamedTextColor.GREEN));
            // カウントダウン音 (1秒ごと)
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.5f);
        } else {
            extractionTimer.remove(p.getUniqueId());
            p.sendMessage(Component.text("脱出に成功しました！", NamedTextColor.GREEN));
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // 脱出成功のリザルトを記録 (経験値はレイド終了時に付与)
            RaidResult result = getOrCreateRaidResult(p.getUniqueId());
            result.outcome = RaidOutcome.SURVIVED;
            result.experienceToGrant += 250L;

            // 脱出回数加算
            com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule dataModule =
                    plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule.class);
            if (dataModule != null && !result.extractionCountIncremented) {
                com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData data = dataModule.getPlayerData(p.getUniqueId());
                data.setExtractions(data.getExtractions() + 1);
                dataModule.saveAsync(p.getUniqueId());
                result.extractionCountIncremented = true;
            }

            players.remove(p.getUniqueId());
            p.hideBossBar(bossBar);
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);

            // メインワールド（オーバーワールド）の初期スポーン地点へテレポート
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            plugin.getRaidMapManager().updateMapSlot(p);

            if (players.isEmpty()) {
                endRaid();
            }
        }
    }

    /**
     * レイドを最終的に終了し、関連するリソースと状態をクリーンアップする。
     *
     * 具体的には：二重終了を防ぎ、スケジュールタスクを停止し、出現中のSCAVを削除し、
     * まだ `ACTIVE` な参加者の結果を `MIA` に設定してボスバーを非表示にし通知を送り、
     * 参加者リストをクリアして蓄積された報酬を付与・結果表示し、レイドをモジュールから削除し、
     * 必要ならAIレイドロガーのセッションを終了する。
     */
    private void endRaid() {
        if (ended) return;
        ended = true;

        if (task != null) task.cancel();

        // Cleanup all spawned SCAVs
        for (VirtualScav vs : virtualScavs) {
            if (vs.isSpawned() && vs.getEntityId() != null) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                RaidResult result = getOrCreateRaidResult(uuid);
                if (result.outcome == RaidOutcome.ACTIVE) {
                    result.outcome = RaidOutcome.MIA;
                }
                p.hideBossBar(bossBar);
                p.sendMessage(Component.text("レイドが終了しました。", NamedTextColor.RED));
            }
        }
        players.clear();
        applyRaidRewardsAndShowResults();
        plugin.getRaidModule().removeRaid(map.getMapId());
        if (plugin.getAiRaidLogger() != null) {
            plugin.getAiRaidLogger().endRaidSession(raidSessionId, endReason);
        }
    }

    /**
     * 参加者の死亡を処理し、対応するレイド結果を記録・更新する。
     *
     * プレイヤーがこのレイドの参加者である場合、抽出タイマーを削除し、該当プレイヤーの結果を
     * `DEAD` に設定して経験値を加算し、参加者リストから除外する。最後の参加者が失われた場合は
     * レイドを終了する。
     *
     * @param player 死亡したプレイヤー
     */
    public void onPlayerDeath(Player player) {
        if (!players.contains(player.getUniqueId())) return;
        extractionTimer.remove(player.getUniqueId());
        player.sendMessage(Component.text("死亡しました。レイド失敗です。", NamedTextColor.RED));

        // 死亡救済のリザルトを記録 (経験値はレイド終了時に付与)
        RaidResult result = getOrCreateRaidResult(player.getUniqueId());
        result.outcome = RaidOutcome.DEAD;
        result.experienceToGrant += 150L;

        players.remove(player.getUniqueId());
        player.hideBossBar(bossBar);
        plugin.getRaidMapManager().updateMapSlot(player);

        if (players.isEmpty()) {
            endReason = "all_dead_or_extracted";
            endRaid();
        }
    }

    /**
     * プレイヤーがサーバーを離脱したときに、そのプレイヤーを現在のレイド参加者から除外し、状態を記録する。
     *
     * プレイヤーがこのレイドの参加者でない場合は何もしない。参加者であれば抽出タイマーを削除し、
     * そのプレイヤーの `RaidResult` がまだ `ACTIVE` であれば `LEFT` に設定してから参加者集合を除去する。
     * 参加者がこれによって空になった場合は `endRaid()` を呼び出してレイドを終了する。
     *
     * @param player サーバーを離れたプレイヤー
     */
    public void onPlayerQuit(Player player) {
        if (!players.contains(player.getUniqueId())) return;

        extractionTimer.remove(player.getUniqueId());
        RaidResult result = getOrCreateRaidResult(player.getUniqueId());
        if (result.outcome == RaidOutcome.ACTIVE) {
            result.outcome = RaidOutcome.LEFT;
        }
        players.remove(player.getUniqueId());
        if (players.isEmpty()) {
            endReason = "all_left";
            endRaid();
        }
    }

    public void stop() {
        endRaid();
    }

    public void joinPlayer(Player player) {
        joinPlayers(Collections.singletonList(player));
    }

    public void joinPlayers(List<Player> participants) {
        // グループ化 (PartyManagerを利用)
        com.lunar_prototype.impossbleEscapeMC.party.PartyManager partyManager = plugin.getPartyManager();
        Map<com.lunar_prototype.impossbleEscapeMC.party.Party, List<Player>> groupMap = new HashMap<>();
        List<Player> soloPlayers = new ArrayList<>();

        for (Player p : participants) {
            com.lunar_prototype.impossbleEscapeMC.party.Party party = partyManager.getParty(p.getUniqueId());
            if (party != null) {
                groupMap.computeIfAbsent(party, k -> new ArrayList<>()).add(p);
            } else {
                soloPlayers.add(p);
            }
        }

        List<Location> spawns = map.getSpawnPoints();
        Set<Location> usedThisWave = new HashSet<>();
        Random random = new Random();

        // パーティーごとに異なる地点へスポーン
        for (List<Player> group : groupMap.values()) {
            spawnGroup(group, spawns, usedThisWave, random);
        }

        // ソロプレイヤーごとに異なる地点へスポーン
        for (Player solo : soloPlayers) {
            spawnGroup(Collections.singletonList(solo), spawns, usedThisWave, random);
        }
    }

    /**
     * 指定したプレイヤー群を安全なスポーン中心の付近に配置し、レイド参加状態と表示を初期化する。
     *
     * 各プレイヤーをこのインスタンスの参加者（およびraidMembers）として登録し、ボスバー表示とアドベンチャーモードを適用する。中心スポーンが決定されている場合は各プレイヤーを中心の近傍にランダムな小オフセットでテレポートし、スポーン保護や開始演出、抽出ポイント表示、マップスロット更新を行う。また各プレイヤー用のRaidResultを確実に作成する。
     *
     * @param group        一緒にスポーンさせるプレイヤーのリスト（パーティー単位など）
     * @param spawns       利用可能なスポーン候補の位置リスト
     * @param usedThisWave そのウェーブ内で既に使用されたスポーン位置を記録する集合（選ばれた中心スポーンはここに追加される）
     * @param random       オフセット等の乱数生成に使う Random インスタンス
     */
    private void spawnGroup(List<Player> group, List<Location> spawns, Set<Location> usedThisWave, Random random) {
        Location centerSpawn = findSafeSpawn(spawns, usedThisWave);
        if (centerSpawn != null) {
            usedThisWave.add(centerSpawn);
        }

        for (Player player : group) {
            players.add(player.getUniqueId());
            raidMembers.add(player.getUniqueId());
            getOrCreateRaidResult(player.getUniqueId());
            player.showBossBar(bossBar);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);

            if (centerSpawn != null) {
                // パーティーメンバー同士が重ならないよう、わずかなオフセットを加える
                double offsetX = (random.nextDouble() - 0.5) * 1.5;
                double offsetZ = (random.nextDouble() - 0.5) * 1.5;
                Location spawnLoc = centerSpawn.clone().add(offsetX, 0.1, offsetZ); // 足が埋まらないよう少し浮かせる
                
                player.teleport(spawnLoc);
                applySpawnProtection(player);
            }

            playStartEffect(player);
            showExtractions(player);
            plugin.getRaidMapManager().updateMapSlot(player);
        }
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getTimeLeft() {
        return plugin.getRaidModule().getGlobalTimeLeft();
    }

    public boolean isParticipant(UUID uuid) {
        return players.contains(uuid);
    }

    /**
     * 指定したプレイヤー UUID の集合をこのレイドの参加者として復帰させる。
     *
     * <p>内部の参加者トラッキング（現在参加中の set と raidMembers）に追加し、
     * 各 UUID について結果用のエントリを作成する。サーバ上でオンラインのプレイヤーには
     * ゲームモードを Adventure に設定し、ボスバーを表示して再参加メッセージを送信する。</p>
     *
     * @param uuids 復帰させるプレイヤーの UUID 集合
     */
    public void restoreParticipants(Set<UUID> uuids) {
        this.players.addAll(uuids);
        this.raidMembers.addAll(uuids);
        for (UUID uuid : uuids) {
            getOrCreateRaidResult(uuid);
        }
        for (UUID uuid : uuids) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.showBossBar(bossBar);
                p.sendMessage(Component.text("レイドへ再復帰しました。", NamedTextColor.GREEN));
            }
        }
    }

    /**
     * 現在このレイドインスタンスに参加しているプレイヤーの UUID 集合を取得する。
     *
     * @return `players` に含まれる参加者の UUID を保持する変更不可の Set。返された Set への変更は実際の参加者リストに影響しない。
     */
    public Set<UUID> getParticipantUuids() {
        return Collections.unmodifiableSet(players);
    }

    /**
     * 指定した UUID に対応する既存の RaidResult を返す。存在しない場合は新しく作成して格納した上で返す。
     *
     * @param uuid 対象プレイヤー（または参加者）の UUID
     * @return 指定 UUID に対応する既存または新規作成された RaidResult
     */
    private RaidResult getOrCreateRaidResult(UUID uuid) {
        return raidResults.computeIfAbsent(uuid, ignored -> new RaidResult());
    }

    /**
     * レイド参加者に蓄積された報酬を付与し、各参加者にレイド結果を表示する。
     *
     * <p>まだ `ACTIVE` のままの参加者は `MIA` として扱われ、各参加者に対して蓄積された経験値を付与し、
     * SCAVキル数・獲得EXP・最終結果をチャットに送信する。レベル管理モジュールが利用できない場合は何もしない。</p>
     */
    private void applyRaidRewardsAndShowResults() {
        com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
        if (levelModule == null) return;

        for (UUID uuid : raidMembers) {
            RaidResult result = getOrCreateRaidResult(uuid);
            if (result.outcome == RaidOutcome.ACTIVE) {
                result.outcome = RaidOutcome.MIA;
            }

            if (result.experienceToGrant > 0) {
                levelModule.addExperience(uuid, result.experienceToGrant);
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text("===== RAID RESULT =====", NamedTextColor.GOLD));
                player.sendMessage(Component.text("結果: " + result.outcome.displayName, NamedTextColor.YELLOW));
                player.sendMessage(Component.text("SCAVキル: " + result.scavKills, NamedTextColor.WHITE));
                player.sendMessage(Component.text("獲得EXP: +" + result.experienceToGrant, NamedTextColor.GREEN));
                player.sendMessage(Component.text("=======================", NamedTextColor.GOLD));
            }
        }
    }
}
