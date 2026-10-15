package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 隠れ家の画面を開いている間のカメラ: プレイヤーをスペクテイターにして、見えないディスプレイエンティティの視点にする。
 * 設備を切り替えると、そのエンティティを滑らかに動かす (teleport_duration)。後ろのぼかしは止める (ScreenEffectService の印)。
 * 終わる時は、元の位置とゲームモードに戻す (ほかのワールドへ移された後は戻さない)
 */
final class HideoutCamera {
    /** 設備を切り替えた時に動く時間 (tick) */
    private static final int MOVE_TICKS = 16;
    static final String TAG = "iem_hideout_camera";

    private record Saved(Location location, GameMode mode, ItemDisplay camera) {
    }

    private final JavaPlugin plugin;
    private final Map<UUID, Saved> active = new HashMap<>();

    HideoutCamera(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** カメラを pose に置いて、プレイヤーの視点にする (同じワールドの時だけ)。できたら true */
    boolean start(Player player, Location pose) {
        if (pose == null || pose.getWorld() == null || !pose.getWorld().equals(player.getWorld())) return false;
        stop(player);
        ItemDisplay camera = pose.getWorld().spawn(pose, ItemDisplay.class, display -> {
            display.setPersistent(false);
            display.setTeleportDuration(0);
            display.addScoreboardTag(TAG);
        });
        active.put(player.getUniqueId(), new Saved(player.getLocation(), player.getGameMode(), camera));
        player.setGameMode(GameMode.SPECTATOR);
        ScreenEffectService.setMenuClear(player, true);
        // エンティティがクライアントに届いてから視点を移す
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Saved saved = active.get(player.getUniqueId());
            if (saved != null && saved.camera() == camera && player.isOnline()) player.setSpectatorTarget(camera);
        }, 2L);
        return true;
    }

    /** カメラを pose へ滑らかに動かす */
    void move(Player player, Location pose) {
        Saved saved = active.get(player.getUniqueId());
        if (saved == null || pose == null || !saved.camera().isValid()) return;
        saved.camera().setTeleportDuration(MOVE_TICKS);
        saved.camera().teleport(pose);
    }

    boolean isActive(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    /** カメラの視点から外れているか (しゃがんで抜けた・別の所へ移された)。始めた直後 (視点を移す前) は外れていない扱い */
    boolean lost(Player player) {
        Saved saved = active.get(player.getUniqueId());
        if (saved == null) return false;
        if (!saved.camera().isValid()) return true;
        return player.getSpectatorTarget() != null ? player.getSpectatorTarget() != saved.camera()
                : saved.camera().getTicksLived() > 10;
    }

    /**
     * カメラをやめて、元の位置とゲームモードに戻す。ほかのワールドへ移された後 (画面を開いたまま出撃した時など) は、
     * 移った先を優先して戻さない (戻すとレイドの参加者が隠れ家に引き戻される)
     */
    void stop(Player player) {
        Saved saved = active.remove(player.getUniqueId());
        if (saved == null) return;
        if (player.getSpectatorTarget() != null) player.setSpectatorTarget(null);
        if (player.getWorld().equals(saved.location().getWorld())) {
            player.teleport(saved.location());
            player.setGameMode(saved.mode());
        }
        ScreenEffectService.setMenuClear(player, false);
        saved.camera().remove();
    }

    /** 全員のカメラをやめる (プラグインを止める時) */
    void stopAll() {
        for (UUID id : active.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                stop(player);
            } else {
                Saved saved = active.remove(id);
                if (saved != null) saved.camera().remove();
            }
        }
    }
}
