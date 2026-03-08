package com.lunar_prototype.impossbleEscapeMC.minigame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MinigameManager {
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, MinigameMap> maps = new HashMap<>();
    
    // Minecraft Teams
    private Team mgTeam1;
    private Team mgTeam2;

    // Game State
    private boolean isRunning = false;
    private MinigameMap currentMap;
    private int currentRound = 0;
    private final int MAX_ROUNDS = 2;
    private int timeLeft = 0;
    
    private final Set<UUID> team1 = new HashSet<>();
    private final Set<UUID> team2 = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();

    // Stats
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Double> damageTaken = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Set<UUID>> killedByPlayer = new HashMap<>(); // Killer -> Set of victims
    
    private BossBar bossBar;
    private BukkitRunnable gameTask;

    public MinigameManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        setupTeams();
        loadMaps();
    }

    private void setupTeams() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        
        mgTeam1 = sb.getTeam("MG_TEAM1");
        if (mgTeam1 == null) mgTeam1 = sb.registerNewTeam("MG_TEAM1");
        
        mgTeam2 = sb.getTeam("MG_TEAM2");
        if (mgTeam2 == null) mgTeam2 = sb.registerNewTeam("MG_TEAM2");

        configureTeam(mgTeam1, NamedTextColor.RED, "Team1");
        configureTeam(mgTeam2, NamedTextColor.BLUE, "Team2");
    }

    private void configureTeam(Team team, NamedTextColor color, String displayName) {
        team.color(color);
        team.displayName(Component.text(displayName));
        team.setAllowFriendlyFire(false);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    public void splitTeams() {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Collections.shuffle(players);
        
        // クリア
        clearMinecraftTeams();
        team1.clear();
        team2.clear();

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            if (i % 2 == 0) {
                team1.add(p.getUniqueId());
                mgTeam1.addEntry(p.getName());
            } else {
                team2.add(p.getUniqueId());
                mgTeam2.addEntry(p.getName());
            }
        }
        Bukkit.broadcast(Component.text("チームを分けました。 Team1: " + team1.size() + "人, Team2: " + team2.size() + "人", NamedTextColor.YELLOW));
    }

    private void clearMinecraftTeams() {
        if (mgTeam1 != null) mgTeam1.getEntries().forEach(e -> mgTeam1.removeEntry(e));
        if (mgTeam2 != null) mgTeam2.getEntries().forEach(e -> mgTeam2.removeEntry(e));
    }

    public void startGame(String mapName) {
        if (isRunning) return;
        currentMap = maps.get(mapName);
        if (currentMap == null) return;
        
        isRunning = true;
        currentRound = 1;
        resetStats();
        startRound();
    }

    private void resetStats() {
        damageDealt.clear();
        damageTaken.clear();
        kills.clear();
        killedByPlayer.clear();
    }

    private void startRound() {
        alivePlayers.clear();
        alivePlayers.addAll(team1);
        alivePlayers.addAll(team2);
        
        timeLeft = 180; // 3分
        
        spawnPlayers();
        
        if (bossBar != null) Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        bossBar = BossBar.bossBar(
                Component.text("Round " + currentRound),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));

        if (gameTask != null) gameTask.cancel();
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    endRound(0); // Draw or check counts
                    return;
                }
                
                updateBossBar();
                checkWinCondition();
                timeLeft--;
            }
        };
        gameTask.runTaskTimer(plugin, 0, 20);
        
        Bukkit.broadcast(Component.text("ラウンド " + currentRound + " 開始！", NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private void spawnPlayers() {
        List<Location> s1 = currentMap.getSpawns(1);
        List<Location> s2 = currentMap.getSpawns(2);
        
        int i = 0;
        for (UUID uuid : team1) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(20);
                p.setFoodLevel(20);
                p.teleport(s1.get(i % s1.size()));
                i++;
            }
        }
        
        i = 0;
        for (UUID uuid : team2) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setGameMode(GameMode.SURVIVAL);
                p.setHealth(20);
                p.setFoodLevel(20);
                p.teleport(s2.get(i % s2.size()));
                i++;
            }
        }
    }

    private void updateBossBar() {
        int t1Alive = (int) alivePlayers.stream().filter(team1::contains).count();
        int t2Alive = (int) alivePlayers.stream().filter(team2::contains).count();
        
        String timeStr = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        bossBar.name(Component.text("Team1: " + t1Alive + " vs Team2: " + t2Alive + " | " + timeStr, NamedTextColor.WHITE));
        bossBar.progress((float) timeLeft / 180.0f);
    }

    private void checkWinCondition() {
        int t1Alive = (int) alivePlayers.stream().filter(team1::contains).count();
        int t2Alive = (int) alivePlayers.stream().filter(team2::contains).count();
        
        if (t1Alive == 0) endRound(2);
        else if (t2Alive == 0) endRound(1);
    }

    private void endRound(int winnerTeam) {
        if (gameTask != null) gameTask.cancel();
        
        if (winnerTeam == 0) { // Time up
            int t1Alive = (int) alivePlayers.stream().filter(team1::contains).count();
            int t2Alive = (int) alivePlayers.stream().filter(team2::contains).count();
            if (t1Alive > t2Alive) winnerTeam = 1;
            else if (t2Alive > t1Alive) winnerTeam = 2;
        }

        String winMsg = (winnerTeam == 1) ? "Team1 の勝利！" : (winnerTeam == 2 ? "Team2 の勝利！" : "引き分け！");
        Bukkit.broadcast(Component.text("ラウンド終了: " + winMsg, NamedTextColor.GOLD, TextDecoration.BOLD));

        // ACE / Clutch Check
        checkSpecialAchievements(winnerTeam);
        
        // Show Rankings
        showRankings();

        int finalWinner = winnerTeam;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentRound < MAX_ROUNDS) {
                    currentRound++;
                    startRound();
                } else {
                    displayFinalVictory(finalWinner);
                    stopGame();
                }
            }
        }.runTaskLater(plugin, 100);
    }

    private void checkSpecialAchievements(int winnerTeam) {
        Set<UUID> winnerMembers = (winnerTeam == 1) ? team1 : team2;
        Set<UUID> loserMembers = (winnerTeam == 1) ? team2 : team1;

        List<UUID> winnersAlive = alivePlayers.stream().filter(winnerMembers::contains).collect(Collectors.toList());
        if (winnersAlive.size() == 1) {
            Player clutcher = Bukkit.getPlayer(winnersAlive.get(0));
            if (clutcher != null && loserMembers.size() >= 2) {
                clutcher.showTitle(Title.title(
                        Component.text("CLUTCH", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("不屈の精神で逆転勝利！", NamedTextColor.YELLOW)
                ));
                Bukkit.broadcast(Component.text(clutcher.getName() + " が CLUTCH を決めました！", NamedTextColor.RED, TextDecoration.ITALIC));
            }
        }

        for (UUID killerId : killedByPlayer.keySet()) {
            Set<UUID> victims = killedByPlayer.get(killerId);
            if (victims.containsAll(loserMembers)) {
                Player acer = Bukkit.getPlayer(killerId);
                if (acer != null) {
                    acer.showTitle(Title.title(
                            Component.text("ACE", NamedTextColor.GOLD, TextDecoration.BOLD),
                            Component.text("敵チームを全滅させた！", NamedTextColor.YELLOW)
                    ));
                    Bukkit.broadcast(Component.text(acer.getName() + " が ACE を達成しました！", NamedTextColor.GOLD, TextDecoration.ITALIC));
                }
            }
        }
    }

    private void showRankings() {
        Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GRAY));
        Bukkit.broadcast(Component.text("   ダメージランキング (与 / 被)", NamedTextColor.YELLOW, TextDecoration.BOLD));
        
        damageDealt.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    double dealt = entry.getValue();
                    double taken = damageTaken.getOrDefault(entry.getKey(), 0.0);
                    Bukkit.broadcast(Component.text(String.format(" %s: %.1f / %.1f", name, dealt, taken), NamedTextColor.WHITE));
                });
        Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GRAY));
    }

    private void displayFinalVictory(int winnerTeam) {
        if (winnerTeam == 0) return;
        Component titleText = Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component subText = Component.text("Team" + winnerTeam + " が総合優勝！", NamedTextColor.YELLOW);
        
        Title title = Title.title(titleText, subText);
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        });
    }

    public void stopGame() {
        isRunning = false;
        if (gameTask != null) gameTask.cancel();
        if (bossBar != null) Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        alivePlayers.clear();
        clearMinecraftTeams();
        Bukkit.getOnlinePlayers().forEach(p -> p.setGameMode(GameMode.SURVIVAL));
    }

    public void addDamage(UUID attacker, UUID victim, double damage) {
        if (!isRunning) return;
        damageDealt.put(attacker, damageDealt.getOrDefault(attacker, 0.0) + damage);
        damageTaken.put(victim, damageTaken.getOrDefault(victim, 0.0) + damage);
    }

    public void recordKill(UUID killer, UUID victim) {
        if (!isRunning) return;
        kills.put(killer, kills.getOrDefault(killer, 0) + 1);
        killedByPlayer.computeIfAbsent(killer, k -> new HashSet<>()).add(victim);
    }

    public void onPlayerDeath(Player player) {
        if (!isRunning) return;
        alivePlayers.remove(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        Bukkit.broadcast(Component.text(player.getName() + " が脱落しました！ 残り: " + alivePlayers.size() + "人", NamedTextColor.RED));
    }

    public void createMap(String name) {
        maps.put(name, new MinigameMap(name));
        saveMaps();
    }

    public MinigameMap getMap(String name) {
        return maps.get(name);
    }

    public List<String> getMapNames() {
        return new ArrayList<>(maps.keySet());
    }

    public void saveMaps() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        if (!dir.exists()) dir.mkdirs();
        
        for (MinigameMap map : maps.values()) {
            File file = new File(dir, map.getName() + ".json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(map, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadMaps() {
        File dir = new File(plugin.getDataFolder(), "minigames");
        if (!dir.exists()) return;
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return;
        
        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                MinigameMap map = gson.fromJson(reader, MinigameMap.class);
                maps.put(map.getName(), map);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }
}
