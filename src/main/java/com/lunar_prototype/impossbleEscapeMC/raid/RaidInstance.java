package com.lunar_prototype.impossbleEscapeMC.raid;

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
    private final List<RaidMap.ExtractionPoint> activeExtractions = new ArrayList<>();
    private final Map<UUID, Integer> extractionTimer = new HashMap<>();
    private final List<VirtualScav> virtualScavs = new ArrayList<>();
    
    private final long startTime;
    private int timeLeft; // seconds
    private BukkitRunnable task;
    private BossBar bossBar;

    public RaidInstance(ImpossbleEscapeMC plugin, RaidMap map, List<Player> participants) {
        this.plugin = plugin;
        this.map = map;
        this.startTime = System.currentTimeMillis();
        this.timeLeft = 1200; // 20 minutes default

        for (Player p : participants) {
            players.add(p.getUniqueId());
        }

        for (RaidMap.ScavSpawnPoint sp : map.getScavSpawnPoints()) {
            virtualScavs.add(new VirtualScav(sp.getLocation(map.getWorldName()), sp.isPermanent()));
        }

        assignExtractions();
        spawnPlayers();
        startTask();
    }

    private void assignExtractions() {
        List<RaidMap.ExtractionPoint> all = new ArrayList<>(map.getExtractionPoints());
        Collections.shuffle(all);
        int count = Math.max(1, all.size() / 2);
        for (int i = 0; i < count; i++) {
            activeExtractions.add(all.get(i));
        }
    }

    private void spawnPlayers() {
        List<Location> spawns = map.getSpawnPoints();
        if (spawns.isEmpty()) return;

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                Location loc = spawns.get(new Random().nextInt(spawns.size()));
                p.teleport(loc);
                p.sendMessage(Component.text("レイド開始: " + map.getMapId(), NamedTextColor.GOLD));
                showExtractions(p);
            }
        }
    }

    private void showExtractions(Player p) {
        p.sendMessage(Component.text("利用可能な脱出地点:", NamedTextColor.YELLOW));
        for (RaidMap.ExtractionPoint ep : activeExtractions) {
            p.sendMessage(Component.text("- " + ep.getName(), NamedTextColor.WHITE));
        }
    }

    private void startTask() {
        bossBar = BossBar.bossBar(
                Component.text("レイド進行中: " + map.getMapId()),
                1.0f,
                BossBar.Color.GREEN,
                BossBar.Overlay.PROGRESS
        );
        
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.showBossBar(bossBar);
        }

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (timeLeft <= 0) {
                    endRaid();
                    return;
                }

                updateBossBar();
                checkExtractions();
                updateScavs();
                timeLeft--;
            }
        };
        task.runTaskTimer(plugin, 0, 20);
    }

    private void updateBossBar() {
        String timeStr = String.format("%02d:%02d", timeLeft / 60, timeLeft % 60);
        bossBar.name(Component.text("Raid: " + map.getMapId() + " | 脱出まで " + timeStr, NamedTextColor.WHITE));
        bossBar.progress((float) timeLeft / 1200.0f);
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

            if (playerNearby && !vs.isSpawned()) {
                UUID uuid = plugin.getScavSpawner().spawnScav(spawnLoc);
                vs.setEntityId(uuid);
                vs.setSpawned(true);
            } else if (!playerNearby && vs.isSpawned() && !vs.isPermanent()) {
                plugin.getScavSpawner().removeScav(vs.getEntityId());
                vs.setSpawned(false);
                vs.setEntityId(null);
            }
        }
    }

    private void checkExtractions() {
        for (UUID uuid : new HashSet<>(players)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            boolean inZone = false;
            for (RaidMap.ExtractionPoint ep : activeExtractions) {
                Location epLoc = ep.getLocation(map.getWorldName());
                if (epLoc != null && p.getLocation().distance(epLoc) <= ep.getRadius()) {
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
        } else {
            p.sendMessage(Component.text("脱出に成功しました！", NamedTextColor.GREEN));
            players.remove(p.getUniqueId());
            p.hideBossBar(bossBar);
            // 本来はロビーに戻すなどの処理
            p.teleport(p.getWorld().getSpawnLocation()); 
            
            if (players.isEmpty()) {
                endRaid();
            }
        }
    }

    private void endRaid() {
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
                p.hideBossBar(bossBar);
                p.sendMessage(Component.text("レイドが終了しました。", NamedTextColor.RED));
            }
        }
        players.clear();
        plugin.getRaidManager().removeRaid(map.getMapId());
    }

    public void onPlayerDeath(Player player) {
        if (!players.contains(player.getUniqueId())) return;
        
        player.sendMessage(Component.text("死亡しました。レイド失敗です。", NamedTextColor.RED));
        players.remove(player.getUniqueId());
        player.hideBossBar(bossBar);

        if (players.isEmpty()) {
            endRaid();
        }
    }

    public void onPlayerQuit(Player player) {
        if (!players.contains(player.getUniqueId())) return;
        
        players.remove(player.getUniqueId());
        if (players.isEmpty()) {
            endRaid();
        }
    }

    public void stop() {
        endRaid();
    }

    public void joinPlayer(Player player) {
        players.add(player.getUniqueId());
        player.showBossBar(bossBar);
        
        List<Location> spawns = map.getSpawnPoints();
        if (!spawns.isEmpty()) {
            Location loc = spawns.get(new Random().nextInt(spawns.size()));
            player.teleport(loc);
        }
        
        player.sendMessage(Component.text("レイドに参加しました: " + map.getMapId(), NamedTextColor.GOLD));
        showExtractions(player);
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public boolean isParticipant(UUID uuid) {
        return players.contains(uuid);
    }
}
