package com.lunar_prototype.impossbleEscapeMC.modules.stamina;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightStage;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillModule;
import com.lunar_prototype.impossbleEscapeMC.modules.skill.SkillStat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
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
    private static final float JUMP_COST = 2.0f;
    // Recovery
    private static final float RECOVERY_PER_TICK = 12.0f / 20.0f;
    private static final long BASE_RECOVERY_DELAY_MS = 1000;
    private static final long EXHAUSTION_PENALTY_MS = 2000;

    /** 診断用の固定条件。スキル・興奮剤なし。ゲーム本体と同じ定数・重量段階を使う。 */
    public static StaminaTravelModel diagnosticTravel(int weightGrams, int food, double reserve,
                                                      double sprintSpeed, double walkSpeed) {
        WeightStage stage = WeightStage.getFromWeight(weightGrams);
        double speedMultiplier = com.lunar_prototype.impossbleEscapeMC.modules.weight.WeightModule.baseSpeedMultiplier(weightGrams);
        return new StaminaTravelModel(MAX_STAMINA,
                SPRINT_COST_PER_TICK * 20 * stage.getStaminaCostMultiplier(weightGrams),
                RECOVERY_PER_TICK * 20 * (.3 + .7 * Math.clamp(food, 0, 20) / 20.0),
                (BASE_RECOVERY_DELAY_MS + stage.getStaminaRecoveryDelayPenalty(weightGrams)) / 1000.0,
                reserve, sprintSpeed * speedMultiplier, walkSpeed * speedMultiplier, weightGrams < 35000);
    }

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
            clearLegacyGravityPenalty(player);
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data == null) continue;

            // Allow sprinting even at low hunger by keeping food level at least at 7 if stamina is available
            if (data.getStamina() > 0 && player.getFoodLevel() < 7) {
                player.setFoodLevel(7);
            }

            boolean isConsuming = false;

            // --- Consumption ---
            if (player.isSprinting()) {
                handleStaminaConsumption(player, data, (float) (SPRINT_COST_PER_TICK * SkillModule.multiplier(player, SkillStat.SPRINT_COST)));
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

            // --- Experience Bar Display ---
            updateExperienceBar(player, data);
        }
    }

    /** スタミナの上限 (スキルで増える) */
    private static float maxStamina(Player player) {
        return (float) (MAX_STAMINA + SkillModule.value(player, SkillStat.STAMINA_MAX));
    }

    /**
     * スタミナは PlayerData では 0〜100 (割合) で持つ。上限が m に増えた人は、減り方・回復を m / 100 で割って入れる
     * (同じ量を使っても割合では少なく減る = 上限が増えたのと同じ)
     */
    private static float scale(Player player) {
        return maxStamina(player) / MAX_STAMINA;
    }

    private void updateExperienceBar(Player player, PlayerData data) {
        float ratio = data.getStamina() / MAX_STAMINA;
        player.setExp(Math.max(0.0f, Math.min(0.999f, ratio)));
        player.setLevel(0);
    }

    private void handleStaminaConsumption(Player player, PlayerData data, float amount) {
        if (data.getStamina() <= 0) return;

        int weight = data.getCurrentWeight();
        WeightStage stage = SkillModule.weightStage(player, weight);
        double multiplier = stage.getStaminaCostMultiplier(rampWeight(player, stage, weight));
        if (stage == WeightStage.CRITICAL) multiplier += SkillModule.value(player, SkillStat.CRITICAL_COST);
        // 上限が増えた人は、同じ量を使っても割合では少なく減る
        float finalCost = (float) (amount * multiplier / scale(player));

        data.setStamina(data.getStamina() - finalCost);
        data.setLastStaminaActionTime(System.currentTimeMillis());
    }

    private void handleStaminaRecovery(Player player, PlayerData data) {
        int weight = data.getCurrentWeight();
        WeightStage stage = SkillModule.weightStage(player, weight);
        long recoveryDelay = BASE_RECOVERY_DELAY_MS + (long) SkillModule.value(player, SkillStat.STAMINA_DELAY_MS)
                + stage.getStaminaRecoveryDelayPenalty(rampWeight(player, stage, weight));
        if (data.isExhausted()) {
            recoveryDelay += EXHAUSTION_PENALTY_MS + (long) SkillModule.value(player, SkillStat.EXHAUSTION_MS);
        }

        if (System.currentTimeMillis() - data.getLastStaminaActionTime() > recoveryDelay) {
            float recoveryMultiplier = 1.0f; // Walking
            if (!player.isSprinting() && player.isSneaking()) {
                recoveryMultiplier = (float) (1.2 + SkillModule.value(player, SkillStat.CROUCH_RECOVERY)); // Crouching
            } else if (player.getVelocity().lengthSquared() < 0.001) {
                recoveryMultiplier = 1.5f; // Standing still
            }

            // --- Hunger Penalty ---
            // Scaled from 1.0x at 20 food to ~0.3x at 0 food
            // (スキルで下限が上がる: 0.3 + v から 1.0 まで)
            float floor = (float) Math.min(1.0, 0.3 + SkillModule.value(player, SkillStat.FOOD_RECOVERY_FLOOR));
            float foodMultiplier = floor + ((1.0f - floor) * (player.getFoodLevel() / 20.0f));

            float finalRecovery = (float) (RECOVERY_PER_TICK * recoveryMultiplier * foodMultiplier
                    * SkillModule.multiplier(player, SkillStat.STAMINA_RECOVERY) / scale(player));
            data.setStamina(data.getStamina() + finalRecovery);
        }
    }

    /** 「重い」の負担が増え始める重さ (20kg) をスキルで遅らせる: 負担の計算に使う重さを、その分だけ軽くする */
    private static int rampWeight(Player player, WeightStage stage, int weight) {
        if (stage != WeightStage.HEAVY) return weight;
        return Math.max(0, weight - (int) SkillModule.value(player, SkillStat.HEAVY_RAMP_START_G));
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data == null) return;

        handleStaminaConsumption(player, data, (float) Math.max(0, JUMP_COST + SkillModule.value(player, SkillStat.JUMP_COST)));
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

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data != null && data.getStamina() > 0 && event.getFoodLevel() < 7) {
            event.setFoodLevel(7);
        }
    }

    private void applyExhaustionEffect(Player player, PlayerData data) {
        data.setExhausted(true);
        // Apply speed reduction
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            double slow = Math.min(0, -0.2 + SkillModule.value(player, SkillStat.EXHAUSTED_SLOW));
            moveSpeed.addModifier(new AttributeModifier(EXHAUSTION_SPEED_MODIFIER_KEY, slow, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private void removeExhaustionEffect(Player player) {
        // Remove speed reduction
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(EXHAUSTION_SPEED_MODIFIER_KEY);
        }
        clearLegacyGravityPenalty(player);
    }

    /** 旧バージョンで付いたスタミナ切れの重力補正だけを解除する。 */
    private void clearLegacyGravityPenalty(Player player) {
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity != null && gravity.getModifier(EXHAUSTION_GRAVITY_MODIFIER_KEY) != null) {
            gravity.removeModifier(EXHAUSTION_GRAVITY_MODIFIER_KEY);
        }
    }
}
