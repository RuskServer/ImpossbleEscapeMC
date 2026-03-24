package com.lunar_prototype.impossbleEscapeMC.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class AiRaidLogger {
    private static final String SCHEMA_VERSION = "1.0.0";
    private final ImpossbleEscapeMC plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, RaidLogSession> sessions = new HashMap<>();
    private final File rootDir;

    private final boolean enabled;
    private final int sampleIntervalTicks;
    private final int maxRaidLogs;
    private final boolean asyncWrite;
    private final boolean captureRaycast;

    private static class RaidLogSession {
        String raidId;
        String mapId;
        String worldName;
        long startTick;
        long endTick;
        long startTimeMs;
        long endTimeMs;
        String endReason;
        File dir;
        final List<Map<String, Object>> timeline = new ArrayList<>();
        final List<Map<String, Object>> events = new ArrayList<>();
        final Map<UUID, ScavStats> statsByScav = new HashMap<>();
        final Map<UUID, PlayerMotionState> playerMotion = new HashMap<>();
    }

    private static class ScavStats {
        int shotsFired;
        int shotsHit;
        int lowEffectiveHits;
        int targetSwitches;
        int damageTakenCount;
        double damageTakenTotal;
        long firstSeenTick;
        long lastSeenTick;
        String brainLevel = "MID";
    }

    private static class PlayerMotionState {
        Location lastLoc;
        long lastTick;
    }

    public AiRaidLogger(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.rootDir = new File(plugin.getDataFolder(), "ai-logs");
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        this.enabled = plugin.getConfig().getBoolean("ai_log.enabled", true);
        this.sampleIntervalTicks = Math.max(1, plugin.getConfig().getInt("ai_log.sample_interval_ticks", 5));
        this.maxRaidLogs = Math.max(1, plugin.getConfig().getInt("ai_log.max_raid_logs", 50));
        this.asyncWrite = plugin.getConfig().getBoolean("ai_log.async_write", true);
        this.captureRaycast = plugin.getConfig().getBoolean("ai_log.capture_raycast", false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getSampleIntervalTicks() {
        return sampleIntervalTicks;
    }

    public boolean isCaptureRaycastEnabled() {
        return captureRaycast;
    }

    public synchronized void startRaidSession(String raidId, String mapId, String worldName, long startTick, long startTimeMs) {
        if (!enabled || sessions.containsKey(raidId)) return;

        RaidLogSession session = new RaidLogSession();
        session.raidId = raidId;
        session.mapId = mapId;
        session.worldName = worldName;
        session.startTick = startTick;
        session.startTimeMs = startTimeMs;
        session.endReason = "unknown";
        session.dir = new File(rootDir, "raid_" + raidId + "_" + startTimeMs);
        if (!session.dir.exists()) {
            session.dir.mkdirs();
        }

        sessions.put(raidId, session);
    }

    public synchronized void endRaidSession(String raidId, String endReason) {
        if (!enabled) return;
        RaidLogSession session = sessions.remove(raidId);
        if (session == null) return;

        session.endTick = Bukkit.getCurrentTick();
        session.endTimeMs = System.currentTimeMillis();
        session.endReason = endReason != null ? endReason : "unknown";

        Runnable writer = () -> writeSession(session);
        if (asyncWrite) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, writer);
        } else {
            writer.run();
        }
    }

    public synchronized void logSnapshot(
            String raidId,
            UUID scavId,
            String brainLevel,
            Location loc,
            UUID targetId,
            boolean canSeeTarget,
            Location lastKnownTargetPos,
            float suppression,
            int searchTicks,
            boolean isSprinting,
            boolean isHoldingAngle,
            float alertness,
            float complacency,
            String behaviorState,
            double homeDistance,
            String mode,
            int moveAction,
            int shootAction,
            float aggression,
            float fear,
            float tactical,
            float tacticalAdvice,
            List<Map<String, Object>> raycasts
    ) {
        if (!enabled) return;
        RaidLogSession session = sessions.get(raidId);
        if (session == null || loc == null) return;

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "snapshot");
        rec.put("raidId", raidId);
        rec.put("tick", Bukkit.getCurrentTick());
        rec.put("timeMs", System.currentTimeMillis());
        rec.put("scavId", scavId.toString());
        rec.put("brainLevel", brainLevel);
        rec.put("pos", posMap(loc));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("targetId", targetId != null ? targetId.toString() : null);
        state.put("canSeeTarget", canSeeTarget);
        state.put("lastKnownTargetPos", lastKnownTargetPos != null ? xyzMap(lastKnownTargetPos) : null);
        state.put("suppression", suppression);
        state.put("searchTicks", searchTicks);
        state.put("isSprinting", isSprinting);
        state.put("isHoldingAngle", isHoldingAngle);
        state.put("alertness", alertness);
        state.put("complacency", complacency);
        state.put("behaviorState", behaviorState);
        state.put("homeDistance", homeDistance);
        rec.put("state", state);

        Map<String, Object> brain = new LinkedHashMap<>();
        brain.put("mode", mode);
        brain.put("actions", Map.of("move", moveAction, "shoot", shootAction));
        brain.put("neurons", Map.of("aggression", aggression, "fear", fear, "tactical", tactical));
        brain.put("tacticalAdvice", tacticalAdvice);
        rec.put("brain", brain);
        if (raycasts != null && !raycasts.isEmpty()) {
            rec.put("raycasts", raycasts);
        }

        session.timeline.add(rec);

        ScavStats stats = session.statsByScav.computeIfAbsent(scavId, id -> new ScavStats());
        stats.brainLevel = brainLevel;
        if (stats.firstSeenTick == 0) stats.firstSeenTick = Bukkit.getCurrentTick();
        stats.lastSeenTick = Bukkit.getCurrentTick();
    }

    public synchronized void logPlayerSnapshot(String raidId, Player player) {
        if (!enabled || player == null) return;
        RaidLogSession session = sessions.get(raidId);
        if (session == null) return;

        long nowTick = Bukkit.getCurrentTick();
        long nowMs = System.currentTimeMillis();
        UUID playerId = player.getUniqueId();
        Location loc = player.getLocation();

        PlayerMotionState motion = session.playerMotion.computeIfAbsent(playerId, id -> new PlayerMotionState());
        double delta = 0.0;
        double speedPerSec = 0.0;
        if (motion.lastLoc != null && motion.lastLoc.getWorld() != null && motion.lastLoc.getWorld().equals(loc.getWorld())) {
            delta = loc.distance(motion.lastLoc);
            long dtTicks = Math.max(1, nowTick - motion.lastTick);
            speedPerSec = delta / (dtTicks / 20.0);
        }

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "player_snapshot");
        rec.put("raidId", raidId);
        rec.put("tick", nowTick);
        rec.put("timeMs", nowMs);
        rec.put("playerId", playerId.toString());
        rec.put("playerName", player.getName());
        rec.put("pos", posMap(loc));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("isSprinting", player.isSprinting());
        state.put("isSneaking", player.isSneaking());
        state.put("isOnGround", player.isOnGround());
        state.put("health", player.getHealth());
        state.put("gameMode", player.getGameMode().name());
        state.put("deltaDistance", delta);
        state.put("speedPerSec", speedPerSec);
        rec.put("state", state);

        session.timeline.add(rec);
        motion.lastLoc = loc.clone();
        motion.lastTick = nowTick;
    }

    public synchronized void logEvent(String raidId, UUID scavId, String eventName, Map<String, Object> payload) {
        if (!enabled) return;
        RaidLogSession session = sessions.get(raidId);
        if (session == null) return;

        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("type", "event");
        rec.put("event", eventName);
        rec.put("raidId", raidId);
        rec.put("tick", Bukkit.getCurrentTick());
        rec.put("timeMs", System.currentTimeMillis());
        rec.put("scavId", scavId.toString());
        rec.put("payload", payload != null ? payload : Collections.emptyMap());
        session.events.add(rec);

        ScavStats stats = session.statsByScav.computeIfAbsent(scavId, id -> new ScavStats());
        if (stats.firstSeenTick == 0) stats.firstSeenTick = Bukkit.getCurrentTick();
        stats.lastSeenTick = Bukkit.getCurrentTick();

        switch (eventName) {
            case "SHOT_FIRED" -> stats.shotsFired++;
            case "SHOT_HIT" -> {
                stats.shotsHit++;
                Object penetrated = payload != null ? payload.get("penetrated") : null;
                Object dmg = payload != null ? payload.get("damage") : null;
                if (Boolean.FALSE.equals(penetrated)) {
                    stats.lowEffectiveHits++;
                } else if (dmg instanceof Number n && n.doubleValue() < 4.0) {
                    stats.lowEffectiveHits++;
                }
            }
            case "TARGET_SWITCH" -> stats.targetSwitches++;
            case "TOOK_DAMAGE" -> {
                stats.damageTakenCount++;
                Object dmg = payload != null ? payload.get("finalDamage") : null;
                if (dmg instanceof Number n) stats.damageTakenTotal += n.doubleValue();
            }
            default -> {
            }
        }
    }

    public synchronized void forceFlushAll(String reason) {
        if (!enabled) return;
        List<String> ids = new ArrayList<>(sessions.keySet());
        for (String id : ids) {
            endRaidSession(id, reason);
        }
    }

    private void writeSession(RaidLogSession session) {
        try {
            writeMeta(session);
            writeJsonLines(new File(session.dir, "timeline.jsonl"), session.timeline);
            writeJsonLines(new File(session.dir, "scav_events.jsonl"), session.events);
            writeSummary(session);
            cleanupOldLogs();
            plugin.getLogger().info("[AI-LOG] Wrote raid log: " + session.dir.getAbsolutePath());
        } catch (Exception e) {
            plugin.getLogger().warning("[AI-LOG] Failed writing raid log " + session.raidId + ": " + e.getMessage());
        }
    }

    private void writeMeta(RaidLogSession session) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schemaVersion", SCHEMA_VERSION);
        meta.put("raidId", session.raidId);
        meta.put("mapId", session.mapId);
        meta.put("world", session.worldName);
        meta.put("startTick", session.startTick);
        meta.put("endTick", session.endTick);
        meta.put("startTimeMs", session.startTimeMs);
        meta.put("endTimeMs", session.endTimeMs);
        meta.put("endReason", session.endReason);
        meta.put("pluginVersion", plugin.getDescription().getVersion());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sampleIntervalTicks", sampleIntervalTicks);
        config.put("asyncWrite", asyncWrite);
        config.put("captureRaycast", captureRaycast);
        meta.put("configSnapshot", config);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(session.dir, "meta.json")))) {
            writer.write(gson.toJson(meta));
        }
    }

    private void writeSummary(RaidLogSession session) throws IOException {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", SCHEMA_VERSION);
        summary.put("raidId", session.raidId);
        summary.put("mapId", session.mapId);
        summary.put("eventCount", session.events.size());
        summary.put("snapshotCount", session.timeline.size());

        List<Map<String, Object>> scavStats = new ArrayList<>();
        for (Map.Entry<UUID, ScavStats> e : session.statsByScav.entrySet()) {
            ScavStats s = e.getValue();
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("scavId", e.getKey().toString());
            rec.put("brainLevel", s.brainLevel);
            rec.put("shotsFired", s.shotsFired);
            rec.put("shotsHit", s.shotsHit);
            rec.put("hitRate", s.shotsFired > 0 ? (double) s.shotsHit / s.shotsFired : 0.0);
            rec.put("lowEffectiveHits", s.lowEffectiveHits);
            rec.put("targetSwitches", s.targetSwitches);
            rec.put("damageTakenCount", s.damageTakenCount);
            rec.put("damageTakenTotal", s.damageTakenTotal);
            rec.put("firstSeenTick", s.firstSeenTick);
            rec.put("lastSeenTick", s.lastSeenTick);
            scavStats.add(rec);
        }
        summary.put("scavs", scavStats);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(session.dir, "summary.json")))) {
            writer.write(gson.toJson(summary));
        }
    }

    private void writeJsonLines(File file, List<Map<String, Object>> lines) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (Map<String, Object> line : lines) {
                writer.write(gson.toJson(line));
                writer.newLine();
            }
        }
    }

    private Map<String, Object> posMap(Location loc) {
        Map<String, Object> out = xyzMap(loc);
        out.put("yaw", loc.getYaw());
        out.put("pitch", loc.getPitch());
        return out;
    }

    private Map<String, Object> xyzMap(Location loc) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", loc.getX());
        out.put("y", loc.getY());
        out.put("z", loc.getZ());
        return out;
    }

    private void cleanupOldLogs() {
        File[] dirs = rootDir.listFiles(File::isDirectory);
        if (dirs == null || dirs.length <= maxRaidLogs) return;

        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified));
        int toDelete = dirs.length - maxRaidLogs;
        for (int i = 0; i < toDelete; i++) {
            deleteRecursively(dirs[i]);
        }
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
