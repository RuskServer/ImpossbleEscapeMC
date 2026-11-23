package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 有効な脱出地点に重ねる煙。近くのレイド参加者がいる間だけ表示を維持する。 */
final class ExtractionSmoke {
    static final int INTERVAL_TICKS = 4;
    static final double RISE_HEIGHT = 8.0;
    private static final int PUFFS = 8;
    private static final int LIFE_TICKS = 128;
    private static final int SHAPES = 3;
    private static final int OPACITY_LEVELS = 5;
    private static final double VIEW_DISTANCE_SQ = 80 * 80;
    private static final double MIN_CLEARANCE = 0.60;
    private static final NamespacedKey MODEL = new NamespacedKey("iem", "extraction_smoke");

    private final JavaPlugin plugin;
    private final Map<RaidMap.ExtractionPoint, Plume> plumes = new HashMap<>();
    private final ItemStack[] sprites = new ItemStack[SHAPES * OPACITY_LEVELS];
    private int ticks;

    ExtractionSmoke(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void tick(World world, List<RaidMap.ExtractionPoint> active, Collection<Player> players) {
        if (world == null || players.isEmpty()) {
            clear();
            return;
        }
        ticks += INTERVAL_TICKS;
        plumes.entrySet().removeIf(entry -> {
            if (active.contains(entry.getKey())) return false;
            entry.getValue().remove();
            return true;
        });
        for (RaidMap.ExtractionPoint point : active) {
            Location origin = point.getLocation(world.getName());
            if (origin == null) continue;
            origin.add(0, 0.27, 0);
            List<Player> viewers = players.stream().filter(player -> world.equals(player.getWorld())
                    && player.getLocation().distanceSquared(origin) <= VIEW_DISTANCE_SQ).toList();
            if (viewers.isEmpty() || !isLoaded(origin)) {
                remove(point);
                continue;
            }
            Plume plume = plumes.get(point);
            if (plume == null) {
                if (!origin.getBlock().isPassable()) {
                    origin.add(0, 1, 0);
                    if (!origin.getBlock().isPassable()) continue;
                }
                plume = new Plume(origin, ticks);
                plumes.put(point, plume);
            }
            plume.tick(viewers, ticks);
        }
    }

    void clear() {
        plumes.values().forEach(Plume::remove);
        plumes.clear();
    }

    private void remove(RaidMap.ExtractionPoint point) {
        Plume previous = plumes.remove(point);
        if (previous != null) previous.remove();
    }

    private static boolean isLoaded(Location location) {
        return location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean loadedBetween(Location from, Location to) {
        World world = from.getWorld();
        int x0 = from.getBlockX() >> 4, z0 = from.getBlockZ() >> 4;
        int x1 = to.getBlockX() >> 4, z1 = to.getBlockZ() >> 4;
        return world.isChunkLoaded(x1, z1) && world.isChunkLoaded(x0, z1) && world.isChunkLoaded(x1, z0);
    }

    private ItemStack sprite(int index) {
        if (sprites[index] == null) {
            ItemStack item = new ItemStack(Material.PAPER);
            item.setData(DataComponentTypes.ITEM_MODEL, MODEL);
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addFloat(index + 1).build());
            sprites[index] = item;
        }
        return sprites[index];
    }

    /** 屋外は約8ブロックまで上がる。天井が近ければ煙の上端も含めて収める。 */
    static Sample sample(double life, double ceiling, int seed, double direction) {
        double spread = life * (1.9 + 0.21 * Math.sin(seed * 2.7));
        double sway = Math.sin(seed * 2.4 + life * 4.2) * (0.12 + 0.37 * life);
        // 粒の輪郭が大きな板に見えないよう、上に行っても広がりは控えめにする。
        float size = (float) Math.min(0.80 + life * 1.30 + Math.sin(seed) * 0.12, ceiling * 0.72);
        double rise = life * Math.min(RISE_HEIGHT, ceiling - size * 0.72);
        // 出始めを薄くし、頂上の手前からゆっくり消す。
        double opacity = Math.min(1.0, life / 0.16) * Math.min(1.0, (1.0 - life) / 0.42);
        int level = Math.clamp((int) (opacity * OPACITY_LEVELS), 0, OPACITY_LEVELS - 1);
        int sprite = Math.floorMod(seed, SHAPES) * OPACITY_LEVELS + level;
        return new Sample(Math.cos(direction) * spread - Math.sin(direction) * sway, rise,
                Math.sin(direction) * spread + Math.cos(direction) * sway, size,
                (float) (Math.sin(seed * 1.7) * 0.17 + life * 0.12), sprite);
    }

    record Sample(double x, double y, double z, float size, float angle, int sprite) { }

    private final class Plume {
        private final Location origin;
        private final int start;
        private final double direction;
        private final Puff[] puffs = new Puff[PUFFS];
        private final Map<UUID, Player> viewing = new HashMap<>();
        private double ceiling = Double.POSITIVE_INFINITY;

        Plume(Location origin, int now) {
            this.origin = origin;
            start = now;
            int cell = origin.getBlockX() * 31 + origin.getBlockZ() * 17;
            direction = 0.45 + Math.sin(cell * 0.17) * 0.65;
            for (int i = 0; i < PUFFS; i++) puffs[i] = new Puff();
        }

        void tick(List<Player> viewers, int now) {
            int age = now - start;
            if (age % 40 == 0) updateCeiling();
            syncViewers(viewers);
            for (int i = 0; i < PUFFS; i++) {
                Puff puff = puffs[i];
                int offset = age + i * LIFE_TICKS / PUFFS;
                int cycle = offset / LIFE_TICKS;
                double life = offset % LIFE_TICKS / (double) LIFE_TICKS;
                if (ceiling < MIN_CLEARANCE) {
                    puff.remove();
                    continue;
                }
                Sample sample = sample(life, ceiling, cycle * PUFFS + i, direction);
                Location position = position(sample);
                if (puff.cycle != cycle || puff.display == null || !puff.display.isValid()) {
                    puff.remove();
                    puff.display = spawn(position, sample);
                    puff.cycle = cycle;
                } else if (!puff.display.teleport(position)) {
                    puff.remove();
                } else {
                    animate(puff.display, sample, puff.sprite);
                }
                puff.sprite = sample.sprite();
            }
        }

        private void updateCeiling() {
            var hit = origin.getWorld().rayTraceBlocks(origin, new Vector(0, 1, 0),
                    RISE_HEIGHT + 2.5, FluidCollisionMode.NEVER, true);
            ceiling = hit == null ? Double.POSITIVE_INFINITY : hit.getHitPosition().getY() - origin.getY() - 0.10;
        }

        private Location position(Sample sample) {
            Location stem = origin.clone().add(0, sample.y(), 0);
            Location target = stem.clone().add(sample.x(), 0, sample.z());
            var wind = new Vector(sample.x(), 0, sample.z());
            double drift = wind.length();
            // 斜めのチャンク境界でも風の判定で未ロードのチャンクを読み込まない。
            if (drift < 0.001 || !loadedBetween(stem, target)) return stem;
            wind.normalize();
            // 中心だけでなく板の幅も壁から離す。
            double radius = sample.size() * 0.5;
            Location forwardEdge = target.clone().add(wind.clone().multiply(radius));
            double reach = loadedBetween(stem, forwardEdge) ? drift + radius : drift;
            var hit = origin.getWorld().rayTraceBlocks(stem, wind, reach, FluidCollisionMode.NEVER, true);
            if (hit == null) return target;
            double travel = Math.max(0, hit.getHitPosition().distance(stem.toVector()) - radius);
            return stem.add(wind.multiply(travel));
        }

        private void syncViewers(List<Player> viewers) {
            Set<UUID> keep = new HashSet<>();
            for (Player player : viewers) {
                UUID id = player.getUniqueId();
                keep.add(id);
                // 同じUUIDでも再ログインしたら新しい接続へ表示し直す。
                if (viewing.put(id, player) == player) continue;
                for (Puff puff : puffs) {
                    if (puff.display != null && puff.display.isValid()) player.showEntity(plugin, puff.display);
                }
            }
            viewing.entrySet().removeIf(entry -> {
                if (keep.contains(entry.getKey())) return false;
                Player player = entry.getValue();
                if (player.isOnline()) {
                    for (Puff puff : puffs) {
                        if (puff.display != null && puff.display.isValid()) player.hideEntity(plugin, puff.display);
                    }
                }
                return true;
            });
        }

        private ItemDisplay spawn(Location position, Sample sample) {
            var display = position.getWorld().spawn(position, ItemDisplay.class, entity -> {
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setVisibleByDefault(false);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setViewRange(2.0f);
                entity.setShadowRadius(0);
                entity.setTeleportDuration(INTERVAL_TICKS);
                entity.setInterpolationDuration(INTERVAL_TICKS);
                animate(entity, sample, -1);
            });
            for (Player player : viewing.values()) player.showEntity(plugin, display);
            return display;
        }

        void remove() {
            for (Puff puff : puffs) puff.remove();
            viewing.clear();
        }
    }

    private void animate(ItemDisplay display, Sample sample, int previousSprite) {
        display.setInterpolationDelay(0);
        float size = sample.size();
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ(sample.angle()),
                new Vector3f(size, size, size), new Quaternionf()));
        if (previousSprite != sample.sprite()) display.setItemStack(sprite(sample.sprite()));
    }

    private static final class Puff {
        private ItemDisplay display;
        private int cycle;
        private int sprite;

        void remove() {
            if (display == null) return;
            display.remove();
            display = null;
        }
    }
}
