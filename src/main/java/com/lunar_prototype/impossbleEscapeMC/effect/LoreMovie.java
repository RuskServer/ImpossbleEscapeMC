package com.lunar_prototype.impossbleEscapeMC.effect;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ロアのムービー (ScreenEffectService の iem:lore_intro) を見せる。見ている間はスペクテイターにして、ホットバーなどの画面の表示を消し、
 * 動けないようにする (画面はムービーが全部描きかえる)。終わったら (ムービーの画面を外した時)、元のゲームモードと場所に戻す。
 * 途中で抜けた時・プラグインを止めた時・時間割が別の演出に差し替えられて終わりが来なかった時 (長さより {@link #SAFETY_TICKS} 過ぎても
 * 見ている扱いのまま) も戻す (スペクテイターのまま残さない)
 */
public final class LoreMovie implements Listener {

    /** 終わりが来なかった時に戻すまでの余り */
    private static final int SAFETY_TICKS = 10 * 20;

    /** 見る前のゲームモードと場所、何回目に流したか (戻す保険の時間が、後から流した物を止めないように) */
    private record Watching(GameMode mode, Location location, long run) {
    }

    private static LoreMovie instance;

    private final JavaPlugin plugin;
    private final Map<UUID, Watching> watching = new HashMap<>();
    private long runs;

    private LoreMovie(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static void init(JavaPlugin plugin) {
        if (instance != null) return;
        instance = new LoreMovie(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
    }

    public static void shutdown() {
        if (instance == null) return;
        for (UUID id : instance.watching.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) instance.finish(player);
        }
        instance = null;
    }

    /** ムービーを流す (見ている途中なら何もしない)。戻り値は流したか */
    public static boolean play(Player player) {
        if (instance == null || instance.watching.containsKey(player.getUniqueId())) return false;
        long run = ++instance.runs;
        instance.watching.put(player.getUniqueId(), new Watching(player.getGameMode(), player.getLocation(), run));
        player.setGameMode(GameMode.SPECTATOR);
        ScreenEffectService.playLoreIntro(player, () -> instance.finish(player));
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(instance.plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null || instance == null) return;
            Watching now = instance.watching.get(id);
            if (now != null && now.run() == run) instance.finish(p);
        }, ScreenEffectService.LORE_INTRO_TICKS + ScreenEffectService.LORE_INTRO_RETURN_TICKS + SAFETY_TICKS);
        return true;
    }

    /** 元のゲームモードと場所に戻す (見ていなければ何もしない) */
    private void finish(Player player) {
        Watching before = watching.remove(player.getUniqueId());
        if (before == null || !player.isOnline()) return;
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.teleport(before.location());
            player.setGameMode(before.mode());
        }
    }

    /** 抜ける時に戻しておく (スペクテイターのまま保存されないように) */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        finish(event.getPlayer());
    }
}
