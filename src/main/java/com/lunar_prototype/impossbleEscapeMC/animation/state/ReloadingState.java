package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.AmmoDefinition;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import com.lunar_prototype.impossbleEscapeMC.item.ItemFactory;
import com.lunar_prototype.impossbleEscapeMC.item.ItemRegistry;
import com.lunar_prototype.impossbleEscapeMC.util.PDCKeys;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.List;
import java.util.Map;

public class ReloadingState implements WeaponState {

    private int elapsed = 0;
    private int totalTicks = 0;
    private GunStats.AnimationStats animStats = null;

    private AmmoDefinition finalAmmoData = null;
    private List<ItemStack> finalAmmoStacks = null;
    private boolean isReloadPossible = false;

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        ItemStack item = ctx.getItem();

        // PUMP_ACTION はショットガン専用ステートへ委譲
        if ("PUMP_ACTION".equalsIgnoreCase(stats.boltType)) {
            if (ctx.getStateMachine() != null) {
                ctx.getStateMachine().transitionTo(new ShotgunReloadingState());
            }
            return;
        }

        // 1. Find Ammo logic
        String targetCaliber = stats.caliber;
        Map<String, List<ItemStack>> ammoPool = ctx.findAmmo(targetCaliber);

        if (ammoPool.isEmpty()) {
            ctx.sendActionBar("§cNo Ammo: " + targetCaliber);
            isReloadPossible = false; // Will trigger transition in update
            return;
        }

        // Select Best Ammo
        String bestAmmoId = null;
        int maxCount = -1;
        for (Map.Entry<String, List<ItemStack>> entry : ammoPool.entrySet()) {
            int total = entry.getValue().stream().mapToInt(ItemStack::getAmount).sum();
            if (total > maxCount) {
                maxCount = total;
                bestAmmoId = entry.getKey();
            }
        }

        finalAmmoData = ItemRegistry.getAmmo(bestAmmoId);
        finalAmmoStacks = ammoPool.get(bestAmmoId);

        boolean isClosedBolt = "CLOSED".equalsIgnoreCase(stats.boltType)
                || "BOLT_ACTION".equalsIgnoreCase(stats.boltType);

        // Check if reload is needed
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean isChamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
        String chamberAmmoId = pdc.get(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING);

        // リロードが必要な条件:
        // 1. マガジンが満タンでない
        // 2. マガジンの弾種がインベントリの最適弾種と異なる
        // 3. (クローズドボルトの場合) チャンバーが空
        // 4. (クローズドボルトの場合) チャンバーの弾種が異なる
        boolean needsRefill = currentAmmo < stats.magSize;
        boolean typeMismatch = !finalAmmoData.id.equals(currentAmmoId);
        boolean chamberNeedsAttention = isClosedBolt && (!isChamberLoaded || !finalAmmoData.id.equals(chamberAmmoId));

        if (!needsRefill && !typeMismatch && !chamberNeedsAttention) {
            isReloadPossible = false; // Already full and correct type
            return;
        }

        isReloadPossible = true;

        boolean isEmpty = currentAmmo <= 0;

        animStats = stats.reloadAnimation;
        if (isClosedBolt && isEmpty && stats.tacticalReloadAnimation != null) {
            animStats = stats.tacticalReloadAnimation;
        }

        // Calculate Duration
        if (animStats != null && animStats.fps > 0 && animStats.playbackSpeed > 0) {
            double durationSeconds = (double) animStats.frameCount / (animStats.fps * animStats.playbackSpeed);
            totalTicks = (int) Math.ceil(durationSeconds * 20);
        } else {
            totalTicks = 20; // Default 1s if no animation or invalid stats
        }

        // Apply initial model
        if (animStats != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(animStats.model));
            // Initial frame
            ctx.applyLayeredModel(animStats, 0, ctx.getIndependentFrameToRender());
        }

        ctx.playSound(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.5f);

        // Reset aim/sprint progress immediately or keep?
        // GunListener logic clears aiming.
        ctx.setAimProgress(0.0);
        ctx.setSprintProgress(0.0);
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        if (!isReloadPossible) {
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
            ctx.applyLayeredModel(animStats, frameIndex, ctx.getIndependentFrameToRender());
        }

        // Render Bar
        displayReloadBar(ctx, elapsed, totalTicks);

        // Completion
        if (elapsed >= totalTicks) {
            completeReload(ctx);
            isReloadPossible = false; // Mark complete

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
        ctx.sendActionBar(""); // Clear bar
        // If cancelled (elapsed < totalTicks), maybe show message
        if (elapsed < totalTicks && isReloadPossible) {
            ctx.sendActionBar("§cReload Cancelled");
        }

        // Reset Item Model is handled by next state (Idle) which applies its own model
        // but it's good practice to ensure clean state.
        ctx.resetCache();
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        // If reload not possible (or finished), process input as if we are in Idle
        if (!isReloadPossible) {
            WeaponState next = new IdleState().handleInput(ctx, input);
            return next != null ? next : new IdleState();
        }

        // Grace period: Ignore input for first few ticks to prevent accidental cancel
        if (elapsed < 5) {
            return null;
        }

        // While reloading...
        switch (input) {
            case RELOAD: // Spamming reload key?
            case SPRINT_START:
            case SPRINT_END:
                return null; // Ignore these, allow reload to continue
            case RIGHT_CLICK_START:
            case LEFT_CLICK: // Shooting attempt
                // Cancel reload and transition to the appropriate state
                WeaponState next = new IdleState().handleInput(ctx, input);
                return next != null ? next : new IdleState();
            default:
                return null;
        }
    }

    private void completeReload(WeaponContext ctx) {
        ItemStack currentItem = ctx.getItem();
        ItemMeta meta = currentItem.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        GunStats stats = ctx.getStats();
        boolean isClosedBolt = "CLOSED".equalsIgnoreCase(stats.boltType)
                || "BOLT_ACTION".equalsIgnoreCase(stats.boltType);

        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        boolean isChamberLoaded = pdc.getOrDefault(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0) == 1;
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);
        String currentChamberId = pdc.get(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING);

        // 1. Eject Magazine content
        if (currentAmmo > 0 && currentAmmoId != null) {
            ItemStack ejected = ItemFactory.create(currentAmmoId);
            if (ejected != null) {
                ejected.setAmount(currentAmmo);
                ctx.getPlayer().getInventory().addItem(ejected).forEach(
                        (i, drop) -> ctx.getPlayer().getWorld().dropItemNaturally(ctx.getPlayer().getLocation(), drop));
            }
            pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        }

        // 2. Handle Chamber
        // Keep chambered round only if it's closed bolt and ammo type matches
        boolean keepChamber = isClosedBolt && isChamberLoaded && finalAmmoData.id.equals(currentChamberId);

        if (isChamberLoaded && !keepChamber) {
            if (currentChamberId != null) {
                ItemStack ejected = ItemFactory.create(currentChamberId);
                if (ejected != null) {
                    ctx.getPlayer().getInventory().addItem(ejected).forEach(
                            (i, drop) -> ctx.getPlayer().getWorld().dropItemNaturally(ctx.getPlayer().getLocation(), drop));
                }
            }
            pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0);
            isChamberLoaded = false;
        }

        // 3. Calculate needed
        int maxPossible = isClosedBolt ? stats.magSize + 1 : stats.magSize;
        int currentInGun = isChamberLoaded ? 1 : 0;
        int neededFromInventory = Math.max(0, maxPossible - currentInGun);

        // 4. Consume from inventory
        int takenFromInventory = 0;
        for (ItemStack ammoStack : finalAmmoStacks) {
            if (neededFromInventory <= 0)
                break;
            if (ammoStack == null || ammoStack.getType() == Material.AIR)
                continue;

            int amount = ammoStack.getAmount();
            int take = Math.min(amount, neededFromInventory);

            ammoStack.setAmount(amount - take);
            takenFromInventory += take;
            neededFromInventory -= take;
        }

        // 5. Update Gun State
        int newTotal = (isChamberLoaded ? 1 : 0) + takenFromInventory;

        if (isClosedBolt) {
            if (newTotal > 0) {
                pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 1);
                pdc.set(PDCKeys.CHAMBER_AMMO_ID, PDCKeys.STRING, finalAmmoData.id);
                newTotal--;
            } else {
                pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0);
            }
            pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, Math.min(newTotal, stats.magSize));
        } else {
            pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, Math.min(newTotal, stats.magSize));
            pdc.set(PDCKeys.CHAMBER_LOADED, PDCKeys.BOOLEAN, (byte) 0);
        }

        pdc.set(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING, finalAmmoData.id);

        currentItem.setItemMeta(meta);
        ItemFactory.updateLore(currentItem);

        ctx.sendActionBar("§a§lRELOAD COMPLETE");
        ctx.playSound(Sound.BLOCK_IRON_DOOR_CLOSE, 1.0f, 1.2f);
    }

    private void displayReloadBar(WeaponContext ctx, int elapsed, int totalTicks) {
        int barCount = 30;
        int progress = (int) ((double) elapsed / totalTicks * barCount);

        double remainingSeconds = Math.max(0, (totalTicks - elapsed) / 20.0);
        String timeStr = String.format("%.1f", remainingSeconds);

        StringBuilder bar = new StringBuilder("§7Reloading ");

        bar.append("§7");
        for (int i = 0; i < progress; i++) {
            bar.append("|");
        }

        bar.append("§c");
        for (int i = progress; i < barCount; i++) {
            bar.append("|");
        }

        bar.append(" §7").append(timeStr).append("s");

        ctx.sendActionBar(bar.toString());
    }
}
