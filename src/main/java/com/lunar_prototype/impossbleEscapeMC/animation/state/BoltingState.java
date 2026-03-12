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

    private enum Phase {
        AIM_OUT, BOLTING, DONE
    }

    private Phase phase = Phase.BOLTING;
    private int elapsed = 0;
    private int totalTicks = 0;
    private boolean isPossible = false;

    // AIM_OUT specifics
    private int aimOutTotalTicks = 0;
    private double startAimProgress = 0.0;

    // BOLTING specifics
    private GunStats.AnimationStats boltingAnim = null;
    private int boltingTotalTicks = 0;

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        ItemStack item = ctx.getItem();
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean isChamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;

        // チャンバーが既に装填されているか、マガジンが空の場合はコッキング不要
        if (isChamberLoaded || currentAmmo <= 0) {
            isPossible = false;
            return;
        }

        isPossible = true;
        boltingAnim = stats.boltingAnimation;

        // Calculate Bolting Duration
        if (boltingAnim != null && boltingAnim.fps > 0 && boltingAnim.playbackSpeed > 0) {
            double durationSeconds = (double) boltingAnim.frameCount / (boltingAnim.fps * boltingAnim.playbackSpeed);
            boltingTotalTicks = (int) Math.ceil(durationSeconds * 20);
        } else {
            boltingTotalTicks = Math.max(1, stats.boltingTime / 50);
        }

        // Check if we need to AIM_OUT first
        startAimProgress = ctx.getAimProgress();
        if (startAimProgress > 0) {
            phase = Phase.AIM_OUT;
            // aimTime (ms) 基準
            aimOutTotalTicks = Math.max(1, (int) (startAimProgress * (stats.adsTime / 50.0)));
            totalTicks = aimOutTotalTicks;
            elapsed = 0;
        } else {
            startBoltingPhase(ctx);
        }
    }

    private void startBoltingPhase(WeaponContext ctx) {
        phase = Phase.BOLTING;
        elapsed = 0;
        totalTicks = boltingTotalTicks;

        ctx.setAimProgress(0.0);
        ctx.setSprintProgress(0.0);

        if (boltingAnim != null) {
            ctx.getItem().setData(DataComponentTypes.ITEM_MODEL, Key.key(boltingAnim.model));
            ctx.applyModel(boltingAnim, 0);
        }

        // コッキング音
        ctx.playSound(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.8f);
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        if (!isPossible) {
            transitionOut(ctx);
            return;
        }

        elapsed++;

        switch (phase) {
            case AIM_OUT -> {
                double progress = 1.0 - ((double) elapsed / aimOutTotalTicks);
                ctx.setAimProgress(Math.max(0.0, startAimProgress * progress));

                // エイムアニメーションを逆再生で表示
                GunStats.AnimationStats aimAnim = ctx.getStats().aimAnimation;
                if (aimAnim != null) {
                    int frame = (int) (progress * (aimAnim.frameCount - 1));
                    ctx.applyModel(aimAnim, Math.max(0, frame));
                }

                if (elapsed >= aimOutTotalTicks) {
                    startBoltingPhase(ctx);
                }
            }
            case BOLTING -> {
                if (boltingAnim != null) {
                    int frameIndex = (int) ((elapsed / 20.0) * boltingAnim.fps * boltingAnim.playbackSpeed);
                    frameIndex = Math.min(frameIndex, boltingAnim.frameCount - 1);
                    ctx.applyModel(boltingAnim, frameIndex);
                }

                if (elapsed >= boltingTotalTicks) {
                    completeBolting(ctx);
                    phase = Phase.DONE;
                }
            }
            case DONE -> transitionOut(ctx);
        }
    }

    private void transitionOut(WeaponContext ctx) {
        if (ctx.getStateMachine() != null) {
            if (ctx.getPlayer().isSprinting()) {
                ctx.getStateMachine().transitionTo(new SprintingState());
            } else {
                ctx.getStateMachine().transitionTo(new IdleState());
            }
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        ctx.resetCache();
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        if (!isPossible || phase == Phase.DONE) {
            WeaponState next = new IdleState().handleInput(ctx, input);
            return next != null ? next : new IdleState();
        }
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
