package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightStage;
import com.lunar_prototype.impossbleEscapeMC.modules.acoustics.FoliageSounds;
import com.lunar_prototype.impossbleEscapeMC.modules.acoustics.FootstepSounds;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC plugin;
    private final PlayerDataModule dataModule;
    private final Map<UUID, Double> walkDistanceMap = new HashMap<>();
    /** 草むら・茂みの中を動いた距離 (かき分ける音を鳴らすまで)。草むらの外では消す */
    private final Map<UUID, Double> foliageDistanceMap = new HashMap<>();
    /** かき分ける音の間隔 (ブロック。走りは長く、しゃがみは短く数える) */
    private static final double RUSTLE_DISTANCE = 1.2;
    private final Map<UUID, Integer> continuousNoiseTicks = new HashMap<>();
    /** 足音をSCAVに最後に知らせたtick。移動イベントは毎tick来るため、知らせる間隔を空ける */
    private final Map<UUID, Integer> lastFootstepAlertTick = new HashMap<>();
    private static final int FOOTSTEP_ALERT_INTERVAL_TICKS = 10;
    /**
     * 足音をSCAVに知らせ始めるまで音を出し続ける時間 (tick)。歩きは2秒続けて初めて気付かれるが、
     * 走る音は大きいので短くする (走って一気に詰めてくる相手に、足音でも気付けないのを防ぐ)
     */
    private static final int FOOTSTEP_ALERT_WALK_TICKS = 40;
    private static final int FOOTSTEP_ALERT_SPRINT_TICKS = 10;
    private final java.util.Random random = new java.util.Random();

    public PlayerListener(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
        this.dataModule = plugin.getServiceContainer().get(PlayerDataModule.class);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;

        handleMovement(player, event.getFrom(), event.getTo(), player.isSprinting(), player.isSneaking());
    }

    @EventHandler
    public void onEntityMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        
        ScavController controller = ScavSpawner.getController(mob.getUniqueId());
        if (controller == null) return;

        handleMovement(mob, event.getFrom(), event.getTo(), controller.isSprinting(), false);
    }

    private void handleMovement(LivingEntity entity, Location from, Location to, boolean isSprinting, boolean isSneaking) {
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            continuousNoiseTicks.put(entity.getUniqueId(), 0);
            return;
        }

        // 地面に接しているかチェック (簡易的)
        if (entity.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir())
            return;

        PlayerData data = (entity instanceof Player p) ? dataModule.getPlayerData(p.getUniqueId()) : null;

        double distance = from.distance(to);

        // 倍率設定
        double multiplier;
        if (isSneaking) {
            multiplier = 1.0;
        } else if (isSprinting) {
            multiplier = 2.0;
        } else {
            multiplier = 1.5;
        }

        // 距離を蓄積
        UUID uuid = entity.getUniqueId();
        double totalWalked = walkDistanceMap.getOrDefault(uuid, 0.0) + (distance * multiplier);
        Vector movementDirection = to.toVector().subtract(from.toVector());
        double movementSpeed = movementDirection.length() * 20.0;
        if (movementDirection.lengthSquared() > 0) {
            movementDirection = movementDirection.normalize();
        } else {
            movementDirection = null;
        }

        // 音を出す移動か？
        boolean isMakingNoise = !isSneaking;
        if (isMakingNoise) {
            int ticks = continuousNoiseTicks.getOrDefault(entity.getUniqueId(), 0) + 1;
            continuousNoiseTicks.put(entity.getUniqueId(), ticks);

            // 歩きは2秒、走りは0.5秒以上音を出している場合
            // SCAV自身の足音で仲間のSCAVを警戒させない (足音の効果音はプレイヤー向けにこの後も鳴らす)
            int alertTicks = isSprinting ? FOOTSTEP_ALERT_SPRINT_TICKS : FOOTSTEP_ALERT_WALK_TICKS;
            if (entity instanceof Player p) {   // スキルで気づかれるまで延びる
                alertTicks += (int) SkillModule.value(p, isSprinting ? SkillStat.SPRINT_ALERT_TICKS : SkillStat.WALK_ALERT_TICKS);
            }
            if (ticks >= alertTicks && !ScavSpawner.isScav(entity.getUniqueId())) {
                int now = org.bukkit.Bukkit.getCurrentTick();
                Integer last = lastFootstepAlertTick.get(entity.getUniqueId());
                if (last == null || now - last >= FOOTSTEP_ALERT_INTERVAL_TICKS) {
                    lastFootstepAlertTick.put(entity.getUniqueId(), now);
                    alertNearbyScavsOfFootsteps(entity, data, movementDirection, movementSpeed, ticks, totalWalked, isSprinting, isSneaking);
                }
            }
        } else {
            continuousNoiseTicks.put(entity.getUniqueId(), 0);
        }

        if (totalWalked > 3.0) {
            // 足元の材質と場所の響きで鳴らし分ける (modules/acoustics)。しゃがみは小さく、走りは大きく、重いほど大きい
            // (音量が 1 を超えると、聞こえる距離が延びる)
            float volume = isSneaking ? 0.35f : isSprinting ? 1.0f : 0.8f;
            if (entity instanceof Player p && !isSneaking && !isSprinting) {   // 歩きの足音はスキルで小さくなる
                volume = (float) Math.max(0.1, volume + SkillModule.value(p, SkillStat.WALK_STEP_VOLUME));
            }
            if (data != null) {
                WeightStage stage = entity instanceof Player p ? SkillModule.weightStage(p, data.getCurrentWeight()) : data.getWeightStage();
                volume *= stage.getFootstepVolumeMultiplier(data.getCurrentWeight());
            }
            float pitch = 0.95f + random.nextFloat() * 0.1f;
            FootstepSounds.play(entity, entity instanceof Player ? SoundCategory.PLAYERS : SoundCategory.HOSTILE, volume, pitch);
            totalWalked = 0;
        }

        walkDistanceMap.put(uuid, totalWalked);

        // 草むら・茂みをかき分ける音。入った時にすぐ鳴らし、中では一定の距離ごとに鳴らす
        String foliage = FoliageSounds.kindAround(entity);
        if (foliage != null) {
            // かき分ける音の間隔はスキルで延びる
            double rustleDistance = RUSTLE_DISTANCE + (entity instanceof Player p ? SkillModule.value(p, SkillStat.RUSTLE_DISTANCE) : 0);
            double rustle = foliageDistanceMap.getOrDefault(uuid, rustleDistance)
                    + distance * (isSneaking ? 0.6 : isSprinting ? 1.3 : 1.0);
            if (rustle >= rustleDistance) {
                float volume = isSneaking ? 0.2f : isSprinting ? 0.85f : 0.55f;
                float pitch = 0.9f + random.nextFloat() * 0.2f;
                FoliageSounds.play(entity, foliage, entity instanceof Player ? SoundCategory.PLAYERS : SoundCategory.HOSTILE, volume, pitch);
                rustle = 0;
            }
            foliageDistanceMap.put(uuid, rustle);
        } else {
            foliageDistanceMap.remove(uuid);
        }
    }

    private void alertNearbyScavsOfFootsteps(LivingEntity source, PlayerData data, Vector movementDirection, double movementSpeed,
                                             int continuousTicks, double walkedDistance, boolean sprinting, boolean sneaking) {
        // 足音で SCAV を呼ぶ範囲 (スキルで狭まる)
        double baseRadius = Math.max(4, 32.0 + (source instanceof Player p ? SkillModule.value(p, SkillStat.FOOTSTEP_ALERT_RADIUS) : 0));
        double radius = baseRadius;
        
        if (data != null) {
            radius *= data.getWeightStage().getFootstepAlertDistanceMultiplier(data.getCurrentWeight());
        }

        // 周囲32ブロック(補正後)以内のSCAVに通知
        for (org.bukkit.entity.Entity entity : source.getNearbyEntities(radius, radius / 2, radius)) {
            if (entity instanceof Mob mob) {
                ScavController controller = ScavSpawner.getController(mob.getUniqueId());
                if (controller != null && !mob.equals(source)) {
                    controller.onSoundHeard(ScavController.SoundContact.footstep(
                            source.getLocation(),
                            movementDirection,
                            movementSpeed > 0 ? movementSpeed : 0.1,
                            sprinting,
                            sneaking,
                            continuousTicks,
                            walkedDistance
                    ));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation();

        // 1か2をランダムに決定
        String soundName = random.nextBoolean() ? "custom.deathvoice1" : "custom.deathvoice2";

        // サーバー全体（その場にいる全員）に聞こえるように再生
        // 引数: 位置, サウンド名, カテゴリ, 音量, ピッチ
        deathLoc.getWorld().playSound(
                deathLoc,
                soundName,
                org.bukkit.SoundCategory.PLAYERS,
                1.0f,
                1.0f);

        if (plugin.getRaidModule().isInRaid(player)) {
            // レイド中の死亡なら死体を生成
            plugin.getCorpseManager().spawnCorpse(player);
            // バニラのドロップを消去
            event.getDrops().clear();
        }

        plugin.getRaidModule().onPlayerDeath(player);
        plugin.getMinigameManager().onPlayerDeath(player);
        clearMovementState(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player.hasMetadata("raid_death_failure")) {
            player.removeMetadata("raid_death_failure", plugin);

            // 脱出失敗（死亡）時はメインワールドの初期スポーン地点へ戻す
            event.setRespawnLocation(org.bukkit.Bukkit.getWorlds().get(0).getSpawnLocation());

            // テレポート（リスポーン）後にエフェクトを適用
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        plugin.getRaidModule().applyFailureEffect(player);
                    }
                }
            }.runTaskLater(plugin, 5); // 0.25秒後に実行
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        plugin.getRaidModule().onPlayerQuit(event.getPlayer());
        plugin.getMinigameManager().onPlayerQuit(event.getPlayer());
        if (plugin.getPartyManager() != null) {
            plugin.getPartyManager().leaveParty(event.getPlayer());
        }
        clearMovementState(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // 除外対象のチェック
        // 1. 防具立てを除外
        if (entity instanceof org.bukkit.entity.ArmorStand)
            return;

        // 2. プレイヤーは先ほどの PlayerDeathEvent で処理しても良いですが、
        // ここで一括管理するなら以下のように書けます

        // 3. 非生物的なもの（もし他にあれば）を除外
        // 額縁などは LivingEntity ではないので、この時点で自動的に除外されています

        Location deathLoc = entity.getLocation();

        // 1か2をランダムに決定
        String soundName = random.nextBoolean() ? "custom.deathvoice1" : "custom.deathvoice2";

        // その場で音を再生
        deathLoc.getWorld().playSound(
                deathLoc,
                soundName,
                org.bukkit.SoundCategory.HOSTILE, // モブならHOSTILEカテゴリが適切
                1.0f,
                1.0f);
    }

    @EventHandler
    public void onScavTakenDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Mob scav)) return;

        // ScavSpawnerからコントローラーを取得
        ScavController controller = ScavSpawner.getController(scav.getUniqueId());

        if (controller != null) {
            // 被弾ダメージを負の報酬として与える
            float penalty = (float) event.getFinalDamage();
            controller.getBrain().reward(-penalty * 0.5f);

            String raidSessionId = ScavSpawner.getRaidSessionId(scav.getUniqueId());
            if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
                java.util.Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("finalDamage", event.getFinalDamage());
                if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent nabe) {
                    payload.put("attackerId", nabe.getDamager().getUniqueId().toString());
                }
                plugin.getAiRaidLogger().logEvent(raidSessionId, scav.getUniqueId(), "TOOK_DAMAGE", payload);
            }
            
            // 攻撃者の方を向く
            if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent nabe) {
                controller.onDamage(nabe.getDamager());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBulletHit(BulletHitEvent event) {
        if (!(event.getShooter() instanceof Mob shooterMob)) return;

        ScavController shooterController = ScavSpawner.getController(shooterMob.getUniqueId());
        if (shooterController == null) return;

        shooterController.onBulletHitDealt(
                event.getVictim(),
                event.getDamage(),
                event.getRawDamage(),
                event.isPenetrated(),
                event.getHitLocation()
        );

        String raidSessionId = ScavSpawner.getRaidSessionId(shooterMob.getUniqueId());
        if (raidSessionId != null && plugin.getAiRaidLogger() != null && plugin.getAiRaidLogger().isEnabled()) {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("victimId", event.getVictim().getUniqueId().toString());
            payload.put("damage", event.getDamage());
            payload.put("hitLocation", event.getHitLocation());
            payload.put("penetrated", event.isPenetrated());
            payload.put("ammoClass", event.getAmmoClass());
            plugin.getAiRaidLogger().logEvent(raidSessionId, shooterMob.getUniqueId(), "SHOT_HIT", payload);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShootBow(org.bukkit.event.entity.EntityShootBowEvent event) {
        // 1. まずはイベントをキャンセル
        event.setCancelled(true);

        // 2. [重要] すでに生成されてしまった「矢」をこの世から消す
        // これをやらないと、一瞬だけクライアント側にエンティティが残ることがあります
        if (event.getProjectile() != null) {
            event.getProjectile().remove();
        }

        ItemStack item = event.getBow();
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof CrossbowMeta crossbowMeta) {
            // 装填済みにしてモデルを維持
            crossbowMeta.addChargedProjectile(new ItemStack(Material.ARROW));
            item.setItemMeta(crossbowMeta);
        }

        // 3. 実行者がプレイヤーの場合、腕の振りを止めたり、アイテムの状態を同期させる
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            // 装填されたままにする（撃ったことにしてアイテムを消費させない）ための処理
            // 必要に応じて、インベントリを更新してクライアントと同期を強制する
            player.updateInventory();
        }
    }

    private void clearMovementState(UUID uuid) {
        walkDistanceMap.remove(uuid);
        continuousNoiseTicks.remove(uuid);
        lastFootstepAlertTick.remove(uuid);
    }
}
