package com.lunar_prototype.impossbleEscapeMC.modules.hideout;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.party.Party;
import com.lunar_prototype.impossbleEscapeMC.modules.party.PartyModule;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import com.lunar_prototype.impossbleEscapeMC.util.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.logging.Level;

/**
 * 隠れ家システムのメインモジュール。
 * 建築はベースと、設備のレベルごとの差分 ({@link HideoutLayout})。プレイヤーの隠れ家は、建てた時の建築データの版が今と違えば
 * 次に入った時に建て直す ({@link #ensureBuilt})。
 * パーティーに所属している人は、入る時はリーダーの隠れ家に集まる ({@link #visit})。設備の利用のレベルの判定は
 * クラフトやスキルの各画面がプレイヤー自身のデータで行うので、どの隠れ家にいても自分のレベルのまま
 */
public class HideoutModule implements IModule {
    private final ImpossbleEscapeMC plugin;
    private final HideoutWorldManager worldManager;
    private final HideoutLayout layout;
    private final HideoutBuilder builder;
    private final HideoutSlots slots;
    private PlayerDataModule dataModule;

    public HideoutModule(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.worldManager = new HideoutWorldManager();
        this.layout = new HideoutLayout(plugin.getDataFolder());
        this.builder = new HideoutBuilder(layout, plugin.getLogger());
        this.slots = new HideoutSlots(new File(plugin.getDataFolder(), "hideout"), plugin.getLogger());
    }

    public void onEnable(ServiceContainer serviceContainer) {
        this.dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
        serviceContainer.get(com.lunar_prototype.impossbleEscapeMC.core.command.CommandRegistry.class).register(new HideoutCommand(this));
        
        // ワールドの初期化
        worldManager.initWorlds();

        // リスナーの登録
        HideoutListener listener = new HideoutListener(this);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        // 隠れ家にいる間はアドベンチャー (入った時だけでなく、ほかの所で戻されても)
        Bukkit.getScheduler().runTaskTimer(plugin, listener::enforceAll, 20L, 10L);
        HideoutDialog.init(this);

        Bukkit.getLogger().info("HideoutModule enabled.");
    }

    public void onDisable() {
        // 画面を開いている人のカメラをやめて、元の位置とゲームモードに戻す
        HideoutDialog.shutdown();
        Bukkit.getLogger().info("HideoutModule disabled.");
    }

    /** 隠れ家の区画の間隔 (x 方向のブロック数)。どの区画かは {@link #slotAt} で座標から求める */
    static final int SLOT_STRIDE = 1000;

    /** プレイヤーの隠れ家の原点 (ベースの最小の角が来る所) */
    public BlockVector3 origin(int index) {
        return BlockVector3.at(index * SLOT_STRIDE, 64, 0);
    }

    /** 座標 x が属する隠れ家の区画の番号 (原点の x を区画の間隔で割った物。負の座標も左の区画になる) */
    static int slotAt(int x) {
        return Math.floorDiv(x, SLOT_STRIDE);
    }

    /** プレイヤーの隠れ家で立つ位置 */
    public Location spawn(int index) {
        HideoutLayout.Base base = layout.base();
        World world = worldManager.getPlayerWorld();
        if (world == null) return null;
        BlockVector3 origin = origin(index);
        if (base == null) return new Location(world, origin.x() + 0.5, origin.y(), origin.z() + 0.5);
        return new Location(world, origin.x() + base.spawnX, origin.y() + base.spawnY, origin.z() + base.spawnZ,
                base.spawnYaw, base.spawnPitch);
    }

    /** 隠れ家へ移す (初めてなら場所を割り当てて建てる)。パーティーのメンバーはリーダーの隠れ家に集まる。移せなかった時は、その理由をプレイヤーに伝えて false */
    public boolean visit(Player player) {
        if (layout.base() == null) {
            player.sendMessage(Component.text("隠れ家はまだ準備中です。", NamedTextColor.RED));
            return false;
        }
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        assignSlot(player.getUniqueId(), data, true);
        // パーティーに所属していてリーダーでなければ、リーダーの隠れ家に建ててもらって集まる (設備のレベルの判定は各自のデータのまま)
        PlayerData host = hostData(player.getUniqueId());
        PlayerData target = host != null ? host : data;
        try {
            ensureBuilt(target);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "隠れ家を建てられませんでした", e);
            player.sendMessage(Component.text("隠れ家を建てられませんでした: " + e.getMessage(), NamedTextColor.RED));
            return false;
        }
        dataModule.saveAsync(target.getUuid());
        Location spawn = spawn(target.getHideoutIndex());
        if (spawn == null) return false;
        player.teleport(spawn);
        player.sendMessage(Component.text(host != null ? "パーティーリーダーの隠れ家に移動しました。" : "自分の隠れ家に移動しました。",
                NamedTextColor.GREEN));
        return true;
    }

    /** 隠れ家に入った時に集まる先の持ち主のデータ。パーティーに所属していない人と、リーダー自身は null (自分の隠れ家) */
    private PlayerData hostData(UUID visitor) {
        PartyModule parties = PartyModule.get();
        UUID host = parties != null ? visitHost(parties.getParty(visitor), visitor) : null;
        if (host == null) return null;
        PlayerData data = dataModule.getPlayerData(host);
        // リーダーがまだ隠れ家に入ったことがなくても、区画を割り当ててから案内する (建てるのは visit の ensureBuilt)
        assignSlot(host, data, true);
        return data;
    }

    /** 隠れ家に入った時の案内先の持ち主。パーティーに所属していてリーダーでなければリーダー、それ以外は null */
    static UUID visitHost(Party party, UUID self) {
        return party != null && party.isMember(self) && !party.isLeader(self) ? party.getLeader() : null;
    }

    /**
     * 隠れ家の場所の番号を台帳 ({@link HideoutSlots}) で確かめる。台帳に初めて載った時 (番号を移した時も) は建て直させる
     * (前に別の人と同じ所を使っていて、その人のレベルで建っているかもしれないため)。
     * create なら、まだ番号の無い人にも番号を配る (隠れ家に入る時)。参加した時は、番号を持っている人だけ確かめる
     */
    public void assignSlot(UUID playerId, PlayerData data, boolean create) {
        if (data == null || (!create && data.getHideoutIndex() < 0)) return;
        HideoutSlots.Claim claim = slots.claim(playerId, data.getHideoutIndex());
        if (!claim.registered() && claim.slot() == data.getHideoutIndex()) return;
        data.setHideoutIndex(claim.slot());
        data.setHideoutBuiltVersion(-1);
        dataModule.saveAsync(playerId);
    }

    /** ロビー (メインのワールドのスポーン。レイドから戻る所と同じ) へ戻す */
    public void returnToLobby(Player player) {
        player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        player.sendMessage(Component.text("ロビーに戻りました。", NamedTextColor.GREEN));
    }

    /**
     * 集水設備の効果: ロビーか自分の隠れ家にいる間の、空腹の減りの倍率 (Lv ごとに {@link HideoutFacility#WATER_FOOD_SAVING_PER_LEVEL} 減る)。
     * レイド中・ほかの所では 1
     */
    public double foodDrainMultiplier(Player player) {
        boolean lobby = player.getWorld().equals(Bukkit.getWorlds().get(0))
                && (plugin.getRaidModule() == null || !plugin.getRaidModule().isInRaid(player));
        if (!lobby && !worldManager.isPlayerWorld(player.getWorld())) return 1.0;
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        int level = data != null ? data.getHideoutLevel(HideoutFacility.WATER.id) : 0;
        return Math.max(0.0, 1.0 - HideoutFacility.WATER_FOOD_SAVING_PER_LEVEL * level);
    }

    /** 建てた時の建築データの版が今と違えば (まだ建てていなければ) 、今のレベルで建て直す */
    public void ensureBuilt(PlayerData data) throws IOException {
        if (data.getHideoutIndex() < 0) return;
        int version = layout.version();
        if (data.getHideoutBuiltVersion() == version) return;
        builder.build(worldManager.getPlayerWorld(), origin(data.getHideoutIndex()), facility -> data.getHideoutLevel(facility.id));
        data.setHideoutBuiltVersion(version);
    }

    /** アップグレード前の共通検証。画面と実際の更新処理の両方で使う。 */
    public String upgradeBlockReason(PlayerData data, HideoutFacility facility) {
        if (data == null || facility == null) return "プレイヤーデータを読み込めません";
        if (data.getHideoutIndex() < 0) return "先に隠れ家を作成してください";
        if (layout.base() == null || !layout.baseSchematic().isFile()) return "ベースの建築が未作成です";
        int level = data.getHideoutLevel(facility.id);
        int next = level + 1;
        if (level >= facility.maxLevel(layout)) return level == 0 ? "まだ建てられません" : "最大レベルです";
        if (!layout.hasLayer(facility, next)) return "Lv." + next + " の建築が未作成です";
        for (HideoutFacility.Prerequisite prerequisite : facility.prerequisites(next)) {
            if (data.getHideoutLevel(prerequisite.facility().id) < prerequisite.level()) {
                return prerequisite.facility().label + " Lv." + prerequisite.level() + " が必要です";
            }
        }
        return null;
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
     * 今いる所の原点 (ベースの最小の角が来る所)。エディターのワールドならいちばん近い区画、隠れ家のワールドなら立っている区画
     * (パーティーでほかの人の隠れ家にいる時もその区画)。どちらでもなければ null
     */
    public BlockVector3 originAt(Player player) {
        Location at = player.getLocation();
        if (worldManager.isEditorWorld(player.getWorld())) {
            int facility = (int) Math.round(at.getX() / HideoutBuilder.EDITOR_STRIDE);
            int level = (int) Math.round(at.getZ() / HideoutBuilder.EDITOR_STRIDE);
            if (facility < 0 || facility >= HideoutFacility.values().length || level < 0) return null;
            return HideoutBuilder.editorOrigin(HideoutFacility.values()[facility], level);
        }
        if (worldManager.isPlayerWorld(player.getWorld())) {
            return origin(slotAt(at.getBlockX()));
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
            return new Location(world, origin.x() + recorded.x, origin.y() + recorded.y, origin.z() + recorded.z,
                    recorded.yaw, recorded.pitch);
        }
        Vector spawn = new Vector(origin.x() + base.spawnX, origin.y() + base.spawnY + 1.6, origin.z() + base.spawnZ);
        int level = data.getHideoutLevel(facility.id);
        Set<BlockVector3> blocks = facility == HideoutFacility.BASE ? Set.of()
                : builder.placedBlocks(facility, Math.max(level, Math.min(level + 1, layout.highestLevel(facility))));
        Vector target;
        double extent;
        if (blocks.isEmpty()) {
            target = new Vector(origin.x() + base.sizeX / 2.0, origin.y() + base.sizeY / 3.0, origin.z() + base.sizeZ / 2.0);
            return look(new Location(world, spawn.getX(), spawn.getY(), spawn.getZ()), target);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockVector3 b : blocks) {
            minX = Math.min(minX, b.x()); maxX = Math.max(maxX, b.x());
            minY = Math.min(minY, b.y()); maxY = Math.max(maxY, b.y());
            minZ = Math.min(minZ, b.z()); maxZ = Math.max(maxZ, b.z());
        }
        target = new Vector(origin.x() + (minX + maxX + 1) / 2.0, origin.y() + (minY + maxY + 1) / 2.0,
                origin.z() + (minZ + maxZ + 1) / 2.0);
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

    // ---- 強化の素材 (スタッシュと持ち物から。支給品は使えない) ----

    /** スタッシュの各ページ (ページの番号と中身)。読めないページは飛ばす */
    private Map<Integer, Inventory> stashPages(PlayerData data) {
        Map<Integer, Inventory> pages = new java.util.TreeMap<>();
        for (Map.Entry<Integer, String> page : data.getStashPages().entrySet()) {
            if (page.getValue() == null) continue;
            try {
                pages.put(page.getKey(), SerializationUtil.deserializeInventory(page.getValue(), Component.empty()));
            } catch (Exception e) {
                plugin.getLogger().warning("スタッシュのページ " + page.getKey() + " を読めませんでした: " + e.getMessage());
            }
        }
        return pages;
    }

    /** クエスト納品と同じ場所（持ち物・バッグ・スタッシュ）の素材を数える。 */
    public Map<String, Integer> countMaterials(Player player, PlayerData data, List<HideoutFacility.ItemCost> costs) {
        return com.lunar_prototype.impossbleEscapeMC.modules.inventory.MaterialDelivery.count(player, data,
                costs.stream().map(HideoutFacility.ItemCost::itemId).toList());
    }

    /** 持ち物とスタッシュへ一括で渡す。全部入らなければ何も動かさずfalse、地面には落とさない。 */
    public boolean deliver(Player player, PlayerData data, List<ItemStack> items) {
        Inventory planned = Bukkit.createInventory(null, 36);
        planned.setMaxStackSize(player.getInventory().getMaxStackSize());
        planned.setContents(com.lunar_prototype.impossbleEscapeMC.modules.inventory.PlayerStorageSnapshot.cloneItems(player.getInventory().getStorageContents()));
        List<Integer> slots = com.lunar_prototype.impossbleEscapeMC.modules.inventory.CostSlotManager.acquisitionSlots(player);
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack item : items) {
            ItemStack rest = com.lunar_prototype.impossbleEscapeMC.modules.inventory.CostSlotManager.insert(planned,slots,slots.size(),item);
            if (rest != null) left.add(rest);
        }
        Map<Integer,String> encoded = new java.util.LinkedHashMap<>();
        try {
            for (var page : stashPages(data).entrySet()) {
                if (left.isEmpty()) break;
                List<ItemStack> next = new ArrayList<>();
                int before = left.stream().mapToInt(ItemStack::getAmount).sum();
                for (ItemStack item : left) next.addAll(page.getValue().addItem(item.clone()).values());
                if (next.stream().mapToInt(ItemStack::getAmount).sum() < before)
                    encoded.put(page.getKey(),SerializationUtil.serializeInventory(page.getValue()));
                left = next;
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,"受け取り先を保存できませんでした",e);
            return false;
        }
        if (!left.isEmpty()) return false;
        for (int slot : slots) player.getInventory().setItem(slot,planned.getItem(slot));
        encoded.forEach(data::setStashPage);
        dataModule.saveAsync(player.getUniqueId());
        return true;
    }

    /** 素材を共通の納品元から取る。不足・保存失敗時には何も減らさない。 */
    public boolean takeMaterials(Player player, PlayerData data, List<HideoutFacility.ItemCost> costs) throws IOException {
        Map<String,Integer> required = new java.util.LinkedHashMap<>();
        for (HideoutFacility.ItemCost cost : costs) required.merge(cost.itemId(), cost.amount(), Math::addExact);
        boolean consumed = com.lunar_prototype.impossbleEscapeMC.modules.inventory.MaterialDelivery.consume(player,data,required);
        if (consumed) dataModule.saveAsync(player.getUniqueId());
        return consumed;
    }

    /** 隠れ家のワールドのブロックなら、それを置いた設備 (ベースのブロック・ほかの場所なら null)。どの区画かは座標から求め、
     *  建っている物はその区画の持ち主のレベルで判定する (パーティーでリーダーの隠れ家にいる時も、リーダーが建てた物が分かる) */
    public HideoutFacility facilityAt(Player player, Block block) {
        if (!worldManager.isPlayerWorld(block.getWorld())) return null;
        int slot = slotAt(block.getX());
        BlockVector3 origin = origin(slot);
        return builder.ownerAt(levelsForSlot(slot, player),
                block.getX() - origin.x(), block.getY() - origin.y(), block.getZ() - origin.z());
    }

    /** 区画に建っている物を判定するための設備レベル: その区画の持ち主のもの。持ち主が分からなければ見ている人のもの */
    private ToIntFunction<HideoutFacility> levelsForSlot(int slot, Player viewer) {
        UUID owner = slots.ownerOf(slot);
        if (owner == null && viewer != null) {
            PlayerData own = dataModule.getPlayerData(viewer.getUniqueId());
            if (own != null && own.getHideoutIndex() == slot) owner = viewer.getUniqueId();
        }
        PlayerData resolved = owner != null ? dataModule.getPlayerData(owner) : null;
        if (resolved == null && viewer != null) resolved = dataModule.getPlayerData(viewer.getUniqueId());
        final PlayerData data = resolved;
        return facility -> data != null ? data.getHideoutLevel(facility.id) : 0;
    }

    /** 設備を 1 レベル上げて、その差分を反映してからレベルを保存する。 */
    public void upgrade(Player player, PlayerData data, HideoutFacility facility) throws IOException {
        String blocked = upgradeBlockReason(data, facility);
        if (blocked != null) throw new IOException(blocked);
        int next = data.getHideoutLevel(facility.id) + 1;
        try {
            // まず現行の建築データでベースを同期し、その後に差分を適用する。
            // これにより、未建築状態のままレベルだけ進むことを防ぐ。
            ensureBuilt(data);
            builder.applyLayer(worldManager.getPlayerWorld(), origin(data.getHideoutIndex()), facility, next);
            data.setHideoutLevel(facility.id, next);
        } catch (IOException e) {
            data.setHideoutBuiltVersion(-1);
            dataModule.saveAsync(player.getUniqueId());
            throw e;
        }
        dataModule.saveAsync(player.getUniqueId());
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
