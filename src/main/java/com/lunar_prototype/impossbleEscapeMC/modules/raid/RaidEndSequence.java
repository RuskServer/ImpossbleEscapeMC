package com.lunar_prototype.impossbleEscapeMC.modules.raid;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.effect.EmpSkySignal;
import com.lunar_prototype.impossbleEscapeMC.effect.ScreenEffectService;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * レイド終盤の演出。クライアントが買った監視網の空白時間 (侵入ウィンドウ) が閉じるまでを、
 * 予告 → 予兆 (サイレン・うなり・照明弾・空が赤く色づく) → ミサイル2発が飛来して上空で EMP 起爆
 * (青白い火球・広がり続ける衝撃波・オーロラ) と進める。時間切れで国連軍の掃討となり、残っていた参加者は今まで通り MIA になる。
 *
 * 段階はマップの残り時間で決める。サイレンと照明弾の位置は、既存のマップデータ (スポーン・脱出・Scav・コンテナ) の
 * 範囲の外側に向けて求めるので、マップごとの追加設定はいらない。
 * 音はリソースパックの iem:raid_end.*、予兆の空の色と起爆の閃光は {@link ScreenEffectService} がこのクラスを見てかける。
 * ミサイルから先の空と雲はリソースパックのシェーダーが描き、{@link EmpSkySignal} がその開始を伝える。
 */
public final class RaidEndSequence {

    private static final String SOUND_SIREN = "iem:raid_end.siren";
    private static final String SOUND_HUM = "iem:raid_end.hum";
    private static final String SOUND_EMP_BLAST = "iem:raid_end.emp_blast";
    private static final String SOUND_EAR_RINGING = "iem:raid_end.ear_ringing";
    private static final String SOUND_ELECTRIC_SHORT = "iem:raid_end.electric_short";
    private static final String SOUND_AMBIENCE = "iem:raid_end.ambience";
    private static final List<String> SOUNDS = List.of(
            SOUND_SIREN, SOUND_HUM, SOUND_EMP_BLAST, SOUND_EAR_RINGING, SOUND_ELECTRIC_SHORT, SOUND_AMBIENCE);

    private static final Key PRECURSOR_1 = Key.key("iem", "emp_precursor_1");
    private static final Key PRECURSOR_2 = Key.key("iem", "emp_precursor_2");
    private static final Key PRECURSOR_3 = Key.key("iem", "emp_precursor_3");

    /** ミサイルの発射から1発目の起爆まで。iem_emp_sky.glsl の EMP_FLIGHT (3秒) と同じにする */
    private static final int MISSILE_FLIGHT_TICKS = 60;
    /** 2発目の起爆の遅れ。iem_emp_sky.glsl の EMP_SECOND_DELAY (0.6秒) と同じにする */
    private static final int SECOND_BLAST_DELAY_TICKS = 12;

    /** うなりの音の長さ (10.2秒) に合わせて鳴らし直す間隔 */
    private static final int HUM_INTERVAL_SECONDS = 10;
    private static final int FLARE_INTERVAL_SECONDS = 6;
    /** サイレンはプレイヤーから外周の方向へこの距離・高さの位置で鳴らす。音量4で届く範囲は64ブロック */
    private static final double SIREN_DISTANCE = 24.0;
    private static final double SIREN_HEIGHT = 12.0;
    private static final float SIREN_VOLUME = 4.0f;
    /** 照明弾はプレイヤーからこの範囲の距離で打ち上げ、爆発に巻き込まないよう全員からこれ以上離す */
    private static final double FLARE_MIN_DISTANCE = 48.0;
    private static final double FLARE_MAX_DISTANCE = 72.0;
    private static final double FLARE_CLEARANCE = 16.0;
    private static final int FLARE_ATTEMPTS = 4;
    /** 起爆の後、ショート音と環境音を鳴らし始めるまで (tick) */
    private static final int ELECTRIC_SHORT_DELAY_TICKS = 12;
    private static final int AMBIENCE_DELAY_TICKS = 50;
    private static final int STATIC_MESSAGE_DELAY_TICKS = 30;

    private final ImpossbleEscapeMC plugin;
    private final RaidMap map;
    private final int warningSeconds;
    private final int precursorSeconds;
    private final int empSeconds;
    private final Random random = new Random();
    /** 終盤の音を聞かせたプレイヤー。レイドを抜けたら音を止める */
    private final Set<UUID> audible = new HashSet<>();
    /** マップの範囲の中心 (x, z)。最初に使う時に求める */
    private Vector center;
    private int timeLeft = Integer.MAX_VALUE;
    private boolean warned;
    private boolean precursorStarted;
    private boolean launched;
    private boolean detonated;

    public RaidEndSequence(ImpossbleEscapeMC plugin, RaidMap map) {
        this.plugin = plugin;
        this.map = map;
        this.warningSeconds = plugin.getConfig().getInt("raid-end-sequence.warning-seconds", 180);
        this.precursorSeconds = plugin.getConfig().getInt("raid-end-sequence.precursor-seconds", 90);
        this.empSeconds = plugin.getConfig().getInt("raid-end-sequence.emp-seconds", 45);
    }

    /** 毎秒、マップの残り時間と今の参加者で段階を進める */
    public void tick(int timeLeft, Set<UUID> players) {
        this.timeLeft = timeLeft;
        stopForDeparted(players);
        List<Player> online = new ArrayList<>();
        for (UUID id : players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) online.add(player);
        }

        if (!warned && timeLeft <= warningSeconds) {
            warned = true;
            online.forEach(this::sendWarning);
        }
        // ミサイルの飛行時間だけ前に発射し、残り empSeconds で起爆させる
        if (!launched && timeLeft <= empSeconds + MISSILE_FLIGHT_TICKS / 20) {
            launched = true;
            online.forEach(this::launchMissiles);
        }
        if (detonated || timeLeft > precursorSeconds) return;

        if (!precursorStarted) {
            precursorStarted = true;
            online.forEach(this::startPrecursor);
        }
        int elapsed = precursorSeconds - timeLeft;
        if (elapsed % HUM_INTERVAL_SECONDS == 0) {
            float volume = (float) (0.25 + 0.75 * precursorProgress());
            for (Player player : online) {
                player.playSound(player, SOUND_HUM, SoundCategory.MASTER, volume, 1.0f);
                audible.add(player.getUniqueId());
            }
        }
        if (elapsed % FLARE_INTERVAL_SECONDS == 0 && !online.isEmpty()) {
            launchFlare(online);
        }
    }

    /** 今の段階で空にかけるポストエフェクト。予兆は3段階でだんだん濃くする。起爆後の空はシェーダーが描くのでかけない */
    public Key skyEffect() {
        if (detonated || timeLeft > precursorSeconds) return null;
        double progress = precursorProgress();
        if (progress < 1.0 / 3.0) return PRECURSOR_1;
        return progress < 2.0 / 3.0 ? PRECURSOR_2 : PRECURSOR_3;
    }

    /** レイドの終了時に、終盤の音と空の演出を全員分止める */
    public void stop() {
        for (UUID id : audible) {
            EmpSkySignal.stop(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) stopSounds(player);
        }
        audible.clear();
    }

    /** 予兆の始まりから起爆までの進み具合 (0..1) */
    private double precursorProgress() {
        double span = Math.max(1, precursorSeconds - empSeconds);
        return Math.min(1.0, Math.max(0.0, (precursorSeconds - timeLeft) / span));
    }

    private void sendWarning(Player player) {
        player.sendMessage(Component.text("[クライアント] ", NamedTextColor.GRAY)
                .append(Component.text("監視網の妨害はあと" + formatDuration(warningSeconds)
                        + "で切れる。回収したものを持って撤収しろ。", NamedTextColor.WHITE)));
        playRadioClick(player);
    }

    private void startPrecursor(Player player) {
        player.sendMessage(Component.text("[国連軍 放送] ", NamedTextColor.GOLD)
                .append(Component.text("当該セクターで未登録の活動を検知した。監視網の復旧後、区域内の全熱源を敵性と判定する。直ちに退去せよ。",
                        NamedTextColor.YELLOW)));
        player.playSound(sirenLocation(player), SOUND_SIREN, SoundCategory.MASTER, SIREN_VOLUME, 1.0f);
        audible.add(player.getUniqueId());
    }

    /** ミサイル2発が地平線から飛来する。空の演出はここから始まり、飛行時間の後に起爆する */
    private void launchMissiles(Player player) {
        EmpSkySignal.start(player);
        audible.add(player.getUniqueId());
        player.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.MASTER, 0.8f, 0.5f);
        player.sendMessage(Component.text("[クライアント] ", NamedTextColor.GRAY)
                .append(Component.text("何か上がった――ミサイルだ、2発!", NamedTextColor.WHITE)));
        later(player, MISSILE_FLIGHT_TICKS, this::detonate);
    }

    private void detonate(Player player) {
        detonated = true;
        player.stopSound(SOUND_SIREN);
        player.stopSound(SOUND_HUM);
        player.playSound(player, SOUND_EMP_BLAST, SoundCategory.MASTER, 1.0f, 1.0f);
        player.playSound(player, SOUND_EAR_RINGING, SoundCategory.MASTER, 0.9f, 1.0f);
        ScreenEffectService.playEmpFlash(player);
        later(player, SECOND_BLAST_DELAY_TICKS,
                p -> p.playSound(p, SOUND_EMP_BLAST, SoundCategory.MASTER, 0.7f, 0.9f));

        player.sendMessage(Component.text("[クライアント] ", NamedTextColor.GRAY)
                .append(Component.text("上空で閃光――EMPだ、急いで脱出し――", NamedTextColor.WHITE)));
        later(player, ELECTRIC_SHORT_DELAY_TICKS,
                p -> p.playSound(p, SOUND_ELECTRIC_SHORT, SoundCategory.MASTER, 0.8f, 1.0f));
        later(player, STATIC_MESSAGE_DELAY_TICKS,
                p -> p.sendMessage(Component.text("[無線] ……ザザッ……", NamedTextColor.DARK_GRAY)));
        later(player, AMBIENCE_DELAY_TICKS,
                p -> p.playSound(p, SOUND_AMBIENCE, SoundCategory.AMBIENT, 1.0f, 1.0f));
    }

    /** 少し後に、まだ終盤の音を聞かせているプレイヤー (レイドに残っている) にだけ実行する */
    private void later(Player player, int delayTicks, Consumer<Player> action) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player current = Bukkit.getPlayer(id);
            if (current != null && audible.contains(id)) action.accept(current);
        }, delayTicks);
    }

    // TODO: 無線の効果音を用意したら差し替える
    private void playRadioClick(Player player) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.6f, 1.6f);
    }

    /** レイドを抜けた (脱出・死亡・切断) プレイヤーの終盤の音と空の演出を止める */
    private void stopForDeparted(Set<UUID> players) {
        for (UUID id : new ArrayList<>(audible)) {
            if (players.contains(id)) continue;
            audible.remove(id);
            EmpSkySignal.stop(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) stopSounds(player);
        }
    }

    private static void stopSounds(Player player) {
        for (String sound : SOUNDS) {
            player.stopSound(sound);
        }
    }

    /** プレイヤーから見て外周 (マップの中心と反対) の方向の空にサイレンを置く */
    private Location sirenLocation(Player player) {
        Location location = player.getLocation();
        Vector outward = location.toVector().subtract(center()).setY(0);
        if (outward.lengthSquared() < 1.0) outward = randomHorizontal();
        return location.clone().add(outward.normalize().multiply(SIREN_DISTANCE)).add(0, SIREN_HEIGHT, 0);
    }

    /** 外周の方向に照明弾を1発打ち上げる。読み込まれていない場所や、誰かの近くには打たない */
    private void launchFlare(List<Player> online) {
        for (int attempt = 0; attempt < FLARE_ATTEMPTS; attempt++) {
            Player origin = online.get(random.nextInt(online.size()));
            World world = origin.getWorld();
            Vector toward = origin.getLocation().toVector().subtract(center()).setY(0);
            Vector direction = toward.lengthSquared() < 1.0 ? randomHorizontal() : toward.normalize();
            // 外周の方向を中心に ±60度 散らす
            direction.rotateAroundY((random.nextDouble() - 0.5) * Math.toRadians(120));
            double distance = FLARE_MIN_DISTANCE + random.nextDouble() * (FLARE_MAX_DISTANCE - FLARE_MIN_DISTANCE);
            Location target = origin.getLocation().add(direction.multiply(distance));
            int x = target.getBlockX();
            int z = target.getBlockZ();
            if (!world.isChunkLoaded(x >> 4, z >> 4) || isNearAnyone(online, target)) continue;

            Location ground = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            world.spawn(ground, Firework.class, firework -> {
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.fromRGB(255, 60, 40))
                        .withFade(Color.fromRGB(255, 150, 70))
                        .flicker(true)
                        .build());
                meta.setPower(2);
                firework.setFireworkMeta(meta);
            });
            return;
        }
    }

    private static boolean isNearAnyone(List<Player> online, Location target) {
        for (Player player : online) {
            Location location = player.getLocation();
            double dx = location.getX() - target.getX();
            double dz = location.getZ() - target.getZ();
            if (dx * dx + dz * dz < FLARE_CLEARANCE * FLARE_CLEARANCE) return true;
        }
        return false;
    }

    private Vector randomHorizontal() {
        double angle = random.nextDouble() * Math.PI * 2;
        return new Vector(Math.cos(angle), 0, Math.sin(angle));
    }

    /** マップの範囲 (登録済みの全座標を囲む四角形) の中心 */
    private Vector center() {
        if (center != null) return center;
        String worldName = map.getWorldName();
        List<Location> points = new ArrayList<>(map.getSpawnPoints());
        for (RaidMap.ExtractionPoint point : map.getExtractionPoints()) points.add(point.getLocation(worldName));
        for (RaidMap.ScavSpawnPoint point : map.getScavSpawnPoints()) points.add(point.getLocation(worldName));
        for (RaidMap.LootContainer point : map.getLootContainers()) points.add(point.getLocation(worldName));

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Location point : points) {
            if (point == null) continue;
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxZ = Math.max(maxZ, point.getZ());
        }
        center = minX > maxX ? new Vector() : new Vector((minX + maxX) / 2, 0, (minZ + maxZ) / 2);
        return center;
    }

    private static String formatDuration(int seconds) {
        int minutes = seconds / 60;
        int rest = seconds % 60;
        if (minutes == 0) return rest + "秒";
        return rest == 0 ? minutes + "分" : minutes + "分" + rest + "秒";
    }
}
