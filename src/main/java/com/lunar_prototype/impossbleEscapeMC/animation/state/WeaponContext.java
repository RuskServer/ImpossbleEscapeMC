package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.ImpossbleEscapeMC;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WeaponContext {
    private final ImpossbleEscapeMC plugin;
    private final Player player;
    private ItemStack item;
    private GunStats stats;

    // Animation progress
    private double aimProgress = 0.0;
    private double sprintProgress = 0.0;

    // Rendering cache
    private String lastModelKey = "";
    private int lastFrame = -1;

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

    // Rendering Logic
    public void applyModel(GunStats.AnimationStats anim, int frameIndex) {
        if (anim == null)
            return;

        // Cache check
        if (anim.model.equals(lastModelKey) && frameIndex == lastFrame) {
            return;
        }

        // Apply model
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key(anim.model));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData()
                .addFloat((float) frameIndex)
                .addStrings(getAttachments(item))
                .build());

        lastModelKey = anim.model;
        lastFrame = frameIndex;
    }

    public void resetCache() {
        this.lastModelKey = "";
        this.lastFrame = -1;
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
        try {
            Sound standardSound = Sound.valueOf(sound.toUpperCase());
            playSound(standardSound, volume, pitch);
        } catch (IllegalArgumentException e) {
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    // Helper to calculate frame index for loop
    public int getLoopFrameIndex(int tick, GunStats.AnimationStats anim) {
        if (anim == null || anim.fps <= 0 || anim.frameCount <= 1)
            return 0;
        return (int) ((tick / 20.0) * anim.fps) % anim.frameCount;
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
