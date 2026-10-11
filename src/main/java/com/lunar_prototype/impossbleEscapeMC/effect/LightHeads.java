package com.lunar_prototype.impossbleEscapeMC.effect;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 照明に使うプレイヤーヘッド (決まったスキンのもの) を、レンズフレアの光源にする。
 * <p>
 * 置いたプレイヤーヘッドは半透明の描き方で描かれ、画面の不透明度に光源の印を残せないため、ヘッドの前に
 * カメラを向く小さな「光の芯」(item_display、リソースパックの iem:light_core) を出す。芯はヘッドの中心から
 * 見ている人の方へ {@link #FORWARD} だけ前に出すので、どの角度から見てもヘッドに隠れない。
 * 芯のテクスチャには光源ブロックと同じ印があり、アイテムのシェーダー (item.fsh) が画面の不透明度に印を書く
 * (IEM_shader/tools/light_heads.py)。色はヘッドごとに {@link #HEADS} で決める。
 * <p>
 * 芯は保存しない (persistent=false)。チャンクが読み込まれた時にヘッドを探して出し、外れる時に消す。
 * 壊された・動かされたヘッドの芯は {@link #CHECK_INTERVAL_TICKS} ごとの見直しで消す
 */
public final class LightHeads implements Listener {

    /** スキンのテクスチャ (textures.minecraft.net/texture/ の後ろ) → 光の色 */
    private static final Map<String, Color> HEADS = Map.of(
            // Orange Emergency Light (wall) (minecraft-heads.com 78724)
            "7e44937f03fd9f820475c3f6aaf96d1ee5ec0c9e43fbc9c4313adc7ae0d8c1f5", Color.fromRGB(0xFF9A2E));

    private static final Key CORE_MODEL = Key.key("iem", "light_core");
    private static final String TAG = "iem_light_head";
    /** 芯の大きさ (ブロック)。ヘッド (0.5、斜めから見ると幅 0.7 ほど) を覆う光にする */
    private static final float SIZE = 0.6f;
    /** 芯をヘッドの中心から見ている人の方へ出す距離。ヘッド (8px の立方体) の中心から角までの 0.433 より少し遠く */
    private static final float FORWARD = 0.45f;
    private static final int CHECK_INTERVAL_TICKS = 40;
    private static final Pattern SKIN_URL = Pattern.compile("\"url\"\\s*:\\s*\"[^\"]*/([0-9a-fA-F]+)\"");

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static LightHeads instance;

    private final ImpossbleEscapeMC plugin;
    private final Map<BlockKey, ItemDisplay> cores = new HashMap<>();
    private final BukkitTask checkTask;

    private LightHeads(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new LightHeads(plugin);
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        // 起動時に読み込まれているチャンク (ワールドの読み込みが終わってから)
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) instance.scan(chunk);
            }
        });
    }

    /** 芯をすべて消す */
    public static void shutdown() {
        if (instance == null) return;
        instance.checkTask.cancel();
        instance.cores.values().forEach(Entity::remove);
        instance.cores.clear();
        instance = null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        // 読み込みの最中にエンティティを足さない
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (chunk.isLoaded()) scan(chunk);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        UUID world = chunk.getWorld().getUID();
        cores.entrySet().removeIf(entry -> {
            BlockKey key = entry.getKey();
            if (!key.world().equals(world) || key.x() >> 4 != chunk.getX() || key.z() >> 4 != chunk.getZ()) return false;
            entry.getValue().remove();
            return true;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;
        // ヘッドのスキンは置いた後に付くので、次の tick に見る
        Bukkit.getScheduler().runTask(plugin, () -> update(block));
    }

    /** チャンクの中の光源ヘッドに芯を出す (前に残った芯は消してから) */
    private void scan(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity.getScoreboardTags().contains(TAG) && !cores.containsValue(entity)) entity.remove();
        }
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Skull skull) update(skull.getBlock(), skull);
        }
    }

    /** 壊された・動かされたヘッドの芯を消し、消えた芯を出し直す */
    private void check() {
        for (Map.Entry<BlockKey, ItemDisplay> entry : new ArrayList<>(cores.entrySet())) {
            BlockKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                entry.getValue().remove();
                cores.remove(key);
                continue;
            }
            update(world.getBlockAt(key.x(), key.y(), key.z()));
        }
    }

    private void update(Block block) {
        update(block, block.getState(false) instanceof Skull skull ? skull : null);
    }

    /** そのブロックが光源ヘッドなら芯を出し (出ていれば何もしない)、違えば芯を消す */
    private void update(Block block, Skull skull) {
        BlockKey key = BlockKey.of(block);
        Color color = skull != null ? HEADS.get(textureOf(skull)) : null;
        ItemDisplay existing = cores.get(key);
        if (color == null) {
            if (existing != null) {
                existing.remove();
                cores.remove(key);
            }
            return;
        }
        if (existing != null && existing.isValid()) return;
        cores.put(key, spawn(block, color));
    }

    private ItemDisplay spawn(Block block, Color color) {
        Location center = block.getLocation().add(0.5, 0.25, 0.5);
        if (block.getType() == Material.PLAYER_WALL_HEAD && block.getBlockData() instanceof Directional wall) {
            // 壁のヘッドは壁に寄せて浮いている: 中心は壁の方へ 4px、高さはブロックの中央
            BlockFace facing = wall.getFacing();
            center = block.getLocation().add(0.5 - facing.getModX() * 0.25, 0.5, 0.5 - facing.getModZ() * 0.25);
        }
        ItemStack item = new ItemStack(Material.PAPER);
        item.setData(DataComponentTypes.ITEM_MODEL, CORE_MODEL);
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addColor(color).build());
        return block.getWorld().spawn(center, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.addScoreboardTag(TAG);
            // カメラを向く板。向けた後の +z が見ている人の方なので、そちらへ出す
            display.setBillboard(Display.Billboard.CENTER);
            display.setTransformation(new Transformation(new Vector3f(0, 0, FORWARD), new Quaternionf(),
                    new Vector3f(SIZE, SIZE, SIZE), new Quaternionf()));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setShadowRadius(0);
        });
    }

    /** ヘッドのスキンのテクスチャ (URL の最後)。無ければnull */
    static String textureOf(Skull skull) {
        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) return null;
        for (ProfileProperty property : profile.getProperties()) {
            if (!"textures".equals(property.getName())) continue;
            try {
                String json = new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8);
                Matcher matcher = SKIN_URL.matcher(json);
                return matcher.find() ? matcher.group(1).toLowerCase(java.util.Locale.ROOT) : null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
