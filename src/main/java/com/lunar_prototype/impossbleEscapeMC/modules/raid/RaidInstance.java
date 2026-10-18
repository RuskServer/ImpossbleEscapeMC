package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavBrain;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.gui.UiIcon;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidKillTracker;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidReport;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.result.RaidResults;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Consumer;

public class RaidInstance {
    private final ImpossbleEscapeMC plugin;
    private final RaidMap map;
    private final Set<UUID> players = new HashSet<>();
    private final Set<UUID> raidMembers = new HashSet<>();
    /** 出撃時点の防具クラスから決めた、各参加者向けSCAV弾薬クラス上限。nullは調整なし */
    private final Map<UUID, Integer> scavAmmoClassCaps = new HashMap<>();
    private final List<RaidMap.ExtractionPoint> activeExtractions = new ArrayList<>();
    private final Map<UUID, Integer> extractionTimer = new HashMap<>();
    private final List<VirtualScav> virtualScavs = new ArrayList<>();
    private final Map<UUID, RaidResult> raidResults = new HashMap<>();
    private final RaidEndSequence endSequence;

    private final long startTime;
    private final String raidSessionId;
    private BukkitRunnable task;
    /** 中央上のコンパスと右上の残り時間 (プレイヤーごとのボスバー) */
    private final RaidHud hud;
    private int respawnTicks = 0;
    private String endReason = "raid_end";
    private boolean ended = false;
    private static final double MIN_SCAV_SPAWN_DISTANCE = 24.0;

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
        private int pmcKills = 0;
        private int bossKills = 0;
        private boolean extractionCountIncremented = false;
        /** 経験値の内訳 (理由 → {回数, 経験値})。リザルトの画面に出す */
        private final Map<String, long[]> expBreakdown = new LinkedHashMap<>();
        /** 倒した相手と、自分を倒した相手 (リザルトのキルログ) */
        private final List<RaidReport.Kill> kills = new ArrayList<>();
        private RaidReport.Kill death;
        private String extractName;
        /** 結果が決まった時刻 (レイドにいた時間) */
        private long endMillis;
        /** 持ち帰った物 (脱出した時に数える) と、失った物 (持ち込んで、死亡・MIA で失った物) の価値 */
        private List<RaidReport.LootStack> loot = List.of();
        private long lostValue;
        /** 経験値を付与した (付与する前のレベル・経験値を覚えておく) */
        private boolean granted;
        private int levelBefore;
        private long expBefore;
    }

    private static final String EXP_SURVIVED = "SURVIVED", EXP_DEAD = "DEAD", EXP_MIA = "MIA", EXP_LEFT = "LEFT",
            EXP_SCAV = "SCAV", EXP_BOSS = "BOSS", EXP_PMC = "PMC", EXP_SKILL = "SKILL";

    /** 経験値を足す (レイド終了時に付与する分) と、リザルトの内訳 */
    private static void grant(RaidResult result, String reason, long amount) {
        result.experienceToGrant += amount;
        long[] entry = result.expBreakdown.computeIfAbsent(reason, k -> new long[2]);
        entry[0]++;
        entry[1] += amount;
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
        this.endSequence = new RaidEndSequence(plugin, map);
        this.hud = new RaidHud(plugin, map, activeExtractions);
        // 時間帯・天候 (周期の始めに当てはめているが、その時ワールドが無かった場合のために、もう一度)
        if (plugin.getRaidModule() != null) plugin.getRaidModule().applyConditions(map.getMapId());

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

    public List<RaidMap.ExtractionPoint> getActiveExtractions() {
        return Collections.unmodifiableList(activeExtractions);
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

    /** レイド開始の音とタイトル (出撃時の PDA の演出が終わってから) */
    private void showStartTitle(Player p) {
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
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
        updateHudTime();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                hud.show(player);
            }
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                updateHudTime();
                enforceRaidGamemode();
                checkExtractions();
                endSequence.tick(getTimeLeft(), players);
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

    private void updateHudTime() {
        hud.setTimeLeft(plugin.getRaidModule().getMapTimeLeft(map.getMapId()));
    }

    /**
     * サイクル終了時に現在参加中の全プレイヤーを「MIA」として処理し、関連状態を更新する。
     *
     * 詳細: endReason を "mia_cycle_end" に設定し、各参加者について抽出タイマーを削除して
     * RaidResult を `MIA` に設定する。オンラインのプレイヤーには失敗メッセージを送信し、
     * ゲームモードを Adventure に変更してメインワールドのスポーンへテレポートし、ボスバーを非表示にし、
     * 失敗エフェクトを適用してマップスロットを更新する。最後に参加者集合をクリアする。
     * 時間内に脱出できなかった (MIA) 参加者は、持ち込んだ物も拾った物もすべて失う ({@link #forfeitLoadout})。
     */
    public void handleMIA() {
        endReason = "mia_cycle_end";

        for (UUID uuid : new HashSet<>(players)) {
            extractionTimer.remove(uuid);
            RaidResult result = getOrCreateRaidResult(uuid);
            result.outcome = RaidOutcome.MIA;
            result.endMillis = System.currentTimeMillis();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                result.lostValue = broughtInValue(p);
                forfeitLoadout(p);
                p.setGameMode(org.bukkit.GameMode.ADVENTURE);
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                hud.hide(p);
                resetCameraDistance(p);
                plugin.getRaidModule().applyFailureEffect(p);
                showIndividualRaidResult(p);
                plugin.getRaidMapManager().updateMapSlot(p);
            }
        }
        players.clear();
    }

    /**
     * MIA: 持ち物 (装備・オフハンド・バッグの中・カーソル・クラフト欄) をすべて失う。地図は updateMapSlot が渡し直す。
     * サーバー停止で終わったレイド (endRaid の MIA) は再起動後に戻れるので、ここは時間切れの時だけ
     */
    private void forfeitLoadout(Player p) {
        p.closeInventory();   // 開いているバッグ・クラフト欄の中身を持ち物に戻してから消す
        p.getInventory().clear();
        p.setItemOnCursor(null);
        p.sendMessage(Component.text("MIA: 時間内に脱出できなかったため、装備をすべて失いました", NamedTextColor.RED));
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
            boolean tooCloseOrVisible = false;
            Location spawnLoc = vs.getSpawnLocation();
            UUID nearestPlayerId = null;
            double nearestPlayerDistance = Double.MAX_VALUE;

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.getWorld().equals(spawnLoc.getWorld())) continue;

                double dist = p.getLocation().distance(spawnLoc);
                if (dist < 64) {
                    playerNearby = true;
                    if (dist < nearestPlayerDistance) {
                        nearestPlayerDistance = dist;
                        nearestPlayerId = uuid;
                    }
                }
                
                // 目の前湧き防止チェック
                if (dist < MIN_SCAV_SPAWN_DISTANCE || isLocationVisible(p, spawnLoc)) {
                    tooCloseOrVisible = true;
                }
            }

            if (playerNearby && !tooCloseOrVisible && !vs.isSpawned() && !vs.isDead()) {
                Integer ammoClassCap = nearestPlayerId == null ? null : scavAmmoClassCaps.get(nearestPlayerId);
                UUID uuid = plugin.getScavSpawner().spawnScav(spawnLoc, raidSessionId, map.getMapId(), ammoClassCap);
                if (uuid == null) continue;   // 出せなかった (銃が無い)。湧いていないままにして、次の判定でまた試す
                vs.setEntityId(uuid);
                vs.setSpawned(true);
            } else if (!playerNearby && vs.isSpawned() && !vs.isPermanent()) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }
    }

    /** 出撃時に装備しているヘルメット・胴防具のうち高い防具クラス。 */
    private int equippedArmorClass(Player player) {
        return Math.max(armorClass(player.getInventory().getHelmet()), armorClass(player.getInventory().getChestplate()));
    }

    private int armorClass(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, 0);
    }

    /** 弱装備に対してだけSCAV弾薬の上限を下げる。防具クラス4以上は通常の抽選のまま。 */
    private Integer scavAmmoClassCap(int armorClass) {
        if (armorClass >= 4) return null;
        if (armorClass >= 3) return 2;
        return 1;
    }

    private boolean isLocationVisible(Player player, Location target) {
        if (!player.getWorld().equals(target.getWorld())) return false;
        
        Location eye = player.getEyeLocation();
        org.bukkit.util.Vector toTarget = target.toVector().subtract(eye.toVector());
        double dist = toTarget.length();
        if (dist > 80) return false; // 描画距離外などは無視
        
        // 1. 方向チェック (視野角 約110度)
        org.bukkit.util.Vector dir = eye.getDirection();
        double dot = dir.dot(toTarget.normalize());
        if (dot < 0.3) return false; // 背後や真横なら見えていないとみなす

        // 2. 遮蔽物チェック (RayTrace)
        var result = player.getWorld().rayTraceBlocks(eye, toTarget.normalize(), dist, org.bukkit.FluidCollisionMode.NEVER, true);
        return result == null; // 何にも当たらなければ見える
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
    public void onScavKilledByPlayer(UUID scavEntityId, UUID killerUuid, ScavBrain.BrainLevel brainLevel) {
        if (!raidMembers.contains(killerUuid)) return;
        
        com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
        int playerLevel = 1;
        if (levelModule != null) {
            playerLevel = levelModule.getLevel(killerUuid);
        }

        // BOSS判定
        if (brainLevel == ScavBrain.BrainLevel.HIGH) {
            RaidResult result = getOrCreateRaidResult(killerUuid);
            result.bossKills += 1;
            grant(result, EXP_BOSS, levelModule != null ? levelModule.getScaledKillReward(playerLevel, 250L) : 250L);
            result.kills.add(killRecord(scavEntityId, killerUuid, RaidReport.Kind.BOSS, null));
        } else {
            RaidResult result = getOrCreateRaidResult(killerUuid);
            result.scavKills += 1;
            grant(result, EXP_SCAV, levelModule != null ? levelModule.getScaledKillReward(playerLevel, 50L) : 50L);
            result.kills.add(killRecord(scavEntityId, killerUuid, RaidReport.Kind.SCAV, null));
        }
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

        // 脱出にかかる時間 10 秒 (スキルで縮む)
        int remaining = Math.max(1, 10 + (int) SkillModule.value(p, SkillStat.EXTRACT_SECONDS)) - count;
        if (remaining > 0) {
            p.sendActionBar(Component.text("脱出中... " + remaining + "s (" + ep.getName() + ")", NamedTextColor.GREEN));
            // カウントダウン音 (1秒ごと)
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.5f);
        } else {
            extractionTimer.remove(p.getUniqueId());
            // 開いている画面 (漁る画面・バッグ) を先に閉じる。漁っている間は持ち物の欄がバックパックの表示になっていて、
            // 本来の持ち物は脇に避けてあるので、閉じて元に戻してから持ち物を読む (クエスト・持ち込みの印)
            p.closeInventory();

            // 脱出成功のリザルトを記録 (経験値はレイド終了時に付与)
            RaidResult result = getOrCreateRaidResult(p.getUniqueId());
            result.outcome = RaidOutcome.SURVIVED;
            result.extractName = ep.getName();
            plugin.getRaidModule().markFinished(map.getMapId(), p.getUniqueId());
            result.endMillis = System.currentTimeMillis();
            result.loot = foundLoot(p);

            com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                    plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
            grant(result, EXP_SURVIVED, levelModule != null
                    ? levelModule.getRaidOutcomeReward(levelModule.getLevel(p.getUniqueId()), "SURVIVED") : 250L);

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
            hud.hide(p);
            p.setGameMode(org.bukkit.GameMode.ADVENTURE);

            // クエストトリガーの発火
            com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule questModule = 
                    plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
            if (questModule != null) {
                if (dataModule != null) {
                    com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData data = dataModule.getPlayerData(p.getUniqueId());
                    Map<String, Object> params = new HashMap<>();
                    params.put("mapId", map.getMapId());
                    questModule.getEventBus().fire(p, data, com.lunar_prototype.impossbleEscapeMC.modules.quest.event.QuestTrigger.RAID_EXTRACT, params);
                }
            }

            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            resetCameraDistance(p);
            showIndividualRaidResult(p);
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
        hud.stop();
        endSequence.stop();

        // Cleanup all spawned SCAVs
        for (VirtualScav vs : virtualScavs) {
            if (vs.isSpawned() && vs.getEntityId() != null) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }

        // Cleanup corpses in the world
        if (plugin.getCorpseManager() != null) {
            org.bukkit.World world = Bukkit.getWorld(map.getWorldName());
            if (world != null) {
                plugin.getCorpseManager().cleanup(world);
            }
        }

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                RaidResult result = getOrCreateRaidResult(uuid);
                if (result.outcome == RaidOutcome.ACTIVE) {
                    result.outcome = RaidOutcome.MIA;
                }
                // 開いている画面 (漁る画面・バッグ) を先に閉じる。漁っている間は持ち物の欄がバックパックの表示になっていて、
                // 本来の持ち物は脇に避けてあるので、閉じて元に戻してから持ち物を読む (クエスト・持ち込みの印)
                p.closeInventory();
                clearRaidMarkersFromCurrentInventory(p);
                hud.hide(p);
                resetCameraDistance(p);
                showIndividualRaidResult(p);
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

        com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
        int playerLevel = 1;
        if (levelModule != null) {
            playerLevel = levelModule.getLevel(player.getUniqueId());
        }

        // 死亡のリザルトを記録 (経験値はレイド終了時に付与)
        RaidResult result = getOrCreateRaidResult(player.getUniqueId());
        result.outcome = RaidOutcome.DEAD;
        result.endMillis = System.currentTimeMillis();
        plugin.getRaidModule().markFinished(map.getMapId(), player.getUniqueId());
        result.lostValue = broughtInValue(player);
        grant(result, EXP_DEAD, levelModule != null ? levelModule.getRaidOutcomeReward(playerLevel, "DEAD") : 150L);
        RaidKillTracker.Hit lastHit = RaidKillTracker.lastHitOn(player.getUniqueId());
        if (lastHit != null) {
            result.death = new RaidReport.Kill(result.endMillis - startTime, lastHit.shooterName(), lastHit.shooterKind(),
                    lastHit.weapon(), lastHit.weaponName(), lastHit.ammoName(), lastHit.distance(), lastHit.part());
        }
        RaidKillTracker.forget(player.getUniqueId());

        // PMCキルの集計 (もしキラーがプレイヤーなら)
        Player killer = player.getKiller();
        if (killer != null && !killer.equals(player) && players.contains(killer.getUniqueId())) {
            RaidResult killerResult = getOrCreateRaidResult(killer.getUniqueId());
            killerResult.pmcKills += 1;
            grant(killerResult, EXP_PMC, levelModule != null
                    ? levelModule.getScaledKillReward(levelModule.getLevel(killer.getUniqueId()), 100L) : 100L);
            killerResult.kills.add(killRecord(player.getUniqueId(), killer.getUniqueId(), RaidReport.Kind.PMC, player.getName()));
        }

        players.remove(player.getUniqueId());
        hud.hide(player);
        resetCameraDistance(player);
        showIndividualRaidResult(player);
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
        // 開いている画面 (漁る画面・バッグ) を先に閉じる。漁っている間は持ち物の欄がバックパックの表示になっていて、
        // 本来の持ち物は脇に避けてあるので、閉じて元に戻す。持ち込みの印は残す: 同じ周期のうちに戻ればレイドを続け
        // ({@link #rejoin})、周期が終わっていれば装備を失う (RaidModule.onPlayerJoin)
        player.closeInventory();
        players.remove(player.getUniqueId());
        resetCameraDistance(player);
        if (players.isEmpty()) {
            endReason = "all_left";
            endRaid();
        }
    }

    public void stop() {
        endRaid();
    }

    public long getAgeInSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /** 途中参加 (パーティーはまとめて渡すと同じ所に出る) */
    public void joinInProgress(List<Player> group) {
        if (ended) return;
        joinPlayers(group);
    }

    public void joinPlayers(List<Player> participants) {
        // グループ化 (パーティーごと)
        com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule parties = com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule.get();
        Map<com.lunar_prototype.impossbleEscapeMC.modules.party.Party, List<Player>> groupMap = new HashMap<>();
        List<Player> soloPlayers = new ArrayList<>();

        for (Player p : participants) {
            com.lunar_prototype.impossbleEscapeMC.modules.party.Party party = parties.getParty(p.getUniqueId());
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
        // パーティーは最も装備が強いメンバーを基準にする。誰か一人でも高装備ならグループ全員を弱体化対象外にする。
        int groupArmorClass = group.stream().mapToInt(this::equippedArmorClass).max().orElse(0);
        Integer ammoClassCap = scavAmmoClassCap(groupArmorClass);

        Location centerSpawn = findSafeSpawn(spawns, usedThisWave);
        if (centerSpawn != null) {
            usedThisWave.add(centerSpawn);
        }

        for (Player player : group) {
            players.add(player.getUniqueId());
            raidMembers.add(player.getUniqueId());
            scavAmmoClassCaps.put(player.getUniqueId(), ammoClassCap);
            getOrCreateRaidResult(player.getUniqueId());
            markRaidBroughtInItems(player);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);

            if (centerSpawn != null) {
                // パーティーメンバー同士が重ならないよう、わずかなオフセットを加える
                double offsetX = (random.nextDouble() - 0.5) * 1.5;
                double offsetZ = (random.nextDouble() - 0.5) * 1.5;
                Location spawnLoc = centerSpawn.clone().add(offsetX, 0.1, offsetZ); // 足が埋まらないよう少し浮かせる
                
                player.teleport(spawnLoc);
            }
            boolean protect = centerSpawn != null;

            setRaidCameraDistance(player);
            plugin.getRaidMapManager().updateMapSlot(player);
            // 出撃時の PDA の演出 (脱出地点の戦術マップ)。しまい終えてから HUD・開始のタイトル・チャットの案内を出し、
            // スポーン保護を始める (演出の間は PdaIntro がダメージを止めている)
            UUID playerId = player.getUniqueId();
            PdaIntro.start(player, map, activeExtractions, getTimeLeft(), () -> {
                Player joined = Bukkit.getPlayer(playerId);
                if (joined == null || !players.contains(playerId)) return;
                hud.show(joined);
                showStartTitle(joined);
                joined.sendMessage(Component.text("レイド開始: " + map.getMapId(), NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD));
                RaidConditions conditions = plugin.getRaidModule() != null ? plugin.getRaidModule().getConditions(map.getMapId()) : null;
                if (conditions != null) {
                    joined.sendMessage(Component.text("[RAID] 現地: " + conditions.describe(), NamedTextColor.GRAY));
                }
                showExtractions(joined);
                if (protect) applySpawnProtection(joined);
            });
        }
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getTimeLeft() {
        return plugin.getRaidModule().getMapTimeLeft(map.getMapId());
    }

    public RaidEndSequence getEndSequence() {
        return endSequence;
    }

    public boolean isParticipant(UUID uuid) {
        return players.contains(uuid);
    }


    /**
     * 指定した UUID の集合をこのレイドの参加者として復帰させる。
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
            if (p != null) onParticipantJoin(p);
        }
    }

    /** 参加者が (サーバーの再起動の後に) 入ってきた: レイドの表示とゲームモードを戻す */
    public void onParticipantJoin(Player p) {
        p.setGameMode(org.bukkit.GameMode.ADVENTURE);
        hud.show(p);
        setRaidCameraDistance(p);
        p.sendMessage(Component.text("レイドへ再復帰しました。", NamedTextColor.GREEN));
    }

    /**
     * 切断した参加者が、同じ周期のうちに戻ってきた: 参加者に戻して続ける (切断した所から。持ち込みの印は切断の時に残してある)。
     * 終わったレイドなら false
     */
    public boolean rejoin(Player p) {
        if (ended) return false;
        players.add(p.getUniqueId());
        raidMembers.add(p.getUniqueId());
        RaidResult result = getOrCreateRaidResult(p.getUniqueId());
        if (result.outcome == RaidOutcome.LEFT) result.outcome = RaidOutcome.ACTIVE;
        onParticipantJoin(p);
        return true;
    }

    /**
     * サーバーを止める時: 結果を出さず・報酬も付けずに止める (再起動の後に RaidModule が参加者を戻して続ける)。
     * 持ち込みの印も残す。SCAV と表示は片付ける
     */
    public void suspend() {
        if (ended) return;
        ended = true;
        if (task != null) task.cancel();
        hud.stop();
        endSequence.stop();
        for (VirtualScav vs : virtualScavs) {
            if (vs.isSpawned() && vs.getEntityId() != null) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.closeInventory();
            hud.hide(p);
            resetCameraDistance(p);
        }
        if (plugin.getAiRaidLogger() != null) {
            plugin.getAiRaidLogger().endRaidSession(raidSessionId, "server_stop");
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

    private void markRaidBroughtInItems(Player player) {
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule != null) {
            backpackModule.saveBackpackFromOpenInventory(player);
        }

        visitPlayerItems(player, item -> {
            setBooleanFlag(item, PDCKeys.RAID_BROUGHT_IN, true);
            setBooleanFlag(item, PDCKeys.FIND_IN_RAID, false);
            setBooleanFlag(item, PDCKeys.DROPPED_BY_PLAYER, false);
        }, backpackModule);
    }

    private void clearRaidMarkersFromCurrentInventory(Player player) {
        BackpackModule backpackModule = plugin.getServiceContainer().get(BackpackModule.class);
        if (backpackModule != null) {
            backpackModule.saveBackpackFromOpenInventory(player);
        }

        visitPlayerItems(player, item -> setBooleanFlag(item, PDCKeys.RAID_BROUGHT_IN, false), backpackModule);
    }

    private void visitPlayerItems(Player player, Consumer<ItemStack> consumer, BackpackModule backpackModule) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            visitItemRecursive(item, consumer, backpackModule);
        }
        visitItemRecursive(inventory.getHelmet(), consumer, backpackModule);
        visitItemRecursive(inventory.getChestplate(), consumer, backpackModule);
        visitItemRecursive(inventory.getLeggings(), consumer, backpackModule);
        visitItemRecursive(inventory.getBoots(), consumer, backpackModule);
        visitItemRecursive(inventory.getItemInOffHand(), consumer, backpackModule);
    }

    private void visitItemRecursive(ItemStack item, Consumer<ItemStack> consumer, BackpackModule backpackModule) {
        if (item == null || item.getType().isAir()) return;

        consumer.accept(item);

        if (backpackModule == null || !backpackModule.isBackpackItem(item)) return;

        Inventory backpackInventory = backpackModule.loadBackpackInventory(item);
        for (ItemStack nested : backpackInventory.getContents()) {
            visitItemRecursive(nested, consumer, backpackModule);
        }
        backpackModule.saveBackpackInventory(item, backpackInventory);
        ItemFactory.updateLore(item);
    }

    private void setBooleanFlag(ItemStack item, NamespacedKey key, boolean value) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (value) {
            pdc.set(key, PDCKeys.BOOLEAN, (byte) 1);
        } else {
            pdc.remove(key);
        }
        item.setItemMeta(meta);
        ItemFactory.updateLore(item);
    }

    private void setRaidCameraDistance(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.CAMERA_DISTANCE);
        if (attr != null) {
            attr.setBaseValue(0.0);
        }
    }

    private void resetCameraDistance(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.CAMERA_DISTANCE);
        if (attr != null) {
            attr.setBaseValue(4.0);
        }
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
            boolean alreadyShown = result.outcome != RaidOutcome.ACTIVE;

            if (result.outcome == RaidOutcome.ACTIVE) {
                result.outcome = RaidOutcome.MIA;
            }

            // Outcomeに応じた追加EXPを最後に計算 (生還・死亡は既に加算済み)
            if (result.outcome == RaidOutcome.MIA || result.outcome == RaidOutcome.LEFT) {
                grant(result, result.outcome.name(), levelModule.getRaidOutcomeReward(levelModule.getLevel(uuid), result.outcome.name()));
            }

            // スキル (交易の記録係): 経験値の合計に割合で足す
            PlayerDataModule dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
            double skillBonus = dataModule != null ? SkillModule.value(dataModule.getPlayerData(uuid), SkillStat.RAID_EXP) : 0;
            long bonus = Math.round(result.experienceToGrant * skillBonus);
            if (bonus > 0) grant(result, EXP_SKILL, bonus);

            result.levelBefore = levelModule.getLevel(uuid);
            result.expBefore = levelModule.getExperience(uuid);
            result.granted = true;
            if (result.experienceToGrant > 0) {
                levelModule.addExperience(uuid, result.experienceToGrant);
            }

            // オンラインのプレイヤーに結果を表示 (まだ表示されていない場合)
            if (!alreadyShown) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    showIndividualRaidResult(player);
                }
            }
        }
    }

    /**
     * リザルトの画面 ({@link RaidResults}) を出す。チャットには 1 行 (押すと開き直せる) だけ出す。
     * 経験値はレイド全体の終了時に付与するので、まだなら付与する前の値から見込みを出す
     */
    private void showIndividualRaidResult(Player player) {
        RaidResult result = getOrCreateRaidResult(player.getUniqueId());
        com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule levelModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.level.LevelModule.class);
        int level = result.granted ? result.levelBefore : levelModule != null ? levelModule.getLevel(player.getUniqueId()) : 1;
        long exp = result.granted ? result.expBefore : levelModule != null ? levelModule.getExperience(player.getUniqueId()) : 0;

        List<RaidReport.ExpLine> lines = new ArrayList<>();
        long[] e;
        if ((e = result.expBreakdown.get(EXP_SURVIVED)) != null) lines.add(new RaidReport.ExpLine(UiIcon.EXTRACT, "脱出成功", result.extractName, e[1]));
        if ((e = result.expBreakdown.get(EXP_DEAD)) != null) lines.add(new RaidReport.ExpLine(UiIcon.FAILED, "死亡", null, e[1]));
        if ((e = result.expBreakdown.get(EXP_MIA)) != null) lines.add(new RaidReport.ExpLine(UiIcon.TIMER, "MIA (時間切れ)", null, e[1]));
        if ((e = result.expBreakdown.get(EXP_LEFT)) != null) lines.add(new RaidReport.ExpLine(UiIcon.FAILED, "離脱", null, e[1]));
        // MIA・離脱の経験値はレイド全体の終了時に足すので、まだなら見込みを出す
        if (!result.granted && levelModule != null && (result.outcome == RaidOutcome.MIA || result.outcome == RaidOutcome.LEFT)
                && !result.expBreakdown.containsKey(result.outcome.name())) {
            lines.add(new RaidReport.ExpLine(result.outcome == RaidOutcome.MIA ? UiIcon.TIMER : UiIcon.FAILED,
                    result.outcome == RaidOutcome.MIA ? "MIA (時間切れ)" : "離脱", null,
                    levelModule.getRaidOutcomeReward(level, result.outcome.name())));
        }
        if ((e = result.expBreakdown.get(EXP_SCAV)) != null) lines.add(new RaidReport.ExpLine(UiIcon.KILL, "SCAV を排除", "×" + e[0], e[1]));
        if ((e = result.expBreakdown.get(EXP_BOSS)) != null) lines.add(new RaidReport.ExpLine(UiIcon.KILL, "BOSS を排除", "×" + e[0], e[1]));
        if ((e = result.expBreakdown.get(EXP_PMC)) != null) lines.add(new RaidReport.ExpLine(UiIcon.KILL, "PMC を排除", "×" + e[0], e[1]));
        // スキルの経験値の上乗せ (付与する前なら、ここまでの合計からの見込み)
        double skillBonus = SkillModule.value(player, SkillStat.RAID_EXP);
        String bonusNote = "+" + Math.round(skillBonus * 100) + "%";
        if ((e = result.expBreakdown.get(EXP_SKILL)) != null) {
            lines.add(new RaidReport.ExpLine(UiIcon.EXP, "スキル (記録係)", bonusNote, e[1]));
        } else if (!result.granted && skillBonus > 0) {
            long sum = lines.stream().mapToLong(RaidReport.ExpLine::amount).sum();
            long bonus = Math.round(sum * skillBonus);
            if (bonus > 0) lines.add(new RaidReport.ExpLine(UiIcon.EXP, "スキル (記録係)", bonusNote, bonus));
        }

        long end = result.endMillis > 0 ? result.endMillis : System.currentTimeMillis();
        RaidReport report = new RaidReport(map.getMapId(), result.outcome == RaidOutcome.SURVIVED,
                result.outcome == RaidOutcome.SURVIVED ? "脱出成功" : result.outcome.displayName, result.extractName, end - startTime,
                List.copyOf(lines), level, exp, !result.granted, result.loot, result.lostValue, List.copyOf(result.kills), result.death);
        RaidResults.show(player, report);
    }

    /** 倒した記録 (弾が当たった時の様子から)。name が null なら当たった時の相手の名前 */
    private RaidReport.Kill killRecord(UUID victim, UUID killer, RaidReport.Kind kind, String name) {
        RaidKillTracker.Hit hit = RaidKillTracker.lastHit(victim, killer);
        String who = name != null ? name : hit != null ? hit.victimName() : kind.name();
        long at = System.currentTimeMillis() - startTime;
        if (hit == null) return new RaidReport.Kill(at, who, kind, null, null, null, -1, null);
        return new RaidReport.Kill(at, who, kind, hit.weapon(), hit.weaponName(), hit.ammoName(), hit.distance(), hit.part());
    }

    /** 持ち帰った物: FIR (レイド内で入手) の物を欄ごとに、価値の高い順 (バッグの中も数える) */
    private List<RaidReport.LootStack> foundLoot(Player player) {
        com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule questModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestModule.class);
        com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule dataModule =
                plugin.getServiceContainer().get(com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule.class);
        com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData data = dataModule != null ? dataModule.getPlayerData(player.getUniqueId()) : null;
        List<RaidReport.LootStack> loot = new ArrayList<>();
        visitPlayerItems(player, item -> {
            if (!com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn.isFir(item)) return;
            boolean usable = questModule != null && data != null
                    && com.lunar_prototype.impossbleEscapeMC.modules.quest.QuestAutoHandIn.usable(data, questModule, item);
            loot.add(new RaidReport.LootStack(item.clone(), RaidResults.valueOf(item), usable));
        }, plugin.getServiceContainer().get(BackpackModule.class));
        loot.sort(Comparator.comparingLong(RaidReport.LootStack::value).reversed());
        return List.copyOf(loot);
    }

    /** 持ち込んだ物 (失うと損失になる物) の価値 */
    private long broughtInValue(Player player) {
        long[] total = {0};
        visitPlayerItems(player, item -> {
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                    .getOrDefault(PDCKeys.RAID_BROUGHT_IN, PDCKeys.BOOLEAN, (byte) 0) == 1) {
                total[0] += RaidResults.valueOf(item);
            }
        }, plugin.getServiceContainer().get(BackpackModule.class));
        return total[0];
    }
}
