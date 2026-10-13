package com.lunar_prototype.impossbleEscapeMC.modules.core;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * プレイヤーデータの読み込み・保存・キャッシュ (userdata/&lt;uuid&gt;.json)。
 * <p>
 * データが消えないための決まり:
 * <ul>
 *   <li>キャッシュに無いデータは必ずディスクから読む。空のデータを作るのは、ファイルも控えも無い (初めての) プレイヤーだけ。
 *       オフラインのプレイヤーへの振り込み (マーケットの売上など) も、本物のデータに足される</li>
 *   <li>読めないファイルは控え (userdata/corrupt) に写し、新しい控えから順に読む。どれも読めなければ空で始めず、
 *       ログインを断り、そのデータは保存しない (壊れたファイルを空のデータで上書きしない)</li>
 *   <li>再ログイン (タイムアウトした古い接続が残っている時など) では、ログイン中のキャッシュをそのまま使い、
 *       古い接続の退出でキャッシュを消さない (ディスクの古いデータや空のデータに置き換わらない)</li>
 *   <li>保存は、メインスレッドで JSON にしてから (変更あり の印を先に消す) 別スレッドでファイルに書く。
 *       書いている間の変更は次の保存に回り、古い書き出しが新しい物を後から上書きしない</li>
 *   <li>控え: 保存のたびに 3 世代 (backups/&lt;uuid&gt;/1〜3.json) と、1 日 1 回の控え 7 日分 (daily-日付.json)</li>
 * </ul>
 */
public class PlayerDataModule implements IModule, Listener {
    private final ImpossbleEscapeMC plugin;
    private final PlayerDataStorage storage;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();
    /** 読めなかった (保存してはいけない) プレイヤー */
    private final Set<UUID> loadFailed = ConcurrentHashMap.newKeySet();
    /**
     * ログインの途中 (読み込んだが、まだ参加していない) のプレイヤーと、その時刻 (ms)。古い接続の退出でキャッシュを消さない。
     * 途中で接続が切れて印が残っても、PENDING_LOGIN_MS を過ぎたら無いものとする
     */
    private final Map<UUID, Long> pendingLogins = new ConcurrentHashMap<>();
    private static final long PENDING_LOGIN_MS = 60_000L;
    /** 別スレッドで書いている途中の保存 (キャッシュから外す前・ディスクから読む前に待つ) */
    private final Map<UUID, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();
    /** 書き出しの番号 (新しい書き出しの後に古い書き出しを書かないように) */
    private final AtomicLong snapshotCounter = new AtomicLong();

    public PlayerDataModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.storage = new PlayerDataStorage(new File(plugin.getDataFolder(), "userdata"), plugin.getLogger());
    }

    @Override
    public void onEnable(ServiceContainer container) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // オートセーブ (5 分ごと)。JSON にするのはメインスレッド、書くのは別スレッド
        Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        // 終了時は全部をその場で書き、書きかけの物も書き終えるのを待つ
        for (PlayerData data : dataCache.values()) {
            if (data.isDirty()) writeNow(data);
        }
        for (UUID uuid : new ArrayList<>(pendingWrites.keySet())) awaitWrites(uuid);
    }

    /**
     * プレイヤーデータ。キャッシュに無ければディスクから読む (オフラインのプレイヤーも本物のデータ)。
     * ファイルも控えも無い時だけ新しいデータになる
     */
    public PlayerData getPlayerData(UUID uuid) {
        PlayerData cached = dataCache.get(uuid);
        if (cached != null) return cached;
        awaitWrites(uuid);   // 書いている途中の物があれば、書き終えてから読む
        return dataCache.computeIfAbsent(uuid, this::load);
    }

    private boolean isPendingLogin(UUID uuid) {
        Long since = pendingLogins.get(uuid);
        if (since == null) return false;
        if (System.currentTimeMillis() - since > PENDING_LOGIN_MS) {
            pendingLogins.remove(uuid, since);
            return false;
        }
        return true;
    }

    private void awaitWrites(UUID uuid) {
        CompletableFuture<Void> pending = pendingWrites.get(uuid);
        if (pending == null) return;
        try {
            pending.join();
        } catch (RuntimeException ignored) {
            // 失敗は書き込みの側で記録している (データは「変更あり」に戻っている)
        }
    }

    // ---- 読み込み ----

    /** ディスクから読む。読めなければ保存しない印を付ける。控えから読んだ時は本体を書き直す */
    private PlayerData load(UUID uuid) {
        PlayerDataStorage.LoadResult result = storage.load(uuid);
        if (result.failed()) {
            loadFailed.add(uuid);
        } else {
            loadFailed.remove(uuid);
        }
        if (result.fromBackup()) result.data().setDirty(true);
        if (result.created()) {
            // 初めてのプレイヤー: 参加した時に初期物資を入れる (StarterKit)。
            // 新しいデータは今まで通り、何か変わるまでは保存しない (参加しないまま読まれただけのデータをファイルにしない)
            result.data().setStarterKitPending(true);
            result.data().setDirty(false);
        }
        return result.data();
    }

    /** ログインの直前に、別スレッドで読む。読めなければログインを断る */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        // キャッシュにあれば (古い接続がまだ残っている・オフラインの間に触られた) それがディスクより新しいので使う
        getPlayerData(uuid);
        if (loadFailed.contains(uuid)) {
            dataCache.remove(uuid);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text(
                    "プレイヤーデータを読み込めませんでした。データを守るため参加を止めています。管理者に連絡してください。",
                    NamedTextColor.RED));
            return;
        }
        pendingLogins.put(uuid, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        // ログインが断られた: 途中の印を外す。ほかに同じプレイヤーが入っていなければキャッシュから外す (変更があれば保存)
        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getPlayer().getUniqueId();
        pendingLogins.remove(uuid);
        Player online = Bukkit.getPlayer(uuid);
        if (online == null || online == event.getPlayer()) release(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        pendingLogins.remove(uuid);
        // プラグインの読み込み直しなどで読み込みが走らなかった時も、ここでディスクから読む
        PlayerData data = getPlayerData(uuid);
        Bukkit.getPluginManager().callEvent(new PlayerDataLoadedEvent(player, data));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = dataCache.get(uuid);
        if (data != null && data.isDirty()) writeNow(data);
        // 同じプレイヤーがログインの途中 (再ログインで古い接続が退出した) なら、キャッシュはその接続が使う
        if (!isPendingLogin(uuid)) release(uuid);
    }

    /** キャッシュから外す (変更が残っていれば先に書く) */
    private void release(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data != null && data.isDirty()) writeNow(data);
        awaitWrites(uuid);   // 次に読む時に、書き終えたファイルを読むように
        dataCache.remove(uuid);
        loadFailed.remove(uuid);
    }

    // ---- 保存 ----

    /** 変更を別スレッドで保存する (JSON にするのは呼んだスレッド = メインスレッド) */
    public void saveAsync(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data == null || !data.isDirty()) return;
        Snapshot snapshot = snapshot(data);
        if (snapshot != null) writeLater(snapshot, data);
    }

    /** 別スレッドで書く。書き終えるまで pendingWrites に残す */
    private void writeLater(Snapshot snapshot, PlayerData data) {
        UUID uuid = snapshot.uuid();
        // 同じプレイヤーの書き込みは順に。前の書き込みが失敗しても次は書く
        CompletableFuture<Void> next = pendingWrites.compute(uuid, (id, previous) ->
                (previous != null ? previous.handle((ignored, error) -> null) : CompletableFuture.completedFuture(null))
                        .thenRunAsync(() -> write(snapshot, data)));
        next.whenComplete((ignored, error) -> {
            pendingWrites.remove(uuid, next);
            if (error != null) plugin.getLogger().severe("プレイヤーデータ " + uuid + " の保存に失敗しました: " + error);
        });
    }

    /** オートセーブ: 変更のあるデータを書き、オフラインで変更の無いデータはキャッシュから外す */
    private void autosave() {
        for (Map.Entry<UUID, PlayerData> entry : dataCache.entrySet()) {
            PlayerData data = entry.getValue();
            if (data.isDirty()) {
                Snapshot snapshot = snapshot(data);
                if (snapshot != null) writeLater(snapshot, data);
            } else if (Bukkit.getPlayer(entry.getKey()) == null && !isPendingLogin(entry.getKey())
                    && !pendingWrites.containsKey(entry.getKey())) {
                dataCache.remove(entry.getKey(), data);
            }
        }
    }

    /** その場で書く (退出・停止の時) */
    private void writeNow(PlayerData data) {
        Snapshot snapshot = snapshot(data);
        if (snapshot != null) write(snapshot, data);
    }

    /** 書き出す中身。「変更あり」は JSON にする前に消す (書いている間の変更は次の保存に回る) */
    private record Snapshot(UUID uuid, long number, String json) {
    }

    private Snapshot snapshot(PlayerData data) {
        UUID uuid = data.getUuid();
        if (loadFailed.contains(uuid)) return null;   // 読めなかったファイルを空のデータで上書きしない
        data.setDirty(false);
        try {
            return new Snapshot(uuid, snapshotCounter.incrementAndGet(), storage.toJson(data));
        } catch (RuntimeException e) {
            data.setDirty(true);
            plugin.getLogger().severe("プレイヤーデータ " + uuid + " を JSON にできませんでした: " + e);
            return null;
        }
    }

    private void write(Snapshot snapshot, PlayerData data) {
        if (!storage.write(snapshot.uuid(), snapshot.number(), snapshot.json())) {
            data.setDirty(true);   // 次の保存でもう一度
        }
    }
}
