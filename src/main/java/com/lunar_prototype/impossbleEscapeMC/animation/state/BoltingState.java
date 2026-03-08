package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class BoltingState implements WeaponState {

    private int elapsed = 0;
    private int totalTicks = 0;
    private GunStats.AnimationStats animStats = null;
    private boolean isBoltingPossible = false;

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        ItemStack item = ctx.getItem();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean isChamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;

        // チャンバーが既に装填されているか、マガジンが空の場合はコッキング不要
        if (isChamberLoaded || currentAmmo <= 0) {
            isBoltingPossible = false;
            return;
        }

        isBoltingPossible = true;
        animStats = stats.boltingAnimation;

        // Calculate Duration
        if (animStats != null && animStats.fps > 0 && animStats.playbackSpeed > 0) {
            double durationSeconds = (double) animStats.frameCount / (animStats.fps * animStats.playbackSpeed);
            totalTicks = (int) Math.ceil(durationSeconds * 20);
        } else {
            totalTicks = Math.max(1, stats.boltingTime / 50);
        }

        // Apply initial model
        if (animStats != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(animStats.model));
            ctx.applyModel(animStats, 0);
        }

        // コッキング音
        ctx.playSound(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.8f);

        ctx.setAimProgress(0.0);
        ctx.setSprintProgress(0.0);
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        if (!isBoltingPossible) {
            if (ctx.getStateMachine() != null) {
                if (ctx.getPlayer().isSprinting()) {
                    ctx.getStateMachine().transitionTo(new SprintingState());
                } else {
                    ctx.getStateMachine().transitionTo(new IdleState());
                }
            }
            return;
        }

        elapsed++;

        // Render Animation
        if (animStats != null) {
            int frameIndex = (int) ((elapsed / 20.0) * animStats.fps * animStats.playbackSpeed);
            if (frameIndex >= animStats.frameCount) {
                frameIndex = animStats.frameCount - 1;
            }
            ctx.applyModel(animStats, frameIndex);
        }

        // Completion
        if (elapsed >= totalTicks) {
            completeBolting(ctx);
            isBoltingPossible = false; // Mark complete

            // Auto transition immediately inside this tick
            if (ctx.getStateMachine() != null) {
                if (ctx.getPlayer().isSprinting()) {
                    ctx.getStateMachine().transitionTo(new SprintingState());
                } else {
                    ctx.getStateMachine().transitionTo(new IdleState());
                }
            }
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        ctx.resetCache();
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        if (!isBoltingPossible) {
            WeaponState next = new IdleState().handleInput(ctx, input);
            return next != null ? next : new IdleState();
        }

        // Allow actions like sprinting or stopping sprint during bolting without cancelling
        return null;
    }

    private void completeBolting(WeaponContext ctx) {
        ItemStack currentItem = ctx.getItem();
        ItemMeta meta = currentItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

        if (currentAmmo > 0) {
            // マガジンから1発減らしてチャンバーへ送る
            pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, currentAmmo - 1);
            pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 1);
            if (currentAmmoId != null) {
                pdc.set(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING, currentAmmoId);
            }
        }

        currentItem.setItemMeta(meta);
        ItemFactory.updateLore(currentItem);

        // ボルトを戻す音
        ctx.playSound(Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.8f);
    }
}
