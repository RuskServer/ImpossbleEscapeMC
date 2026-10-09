package com.lunar_prototype.impossbleEscapeMC.ai;

import com.lunar_prototype.impossbleEscapeMC.ai.weapon.DatapackGunProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * SCAVの代わりにToi's Armoryのデータパック銃を撃つ不可視のFakePlayer。
 *
 * データパックは射撃者を @p / @a で選ぶため、プレイヤーリストに載ったServerPlayerでなければ
 * 弾道・ダメージ・発射エフェクトが動かない。そこでSCAV (Mob) ごとに1体のFakePlayerを
 * PlayerList とワールドへ直接登録し (参加イベント・参加メッセージは出さない)、毎tick SCAVの目の位置と向きに合わせる。
 * 射撃はデータパックの射撃入力スコア toisarm.timer.trigger を立てるだけで、発射以降はデータパックが処理する。
 */
public final class DatapackGunner {

    private static final String GIVE_GUN_FUNCTION = "toisarm:dialog/get_gun_with_id/with_trigger_count/with_id/with_data/";
    private static final String FILL_MAGAZINE_FUNCTION = "toisarm:util/fill_magazine/";
    /** 射撃入力。データパックはクロスボウ発射時にこの値を4にし、毎tick減らしながら0以上の間を引き金を引いている状態とみなす */
    private static final String TRIGGER_OBJECTIVE = "toisarm.timer.trigger";
    private static final int TRIGGER_PULL_VALUE = 4;
    private static final int GIVE_GUN_DELAY_TICKS = 2;
    /** inaccuracy (BulletTaskの拡散量) を照準のブレ角度 (度) に換算する係数 */
    private static final double INACCURACY_TO_DEGREES = 45.0;
    /**
     * 引き金を引いた時の照準を保つtick数。データパックは引き金を引いてから (TRIGGER_PULL_VALUE + 1) tick撃ち続けるが、
     * その間にSCAVの体と頭は移動方向へ向き直るため、SCAVの向きをそのまま写すと2発目以降が横に逸れる
     */
    private static final int AIM_HOLD_TICKS = TRIGGER_PULL_VALUE + 2;

    /** 送信パケットをすべて捨てる接続 */
    private static final class DiscardingConnection extends Connection {
        DiscardingConnection() {
            super(PacketFlow.SERVERBOUND);
            this.channel = new EmbeddedChannel();
        }

        @Override public void send(Packet<?> packet) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener) {}
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {}
    }

    private final Mob owner;
    private final DatapackGunProfile profile;
    private final int slot;
    private final ServerPlayer handle;
    private int age;
    private boolean armed;
    private int reloadTicks;
    /** 引き金を引いた時の照準 (ブレ込み)。aimHoldTicks の間はSCAVの向きの代わりにこれを使う */
    private float aimYaw;
    private float aimPitch;
    private int aimHoldTicks;

    private DatapackGunner(Mob owner, DatapackGunProfile profile, int slot, ServerPlayer handle) {
        this.owner = owner;
        this.profile = profile;
        this.slot = slot;
        this.handle = handle;
    }

    static UUID uuidForSlot(int slot) {
        return UUID.nameUUIDFromBytes(("ImpossbleEscapeMC:scav-gunner:" + slot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    static String nameForSlot(int slot) {
        return "SCAV_" + slot;
    }

    /**
     * SCAVの位置にFakePlayerを生成して登録する。
     * UUIDと名前はスロット番号から固定で決まるため、オートセーブで書かれるプレイヤーデータは同時稼働数ぶんしか増えない。
     */
    static DatapackGunner spawn(Plugin plugin, Mob owner, DatapackGunProfile profile, int slot) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) owner.getWorld()).getHandle();
        GameProfile gameProfile = new GameProfile(uuidForSlot(slot), nameForSlot(slot));
        // 視界距離は最小にしてFakePlayerによるチャンク読み込みを抑える
        ClientInformation info = new ClientInformation("en_us", 2, ChatVisiblity.HIDDEN, false, 0, HumanoidArm.RIGHT, false, false, ParticleStatus.MINIMAL);
        ServerPlayer handle = new ServerPlayer(server, level, gameProfile, info);
        handle.connection = new ServerGamePacketListenerImpl(server, new DiscardingConnection(), handle, CommonListenerCookie.createInitial(gameProfile, false));

        DatapackGunner gunner = new DatapackGunner(owner, profile, slot, handle);
        gunner.syncToOwner();

        // PlayerList#placeNewPlayer を通すと参加イベント・参加メッセージ・プレイヤーデータ読み込みが走るため、必要な登録だけ行う
        PlayerList playerList = server.getPlayerList();
        playerListPlayers(playerList).add(handle);
        playerListByUuid(playerList).put(handle.getUUID(), handle);
        playerListByName(playerList).put(handle.getScoreboardName().toLowerCase(java.util.Locale.ROOT), handle);
        level.addNewPlayer(handle);

        Player bukkit = handle.getBukkitEntity();
        bukkit.setCollidable(false);
        bukkit.setCanPickupItems(false);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!DatapackGunnerManager.isGunner(online)) {
                online.hidePlayer(plugin, bukkit);
            }
        }
        return gunner;
    }

    Mob getOwner() { return owner; }
    int getSlot() { return slot; }
    UUID getUniqueId() { return handle.getUUID(); }
    Player getBukkitPlayer() { return handle.getBukkitEntity(); }

    /** 毎tick呼ぶ。位置同期・銃の受け取り・再装填を行う */
    void tick() {
        age++;
        if (aimHoldTicks > 0) aimHoldTicks--;
        syncToOwner();

        if (age == GIVE_GUN_DELAY_TICKS) {
            // データパックの初期化 (toisarm.id付与) が先に済むよう数tick待ってから渡す
            runSilently("execute as " + handle.getUUID() + " run function " + GIVE_GUN_FUNCTION
                    + " {id:\"" + profile.id() + "\",display_name:\"" + profile.id() + "\"}");
            return;
        }
        if (age < GIVE_GUN_DELAY_TICKS) return;

        if (!armed) {
            // 銃データの読み込み (装弾数スコアの設定) を待ってから装填する
            if (score("toisarm.state.ammo_capacity") > 0) {
                runSilently("execute as " + handle.getUUID() + " run function " + FILL_MAGAZINE_FUNCTION);
                armed = true;
            }
            return;
        }

        if (reloadTicks > 0) {
            if (--reloadTicks == 0) {
                runSilently("execute as " + handle.getUUID() + " run function " + FILL_MAGAZINE_FUNCTION);
            }
        } else if (ammo() <= 0) {
            // 弾切れ: データパックの弾切れリロード時間の後に再装填する
            reloadTicks = Math.max(1, profile.emptyReloadTicks());
        }
    }

    /** 撃てる状態 (銃の受け取りと装填が済み、リロード中でない) か */
    public boolean isReady() {
        return armed && reloadTicks == 0;
    }

    /** 弾切れからの再装填待ちか (生成直後の準備中は含めない) */
    public boolean isReloading() {
        return reloadTicks > 0;
    }

    /** 撃てる弾数 (マガジン + 薬室)。装填前は装弾数を返す */
    public int ammo() {
        if (!armed) return profile.magazineSize();
        return Math.max(0, score("toisarm.ammo_remaining")) + Math.max(0, score("toisarm.chamber"));
    }

    /**
     * 引き金を引く。実際の発射レート・セミ/フルオートの扱いはデータパック側の銃設定に従う
     *
     * @return 引き金を引けた場合true (準備中・リロード中はfalse)
     */
    public boolean pullTrigger(double inaccuracy) {
        if (!isReady()) return false;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = inaccuracy * INACCURACY_TO_DEGREES;
        // SCAVのAIは引き金を引く直前に照準を合わせているので、その向きを撃ち終わるまで保つ
        Location eye = owner.getEyeLocation();
        aimYaw = eye.getYaw() + (float) ((random.nextDouble() * 2 - 1) * spread);
        aimPitch = Math.max(-90f, Math.min(90f, eye.getPitch() + (float) ((random.nextDouble() * 2 - 1) * spread * 0.6)));
        aimHoldTicks = AIM_HOLD_TICKS;
        syncToOwner();

        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(TRIGGER_OBJECTIVE);
        if (objective != null) {
            objective.getScore(handle.getScoreboardName()).setScore(TRIGGER_PULL_VALUE);
        }
        return true;
    }

    /** SCAVの目の位置にFakePlayerの目の位置を合わせる。向きは撃っている間は引き金を引いた時の照準、それ以外はSCAVの向き */
    private void syncToOwner() {
        Location eye = owner.getEyeLocation();
        boolean holdingAim = aimHoldTicks > 0;
        float yaw = holdingAim ? aimYaw : eye.getYaw();
        float pitch = holdingAim ? aimPitch : eye.getPitch();
        handle.snapTo(eye.getX(), eye.getY() - handle.getEyeHeight(), eye.getZ(), yaw, pitch);
        handle.setYHeadRot(yaw);
        if (handle.level() instanceof ServerLevel level && handle.connection != null && level.players().contains(handle)) {
            level.getChunkSource().move(handle);
        }
    }

    /** PlayerListとワールドから取り除き、データパック側に残るスコア・個別ストレージも消す */
    void remove() {
        int datapackId = score("toisarm.id");
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        PlayerList playerList = server.getPlayerList();
        if (handle.level() instanceof ServerLevel level) {
            level.removePlayerImmediately(handle, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        playerListPlayers(playerList).remove(handle);
        playerListByUuid(playerList).remove(handle.getUUID(), handle);
        playerListByName(playerList).remove(handle.getScoreboardName().toLowerCase(java.util.Locale.ROOT), handle);

        if (datapackId > 0) {
            runSilently("data remove storage toisarm:private " + datapackId);
        }
        runSilently("scoreboard players reset " + handle.getScoreboardName());
    }

    private int score(String objectiveName) {
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(objectiveName);
        if (objective == null) return 0;
        Score score = objective.getScore(handle.getScoreboardName());
        return score.isScoreSet() ? score.getScore() : 0;
    }

    static void runSilently(String command) {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    // --- PlayerList の private フィールドへのアクセス (Paper 26.x は実行時もMojangマッピング) ---

    private static Field playersField;
    private static Field byUuidField;
    private static Field byNameField;

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> playerListPlayers(PlayerList playerList) {
        return (List<ServerPlayer>) readField(playerList, "players");
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playerListByUuid(PlayerList playerList) {
        return (Map<UUID, ServerPlayer>) readField(playerList, "playersByUUID");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ServerPlayer> playerListByName(PlayerList playerList) {
        return (Map<String, ServerPlayer>) readField(playerList, "playersByName");
    }

    private static Object readField(PlayerList playerList, String name) {
        try {
            Field field = switch (name) {
                case "players" -> playersField != null ? playersField : (playersField = accessible(name));
                case "playersByUUID" -> byUuidField != null ? byUuidField : (byUuidField = accessible(name));
                default -> byNameField != null ? byNameField : (byNameField = accessible(name));
            };
            return field.get(playerList);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("PlayerList." + name + " にアクセスできません", e);
        }
    }

    private static Field accessible(String name) throws NoSuchFieldException {
        Field field = PlayerList.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
