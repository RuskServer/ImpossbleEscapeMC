package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class WeaponContext {
    private final ImpossbleEscapeMC plugin;
    private final Player player;
    private ItemStack item;
    private GunStats stats;
    private WeaponStateMachine stateMachine;

    // Animation progress
    private double aimProgress = 0.0;
    private double sprintProgress = 0.0;
    private int independentAnimElapsed = -1;

    // Rendering cache
    private String lastModelKey = "";
    private int lastFrame = -1;
    private int lastIndependentFrame = -1;
    private float lastSentWalkSpeed = -1.0f;

    public WeaponContext(ImpossbleEscapeMC plugin, Player player, ItemStack item, GunStats stats) {
        this.plugin = plugin;
        this.player = player;
        this.item = item;
        this.stats = stats;

        // Initialize progress based on current player state
        if (player.isSprinting()) {
            this.sprintProgress = 1.0;
        }
    }

    public ImpossbleEscapeMC getPlugin() {
        return plugin;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getItem() {
        return item;
    }

    public GunStats getStats() {
        return stats;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public void setStats(GunStats stats) {
        this.stats = stats;
    }

    public void setStateMachine(WeaponStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public WeaponStateMachine getStateMachine() {
        return stateMachine;
    }

    // Progress Accessors
    public double getAimProgress() {
        return aimProgress;
    }

    public void setAimProgress(double aimProgress) {
        this.aimProgress = aimProgress;
    }

    public double getSprintProgress() {
        return sprintProgress;
    }

    public void setSprintProgress(double sprintProgress) {
        this.sprintProgress = sprintProgress;
    }

    // Independent Animation Logic
    public void startIndependentAnimation() {
        // 現在のステートが許可されているか確認
        if (stats == null || stats.validIndependentAnimStates == null) return;
        
        String currentStateStr = "IDLE";
        if (sprintProgress > 0) currentStateStr = "SPRINT";
        else if (aimProgress > 0) currentStateStr = "ADS";
        else if (stateMachine != null) {
            WeaponState s = stateMachine.getCurrentState();
            if (s instanceof ReloadingState || s instanceof ShotgunReloadingState) currentStateStr = "RELOAD";
        }

        if (stats.validIndependentAnimStates.contains(currentStateStr)) {
            // 撃った瞬間にアニメーションを0フレーム目から確実に開始させ、射撃間隔と同期させる
            independentAnimElapsed = 0;
            
            // トリガーした直後に即座に描画パケットを送信し、1tickの遅延やジッターを防ぐ
            if (stateMachine != null && stateMachine.getCurrentState() != null) {
                stateMachine.getCurrentState().onUpdate(this);
            }
        }
    }

    public void updateIndependentAnimation() {
        if (independentAnimElapsed >= 0 && stats != null && stats.independentAnimation != null) {
            independentAnimElapsed++;

            // アニメーションの最大フレーム長（Tick）
            double durationTicks = (stats.independentAnimation.frameCount / (stats.independentAnimation.fps * stats.independentAnimation.playbackSpeed)) * 20.0;
            
            // アニメーション終了判定
            if (independentAnimElapsed > durationTicks) {
                // 撃ちきり保持のチェック: チャンバーが空かつマガジンが空ならホールド
                if (isAmmoEmpty()) {
                    independentAnimElapsed = (int) durationTicks; // 最終フレーム付近で止める
                } else {
                    independentAnimElapsed = -1; // 通常通りリセット
                }
            }
        } else {
            // アニメーションが再生されていない、または再開された場合の処理
            if (independentAnimElapsed < 0 && stats != null && stats.independentAnimation != null) {
                // すでにリセット状態だが、もし撃ちきり状態のままならホールド状態を復元（持ち替え後など）
                if (isAmmoEmpty()) {
                    double durationTicks = (stats.independentAnimation.frameCount / (stats.independentAnimation.fps * stats.independentAnimation.playbackSpeed)) * 20.0;
                    independentAnimElapsed = (int) durationTicks;
                }
            }
        }
    }

    private boolean isAmmoEmpty() {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        boolean isChamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;
        int ammo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        return !isChamberLoaded && ammo <= 0;
    }

    public int getIndependentFrameToRender() {
        if (independentAnimElapsed < 0 || stats == null || stats.independentAnimation == null) return 0;
        int frame = (int) ((independentAnimElapsed / 20.0) * stats.independentAnimation.fps * stats.independentAnimation.playbackSpeed);
        return Math.min(frame, stats.independentAnimation.frameCount - 1);
    }

    // Rendering Logic
    public void applyModel(GunStats.AnimationStats anim, int frameIndex) {
        applyLayeredModel(anim, frameIndex, getIndependentFrameToRender());
    }

    public void applyLayeredModel(GunStats.AnimationStats baseAnim, int baseFrame, int independentFrame) {
        if (baseAnim == null)
            return;

        // Cache check
        if (baseAnim.model.equals(lastModelKey) && baseFrame == lastFrame && independentFrame == lastIndependentFrame) {
            return;
        }

        // Apply model
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(baseAnim.model));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addFloat((float) baseFrame)
                .addFloat(0.0f) // 【第2float: 未実装用予約】
                .addFloat((float) independentFrame) // 【第3float: 独立アニメーション】
                .addStrings(getAttachments(item))
                .build());

        lastModelKey = baseAnim.model;
        lastFrame = baseFrame;
        lastIndependentFrame = independentFrame;
    }

    public void resetProgress() {
        this.aimProgress = 0.0;
        this.sprintProgress = player.isSprinting() ? 1.0 : 0.0;
        this.independentAnimElapsed = -1;
        this.resetCache();
    }

    public void resetCache() {
        this.lastModelKey = "";
        this.lastFrame = -1;
        this.lastIndependentFrame = -1;
        this.lastSentWalkSpeed = -1.0f;
    }

    public float getLastSentWalkSpeed() {
        return lastSentWalkSpeed;
    }

    public void setLastSentWalkSpeed(float lastSentWalkSpeed) {
        this.lastSentWalkSpeed = lastSentWalkSpeed;
    }

    private List<String> getAttachments(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return Collections.emptyList();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String joined = pdc.get(PDCKeys.ATTACHMENTS, PDCKeys.STRING);
        if (joined == null || joined.isEmpty())
            return Collections.emptyList();
        return Arrays.asList(joined.split(","));
    }

    // Sound Utility
    public void playSound(Sound sound, float volume, float pitch) {
        player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
    }

    public void playSound(String sound, float volume, float pitch) {
        Sound standardSound = null;
        try {
            NamespacedKey key = sound.contains(":") ?
                    NamespacedKey.fromString(sound.toLowerCase(Locale.ROOT)) :
                    NamespacedKey.minecraft(sound.toLowerCase(Locale.ROOT).replace("_", "."));
            if (key != null) standardSound = Registry.SOUNDS.get(key);
        } catch (Exception ignored) {}

        if (standardSound != null) {
            playSound(standardSound, volume, pitch);
        } else {
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    // Helper to calculate frame index for loop
    public int getLoopFrameIndex(int tick, GunStats.AnimationStats anim) {
        if (anim == null || anim.fps <= 0 || anim.frameCount <= 1)
            return 0;
        return (int) ((tick / 20.0) * anim.fps * anim.playbackSpeed) % anim.frameCount;
    }

    // Helper to calculate frame index for progress (0.0 - 1.0)
    public int getProgressFrameIndex(double progress, GunStats.AnimationStats anim) {
        if (anim == null)
            return 0;
        int maxFrame = Math.max(0, anim.frameCount - 1);
        return (int) Math.round(progress * maxFrame);
    }

    // --- Reload Utilities ---

    public java.util.Map<String, List<ItemStack>> findAmmo(String caliber) {
        java.util.Map<String, List<ItemStack>> ammoPool = new java.util.HashMap<>();
        for (ItemStack invItem : player.getInventory().getContents()) {
            if (invItem == null || invItem.getType() == org.bukkit.Material.AIR || !invItem.hasItemMeta())
                continue;

            String id = invItem.getItemMeta().getPersistentDataContainer().get(PDCKeys.ITEM_ID, PDCKeys.STRING);
            com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition foundAmmo = com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry
                    .getAmmo(id);

            if (foundAmmo != null && foundAmmo.caliber.equalsIgnoreCase(caliber)) {
                ammoPool.computeIfAbsent(id, k -> new java.util.ArrayList<>()).add(invItem);
            }
        }
        return ammoPool;
    }

    public void sendActionBar(String message) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(message));
    }
}
