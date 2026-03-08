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
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.item.ItemDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
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
    private boolean isCountdown = false;
    private MinigameMap currentMap;
    private int currentRound = 0;
    private int maxRounds = 2; // Default
    private int timeLeft = 0;
    
    private final Set<UUID> team1 = new HashSet<>();
    private final Set<UUID> team2 = new HashSet<>();
    private final Set<UUID> alivePlayers = new HashSet<>();
    private final Set<UUID> roundParticipants = new HashSet<>();

    // Loadout & Inventory
    private Map<UUID, String> chosenLoadout = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();

    // Win Counters
    private int team1Wins = 0;
    private int team2Wins = 0;

    // Stats
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    private final Map<UUID, Double> damageTaken = new HashMap<>();
    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, Integer> deaths = new HashMap<>();
    private final Map<UUID, Integer> assists = new HashMap<>();
    private final Map<UUID, Map<UUID, Double>> victimDamageMap = new HashMap<>(); // Victim -> (Attacker -> Damage)
    private final Map<UUID, Set<UUID>> roundKills = new HashMap<>(); // Killer -> Set of victims in current round
    private final Map<UUID, Set<UUID>> killedByPlayer = new HashMap<>(); // Killer -> Set of victims (Cumulative)
    
    private BossBar bossBar;
    private BukkitRunnable gameTask;

    public MinigameManager(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        setupTeams();
        loadMaps();
        loadLoadouts();
    }

    private void loadLoadouts() {
        File file = new File(plugin.getDataFolder(), "loadouts.json");
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            java.lang.reflect.Type type = new com.google.common.reflect.TypeToken<Map<UUID, String>>(){}.getType();
            Map<UUID, String> loaded = gson.fromJson(reader, type);
            if (loaded != null) chosenLoadout = loaded;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveLoadouts() {
        File file = new File(plugin.getDataFolder(), "loadouts.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(chosenLoadout, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        // ユーザー環境に合わせて FOR_OTHER_TEAMS を使用
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.FOR_OTHER_TEAMS);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
    }

    public void splitTeams() {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(players);
        
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

    public void startGame(String mapName, int rounds) {
        if (isRunning) return;
        currentMap = maps.get(mapName);
        if (currentMap == null) return;
        
        // Save Inventories for all participants
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(team1);
        allParticipants.addAll(team2);
        for (UUID uuid : allParticipants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                saveInventory(p);
            }
        }

        this.maxRounds = rounds;
        isRunning = true;
        currentRound = 1;
        team1Wins = 0;
        team2Wins = 0;
        resetStats();
        startRound();
    }

    private void resetStats() {
        damageDealt.clear();
        damageTaken.clear();
        kills.clear();
        deaths.clear();
        assists.clear();
        victimDamageMap.clear();
        roundKills.clear();
        killedByPlayer.clear();
    }

    private void startRound() {
        alivePlayers.clear();
        alivePlayers.addAll(team1);
        alivePlayers.addAll(team2);
        
        roundParticipants.clear();
        roundParticipants.addAll(alivePlayers);

        // Round stats reset
        roundKills.clear();
        victimDamageMap.clear(); // Damage for assists is per-round logic
        
        spawnPlayers();
        startCountdown();
    }

    private void startCountdown() {
        isCountdown = true;
        new BukkitRunnable() {
            int count = 5;

            @Override
            public void run() {
                if (!isRunning) {
                    this.cancel();
                    return;
                }

                if (count <= 0) {
                    isCountdown = false;
                    beginActualRound();
                    this.cancel();
                    return;
                }

                NamedTextColor color = count <= 3 ? NamedTextColor.RED : NamedTextColor.YELLOW;
                Title title = Title.title(
                        Component.text(count, color, TextDecoration.BOLD),
                        Component.text("準備してください...", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(800), Duration.ofMillis(200))
                );
                
                Bukkit.getOnlinePlayers().forEach(p -> {
                    p.showTitle(title);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                });

                count--;
            }
        }.runTaskTimer(plugin, 0, 20);
    }

    private void beginActualRound() {
        timeLeft = 180; // 3分
        
        if (bossBar != null) Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        bossBar = BossBar.bossBar(
                Component.text("Round " + currentRound + " / " + maxRounds),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        Bukkit.getOnlinePlayers().forEach(p -> p.showBossBar(bossBar));

        Title startTitle = Title.title(
                Component.text("START!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("殲滅せよ", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1500), Duration.ofMillis(500))
        );
        Bukkit.getOnlinePlayers().forEach(p -> {
            p.showTitle(startTitle);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        });

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
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.teleport(s1.get(i % s1.size()));
            giveLoadout(p);
            i++;
        }
    }

    i = 0;
    for (UUID uuid : team2) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.ADVENTURE);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.teleport(s2.get(i % s2.size()));
            giveLoadout(p);
            i++;
        }
    }
}

    private void updateBossBar() {
        int t1Alive = (int) alivePlayers.stream().filter(team1::contains).count();
        int t2Alive = (int) alivePlayers.stream().filter(team2::contains).count();
        
        String timeStr = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        bossBar.name(Component.text("Team1: " + t1Alive + " vs Team2: " + t2Alive + " | " + timeStr + " (R" + currentRound + "/" + maxRounds + ")", NamedTextColor.WHITE));
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

        if (winnerTeam == 1) team1Wins++;
        else if (winnerTeam == 2) team2Wins++;

        String winMsg = (winnerTeam == 1) ? "Team1 の勝利！" : (winnerTeam == 2 ? "Team2 の勝利！" : "引き分け！");
        Bukkit.broadcast(Component.text("ラウンド終了: " + winMsg, NamedTextColor.GOLD, TextDecoration.BOLD));

        // ACE / Clutch Check
        checkSpecialAchievements(winnerTeam);

        // Show cumulative damage ranking after round
        showRankings(false);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (currentRound < maxRounds) {
                    currentRound++;
                    startRound();
                } else {
                    displayFinalResult();
                    stopGame();
                }
            }
        }.runTaskLater(plugin, 100);
    }

    private void checkSpecialAchievements(int winnerTeam) {
        if (winnerTeam == 0) return;
        Set<UUID> winnerMembers = (winnerTeam == 1) ? team1 : team2;
        Set<UUID> loserMembers = (winnerTeam == 1) ? team2 : team1;
        
        // Initial participants of the losing team this round
        Set<UUID> initialLoserParticipants = roundParticipants.stream()
                .filter(loserMembers::contains)
                .collect(Collectors.toSet());

        // CLUTCH Check: Only if the winner survived alone against 2+ initial enemies
        List<UUID> winnersAlive = alivePlayers.stream().filter(winnerMembers::contains).collect(Collectors.toList());
        if (winnersAlive.size() == 1) {
            Player clutcher = Bukkit.getPlayer(winnersAlive.get(0));
            if (clutcher != null && initialLoserParticipants.size() >= 2) {
                clutcher.showTitle(Title.title(
                        Component.text("CLUTCH", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("不屈の精神で逆転勝利！", NamedTextColor.YELLOW)
                ));
                Bukkit.broadcast(Component.text(clutcher.getName() + " が CLUTCH を決めました！", NamedTextColor.RED, TextDecoration.ITALIC));
            }
        }

        // ACE Check: Winner killed ALL enemies who started THIS round
        for (UUID killerId : roundKills.keySet()) {
            if (!winnerMembers.contains(killerId)) continue; // Must be on the winning team
            
            Set<UUID> victimsInRound = roundKills.get(killerId);
            if (victimsInRound.containsAll(initialLoserParticipants)) {
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

    private void showRankings(boolean isFinal) {
        Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GRAY));
        String title = isFinal ? "   最終 KD ランキング" : "   累計ダメージランキング";
        Bukkit.broadcast(Component.text(title, NamedTextColor.YELLOW, TextDecoration.BOLD));
        
        damageDealt.keySet().stream()
                .sorted((u1, u2) -> {
                    if (isFinal) {
                        return Double.compare(getKDRatio(u2), getKDRatio(u1));
                    } else {
                        return Double.compare(damageDealt.getOrDefault(u2, 0.0), damageDealt.getOrDefault(u1, 0.0));
                    }
                })
                .limit(5)
                .forEach(uuid -> {
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    double dealt = damageDealt.getOrDefault(uuid, 0.0);
                    double taken = damageTaken.getOrDefault(uuid, 0.0);
                    int k = kills.getOrDefault(uuid, 0);
                    int d = deaths.getOrDefault(uuid, 0);
                    int a = assists.getOrDefault(uuid, 0);
                    double kd = getKDRatio(uuid);
                    Bukkit.broadcast(Component.text(String.format(" %s: %.1f / %.1f [K:%d D:%d A:%d KD:%.2f]", name, dealt, taken, k, d, a, kd), NamedTextColor.WHITE));
                });
        Bukkit.broadcast(Component.text("--------------------------------", NamedTextColor.GRAY));
    }

    private double getKDRatio(UUID uuid) {
        int k = kills.getOrDefault(uuid, 0);
        int d = deaths.getOrDefault(uuid, 0);
        return (d == 0) ? k : (double) k / d;
    }

    private void displayFinalResult() {
        if (team1Wins > team2Wins) {
            broadcastFinalResult(1);
        } else if (team2Wins > team1Wins) {
            broadcastFinalResult(2);
        } else {
            broadcastFinalResult(0);
        }
        showRankings(true);
    }

    private void broadcastFinalResult(int winnerTeam) {
        Component victoryTitle = Component.text("VICTORY", NamedTextColor.GOLD, TextDecoration.BOLD);
        Component defeatTitle = Component.text("DEFEAT", NamedTextColor.RED, TextDecoration.BOLD);
        Component drawTitle = Component.text("DRAW", NamedTextColor.GRAY, TextDecoration.BOLD);

        String scoreStr = "(" + team1Wins + " - " + team2Wins + ")";
        Component subText1 = Component.text("Team1 の総合優勝！ " + scoreStr, NamedTextColor.YELLOW);
        Component subText2 = Component.text("Team2 の総合優勝！ " + scoreStr, NamedTextColor.YELLOW);
        Component subTextDraw = Component.text("同点！ " + scoreStr, NamedTextColor.WHITE);

        Bukkit.getOnlinePlayers().forEach(p -> {
            Title title;
            Sound sound;
            if (winnerTeam == 0) {
                title = Title.title(drawTitle, subTextDraw);
                sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
            } else {
                boolean isWinner = (winnerTeam == 1 && team1.contains(p.getUniqueId())) || (winnerTeam == 2 && team2.contains(p.getUniqueId()));
                if (isWinner) {
                    title = Title.title(victoryTitle, (winnerTeam == 1 ? subText1 : subText2));
                    sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
                } else {
                    title = Title.title(defeatTitle, (winnerTeam == 1 ? subText1 : subText2));
                    sound = Sound.ENTITY_WITHER_SPAWN;
                }
            }
            p.showTitle(title);
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        });

        NamedTextColor broadcastColor = (winnerTeam == 1 || winnerTeam == 2) ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        Bukkit.broadcast(Component.text("ゲーム終了！ 最終結果: Team1 [" + team1Wins + "] - [" + team2Wins + "] Team2", broadcastColor, TextDecoration.BOLD));
    }
    public void stopGame() {
        isRunning = false;
        isCountdown = false;
        if (gameTask != null) gameTask.cancel();
        if (bossBar != null) Bukkit.getOnlinePlayers().forEach(p -> p.hideBossBar(bossBar));
        
        Set<UUID> participants = new HashSet<>();
        participants.addAll(team1);
        participants.addAll(team2);
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                restoreInventory(p);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
        
        alivePlayers.clear();
        // clearMinecraftTeams(); // ゲーム終了後もチーム分けを維持するためコメントアウト
    }

    public void setLoadout(Player player, String loadout) {
        chosenLoadout.put(player.getUniqueId(), loadout.toLowerCase());
        saveLoadouts();
        player.sendMessage(Component.text("ロードアウトを " + loadout.toUpperCase() + " に設定しました。", NamedTextColor.GREEN));
    }

    private void giveLoadout(Player player) {
        player.getInventory().clear();
        String loadout = chosenLoadout.getOrDefault(player.getUniqueId(), "m4a1");
        
        // Weapon
        ItemStack weapon = ItemFactory.create(loadout);
        if (weapon != null) {
            player.getInventory().setItem(0, weapon);
            
            // Ammo
            ItemDefinition def = ItemRegistry.get(loadout);
            if (def != null && def.gunStats != null) {
                AmmoDefinition ammoDef = ItemRegistry.getWeakestAmmoForCaliber(def.gunStats.caliber);
                if (ammoDef != null) {
                    ItemStack ammo = ItemFactory.create(ammoDef.id);
                    if (ammo != null) {
                        ammo.setAmount(64);
                        player.getInventory().setItem(1, ammo);
                        player.getInventory().setItem(2, ammo.clone());
                        player.getInventory().setItem(3, ammo.clone());
                        ItemStack lastAmmo = ammo.clone();
                        lastAmmo.setAmount(8); // 64*3 + 8 = 200
                        player.getInventory().setItem(4, lastAmmo);
                    }
                }
            }
        }

        // Iron Armor (Fallback to vanilla with PDC if custom definition missing)
        player.getInventory().setHelmet(createArmor("iron_helmet", Material.IRON_HELMET, 2));
        player.getInventory().setChestplate(createArmor("iron_chestplate", Material.IRON_CHESTPLATE, 2));
    }

    private ItemStack createArmor(String id, Material fallback, int armorClass) {
        ItemStack item = ItemFactory.create(id);
        if (item != null) return item;
        
        ItemStack vanilla = new ItemStack(fallback);
        var meta = vanilla.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(PDCKeys.ARMOR_CLASS, PDCKeys.INTEGER, armorClass);
            vanilla.setItemMeta(meta);
        }
        return vanilla;
    }

    private void saveInventory(Player player) {
        savedInventories.put(player.getUniqueId(), player.getInventory().getContents());
        savedArmor.put(player.getUniqueId(), player.getInventory().getArmorContents());
        player.getInventory().clear();
    }

    private void restoreInventory(Player player) {
        if (savedInventories.containsKey(player.getUniqueId())) {
            player.getInventory().setContents(savedInventories.remove(player.getUniqueId()));
        }
        if (savedArmor.containsKey(player.getUniqueId())) {
            player.getInventory().setArmorContents(savedArmor.remove(player.getUniqueId()));
        }
    }

    public void addDamage(UUID attacker, UUID victim, double damage) {
        if (!isRunning || isCountdown) return;
        damageDealt.put(attacker, damageDealt.getOrDefault(attacker, 0.0) + damage);
        damageTaken.put(victim, damageTaken.getOrDefault(victim, 0.0) + damage);
        
        // Track per-victim damage for assists
        victimDamageMap.computeIfAbsent(victim, k -> new HashMap<>())
                       .merge(attacker, damage, Double::sum);
    }

    public void recordKill(UUID killer, UUID victim) {
        if (!isRunning) return;
        kills.put(killer, kills.getOrDefault(killer, 0) + 1);
        roundKills.computeIfAbsent(killer, k -> new HashSet<>()).add(victim);
        killedByPlayer.computeIfAbsent(killer, k -> new HashSet<>()).add(victim);

        // Calculate Assists: Everyone who damaged the victim but is NOT the killer
        Map<UUID, Double> damageSources = victimDamageMap.get(victim);
        if (damageSources != null) {
            for (UUID attacker : damageSources.keySet()) {
                if (attacker.equals(killer)) continue;
                // Deal at least 1 damage to get an assist (prevents accidental assists)
                if (damageSources.get(attacker) >= 1.0) {
                    assists.put(attacker, assists.getOrDefault(attacker, 0) + 1);
                }
            }
        }
    }

    public void onPlayerDeath(Player player) {
        if (!isRunning) return;
        deaths.put(player.getUniqueId(), deaths.getOrDefault(player.getUniqueId(), 0) + 1);
        alivePlayers.remove(player.getUniqueId());
        player.setGameMode(GameMode.SPECTATOR);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        Bukkit.broadcast(Component.text(player.getName() + " が脱落しました！ 残り: " + alivePlayers.size() + "人", NamedTextColor.RED));
        checkWinCondition();
    }

    public void onPlayerQuit(Player player) {
        if (!isRunning) return;
        alivePlayers.remove(player.getUniqueId());
        restoreInventory(player);
        team1.remove(player.getUniqueId());
        team2.remove(player.getUniqueId());
        checkWinCondition();
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

    public boolean isCountdown() {
        return isCountdown;
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }
}
