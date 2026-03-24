package com.lunar_prototype.impossbleEscapeMC.modules.weight;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;

public class WeightModule implements IModule, Listener {
    private ImpossbleEscapeMC plugin;
    private PlayerDataModule dataModule;
    private BackpackModule backpackModule;
    private static final NamespacedKey SPEED_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "weight_speed_penalty");
    private static final NamespacedKey GRAVITY_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "weight_gravity_penalty");
    private final Map<UUID, WeightStage> lastStages = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        this.dataModule = container.get(PlayerDataModule.class);
        this.backpackModule = container.get(BackpackModule.class);

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 定期的な重量計算とステータス更新タスク (1秒ごと)
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllPlayers, 20L, 20L);
    }

    @Override
    public void onDisable() {
        // 速度補正をリセット
        for (Player player : Bukkit.getOnlinePlayers()) {
            resetAttributes(player);
        }
        lastStages.clear();
    }

    private void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerWeight(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updatePlayerWeight(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            // インベントリ操作の直後だとアイテムが移動完了していない場合があるため、1tick後に更新
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerWeight(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerWeight(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> updatePlayerWeight(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> updatePlayerWeight(event.getPlayer()));
    }

    /**
     * プレイヤーの総重量を再計算し、ステータスを更新する
     */
    public void updatePlayerWeight(Player player) {
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data == null) return;

        int totalWeight = calculateTotalWeight(player);
        data.setCurrentWeight(totalWeight); // setCurrentWeight内でStageも自動更新される

        WeightStage currentStage = data.getWeightStage();
        WeightStage lastStage = lastStages.get(player.getUniqueId());

        if (lastStage != currentStage) {
            lastStages.put(player.getUniqueId(), currentStage);
            sendStageNotification(player, currentStage);
        }

        applyWeightEffects(player, data);
    }

    private void sendStageNotification(Player player, WeightStage stage) {
        if (stage == WeightStage.NORMAL && lastStages.get(player.getUniqueId()) == null) return;

        player.sendActionBar(net.kyori.adventure.text.Component.text("Weight Stage: ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(stage.getDisplayName(), stage.getColor())));
    }

    private int calculateTotalWeight(Player player) {
        int total = 0;
        // メインインベントリ（ホットバー含む）
        for (ItemStack item : player.getInventory().getContents()) {
            total += getItemWeight(item);
            if (backpackModule != null && backpackModule.isBackpackItem(item)) {
                total += backpackModule.calculateEffectiveContentsWeight(item);
            }
        }
        return total;
    }

    private int getItemWeight(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return 0;
        int weightPerItem = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(PDCKeys.ITEM_WEIGHT, PDCKeys.INTEGER, 0);
        return weightPerItem * item.getAmount();
    }

    private void applyWeightEffects(Player player, PlayerData data) {
        int weight = data.getCurrentWeight();

        // 1. 移動速度の補正
        updateSpeedModifier(player, weight);

        // 2. ダッシュ制限 (Critical: 35kg〜)
        if (weight >= 35000) {
            if (player.isSprinting()) {
                player.setSprinting(false);
            }
        }
        
        // 3. ジャンプ力(重力)の補正
        updateGravityModifier(player, weight);
    }

    private void updateSpeedModifier(Player player, int weight) {
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed == null) return;

        // 既存の重量補正を削除
        moveSpeed.removeModifier(SPEED_MODIFIER_KEY);

        double penalty = 0.0;
        // 15kg (Normalの上限) から速度ペナルティを開始
        if (weight > 15000) {
            // 以前は40kgで-20%, 50kgで停止(-100%)だったが、これを半分に緩和
            // 15kg〜50kgの間で線形に増加し、50kgで最大-50%にする
            double ratio = Math.min(1.0, (double) (weight - 15000) / (50000 - 15000));
            penalty = -ratio * 0.5; // 最大で移動速度 -50% (以前は最大-100%)
        }

        if (penalty != 0.0) {
            moveSpeed.addModifier(new AttributeModifier(SPEED_MODIFIER_KEY, penalty, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private void updateGravityModifier(Player player, int weight) {
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity == null) return;

        gravity.removeModifier(GRAVITY_MODIFIER_KEY);

        // 15kg (Normalの上限) から重力ペナルティを開始
        if (weight > 15000) {
            // 15,001g -> 50,000g (ハード上限) の範囲で線形に増加
            // 以前は最大 0.2 だったものを半分 (0.1) に緩和
            double ratio = Math.min(1.0, (double) (weight - 15000) / (50000 - 15000));
            double penalty = ratio * 0.1; // Max penalty of +0.1 makes jumping still possible even at high weight

            gravity.addModifier(new AttributeModifier(GRAVITY_MODIFIER_KEY, penalty, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void resetAttributes(Player player) {
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(SPEED_MODIFIER_KEY);
        }
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(GRAVITY_MODIFIER_KEY);
        }
    }

    @EventHandler
    public void onJump(PlayerJumpEvent event) {
        // ジャンプ力の制御はAttributeで行うため、イベントキャンセルは不要
    }
}
