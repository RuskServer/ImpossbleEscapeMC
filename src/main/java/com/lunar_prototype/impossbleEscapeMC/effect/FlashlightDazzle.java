package com.lunar_prototype.impossbleEscapeMC.effect;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.Flashlight;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 点けたフラッシュライトを向けられた時のまぶしさ。{@link #INTERVAL_TICKS} ごとに、点けたフラッシュライトを持っている人 (照らす人) から
 * 周りのプレイヤーへの向きを調べる。
 * <ul>
 *   <li>光の芯: 照らす人の手元に、カメラを向く小さな光の芯 (item_display、リソースパックの iem:light_core。LightHeads と同じ物) を出し、
 *       光の中にいる人にだけ見せる。芯はレンズフレアの光源になる (照らす人の体に隠れないよう、見ている人の方へ {@link #FORWARD} 出す)</li>
 *   <li>画面: 光の中にいて光の方を向いている人に、強さに合わせて iem:dazzle_1〜3 (ScreenEffectService のまぶしさの層) をかける。
 *       光から外れると 1 段ずつ戻す</li>
 *   <li>強さ: 光の芯から外れるほど ({@link #CORE_DEG}〜{@link #EDGE_DEG})・遠いほど ({@link #HALF_DISTANCE} で半分) 弱く、
 *       周りが明るい (昼・照明の下) ほど弱く、光が視線から外れるほど弱い。間にブロックがあれば届かない</li>
 * </ul>
 */
public final class FlashlightDazzle {

    static final int INTERVAL_TICKS = 2;
    /** これより遠くは調べない (m) */
    static final double RANGE = 25.0;
    /** 光の芯の半角と、光の縁 (度)。flashlight.fsh の芯 (Cone) より少し狭く */
    static final double CORE_DEG = 5.0, EDGE_DEG = 18.0;
    /** 強さが半分になる距離 (m) */
    static final double HALF_DISTANCE = 8.0;
    /** 周りの明るさ 15 の時に残る強さの割合 */
    static final double DAYLIGHT_KEEP = 0.25;
    /** 光が視線のこの角度 (度) の内なら全部、外の角度で 0 */
    static final double FACING_FULL_DEG = 20.0, FACING_NONE_DEG = 70.0;
    /** 芯を見せる強さと、画面の段 (1〜3) に入る強さ */
    static final double CORE_MIN = 0.08;
    static final double[] LEVELS = {0.1, 0.3, 0.6};

    private static final Key CORE_MODEL = Key.key("iem", "light_core");
    private static final Color CORE_COLOR = Color.fromRGB(0xFFF4E0);
    private static final String TAG = "iem_flashlight_core";
    private static final float SIZE = 0.35f;
    private static final float FORWARD = 0.3f;

    private static FlashlightDazzle instance;

    private final ImpossbleEscapeMC plugin;
    private final BukkitTask task;
    /** 照らす人 → 手元の光の芯と、芯を見せている人 */
    private final Map<UUID, ItemDisplay> cores = new HashMap<>();
    private final Map<UUID, Set<UUID>> shownTo = new HashMap<>();
    /** 画面の段をかけている人 → 段 */
    private final Map<UUID, Integer> levels = new HashMap<>();

    private FlashlightDazzle(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new FlashlightDazzle(plugin);
    }

    public static void shutdown() {
        if (instance == null) return;
        instance.task.cancel();
        instance.cores.values().forEach(ItemDisplay::remove);
        instance.cores.clear();
        instance.shownTo.clear();
        instance.levels.clear();
        instance = null;
    }

    private void tick() {
        Map<UUID, Double> power = new HashMap<>();
        Set<UUID> holders = new HashSet<>();
        for (Player holder : Bukkit.getOnlinePlayers()) {
            if (!Flashlight.isLit(holder)) continue;
            holders.add(holder.getUniqueId());
            Location origin = handOf(holder);
            Vector dir = holder.getEyeLocation().getDirection();
            Set<UUID> lit = new HashSet<>();
            for (Player viewer : holder.getWorld().getPlayers()) {
                if (viewer == holder || viewer.getGameMode() == GameMode.SPECTATOR || viewer.getGameMode() == GameMode.CREATIVE
                        || viewer.isDead()) continue;
                Location eye = viewer.getEyeLocation();
                Vector to = eye.toVector().subtract(origin.toVector());
                double d = to.length();
                if (d < 1e-3 || d > RANGE) continue;
                to.multiply(1.0 / d);
                double raw = strength(Math.toDegrees(dir.angle(to)), d, eye.getBlock().getLightLevel());
                if (raw < CORE_MIN) continue;
                if (holder.getWorld().rayTraceBlocks(origin, to, d, FluidCollisionMode.NEVER, true) != null) continue;
                lit.add(viewer.getUniqueId());
                double facing = facing(Math.toDegrees(eye.getDirection().angle(to.clone().multiply(-1))));
                power.merge(viewer.getUniqueId(), raw * facing, Math::max);
            }
            updateCore(holder, origin, lit);
        }
        // 消した・持ち替えた・抜けた人の芯を消す
        for (UUID id : new HashSet<>(cores.keySet())) {
            if (!holders.contains(id)) removeCore(id);
        }
        updateLevels(power);
    }

    /** 光の向きからの角度 (度)・距離 (m)・目の所の明るさ (0〜15) での強さ (視線の向きは除く) */
    static double strength(double angle, double distance, int light) {
        double cone = 1.0 - smoothstep(CORE_DEG, EDGE_DEG, angle);
        double fall = 1.0 / (1.0 + (distance / HALF_DISTANCE) * (distance / HALF_DISTANCE));
        double ambient = 1.0 - (1.0 - DAYLIGHT_KEEP) * Math.max(0, Math.min(15, light)) / 15.0;
        return cone * fall * ambient;
    }

    /** 光が視線から angle 度の所にある時の割合 */
    static double facing(double angle) {
        return 1.0 - smoothstep(FACING_FULL_DEG, FACING_NONE_DEG, angle);
    }

    /** 強さの画面の段 (0 は無し) */
    static int levelOf(double power) {
        int level = 0;
        for (int i = 0; i < LEVELS.length; i++) {
            if (power >= LEVELS[i]) level = i + 1;
        }
        return level;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** 光の出る所: 目の少し右下・前 (手に持った位置。flashlight.fsh の Origin と同じくらい) */
    private static Location handOf(Player holder) {
        Location eye = holder.getEyeLocation();
        Vector forward = eye.getDirection();
        Vector right = new Vector(-Math.sin(Math.toRadians(eye.getYaw() + 90)), 0, Math.cos(Math.toRadians(eye.getYaw() + 90)));
        Vector up = right.getCrossProduct(forward).normalize();
        return eye.add(right.multiply(0.3)).add(up.multiply(-0.25)).add(forward.multiply(0.35));
    }

    private void updateCore(Player holder, Location at, Set<UUID> lit) {
        UUID id = holder.getUniqueId();
        ItemDisplay core = cores.get(id);
        if (lit.isEmpty()) {
            if (core != null) removeCore(id);
            return;
        }
        if (core == null || !core.isValid() || !core.getWorld().equals(at.getWorld())) {
            if (core != null) removeCore(id);
            core = spawnCore(at);
            cores.put(id, core);
        } else {
            core.teleport(at);
        }
        Set<UUID> shown = shownTo.computeIfAbsent(id, k -> new HashSet<>());
        for (UUID viewerId : new HashSet<>(shown)) {
            if (lit.contains(viewerId)) continue;
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) viewer.hideEntity(plugin, core);
            shown.remove(viewerId);
        }
        for (UUID viewerId : lit) {
            if (!shown.add(viewerId)) continue;
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer != null) viewer.showEntity(plugin, core);
        }
    }

    private static ItemDisplay spawnCore(Location at) {
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, CORE_MODEL);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(CORE_COLOR).build());
        return at.getWorld().spawn(at, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            // 光の中にいる人にだけ見せる (照らす人本人には見せない)
            display.setVisibleByDefault(false);
            // カメラを向く板。向けた後の +z が見ている人の方なので、そちらへ出す
            display.setBillboard(Display.Billboard.CENTER);
            display.setTransformation(new Transformation(new Vector3f(0, 0, FORWARD), new Quaternionf(),
                    new Vector3f(SIZE, SIZE, SIZE), new Quaternionf()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setShadowRadius(0);
            display.setTeleportDuration(INTERVAL_TICKS);
        });
    }

    private void removeCore(UUID holder) {
        ItemDisplay core = cores.remove(holder);
        if (core != null) core.remove();
        shownTo.remove(holder);
    }

    /** 画面の段: 強くなる時はすぐ、弱くなる時は 1 段ずつ */
    private void updateLevels(Map<UUID, Double> power) {
        Set<UUID> ids = new HashSet<>(levels.keySet());
        ids.addAll(power.keySet());
        for (UUID id : ids) {
            int previous = levels.getOrDefault(id, 0);
            int level = Math.max(levelOf(power.getOrDefault(id, 0.0)), previous - 1);
            if (level == previous) continue;
            if (level == 0) levels.remove(id);
            else levels.put(id, level);
            Player player = Bukkit.getPlayer(id);
            if (player != null) ScreenEffectService.setDazzle(player, level);
        }
    }
}
