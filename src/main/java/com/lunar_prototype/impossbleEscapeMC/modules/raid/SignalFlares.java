package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * レイド終盤の照明弾。地面から打ち上がって上空で点き、パラシュートでゆっくり落ちながら燃え、燃え尽きると消える。
 * 見た目はリソースパック (IEM_shader/tools/signal_flare.py) が描く:
 * <ul>
 *   <li>光の玉: カメラを向く item_display (iem:flare_core)。レンズフレアの光源になって赤くにじむ。全員に見せる</li>
 *   <li>照明弾の印: 小さな item_display (iem:flare_marker)。アイテムのシェーダーが照明弾の位置と明るさ (アイテムの色の赤) を
 *       画面の数値の画素に書き、ポストエフェクト iem:flare_light_&lt;段階&gt; がその位置から周りを赤く照らす</li>
 * </ul>
 * 照らす光には影が無いので、照明弾が見えている (目から照明弾までブロックが無い) 人にだけ照らす: その人にだけ印を見せ、
 * 照らす層 ({@link #lightEffect}) をかける。見えなくなったら段階を下げてゆっくり消してから印を隠す。
 * 1 人が照らされる照明弾は 1 発 (見えている中でいちばん近い物)
 */
final class SignalFlares {

    private static final Key CORE_MODEL = Key.key("iem", "flare_core");
    private static final Key MARKER_MODEL = Key.key("iem", "flare_marker");
    static final String TAG = "iem_signal_flare";
    /** 照らす光の段階の数 (signal_flare.py の LEVELS と同じ) */
    private static final int LIGHT_LEVELS = 4;

    /** 打ち上げから点くまで (tick) と、点く高さ (地面から、m) */
    private static final int RISE_TICKS = 30;
    private static final double APEX_MIN = 40.0;
    private static final double APEX_MAX = 55.0;
    /** 点いてから燃え尽きるまで (tick)。最後の FADE_TICKS で暗くなる。点いてすぐ IGNITE_TICKS で明るくなる */
    private static final int BURN_TICKS = 220;
    private static final int IGNITE_TICKS = 6;
    private static final int FADE_TICKS = 40;
    /** パラシュートで落ちる速さ (m/tick) と、風で流れる速さの上限 (m/tick) */
    private static final double FALL_PER_TICK = 0.06;
    private static final double DRIFT_PER_TICK = 0.03;
    /** 動かす間隔 (tick)。その間はクライアントが補間する (teleport_duration) */
    private static final int MOVE_INTERVAL = 4;
    private static final float CORE_SIZE = 1.8f;
    /** 表示する距離の倍率 (1 で 64 ブロック。クライアントのエンティティの描画距離の設定も掛かる) */
    private static final float VIEW_RANGE = 4.0f;
    /** 見えているかを調べ、照らす段階を 1 つ進める間隔 (tick) */
    private static final int VISIBILITY_INTERVAL = 5;
    private static final float SOUND_VOLUME = 6.0f;

    private final ImpossbleEscapeMC plugin;
    private final Random random = new Random();
    private final List<Flare> flares = new ArrayList<>();
    /** プレイヤーごとの、照らしている照明弾と段階 */
    private final Map<UUID, Lit> lit = new HashMap<>();
    private Set<UUID> players = Set.of();
    private BukkitTask task;
    private int ticks;

    private static final class Flare {
        final World world;
        final Location ground;
        final Location apex;
        Location position;
        final Vector drift;
        int age;
        ItemDisplay core;
        ItemDisplay marker;

        Flare(World world, Location ground, Location apex, Vector drift) {
            this.world = world;
            this.ground = ground;
            this.apex = apex;
            this.position = ground.clone();
            this.drift = drift;
        }

        boolean burning() {
            return core != null && age >= RISE_TICKS;
        }

        /** 明るさ (0..1): 点いてすぐ明るくなり、燃え尽きる前に暗くなる */
        double power() {
            int burn = age - RISE_TICKS;
            if (core == null || burn < 0) return 0.0;
            double ignite = Math.min(1.0, (burn + 1) / (double) IGNITE_TICKS);
            double fade = Math.min(1.0, Math.max(0.0, (BURN_TICKS - burn) / (double) FADE_TICKS));
            return ignite * fade;
        }
    }

    private static final class Lit {
        Flare flare;
        int level;
    }

    SignalFlares(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
    }

    /** 照らす人を決める対象 (レイドに残っている人)。毎秒の段階の進行で渡す */
    void setPlayers(Set<UUID> players) {
        this.players = Set.copyOf(players);
    }

    /** ground (地面の上) から 1 発打ち上げる */
    void launch(Location ground) {
        World world = ground.getWorld();
        double height = APEX_MIN + random.nextDouble() * (APEX_MAX - APEX_MIN);
        double apexY = Math.min(ground.getY() + height, world.getMaxHeight() - 4);
        Location apex = ground.clone().add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
        apex.setY(apexY);
        double angle = random.nextDouble() * Math.PI * 2;
        double speed = DRIFT_PER_TICK * (0.3 + 0.7 * random.nextDouble());
        Vector drift = new Vector(Math.cos(angle) * speed, 0, Math.sin(angle) * speed);
        flares.add(new Flare(world, ground, apex, drift));
        world.playSound(ground, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.MASTER, SOUND_VOLUME, 0.7f);
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** このプレイヤーにかける照らす光のエフェクト (照らしていなければ null) */
    Key lightEffect(UUID playerId) {
        Lit state = lit.get(playerId);
        return state != null && state.level > 0 ? Key.key("iem", "flare_light_" + state.level) : null;
    }

    /** レイドの終了時: 照明弾をすべて消す */
    void stop() {
        for (Flare flare : flares) remove(flare);
        flares.clear();
        lit.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        ticks++;
        for (Iterator<Flare> it = flares.iterator(); it.hasNext(); ) {
            Flare flare = it.next();
            flare.age++;
            if (flare.age < RISE_TICKS) {
                rise(flare);
            } else if (flare.age == RISE_TICKS) {
                ignite(flare);
            } else if (flare.age - RISE_TICKS >= BURN_TICKS) {
                remove(flare);
                it.remove();
            } else {
                burn(flare);
            }
        }
        if (ticks % VISIBILITY_INTERVAL == 0) updateLight();
        if (flares.isEmpty() && lit.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 打ち上げ: 煙と火花の尾を引いて上がる (少し減速しながら) */
    private void rise(Flare flare) {
        double t = flare.age / (double) RISE_TICKS;
        double eased = 1.0 - (1.0 - t) * (1.0 - t);
        flare.position = flare.ground.clone().add(flare.apex.clone().subtract(flare.ground).toVector().multiply(eased));
        flare.world.spawnParticle(Particle.FIREWORK, flare.position, 1, 0.05, 0.05, 0.05, 0.01, null, true);
        flare.world.spawnParticle(Particle.SMOKE, flare.position, 2, 0.05, 0.05, 0.05, 0.005, null, true);
    }

    /** 上空で点く: 光の玉と照明弾の印を出す */
    private void ignite(Flare flare) {
        flare.position = flare.apex.clone();
        flare.world.playSound(flare.position, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, SoundCategory.MASTER, SOUND_VOLUME, 0.8f);
        if (!flare.world.isChunkLoaded(flare.position.getBlockX() >> 4, flare.position.getBlockZ() >> 4)) return;
        ItemStack coreItem = new ItemStack(Material.PAPER);
        coreItem.setData(DataComponentTypes.ITEM_MODEL, CORE_MODEL);
        flare.core = flare.world.spawn(flare.position, ItemDisplay.class, display -> {
            display.setItemStack(coreItem);
            setup(display, CORE_SIZE);
        });
        flare.marker = flare.world.spawn(flare.position, ItemDisplay.class, display -> {
            display.setItemStack(markerItem(0.0));
            setup(display, 1.0f);
            // 見えている人にだけ見せる (updateLight)
            display.setVisibleByDefault(false);
        });
    }

    private static void setup(ItemDisplay display, float size) {
        display.setPersistent(false);
        display.addScoreboardTag(TAG);
        display.setBillboard(Display.Billboard.CENTER);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setShadowRadius(0);
        display.setViewRange(VIEW_RANGE);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
        display.setTeleportDuration(MOVE_INTERVAL);
        display.setInterpolationDuration(MOVE_INTERVAL);
    }

    /** 照明弾の印のアイテム。明るさは色の赤 (シェーダーが読む) */
    private static ItemStack markerItem(double power) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, MARKER_MODEL);
        int red = (int) Math.round(Math.max(0.0, Math.min(1.0, power)) * 255);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(Color.fromRGB(red, 0, 0)).build());
        return item;
    }

    /** 燃えている間: ゆっくり落ちて風に流れ、煙を残す。明るさを印に、燃え尽きる前の縮みを光の玉に反映する */
    private void burn(Flare flare) {
        if (flare.core == null) return;
        int burn = flare.age - RISE_TICKS;
        if (burn % 2 == 0) flare.world.spawnParticle(Particle.LARGE_SMOKE, flare.position, 1, 0.1, 0.1, 0.1, 0.005, null, true);
        if (burn % MOVE_INTERVAL != 0) return;
        double sway = Math.sin(flare.age * 0.08) * 0.02;
        flare.position = flare.position.clone().add(flare.drift.clone().multiply(MOVE_INTERVAL))
                .add(sway * MOVE_INTERVAL, -FALL_PER_TICK * MOVE_INTERVAL, 0);
        if (!flare.core.isValid() || !flare.marker.isValid()) return;
        flare.core.teleport(flare.position);
        flare.marker.teleport(flare.position);
        double power = flare.power();
        flare.marker.setItemStack(markerItem(power));
        float size = (float) (CORE_SIZE * (0.4 + 0.6 * power));
        flare.core.setInterpolationDelay(0);
        flare.core.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
    }

    private static void remove(Flare flare) {
        if (flare.core != null) flare.core.remove();
        if (flare.marker != null) flare.marker.remove();
        flare.core = null;
        flare.marker = null;
    }

    /**
     * 照らす光: 人ごとに、見えている中でいちばん近い照明弾を照らす。別の照明弾に替わる時や見えなくなった時は、
     * 段階を下げきってから印を隠す (急に消えないように)
     */
    private void updateLight() {
        for (Iterator<Map.Entry<UUID, Lit>> it = lit.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Lit> entry = it.next();
            if (players.contains(entry.getKey())) continue;
            hide(entry.getKey(), entry.getValue().flare);
            it.remove();
        }
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            Flare best = visibleFlare(player);
            Lit state = lit.computeIfAbsent(id, key -> new Lit());
            if (state.flare != null && !flares.contains(state.flare)) {
                // 燃え尽きた
                state.flare = null;
                state.level = 0;
            }
            if (state.flare != null && state.flare != best) {
                if (--state.level <= 0) {
                    hide(id, state.flare);
                    state.flare = null;
                    state.level = 0;
                }
            } else if (state.flare == null && best != null) {
                state.flare = best;
                player.showEntity(plugin, best.marker);
                state.level = 1;
            } else if (state.flare != null) {
                state.level = Math.min(LIGHT_LEVELS, state.level + 1);
            }
            if (state.flare == null) lit.remove(id);
        }
    }

    private void hide(UUID playerId, Flare flare) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && flare != null && flare.marker != null) player.hideEntity(plugin, flare.marker);
    }

    /** 目から照明弾までブロックが無い、燃えている照明弾のうち、いちばん近い物 */
    private Flare visibleFlare(Player player) {
        Location eye = player.getEyeLocation();
        Flare best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Flare flare : flares) {
            if (!flare.burning() || flare.marker == null || flare.world != eye.getWorld()) continue;
            Vector toFlare = flare.position.toVector().subtract(eye.toVector());
            double distance = toFlare.length();
            if (distance < 1e-3 || distance >= bestDistance) continue;
            if (eye.getWorld().rayTraceBlocks(eye, toFlare.multiply(1.0 / distance), distance, FluidCollisionMode.NEVER, true) != null) continue;
            best = flare;
            bestDistance = distance;
        }
        return best;
    }
}
