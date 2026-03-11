package com.lunar_prototype.impossbleEscapeMC.listener;

import com.lunar_prototype.impossbleEscapeMC.ai.ScavController;
import com.lunar_prototype.impossbleEscapeMC.ai.ScavSpawner;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC plugin;
    private final Map<UUID, Double> walkDistanceMap = new HashMap<>();
    private final Map<UUID, Integer> continuousNoiseTicks = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

    public PlayerListener(com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC plugin) {
        this.plugin = plugin;
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

        // 音を出す移動か？
        boolean isMakingNoise = !isSneaking;
        if (isMakingNoise) {
            int ticks = continuousNoiseTicks.getOrDefault(entity.getUniqueId(), 0) + 1;
            continuousNoiseTicks.put(entity.getUniqueId(), ticks);

            // 2秒(40ticks)以上音を出している場合
            if (ticks >= 40) {
                alertNearbyScavsOfFootsteps(entity);
            }
        } else {
            continuousNoiseTicks.put(entity.getUniqueId(), 0);
        }

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

        if (totalWalked > 3.0) {
            entity.getWorld().playSound(
                    entity.getLocation(),
                    Sound.BLOCK_STONE_STEP,
                    0.8f,
                    1.0f
            );
            totalWalked = 0;
        }

        walkDistanceMap.put(uuid, totalWalked);
    }

    private void alertNearbyScavsOfFootsteps(LivingEntity source) {
        // 周囲32ブロック以内のSCAVに通知
        for (org.bukkit.entity.Entity entity : source.getNearbyEntities(32, 16, 32)) {
            if (entity instanceof Mob mob) {
                ScavController controller = ScavSpawner.getController(mob.getUniqueId());
                if (controller != null && !mob.equals(source)) {
                    controller.onSoundHeard(source.getLocation());
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
            
            // 攻撃者の方を向く
            if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent nabe) {
                controller.onDamage(nabe.getDamager());
            }
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
    }
}
