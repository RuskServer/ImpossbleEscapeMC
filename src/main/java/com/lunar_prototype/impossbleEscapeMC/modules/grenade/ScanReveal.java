package com.lunar_prototype.impossbleEscapeMC.modules.grenade;

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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 探知グレネードで映った相手を、決まった時間だけ赤く光らせる (壁越しの縁取り)。光るのは見る人 (投げた人とそのパーティー) の画面でだけ。
 * <ul>
 *   <li>光らせ方: エンティティのデータの 0 番の光るビットを、見る人へ送るパケットでだけ立てる (送り直されても立て直す)</li>
 *   <li>色: 見る人のスコアボード (プレイヤーごと) の赤のチーム {@link #TEAM} に、相手の名前 (プレイヤー) か UUID (ほかの生き物) を入れる</li>
 * </ul>
 * 時間切れは {@link #CHECK_TICKS} ごとに見て、縁取りを消す (party/PartyGlow と同じやり方)
 */
public final class ScanReveal extends PacketListenerAbstract {

    private static final int CHECK_TICKS = 5;
    private static final byte GLOWING_FLAG = 0x40;
    /** 映った相手を入れる、見る人のスコアボードのチーム */
    public static final String TEAM = "scan_glow";

    /** 光らせている相手 1 つ (エンティティ、いつまで、チームに入れた名前) */
    private record Shown(UUID entity, long until, String entry) {
    }

    private static ScanReveal instance;

    private final ImpossbleEscapeMC plugin;
    /** 見る人 → (エンティティの番号 → 光らせている相手)。別のスレッドが読む */
    private final Map<UUID, Map<Integer, Shown>> shown = new ConcurrentHashMap<>();
    private final BukkitTask task;

    private ScanReveal(ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::expire, CHECK_TICKS, CHECK_TICKS);
    }

    static void init(ImpossbleEscapeMC plugin) {
        if (instance != null) return;
        instance = new ScanReveal(plugin);
        PacketEvents.getAPI().getEventManager().registerListener(instance);
    }

    static void shutdown() {
        if (instance == null) return;
        instance.task.cancel();
        PacketEvents.getAPI().getEventManager().unregisterListener(instance);
        for (UUID viewer : new ArrayList<>(instance.shown.keySet())) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) instance.clearAll(p);
        }
        instance.shown.clear();
        instance = null;
    }

    /** viewer の画面で target を ticks の間光らせる (光っている間にまた映れば延ばす) */
    static void reveal(Player viewer, LivingEntity target, int ticks) {
        if (instance == null) return;
        String entry = target instanceof Player p ? p.getName() : target.getUniqueId().toString();
        long until = Bukkit.getCurrentTick() + ticks;
        Map<Integer, Shown> next = new HashMap<>(instance.shown.getOrDefault(viewer.getUniqueId(), Map.of()));
        boolean already = next.containsKey(target.getEntityId());
        next.put(target.getEntityId(), new Shown(target.getUniqueId(), until, entry));
        instance.shown.put(viewer.getUniqueId(), Map.copyOf(next));
        team(viewer, true).ifPresent(t -> {
            if (!t.hasEntry(entry)) t.addEntry(entry);
        });
        if (!already) send(viewer, target, true);
    }

    private void expire() {
        long now = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, Map<Integer, Shown>> e : new ArrayList<>(shown.entrySet())) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            if (viewer == null) {
                shown.remove(e.getKey());
                continue;
            }
            Map<Integer, Shown> next = new HashMap<>(e.getValue());
            List<Shown> ended = new ArrayList<>();
            next.values().removeIf(s -> {
                if (s.until() > now) return false;
                ended.add(s);
                return true;
            });
            if (ended.isEmpty()) continue;
            if (next.isEmpty()) shown.remove(e.getKey());
            else shown.put(e.getKey(), Map.copyOf(next));
            for (Shown s : ended) hide(viewer, s);
        }
    }

    private void clearAll(Player viewer) {
        Map<Integer, Shown> current = shown.remove(viewer.getUniqueId());
        if (current != null) current.values().forEach(s -> hide(viewer, s));
    }

    /** 縁取りを消し、チームから外す */
    private static void hide(Player viewer, Shown s) {
        team(viewer, false).ifPresent(t -> t.removeEntry(s.entry()));
        Entity entity = Bukkit.getEntity(s.entity());
        if (entity instanceof LivingEntity living && living.isValid()) send(viewer, living, false);
    }

    /** 見る人のスコアボードの赤のチーム (スコアボードがサーバー共通の物なら無し。create なら無ければ作る) */
    private static java.util.Optional<Team> team(Player viewer, boolean create) {
        Scoreboard board = viewer.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) return java.util.Optional.empty();
        Team team = board.getTeam(TEAM);
        if (team == null && create) {
            team = board.registerNewTeam(TEAM);
            team.color(NamedTextColor.RED);
            // 名札はレイド中のほかの人と同じく出さない (ScoreboardHandler の hide_tags と同じ)
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        return java.util.Optional.ofNullable(team);
    }

    /** 相手の状態のビットを、光る縁取りを付けて・外して、この人にだけ送り直す */
    private static void send(Player viewer, LivingEntity target, boolean glow) {
        byte flags = flags(target);
        if (glow) flags |= GLOWING_FLAG;
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerEntityMetadata(target.getEntityId(),
                List.of(new EntityData<>(0, EntityDataTypes.BYTE, flags))));
    }

    /** サーバーが送る状態のビット (燃えている・しゃがみ・ダッシュ・泳ぎ・透明・光る・滑空) */
    private static byte flags(LivingEntity e) {
        int f = 0;
        if (e.getFireTicks() > 0) f |= 0x01;
        if (e instanceof Player p) {
            if (p.isSneaking()) f |= 0x02;
            if (p.isSprinting()) f |= 0x08;
            if (p.isSwimming()) f |= 0x10;
        }
        if (e.isInvisible()) f |= 0x20;
        if (e.isGlowing() || e.hasPotionEffect(PotionEffectType.GLOWING)) f |= GLOWING_FLAG;
        if (e.isGliding()) f |= 0x80;
        return (byte) f;
    }

    /** 状態のビット (0 番) に光るビットを立てたデータ。0 番が無い・もう光っていれば null */
    @SuppressWarnings("unchecked")
    private static List<EntityData<?>> withGlow(List<EntityData<?>> data) {
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
        UUID viewer = event.getUser() != null ? event.getUser().getUUID() : null;
        Map<Integer, Shown> targets = viewer != null ? shown.get(viewer) : null;
        if (targets == null) return;
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
            if (!targets.containsKey(packet.getEntityId())) return;
            List<EntityData<?>> data = withGlow(packet.getEntityMetadata());
            if (data == null) return;
            packet.setEntityMetadata(data);
            event.markForReEncode(true);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            // 見えなくなって出直した相手は、状態のビットが 0 なら送られてこないので、出た後に光らせ直す
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(event);
            Shown s = targets.get(packet.getEntityId());
            if (s == null) return;
            event.getPostTasks().add(() -> {
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player v = Bukkit.getPlayer(viewer);
                    Map<Integer, Shown> current = shown.get(viewer);
                    if (v == null || current == null || !current.containsKey(packet.getEntityId())) return;
                    if (Bukkit.getEntity(s.entity()) instanceof LivingEntity living) send(v, living, true);
                });
            });
        }
    }
}
