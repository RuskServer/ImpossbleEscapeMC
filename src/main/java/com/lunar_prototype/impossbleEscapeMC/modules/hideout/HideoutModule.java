package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Set;

/**
 * 隠れ家システムのメインモジュール。
 * 建築はベースと、設備のレベルごとの差分 ({@link HideoutLayout})。プレイヤーの隠れ家は、建てた時の建築データの版が今と違えば
 * 次に入った時に建て直す ({@link #ensureBuilt})
 */
public class HideoutModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final HideoutWorldManager worldManager;
    private final HideoutLayout layout;
    private final HideoutBuilder builder;
    private PlayerDataModule dataModule;

    public HideoutModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.worldManager = new HideoutWorldManager();
        this.layout = new HideoutLayout(plugin.getDataFolder());
        this.builder = new HideoutBuilder(layout, plugin.getLogger());
    }

    public void onEnable(ServiceContainer serviceContainer) {
        this.dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        
        // ワールドの初期化
        worldManager.initWorlds();

        // リスナーの登録
        Bukkit.getPluginManager().registerEvents(new HideoutListener(this), plugin);
        HideoutDialog.init(this);

        Bukkit.getLogger().info("HideoutModule enabled.");
    }

    public void onDisable() {
        // 画面を開いている人のカメラをやめて、元の位置とゲームモードに戻す
        HideoutDialog.shutdown();
        Bukkit.getLogger().info("HideoutModule disabled.");
    }

    /** プレイヤーの隠れ家の原点 (ベースの最小の角が来る所) */
    public BlockVector3 origin(int index) {
        return BlockVector3.at(index * 1000, 64, 0);
    }

    /** プレイヤーの隠れ家で立つ位置 */
    public Location spawn(int index) {
        HideoutLayout.Base base = layout.base();
        World world = worldManager.getPlayerWorld();
        if (world == null) return null;
        BlockVector3 origin = origin(index);
        if (base == null) return new Location(world, origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        return new Location(world, origin.getX() + base.spawnX, origin.getY() + base.spawnY, origin.getZ() + base.spawnZ,
                base.spawnYaw, base.spawnPitch);
    }

    /** 建てた時の建築データの版が今と違えば (まだ建てていなければ) 、今のレベルで建て直す */
    public void ensureBuilt(PlayerData data) throws IOException {
        if (data.getHideoutIndex() < 0) return;
        int version = layout.version();
        if (data.getHideoutBuiltVersion() == version) return;
        builder.build(worldManager.getPlayerWorld(), origin(data.getHideoutIndex()), facility -> data.getHideoutLevel(facility.id));
        data.setHideoutBuiltVersion(version);
    }

    /** 設備と建物のレベルをすべて 0 に戻し、ベースだけの状態に建て直す (運営用) */
    public void reset(Player player, PlayerData data) throws IOException {
        data.getHideoutLevels().clear();
        data.setHideoutBuiltVersion(-1);
        dataModule.saveAsync(player.getUniqueId());
        ensureBuilt(data);
        dataModule.saveAsync(player.getUniqueId());
    }

    /**
     * 今いる所の原点 (ベースの最小の角が来る所)。エディターのワールドならいちばん近い区画、隠れ家のワールドなら自分の隠れ家。
     * どちらでもなければ null
     */
    public BlockVector3 originAt(Player player, PlayerData data) {
        String world = player.getWorld().getName();
        Location at = player.getLocation();
        if (world.equals(HideoutWorldManager.EDITOR_WORLD_NAME)) {
            int facility = (int) Math.round(at.getX() / HideoutBuilder.EDITOR_STRIDE);
            int level = (int) Math.round(at.getZ() / HideoutBuilder.EDITOR_STRIDE);
            if (facility < 0 || facility >= HideoutFacility.values().length || level < 0) return null;
            return HideoutBuilder.editorOrigin(HideoutFacility.values()[facility], level);
        }
        if (world.equals(HideoutWorldManager.PLAYER_WORLD_NAME) && data != null && data.getHideoutIndex() >= 0) {
            return origin(data.getHideoutIndex());
        }
        return null;
    }

    /** カメラが設備を画面の右寄りに映すよう、設備の方向から左へずらす角度 (度) */
    private static final float CAMERA_YAW_OFFSET = -15f;

    /**
     * 隠れ家の画面のカメラの位置と向き。設備 (未設置なら次のレベル) の差分のブロックの真ん中を、隠れ家に入った時の位置の方から
     * 少し見下ろす。間に壁があればその手前まで寄る。設備のブロックが無い時 (建物など) は、入った時の位置から建物の真ん中を見る
     */
    public Location cameraPose(PlayerData data, HideoutFacility facility) {
        HideoutLayout.Base base = layout.base();
        World world = worldManager.getPlayerWorld();
        if (base == null || world == null || data.getHideoutIndex() < 0) return null;
        BlockVector3 origin = origin(data.getHideoutIndex());
        // /hideout camera で記録した位置があればそれ
        HideoutLayout.CameraPose recorded = layout.camera(facility);
        if (recorded != null) {
            return new Location(world, origin.getX() + recorded.x, origin.getY() + recorded.y, origin.getZ() + recorded.z,
                    recorded.yaw, recorded.pitch);
        }
        Vector spawn = new Vector(origin.getX() + base.spawnX, origin.getY() + base.spawnY + 1.6, origin.getZ() + base.spawnZ);
        int level = data.getHideoutLevel(facility.id);
        Set<BlockVector3> blocks = facility == HideoutFacility.BASE ? Set.of()
                : builder.placedBlocks(facility, Math.max(level, Math.min(level + 1, layout.highestLevel(facility))));
        Vector target;
        double extent;
        if (blocks.isEmpty()) {
            target = new Vector(origin.getX() + base.sizeX / 2.0, origin.getY() + base.sizeY / 3.0, origin.getZ() + base.sizeZ / 2.0);
            return look(new Location(world, spawn.getX(), spawn.getY(), spawn.getZ()), target);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockVector3 b : blocks) {
            minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY()); maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ()); maxZ = Math.max(maxZ, b.getZ());
        }
        target = new Vector(origin.getX() + (minX + maxX + 1) / 2.0, origin.getY() + (minY + maxY + 1) / 2.0,
                origin.getZ() + (minZ + maxZ + 1) / 2.0);
        extent = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 1;

        // 入った時の位置の方 (横向き) から、設備の大きさに合わせた距離で、少し上から
        Vector away = spawn.clone().subtract(target).setY(0);
        if (away.lengthSquared() < 1e-6) away = new Vector(0, 0, 1);
        away.normalize();
        double distance = Math.max(3.5, Math.min(12.0, extent * 0.9 + 3.0));
        Vector toCamera = away.multiply(distance).add(new Vector(0, 1.5 + extent * 0.2, 0));
        // 間に壁があれば手前まで (設備のブロック自身に当たらないよう、設備の外から調べる)
        Vector direction = toCamera.clone().normalize();
        double skip = extent * 0.5 + 0.5;
        Location from = target.clone().add(direction.clone().multiply(skip)).toLocation(world);
        double length = Math.max(0.1, toCamera.length() - skip);
        RayTraceResult hit = world.rayTraceBlocks(from, direction, length, FluidCollisionMode.NEVER, true);
        Vector camera = hit != null
                ? hit.getHitPosition().subtract(direction.clone().multiply(0.6))
                : target.clone().add(toCamera);
        return look(camera.toLocation(world), target);
    }

    private static Location look(Location from, Vector target) {
        from.setDirection(target.clone().subtract(from.toVector()));
        from.setYaw(from.getYaw() + CAMERA_YAW_OFFSET);
        return from;
    }

    /** 自分の隠れ家のブロックなら、それを置いた設備 (ベースのブロック・ほかの場所なら null) */
    public HideoutFacility facilityAt(Player player, Block block) {
        if (!block.getWorld().getName().equals(HideoutWorldManager.PLAYER_WORLD_NAME)) return null;
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data == null || data.getHideoutIndex() < 0) return null;
        BlockVector3 origin = origin(data.getHideoutIndex());
        return builder.ownerAt(facility -> data.getHideoutLevel(facility.id),
                block.getX() - origin.getX(), block.getY() - origin.getY(), block.getZ() - origin.getZ());
    }

    /**
     * 設備を 1 レベル上げて、その差分を重ねる (建築データが変わっていれば建て直す)。
     * 建築の反映に失敗しても上がったレベルは残し、次に入った時に建て直す
     */
    public void upgrade(Player player, PlayerData data, HideoutFacility facility) throws IOException {
        int next = data.getHideoutLevel(facility.id) + 1;
        data.setHideoutLevel(facility.id, next);
        dataModule.saveAsync(player.getUniqueId());
        if (data.getHideoutIndex() < 0) return;
        try {
            if (data.getHideoutBuiltVersion() == layout.version()) {
                builder.applyLayer(worldManager.getPlayerWorld(), origin(data.getHideoutIndex()), facility, next);
            } else {
                ensureBuilt(data);
            }
        } catch (IOException e) {
            data.setHideoutBuiltVersion(-1);
            throw e;
        } finally {
            dataModule.saveAsync(player.getUniqueId());
        }
    }

    public ImpossbleEscapeMC getPlugin() {
        return plugin;
    }

    public HideoutWorldManager getWorldManager() {
        return worldManager;
    }

    public HideoutLayout getLayout() {
        return layout;
    }

    public HideoutBuilder getBuilder() {
        return builder;
    }

    public PlayerDataModule getDataModule() {
        return dataModule;
    }
}
