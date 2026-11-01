package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 発射の1tick信号。modには1byte、通常クライアントには専用の2画素だけを渡す。 */
final class MuzzleFlashSignal {
    static final String CHANNEL = "iem:muzzle";
    private final ImpossbleEscapeMC plugin;
    private final Map<UUID, Integer> shots = new HashMap<>();
    private final Map<UUID, TextDisplay> displays = new HashMap<>();

    MuzzleFlashSignal(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    void flash(Player player, int mode) {
        shots.put(player.getUniqueId(), Bukkit.getCurrentTick());
        send(player, mode);
    }

    void tick(int now) {
        for (var entry : new HashMap<>(shots).entrySet()) {
            if (now - entry.getValue() < 1) continue;
            shots.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) send(player, 0);
        }
        for (var entry : new HashMap<>(displays).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            TextDisplay display = entry.getValue();
            if (player == null || !display.isValid() || player.getWorld() != display.getWorld()) {
                clear(entry.getKey());
            } else if (display.getLocation().distanceSquared(player.getLocation()) > 4) {
                display.teleport(player.getLocation().add(0, 1.5, 0));
            }
        }
    }

    private void send(Player player, int mode) {
        if (player.getListeningPluginChannels().contains(CHANNEL)) {
            TextDisplay old = displays.remove(player.getUniqueId());
            if (old != null) old.remove();
            player.sendPluginMessage(plugin, CHANNEL, new byte[]{(byte) mode});
            return;
        }
        TextDisplay display = displays.get(player.getUniqueId());
        if (display == null || !display.isValid() || display.getWorld() != player.getWorld()) {
            if (display != null) display.remove();
            display = player.getWorld().spawn(player.getLocation().add(0, 1.5, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setSeeThrough(true);
                d.setShadowed(false);
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setLineWidth(32);
                d.setViewRange(4);
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(.01f), new Quaternionf()));
            });
            displays.put(player.getUniqueId(), display);
            player.showEntity(plugin, display);
        }
        // 専用色をcore/textで識別する。位置はワールド座標ではなく専用画素へ移す。
        display.text(Component.text("\u4e00").font(Key.key("iem", "data")).color(TextColor.color(212, 123, mode)));
    }

    void clear(UUID id) {
        shots.remove(id);
        TextDisplay display = displays.remove(id);
        if (display != null) display.remove();
        Player player = Bukkit.getPlayer(id);
        if (player != null && player.getListeningPluginChannels().contains(CHANNEL))
            player.sendPluginMessage(plugin, CHANNEL, new byte[]{0});
    }

    void close() {
        for (UUID id : new HashMap<>(displays).keySet()) clear(id);
        for (UUID id : new HashMap<>(shots).keySet()) clear(id);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
    }
}
