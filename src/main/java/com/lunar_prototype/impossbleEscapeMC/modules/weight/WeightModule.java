package com.lunar_prototype.impossbleEscapeMC.modules.weight;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.core.IModule;
import com.lunar_prototype.impossbleEscapeMC.core.ServiceContainer;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerData;
import com.lunar_prototype.impossbleEscapeMC.modules.core.PlayerDataModule;
import com.lunar_prototype.impossbleEscapeMC.modules.backpack.BackpackModule;
import com.lunar_prototype.impossbleEscapeMC.modules.rig.RigModule;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import org.bukkit.event.player.PlayerInteractEvent; // Import for PlayerInteractEvent
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound; // Import for Sound

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component; // Import for Component
import net.kyori.adventure.text.format.NamedTextColor; // Import for NamedTextColor

public class WeightModule implements IModule, Listener {
    private ImpossbleEscapeMC plugin;
    private PlayerDataModule dataModule;
    private BackpackModule backpackModule;
    private RigModule rigModule;
    private static final NamespacedKey SPEED_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "weight_speed_penalty");
    private static final NamespacedKey GRAVITY_MODIFIER_KEY = new NamespacedKey("impossbleescapemc", "weight_gravity_penalty");
    private final Map<UUID, WeightStage> lastStages = new HashMap<>();

    @Override
    public void onEnable(ServiceContainer container) {
        this.plugin = ImpossbleEscapeMC.getInstance();
        this.dataModule = container.get(PlayerDataModule.class);
        this.backpackModule = container.get(BackpackModule.class);
        this.rigModule = container.get(RigModule.class);

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

    @EventHandler
    public void onPlayerUseExcitant(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !event.getAction().isRightClick()) return;
        if (!item.hasItemMeta()) return;

        String itemId = item.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);

        if ("EXCITANT".equals(itemId)) {
            PlayerData data = dataModule.getPlayerData(player.getUniqueId());
            if (data != null) {
                if (data.isExcitantActive()) {
                    player.sendActionBar(Component.text("興奮剤はすでに効果を発揮しています！", NamedTextColor.YELLOW));
                    event.setCancelled(true);
                    return;
                }
                data.activateExcitant(60 * 1000L); // 60秒間効果持続
                item.subtract(1); // アイテムを1つ消費
                player.sendActionBar(Component.text("興奮剤を摂取しました！重量許容量が一時的に増加 (-15kg)", NamedTextColor.BLUE));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                // 即座に重量を更新してフィードバック
                updatePlayerWeight(player);
            }
            event.setCancelled(true); // デフォルトのアイテム使用を防ぐ
        }
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

        for (int slot = 0; slot <= 8; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            total += getItemWeight(item);
            if (backpackModule != null && backpackModule.isBackpackItem(item)) {
                total += backpackModule.calculateEffectiveContentsWeight(item);
            }
        }

        int unlockedEnd = -1;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            unlockedEnd = 35;
        } else if (rigModule != null) {
            unlockedEnd = rigModule.getUnlockedMainInventoryEndSlot(player);
        }

        for (int slot = 9; slot <= 35; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            if (slot <= unlockedEnd && rigModule != null && rigModule.isRestrictedMode(player)) {
                if (backpackModule != null && backpackModule.isBackpackItem(item)) {
                    total += backpackModule.calculateEffectiveContentsWeight(item);
                }
                continue;
            }

            total += getItemWeight(item);
            if (backpackModule != null && backpackModule.isBackpackItem(item)) {
                total += backpackModule.calculateEffectiveContentsWeight(item);
            }
        }

        if (rigModule != null && rigModule.isRestrictedMode(player)) {
            total += rigModule.calculateEffectiveUnlockedInventoryWeight(player);
        }

        for (int slot = 36; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            total += getItemWeight(item);
            if (backpackModule != null && backpackModule.isBackpackItem(item)) {
                total += backpackModule.calculateEffectiveContentsWeight(item);
            }
        }

        // 興奮剤の効果を適用
        PlayerData data = dataModule.getPlayerData(player.getUniqueId());
        if (data != null && data.isExcitantActive()) {
            total -= 15000; // 15kg (15000グラム) 軽減
            if (total < 0) total = 0; // 重量が負にならないようにする
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
        
        // 3. 重力補正の解除（以前の仕様からのクリーンアップ）
        AttributeInstance gravity = player.getAttribute(Attribute.GRAVITY);
        if (gravity != null) {
            gravity.removeModifier(GRAVITY_MODIFIER_KEY);
        }
    }

    private void updateSpeedModifier(Player player, int weight) {
        AttributeInstance moveSpeed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (moveSpeed == null) return;

        // 既存の重量補正を削除
        moveSpeed.removeModifier(SPEED_MODIFIER_KEY);

        double penalty = 0.0;
        final int SPEED_DEBUFF_START_WEIGHT = 40000; // 40kg
        final int SPEED_DEBUFF_MAX_WEIGHT = 60000; // 60kg
        final double MAX_SPEED_PENALTY = 0.75; // -75% speed reduction

        if (weight > SPEED_DEBUFF_START_WEIGHT) {
            double ratio = Math.min(1.0, (double) (weight - SPEED_DEBUFF_START_WEIGHT) / (SPEED_DEBUFF_MAX_WEIGHT - SPEED_DEBUFF_START_WEIGHT));
            penalty = -ratio * MAX_SPEED_PENALTY;
        }

        if (penalty != 0.0) {
            moveSpeed.addModifier(new AttributeModifier(SPEED_MODIFIER_KEY, penalty, AttributeModifier.Operation.ADD_SCALAR));
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
