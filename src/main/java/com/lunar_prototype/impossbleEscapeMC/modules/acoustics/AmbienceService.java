package com.lunar_prototype.impossbleEscapeMC.modules.acoustics;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * レイド中の背景音 (環境音)。今いる場所 ({@link AcousticMap}) と時間帯で背景を選び、
 * リソースパックの iem:ambient.&lt;背景&gt; (IEM_shader/tools/ambience.py が作る SEGMENT_TICKS の切れ端) を、
 * FADE_TICKS ずつ重ねて次々に本人にだけ鳴らす。切れ端は頭と尻にフェードが焼き込んであるので継ぎ目は聞こえない。
 * <ul>
 *   <li>背景はステレオで距離で減衰しないので、本人に付けて鳴らせば (Emitter.self) 背景として聞こえる</li>
 *   <li>場所が変わったら、前の切れ端が末尾のフェードに入るまで待って次の背景へ切り替える</li>
 *   <li>出入り口で行ったり来たりしないよう、同じ背景が CONFIRM_CHECKS 回続いてから切り替える</li>
 *   <li>雨はバニラの雨の音 (屋根の下かどうかも扱う) に任せる</li>
 * </ul>
 */
public final class AmbienceService {

    /** ambience.py の SEGMENT・FADE と同じ (tick) */
    private static final int SEGMENT_TICKS = 80;
    private static final int FADE_TICKS = 10;
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final int CONFIRM_CHECKS = 2;
    private static final float VOLUME = 0.5f; // 全体を約-6dB。背景ごとの追加調整はvolume()。

    private static AmbienceService instance;

    private final Map<UUID, State> states = new HashMap<>();

    static final class State {
        String bed;
        String previousBed;
        String candidate;
        int candidateChecks;
        int nextTick;

        /** 更新時刻が来るまでは、確定した候補も待機させる。二つを超える背景を重ねない。 */
        String next(String wanted, int now) {
            if (!wanted.equals(candidate)) {
                candidate = wanted;
                candidateChecks = 0;
            }
            candidateChecks = Math.min(CONFIRM_CHECKS, candidateChecks + 1);
            if (bed != null && now < nextTick) return null;
            previousBed = bed;
            if (bed == null || candidateChecks >= CONFIRM_CHECKS) bed = candidate;
            nextTick = now + SEGMENT_TICKS - FADE_TICKS;
            return bed;
        }

        /** 脱出・死亡後に背景だけが残らないよう、自分が鳴らしたイベントだけを止める。 */
        void stop(Player player) {
            if (bed != null) stopBed(player, bed);
            if (previousBed != null && !previousBed.equals(bed)) stopBed(player, previousBed);
        }
    }

    private static void stopBed(Player player, String bed) {
        player.stopSound(SoundStop.namedOnSource(Key.key("iem", "ambient." + bed), Sound.Source.AMBIENT));
    }

    /** 屋外の鳥・虫を含む背景や地下の唸りは、部屋の薄い空気音より一段控える。 */
    static float volume(String bed) {
        return VOLUME * switch (bed) {
            case "outdoor_day", "outdoor_night" -> 0.8f;
            case "hall" -> 0.9f;
            case "underground" -> 0.7f;
            default -> 1.0f;
        };
    }

    private AmbienceService(ImpossbleEscapeMC plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public static void init(ImpossbleEscapeMC plugin) {
        if (instance == null) instance = new AmbienceService(plugin);
    }

    /** 場所と時間帯から背景の名前 (ambience.py の BEDS) */
    static String bed(AcousticZone zone, boolean night) {
        return switch (zone) {
            case OUTDOOR -> night ? "outdoor_night" : "outdoor_day";
            case INDOOR -> night ? "indoor_night" : "indoor_day";
            case HALL -> "hall";
            case UNDERGROUND -> "underground";
        };
    }

    /** 夜 (日没から日の出まで) か */
    static boolean isNight(long worldTime) {
        long time = Math.floorMod(worldTime, 24000L);
        return time >= 13000 && time < 23000;
    }

    private void tick() {
        var raidModule = ImpossbleEscapeMC.getInstance().getRaidModule();
        int now = Bukkit.getCurrentTick();
        states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean active = raidModule != null && raidModule.isInRaid(player) && !player.isDead()
                    && player.getGameMode() != GameMode.SPECTATOR;
            if (!active) {
                State stopped = states.remove(player.getUniqueId());
                if (stopped != null) stopped.stop(player);
                continue;
            }
            State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
            String wanted = bed(AcousticMap.zoneAt(player.getLocation()), isNight(player.getWorld().getTime()));
            String next = state.next(wanted, now);
            if (next != null) {
                player.playSound(Sound.sound(Key.key("iem", "ambient." + next), Sound.Source.AMBIENT,
                        volume(next), 1.0f), Sound.Emitter.self());
            }
        }
    }
}
