package com.lunar_prototype.impossbleEscapeMC.modules.medical;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.api.event.BulletHitEvent;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

/**
 * 状態異常（骨折、出血、鎮痛効果）のロジックを専門に管理するクラス
 */
public class StatusEffectManager implements Listener {
    private final ImpossbleEscapeMC plugin;
    private final PlayerDataModule dataModule;

    public StatusEffectManager(ImpossbleEscapeMC plugin, PlayerDataModule dataModule) {
        this.plugin = plugin;
        this.dataModule = dataModule;
    }

    /**
     * 全オンラインプレイヤーの状態異常を更新 (1秒周期)
     */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data == null) continue;

            handleBleeding(player, data);
            handleFractureEffects(player, data);
            handlePainkillerExpiry(player, data);
        }
    }

    private void handleBleeding(Player player, PlayerData data) {
        if (data.getBleedingLevel() <= 0) return;

        double damage = data.getBleedingLevel() * 1.0;
        player.damage(damage);
        
        // 血のパーティクル
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 5, 0.2, 0.5, 0.2, 
            new Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 1.0f));
        
        player.sendActionBar(Component.text("出血中...", NamedTextColor.RED));
    }

    private void handleFractureEffects(Player player, PlayerData data) {
        // 足の骨折: 走るとダメージ (鎮痛剤なしの場合)
        if (data.hasLegFracture() && player.isSprinting() && !data.isPainkillerActive()) {
            player.damage(0.5);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.8f);
            player.sendActionBar(Component.text("足の骨折: 走るとダメージを受けます！", NamedTextColor.RED));
        }
    }

    private void handlePainkillerExpiry(Player player, PlayerData data) {
        if (data.getPainkillerUntil() > 0 && !data.isPainkillerActive()) {
            data.setPainkillerUntil(0);
            player.sendMessage(Component.text("鎮痛効果が切れました。", NamedTextColor.YELLOW));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
        }
    }

    /**
     * 被弾時の負傷判定と自動鎮痛
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBulletHit(BulletHitEvent event) {
        if (!(event.getVictim() instanceof Player victim)) return;

        PlayerData data = dataModule.getPlayerData(victim.getUniqueId());
        if (data == null) return;

        long now = System.currentTimeMillis();

        // 1. 自動鎮痛効果 (5分クールダウン, 5秒持続)
        if (now - data.getLastPainkillerTrigger() > 300_000) {
            data.setPainkillerUntil(now + 5000);
            data.setLastPainkillerTrigger(now);
            victim.sendMessage(Component.text("アドレナリン放出！ (5秒間の鎮痛効果)", NamedTextColor.AQUA));
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
        }

        // 2. 負傷判定
        applyInjuryChance(victim, data, event.getHitLocation());
    }

    private void applyInjuryChance(Player victim, PlayerData data, String hitLocation) {
        double roll = Math.random();

        // 1. 足の骨折 (足に当たった場合のみ)
        if ("legs".equals(hitLocation) && roll < 0.25) {
            if (!data.hasLegFracture()) {
                data.setLegFracture(true);
                victim.sendMessage(Component.text("足に重傷を負いました！ (骨折)", NamedTextColor.RED));
                victim.playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 0.8f);
            }
        } 
        // 2. 腕の骨折 (腕に当たった場合のみ)
        else if ("arms".equals(hitLocation) && roll < 0.15) {
            if (!data.hasArmFracture()) {
                data.setArmFracture(true);
                victim.sendMessage(Component.text("腕に重傷を負いました！ (骨折)", NamedTextColor.RED));
                victim.playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.2f);
            }
        }

        // 3. 出血 (どの部位でも発生するが、確率は低め)
        if (roll < 0.20) {
            data.setBleedingLevel(data.getBleedingLevel() + 1);
            victim.sendMessage(Component.text("出血しています！", NamedTextColor.RED));
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1.0f, 1.0f);
        }
    }
}
