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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.IOException;

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

        Bukkit.getLogger().info("HideoutModule enabled.");
    }

    public void onDisable() {
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
