package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * プレイヤーデータのファイル (userdata/&lt;uuid&gt;.json) の読み書き。サーバーに頼らない (テストで確かめられるように)。
 * <ul>
 *   <li>読む: 本体 → 3 世代の控え (新しい順) → 1 日 1 回の控え (新しい順)。読めない本体は corrupt/ に写す</li>
 *   <li>書く: 一時ファイルに書いて置き換える。置き換える前の本体を控えに残す。番号の古い書き出しは書かない</li>
 * </ul>
 */
final class PlayerDataStorage {
    static final int ROTATING_BACKUPS = 3;
    static final int DAILY_BACKUPS = 7;

    /**
     * 読んだ結果。failed は「ファイルはあるのに、本体からも控えからも読めなかった」(このデータは保存してはいけない)。
     * fromBackup は控えから読んだ (本体を書き直すべき)。created は本体も控えも 1 つも無く、新しいデータを作った (初めてのプレイヤー)
     */
    record LoadResult(PlayerData data, boolean failed, boolean fromBackup, boolean created) {
    }

    private final File dataFolder;
    private final File backupFolder;
    private final File corruptFolder;
    private final Logger logger;
    private final Supplier<LocalDate> today;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> written = new ConcurrentHashMap<>();

    PlayerDataStorage(File dataFolder, Logger logger) {
        this(dataFolder, logger, LocalDate::now);
    }

    PlayerDataStorage(File dataFolder, Logger logger, Supplier<LocalDate> today) {
        this.dataFolder = dataFolder;
        this.backupFolder = new File(dataFolder, "backups");
        this.corruptFolder = new File(dataFolder, "corrupt");
        this.logger = logger;
        this.today = today;
        dataFolder.mkdirs();
        backupFolder.mkdirs();
    }

    File mainFile(UUID uuid) {
        return new File(dataFolder, uuid + ".json");
    }

    File backupDir(UUID uuid) {
        return new File(backupFolder, uuid.toString());
    }

    String toJson(PlayerData data) {
        return gson.toJson(data);
    }

    LoadResult load(UUID uuid) {
        File main = mainFile(uuid);
        List<File> candidates = new ArrayList<>();
        candidates.add(main);
        File backups = backupDir(uuid);
        for (int i = 1; i <= ROTATING_BACKUPS; i++) candidates.add(new File(backups, i + ".json"));
        File[] daily = backups.listFiles((dir, name) -> name.startsWith("daily-") && name.endsWith(".json"));
        if (daily != null) {
            Arrays.sort(daily, (a, b) -> b.getName().compareTo(a.getName()));
            candidates.addAll(Arrays.asList(daily));
        }

        boolean anyFile = false;
        for (File file : candidates) {
            if (!file.isFile()) continue;
            anyFile = true;
            try {
                PlayerData data = gson.fromJson(Files.readString(file.toPath(), StandardCharsets.UTF_8), PlayerData.class);
                if (data == null || data.getUuid() == null) throw new IOException("空、または uuid の無いデータ");
                if (!uuid.equals(data.getUuid())) throw new IOException("別のプレイヤー (" + data.getUuid() + ") のデータ");
                if (file != main) {
                    logger.warning("プレイヤーデータ " + uuid + " を控え " + file.getName() + " から読みました (本体が読めませんでした)");
                }
                return new LoadResult(data, false, file != main, false);
            } catch (IOException | RuntimeException e) {
                logger.severe("プレイヤーデータ " + uuid + " の " + file.getName() + " を読めませんでした: " + e.getMessage());
                if (file == main) keepCorrupt(uuid, main);
            }
        }
        if (anyFile) {
            logger.severe("プレイヤーデータ " + uuid + " を本体からも控えからも読めませんでした。このプレイヤーの保存を止めます");
        }
        return new LoadResult(new PlayerData(uuid), anyFile, false, !anyFile);
    }

    /** 読めなかったファイルを corrupt/ に写しておく (後で調べる・手で直す用) */
    private void keepCorrupt(UUID uuid, File file) {
        try {
            corruptFolder.mkdirs();
            Files.copy(file.toPath(), new File(corruptFolder, uuid + "-" + System.currentTimeMillis() + ".json").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.warning("壊れたプレイヤーデータを控えに写せませんでした: " + e.getMessage());
        }
    }

    /**
     * 書く。number が前に書いた物以下なら書かない (古い書き出しが新しい物を後から上書きしない)。
     * 書けた・書かなくてよかった時は true、失敗は false
     */
    boolean write(UUID uuid, long number, String json) {
        Object lock = locks.computeIfAbsent(uuid, ignored -> new Object());
        synchronized (lock) {
            Long last = written.get(uuid);
            if (last != null && last >= number) return true;
            File finalFile = mainFile(uuid);
            File tempFile = new File(dataFolder, uuid + ".json.tmp");
            try {
                dataFolder.mkdirs();
                Files.writeString(tempFile.toPath(), json, StandardCharsets.UTF_8);
                if (finalFile.exists()) backup(uuid, finalFile);
                try {
                    Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                written.put(uuid, number);
                return true;
            } catch (IOException e) {
                logger.severe("プレイヤーデータ " + uuid + " を保存できませんでした: " + e.getMessage());
                tempFile.delete();
                return false;
            }
        }
    }

    /** 書き換える前の本体を控えに残す: 3 世代 (1 が新しい) と、その日の最初の 1 つ (DAILY_BACKUPS 日分) */
    private void backup(UUID uuid, File mainFile) {
        File backupDir = backupDir(uuid);
        backupDir.mkdirs();
        try {
            for (int i = ROTATING_BACKUPS; i >= 1; i--) {
                File current = new File(backupDir, i + ".json");
                if (!current.exists()) continue;
                if (i == ROTATING_BACKUPS) {
                    Files.delete(current.toPath());
                } else {
                    Files.move(current.toPath(), new File(backupDir, (i + 1) + ".json").toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.copy(mainFile.toPath(), new File(backupDir, "1.json").toPath(), StandardCopyOption.REPLACE_EXISTING);

            File daily = new File(backupDir, "daily-" + today.get() + ".json");
            if (!daily.exists()) {
                Files.copy(mainFile.toPath(), daily.toPath());
                File[] all = backupDir.listFiles((dir, name) -> name.startsWith("daily-") && name.endsWith(".json"));
                if (all != null && all.length > DAILY_BACKUPS) {
                    Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
                    for (int i = 0; i < all.length - DAILY_BACKUPS; i++) Files.deleteIfExists(all[i].toPath());
                }
            }
        } catch (IOException e) {
            logger.warning("プレイヤーデータ " + uuid + " の控えを作れませんでした: " + e.getMessage());
        }
    }
}
