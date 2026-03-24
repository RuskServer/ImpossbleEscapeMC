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

    private void enforceRaidGamemode() {
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            }
        }
    }

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

    public Set<UUID> getParticipantUuids() {
        return Collections.unmodifiableSet(players);
    }

    private RaidResult getOrCreateRaidResult(UUID uuid) {
        return raidResults.computeIfAbsent(uuid, ignored -> new RaidResult());
    }

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
