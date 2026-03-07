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
    private final Map<UUID, Double> walkDistanceMap = new HashMap<>();
    private final Map<UUID, Integer> continuousNoiseTicks = new HashMap<>();
    private final java.util.Random random = new java.util.Random();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR)
            return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            continuousNoiseTicks.put(player.getUniqueId(), 0); // 止まればリセット
            return;
        }

        // 空中にいる（落下中）ならカウントしない
        if (player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir())
            return;

        // 音を出す移動か？
        boolean isMakingNoise = !player.isSneaking();
        if (isMakingNoise) {
            int ticks = continuousNoiseTicks.getOrDefault(player.getUniqueId(), 0) + 1;
            continuousNoiseTicks.put(player.getUniqueId(), ticks);

            // 2秒(40ticks)以上音を出している場合
            if (ticks >= 40) {
                alertNearbyScavsOfFootsteps(player);
            }
        } else {
            continuousNoiseTicks.put(player.getUniqueId(), 0);
        }

        double distance = from.distance(to);

        // 状態に応じた倍率設定 (Skriptの add 1.5, 2, 1 に対応)
        double multiplier;
        if (player.isSneaking()) {
            multiplier = 1.0; // スニーク
        } else if (player.isSprinting()) {
            multiplier = 2.0; // ダッシュ
        } else {
            multiplier = 1.5; // 通常歩き
        }

        // 距離を蓄積
        UUID uuid = player.getUniqueId();
        double totalWalked = walkDistanceMap.getOrDefault(uuid, 0.0) + (distance * multiplier);

        // しきい値チェック (Skriptの 10 に相当。Javaの座標系だと 2.5〜3.0 くらいが適切)
        if (totalWalked > 3.0) {
            // 足音を再生
            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.BLOCK_STONE_STEP, // Skriptの stone.fall より step の方が足音らしいです
                    0.8f, // 音量
                    1.0f // ピッチ
            );

            // カウントをリセット
            totalWalked = 0;
        }

        walkDistanceMap.put(uuid, totalWalked);
    }

    private void alertNearbyScavsOfFootsteps(Player player) {
        // 周囲32ブロック以内のSCAVに通知
        for (org.bukkit.entity.Entity entity : player.getNearbyEntities(32, 16, 32)) {
            if (entity instanceof Mob mob) {
                ScavController controller = ScavSpawner.getController(mob.getUniqueId());
                if (controller != null) {
                    controller.onSoundHeard(player.getLocation());
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
}
