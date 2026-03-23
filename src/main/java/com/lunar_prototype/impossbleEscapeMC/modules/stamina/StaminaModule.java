package com.lunar_prototype.impossbleEscapeMC.modules.stamina;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightStage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;

import java.util.UUID;

public class StaminaModule implements IModule, Listener {

    private ImpossbleEscapeMC plugin;
    private PlayerDataModule dataModule;
    private static final NamespacedKey EXHAUSTION_SPEED_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "exhaustion_speed_penalty");
    private static final NamespacedKey EXHAUSTION_GRAVITY_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "exhaustion_gravity_penalty");

    // --- Constants ---
    private static final float MAX_STAMINA = 100.0f;
    // Consumption (per tick)
    private static final float SPRINT_COST_PER_TICK = 4.0f / 20.0f;
    private static final float JUMP_COST = 4.0f;
    // Recovery
    private static final float RECOVERY_PER_TICK = 12.0f / 20.0f;
    private static final long BASE_RECOVERY_DELAY_MS = 1500;
    private static final long EXHAUSTION_PENALTY_MS = 2000;

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        this.dataModule = container.get(PlayerDataModule.class);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeExhaustionEffect(player);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data == null) continue;

            boolean isConsuming = false;

            // --- Consumption ---
            if (player.isSprinting()) {
                handleStaminaConsumption(data, SPRINT_COST_PER_TICK);
                isConsuming = true;
            }

            // --- Recovery ---
            if (!isConsuming) {
                handleStaminaRecovery(player, data);
            }

            // --- Exhaustion Check ---
            if (data.getStamina() <= 0 && !data.isExhausted()) {
                applyExhaustionEffect(player, data);
            } else if (data.getStamina() > 0 && data.isExhausted()) {
                removeExhaustionEffect(player);
                data.setExhausted(false);
            }
        }
    }

    private void handleStaminaConsumption(PlayerData data, float amount) {
        if (data.getStamina() <= 0) return;

        double multiplier = data.getWeightStage().getStaminaCostMultiplier(data.getCurrentWeight());
        float finalCost = (float) (amount * multiplier);

        data.setStamina(data.getStamina() - finalCost);
        data.setLastStaminaActionTime(System.currentTimeMillis());
    }

    private void handleStaminaRecovery(Player player, PlayerData data) {
        long recoveryDelay = BASE_RECOVERY_DELAY_MS + data.getWeightStage().getStaminaRecoveryDelayPenalty(data.getCurrentWeight());
        if (data.isExhausted()) {
            recoveryDelay += EXHAUSTION_PENALTY_MS;
        }

        if (System.currentTimeMillis() - data.getLastStaminaActionTime() > recoveryDelay) {
            float recoveryMultiplier = 1.0f; // Walking
            if (!player.isSprinting() && player.isSneaking()) {
                recoveryMultiplier = 1.2f; // Crouching
            } else if (player.getVelocity().lengthSquared() < 0.001) {
                recoveryMultiplier = 1.5f; // Standing still
            }

            float finalRecovery = RECOVERY_PER_TICK * recoveryMultiplier;
            data.setStamina(data.getStamina() + finalRecovery);
        }
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data == null) return;

        if (data.getStamina() < JUMP_COST) {
            event.setCancelled(true);
            return;
        }
        handleStaminaConsumption(data, JUMP_COST);
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;

        Player player = event.getPlayer();
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data != null && data.getStamina() <= 0) {
            event.setCancelled(true);
        }
    }

    private void applyExhaustionEffect(Player player, PlayerData data) {
        data.setExhausted(true);
        // Apply speed reduction
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.addModifier(new AttributeModifier(EXHAUSTION_SPEED_MODIFIER_KEY, -0.2, AttributeModifier.Operation.ADD_SCALAR));
        }
        // Apply gravity penalty (halve jump height)
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            gravity.addModifier(new AttributeModifier(EXHAUSTION_GRAVITY_MODIFIER_KEY, 0.08, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void removeExhaustionEffect(Player player) {
        // Remove speed reduction
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(EXHAUSTION_SPEED_MODIFIER_KEY);
        }
        // Remove gravity penalty
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(EXHAUSTION_GRAVITY_MODIFIER_KEY);
        }
    }
}
