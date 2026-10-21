package com.lunar_prototype.impossbleEscapeMC.modules.party;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidInstance;
import com.lunar_prototype.impossbleEscapeMC.modules.raid.RaidModule;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * レイド中、同じパーティーの味方を青く光らせる (壁越しでも見える縁取り)。光るのは味方の画面でだけ。
 * <ul>
 *   <li>光らせ方: エンティティのデータの 0 番 (状態のビット) の光るビットを、その人へ送るパケットでだけ立てる。
 *       サーバーがしゃがみ・ダッシュなどで 0 番を送り直す時も、ここで立て直す</li>
 *   <li>色: 縁取りの色はチームの色なので、見る人のスコアボード (プレイヤーごと) に青のチーム {@link #TEAM} を作り、味方を入れる</li>
 *   <li>対象: 見る人と同じレイドに参加していて、同じワールドにいる味方 (観戦中は除く)</li>
 * </ul>
 * 誰を光らせるかはメインスレッドで {@link #REFRESH_TICKS} ごとに決め直し、変わった所だけ送る
 * (パケットは別のスレッドで書き換えるので、パーティーやレイドは直接読まない)
 */
public final class PartyGlow extends PacketListenerAbstract {

    private static final int REFRESH_TICKS = 5;
    /** エンティティのデータの 0 番 (状態のビット) の、光る縁取りのビット */
    private static final byte GLOWING_FLAG = 0x40;
    /** 味方を入れる、見る人のスコアボードのチーム */
    public static final String TEAM = "party_glow";

    private static PartyGlow instance;

    private final ImpossbleEscapeMC plugin;
    /** 見る人 → 光らせている味方 (エンティティの番号 → プレイヤー)。中の表は作り直して入れ替える (別のスレッドが読む) */
    private final Map<UUID, Map<Integer, UUID>> glowing = new ConcurrentHashMap<>();
    private final BukkitTask task;

    private PartyGlow(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, REFRESH_TICKS, REFRESH_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new PartyGlow(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    /** 光らせている味方を元に戻す (プラグインの停止時) */
    public static void shutdown() {
        if (instance == null) return;
        instance.task.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance);
        for (Player viewer : Bukkit.getOnlinePlayers()) instance.apply(viewer, Map.of());
        instance.glowing.clear();
        instance = null;
    }

    private void refreshAll() {
        glowing.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player viewer : Bukkit.getOnlinePlayers()) apply(viewer, collect(viewer));
    }

    /** この人の画面で光らせる味方 (エンティティの番号 → プレイヤー) */
    private Map<Integer, Player> collect(Player viewer) {
        PartyModule parties = PartyModule.get();
        RaidModule raids = plugin.getRaidModule();
        if (parties == null || raids == null) return Map.of();
        Party party = parties.getParty(viewer.getUniqueId());
        if (party == null || party.getSize() < 2) return Map.of();
        RaidInstance raid = raids.getRaidOf(viewer);
        if (raid == null) return Map.of();
        Map<Integer, Player> out = new HashMap<>();
        for (UUID id : party.getMembers()) {
            if (id.equals(viewer.getUniqueId())) continue;
            Player mate = Bukkit.getPlayer(id);
            if (mate == null || mate.getGameMode() == GameMode.SPECTATOR || mate.getWorld() != viewer.getWorld()) continue;
            if (raids.getRaidOf(mate) != raid) continue;
            out.put(mate.getEntityId(), mate);
        }
        return out;
    }

    /** 光らせる味方を now に替える (外れた味方の縁取りを消し、入った味方を光らせ、チームの顔ぶれを合わせる) */
    private void apply(Player viewer, Map<Integer, Player> now) {
        Map<Integer, UUID> before = glowing.getOrDefault(viewer.getUniqueId(), Map.of());
        Map<Integer, UUID> after = new HashMap<>();
        now.forEach((entityId, mate) -> after.put(entityId, mate.getUniqueId()));
        if (after.isEmpty()) glowing.remove(viewer.getUniqueId());
        else glowing.put(viewer.getUniqueId(), Map.copyOf(after));

        syncTeam(viewer, now.values());
        for (Map.Entry<Integer, UUID> e : before.entrySet()) {
            if (after.containsKey(e.getKey())) continue;
            Player mate = Bukkit.getPlayer(e.getValue());
            if (mate != null) send(viewer, mate, false);
        }
        for (Map.Entry<Integer, Player> e : now.entrySet()) {
            if (!before.containsKey(e.getKey())) send(viewer, e.getValue(), true);
        }
    }

    /** 見る人のスコアボードの青のチームに、光らせる味方だけを入れる (スコアボードがサーバー共通の物なら触らない) */
    private static void syncTeam(Player viewer, java.util.Collection<Player> mates) {
        Scoreboard board = viewer.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) return;
        Team team = board.getTeam(TEAM);
        if (team == null) {
            if (mates.isEmpty()) return;
            team = board.registerNewTeam(TEAM);
            team.color(NamedTextColor.BLUE);
            // 名札はレイド中のほかの人と同じく出さない (ScoreboardHandler の hide_tags と同じ)
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
        }
        Set<String> names = new HashSet<>();
        for (Player mate : mates) names.add(mate.getName());
        for (String entry : new ArrayList<>(team.getEntries())) {
            if (!names.contains(entry)) team.removeEntry(entry);
        }
        for (String name : names) {
            if (!team.hasEntry(name)) team.addEntry(name);
        }
    }

    /** 味方の状態のビットを、光る縁取りを付けて・外して、この人にだけ送り直す (ほかのビットはサーバーの今の状態) */
    private static void send(Player viewer, Player mate, boolean glow) {
        byte flags = flags(mate);
        if (glow) flags |= GLOWING_FLAG;
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(mate.getEntityId(),
                List.of(new EntityData<>(0, EntityDataTypes.BYTE, flags))));
    }

    /** サーバーが送る状態のビット (燃えている・しゃがみ・ダッシュ・泳ぎ・透明・光る・滑空) */
    private static byte flags(Player p) {
        int f = 0;
        if (p.getFireTicks() > 0) f |= 0x01;
        if (p.isSneaking()) f |= 0x02;
        if (p.isSprinting()) f |= 0x08;
        if (p.isSwimming()) f |= 0x10;
        if (p.isInvisible()) f |= 0x20;
        if (p.isGlowing() || p.hasPotionEffect(PotionEffectType.GLOWING)) f |= GLOWING_FLAG;
        if (p.isGliding()) f |= 0x80;
        return (byte) f;
    }

    /** 状態のビット (0 番) に光るビットを立てたデータ。0 番が無い・もう光っていれば null (書き換えない) */
    @SuppressWarnings("unchecked")
    static List<EntityData<?>> withGlow(List<EntityData<?>> data) {
        for (int i = 0; i < data.size(); i++) {
            EntityData<?> d = data.get(i);
            if (d.getIndex() != 0 || !(d.getValue() instanceof Byte b) || (b & GLOWING_FLAG) != 0) continue;
            List<EntityData<?>> out = new ArrayList<>(data);
            out.set(i, new EntityData<>(0, (EntityDataType<Byte>) d.getType(), (byte) (b | GLOWING_FLAG)));
            return out;
        }
        return null;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            UUID viewer = event.getUser().getUUID();
            Map<Integer, UUID> mates = viewer != null ? glowing.get(viewer) : null;
            if (mates == null) return;
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (!mates.containsKey(packet.getEntityId())) return;
            List<EntityData<?>> data = withGlow(packet.getEntityMetadata());
            if (data == null) return;
            packet.setEntityMetadata(data);
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            // 見えなくなって・死んで・ワールドを移って出直した味方は、状態のビットが 0 なら送られてこないので、出た後に光らせ直す
            UUID viewer = event.getUser().getUUID();
            Map<Integer, UUID> mates = viewer != null ? glowing.get(viewer) : null;
            if (mates == null) return;
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            UUID mateId = mates.get(packet.getEntityId());
            if (mateId == null) return;
            event.getPostTasks().add(() -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player v = Bukkit.getPlayer(viewer);
                    Player mate = Bukkit.getPlayer(mateId);
                    Map<Integer, UUID> current = glowing.get(viewer);
                    if (v != null && mate != null && current != null && mateId.equals(current.get(mate.getEntityId()))) send(v, mate, true);
                });
            });
        }
    }
}
