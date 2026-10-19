package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 隠れ家の場所の番号の台帳 (プレイヤー → 番号。hideout/slots.json)。番号は重ならないように配る。
 * <p>
 * 前は番号を「今までにサーバーに来た人の数」で決めていたので、同じ日に初めて隠れ家に入った人どうしが同じ番号になることがあった。
 * また、番号を足す前のプレイヤーデータは番号 0 として読まれる。台帳に無い人が来た時は、持っている番号がまだ誰の物でもなければ
 * それを認め、誰かの物ならまだ使われていない番号に移す ({@link #claim})
 */
final class HideoutSlots {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File file;
    private final Logger logger;
    private final Map<UUID, Integer> slots = new LinkedHashMap<>();

    HideoutSlots(File folder, Logger logger) {
        this.file = new File(folder, "slots.json");
        this.logger = logger;
        load();
        reserveExistingPlayerSlots(new File(folder.getParentFile(), "userdata"));
    }

    /** 台帳に載った番号か、新しく決めた番号 */
    record Claim(int slot, boolean registered) {
    }

    /**
     * このプレイヤーの番号を決める。台帳にあればその番号。無ければ、current (プレイヤーデータの番号) が 0 以上で誰の物でもなければそれ、
     * そうでなければまだ使われていない番号を台帳に載せる (registered = true。前に別の人と同じ所を使っていたかもしれないので、建て直しが要る)
     */
    synchronized Claim claim(UUID player, int current) {
        Integer known = slots.get(player);
        if (known != null) return new Claim(known, false);
        int slot = current >= 0 && !slots.containsValue(current) ? current : next();
        slots.put(player, slot);
        save();
        return new Claim(slot, true);
    }

    private int next() {
        int slot = 0;
        while (slots.containsValue(slot)) slot++;
        return slot;
    }

    /**
     * 台帳導入前から保存されているプレイヤーの番号を先に予約する。
     * オフラインの人や、まだ隠れ家に入ったことがない人の番号を知らずに再利用すると、
     * 別の人が visit した時の建て直しで元の隠れ家を上書きしてしまう。
     */
    private void reserveExistingPlayerSlots(File dataFolder) {
        Map<UUID, File> players = new HashMap<>();
        File[] files = dataFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files != null) {
            for (File main : files) {
                try {
                    UUID player = UUID.fromString(main.getName().substring(0, main.getName().length() - 5));
                    players.put(player, main);
                } catch (IllegalArgumentException ignored) {
                    // userdata に UUID 以外の JSON があっても無視する。
                }
            }
        }
        File[] backupDirs = new File(dataFolder, "backups").listFiles(File::isDirectory);
        if (backupDirs != null) {
            for (File backupDir : backupDirs) {
                try {
                    UUID player = UUID.fromString(backupDir.getName());
                    players.putIfAbsent(player, new File(dataFolder, player + ".json"));
                } catch (IllegalArgumentException ignored) {
                    // UUID 形式でないバックアップフォルダーは無視する。
                }
            }
        }

        boolean changed = false;
        List<UUID> orderedPlayers = new ArrayList<>(players.keySet());
        orderedPlayers.sort(Comparator.comparing(UUID::toString));
        for (UUID player : orderedPlayers) {
            if (slots.containsKey(player)) continue;

            PlayerData data = readExistingData(player, players.get(player), new File(dataFolder, "backups"));
            if (data == null || data.getHideoutIndex() < 0) continue;

            int oldSlot = data.getHideoutIndex();
            int reserved = slots.containsValue(oldSlot) ? next() : oldSlot;
            slots.put(player, reserved);
            changed = true;
            if (reserved != oldSlot) {
                logger.warning("隠れ家番号 " + oldSlot + " が重複しています。未登録プレイヤー " + player
                        + " の番号を " + reserved + " に割り当てます");
            }
        }
        if (changed) save();
    }

    private PlayerData readExistingData(UUID player, File main, File backupRoot) {
        List<File> candidates = new ArrayList<>();
        candidates.add(main);
        File backupDir = new File(backupRoot, player.toString());
        for (int i = 1; i <= 3; i++) candidates.add(new File(backupDir, i + ".json"));
        File[] daily = backupDir.listFiles((dir, name) -> name.startsWith("daily-") && name.endsWith(".json"));
        if (daily != null) {
            Arrays.sort(daily, Comparator.comparing(File::getName).reversed());
            candidates.addAll(Arrays.asList(daily));
        }
        for (File candidate : candidates) {
            if (!candidate.isFile()) continue;
            try {
                PlayerData data = GSON.fromJson(Files.readString(candidate.toPath(), StandardCharsets.UTF_8), PlayerData.class);
                if (data != null && player.equals(data.getUuid())) return data;
            } catch (IOException | RuntimeException e) {
                logger.warning("既存の隠れ家番号を読み取れませんでした: " + candidate + ": " + e.getMessage());
            }
        }
        return null;
    }

    private void load() {
        if (!file.isFile()) return;
        try {
            Map<String, Integer> raw = GSON.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8),
                    new TypeToken<HashMap<String, Integer>>() { }.getType());
            if (raw != null) raw.forEach((id, slot) -> slots.put(UUID.fromString(id), slot));
        } catch (IOException | RuntimeException e) {
            // 空の台帳から始めても、各自が持っている番号を認め直す (重なった人だけ移す) ので、隠れ家は重ならない
            logger.warning("隠れ家の台帳を読めませんでした (空から作り直します): " + e.getMessage());
        }
    }

    private void save() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        slots.forEach((id, slot) -> raw.put(id.toString(), slot));
        try {
            file.getParentFile().mkdirs();
            Files.writeString(file.toPath(), GSON.toJson(raw), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warning("隠れ家の台帳を書けませんでした: " + e.getMessage());
        }
    }
}
