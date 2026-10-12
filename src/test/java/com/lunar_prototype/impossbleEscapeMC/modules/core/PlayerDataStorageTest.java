package com.lunar_prototype.impossbleEscapeMC.modules.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * プレイヤーデータのファイルの読み書きを、一時フォルダで確かめる。サーバーは要らない。
 * <ul>
 *   <li>無いプレイヤーは新しいデータ (失敗ではない)</li>
 *   <li>本体が壊れていれば控えから読み、壊れた本体を corrupt/ に写す</li>
 *   <li>本体も控えも壊れていれば「失敗」(保存してはいけない)</li>
 *   <li>番号の古い書き出しは、新しい物の後から書かない</li>
 *   <li>書くたびに 3 世代の控えが回り、1 日 1 回の控えは 7 日分だけ残る</li>
 * </ul>
 */
public final class PlayerDataStorageTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static PlayerData data(UUID uuid, double balance) {
        PlayerData data = new PlayerData(uuid);
        data.setBalance(balance);
        return data;
    }

    public static void main(String[] args) throws Exception {
        File dir = Files.createTempDirectory("iem-userdata").toFile();
        Logger logger = Logger.getLogger("PlayerDataStorageTest");
        logger.setLevel(Level.OFF);
        AtomicReference<LocalDate> today = new AtomicReference<>(LocalDate.of(2026, 10, 1));
        PlayerDataStorage storage = new PlayerDataStorage(dir, logger, today::get);
        UUID uuid = UUID.randomUUID();

        // 初めてのプレイヤー
        PlayerDataStorage.LoadResult fresh = storage.load(uuid);
        check(!fresh.failed() && !fresh.fromBackup() && fresh.data().getBalance() == 0, "fresh player");

        // 書いて読む
        check(storage.write(uuid, 1, storage.toJson(data(uuid, 100))), "write 1");
        check(storage.load(uuid).data().getBalance() == 100, "read back");

        // 古い番号は書かない
        check(storage.write(uuid, 3, storage.toJson(data(uuid, 300))), "write 3");
        check(storage.write(uuid, 2, storage.toJson(data(uuid, 200))), "stale write is skipped");
        check(storage.load(uuid).data().getBalance() == 300, "stale write did not overwrite");

        // 本体が壊れたら控えから読む (1.json = 最後に書く前の本体 = 100)
        Files.writeString(storage.mainFile(uuid).toPath(), "{ broken", StandardCharsets.UTF_8);
        PlayerDataStorage.LoadResult fromBackup = storage.load(uuid);
        check(!fromBackup.failed() && fromBackup.fromBackup(), "loads from backup");
        check(fromBackup.data().getBalance() == 100, "newest backup is the file before the last write: " + fromBackup.data().getBalance());
        File[] corrupt = new File(dir, "corrupt").listFiles();
        check(corrupt != null && corrupt.length == 1, "corrupt main kept");

        // 本体も控えも壊れていたら失敗 (保存してはいけない)
        for (File f : storage.backupDir(uuid).listFiles()) Files.writeString(f.toPath(), "not json", StandardCharsets.UTF_8);
        PlayerDataStorage.LoadResult failed = storage.load(uuid);
        check(failed.failed(), "all broken -> failed");

        // 別のプレイヤーのデータが入っていたら読まない
        UUID other = UUID.randomUUID();
        Files.writeString(storage.mainFile(other).toPath(), storage.toJson(data(uuid, 5)), StandardCharsets.UTF_8);
        check(storage.load(other).failed(), "wrong uuid inside the file");

        // 控えの数: 3 世代、1 日 1 回は 7 日分
        UUID rotating = UUID.randomUUID();
        for (int day = 0; day < 10; day++) {
            today.set(LocalDate.of(2026, 10, 1).plusDays(day));
            for (int i = 0; i < 3; i++) storage.write(rotating, 100L + day * 10 + i, storage.toJson(data(rotating, day * 10 + i)));
        }
        File[] backups = storage.backupDir(rotating).listFiles();
        long rotatingCount = java.util.Arrays.stream(backups).filter(f -> f.getName().matches("\\d\\.json")).count();
        long dailyCount = java.util.Arrays.stream(backups).filter(f -> f.getName().startsWith("daily-")).count();
        check(rotatingCount == PlayerDataStorage.ROTATING_BACKUPS, "rotating backups " + rotatingCount);
        check(dailyCount == PlayerDataStorage.DAILY_BACKUPS, "daily backups " + dailyCount);
        check(new File(storage.backupDir(rotating), "daily-2026-10-10.json").exists(), "latest daily kept");
        check(!new File(storage.backupDir(rotating), "daily-2026-10-01.json").exists(), "oldest daily removed");
        System.out.println("PlayerDataStorageTest: load fallback, failure guard, stale writes and backups passed");
    }
}
