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

        // 1. Find Ammo logic
        String targetCaliber = stats.caliber;
        Map<String, List<ItemStack>> ammoPool = ctx.findAmmo(targetCaliber);

        if (ammoPool.isEmpty()) {
            ctx.sendActionBar("§cNo Ammo: " + targetCaliber);
            isReloadPossible = false; // Will trigger transition in update
            return;
        }

        // 2. Select Best Ammo
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

        // Check if reload is needed
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        int currentAmmo = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        int maxAmmo = stats.magSize;
        String currentAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

        if (currentAmmo >= maxAmmo && finalAmmoData.id.equals(currentAmmoId)) {
            isReloadPossible = false; // Already full
            return;
        }

        isReloadPossible = true;

        // Determine Animation Type
        boolean isClosedBolt = "CLOSED".equalsIgnoreCase(stats.boltType);
        boolean isEmpty = currentAmmo <= 0;

        animStats = stats.reloadAnimation;
        if (isClosedBolt && isEmpty && stats.tacticalReloadAnimation != null) {
            animStats = stats.tacticalReloadAnimation;
        }

        // Calculate Duration
        if (animStats != null && animStats.fps > 0) {
            double durationSeconds = (double) animStats.frameCount / animStats.fps;
            totalTicks = (int) Math.ceil(durationSeconds * 20);
        } else {
            totalTicks = Math.max(1, stats.reloadTime / 50);
        }

        // Apply initial model
        if (animStats != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, Key.key(animStats.model));
            // Initial frame
            ctx.applyModel(animStats, 0);
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
            // Abort immediately
            // Ideally we transitionTo(new IdleState()) but handleInput handles logic?
            // No, update is void. We assume Statemachine checks specific flag or we use
            // external trigger.
            // But usually SM updates don't trigger transition unless via specific method.
            // Here we might need a way to auto-transition.
            // Since we don't have reference to SM here easily unless passed, we can't
            // switch state directly easily.
            // Wait, handleInput is for External input.
            // If we need internal transition (animation done), we need return value from
            // onUpdate?
            // Or ctx.getStateMachine().transitionTo(...).
            // Let's assume onUpdate logic handles rendering, and if finished, we trigger
            // something.
            // But my interface onUpdate returns void.
            // FIX: Let's assume handleInput(InputType.AUTO...) or similar.
            // Or... GunListener calls handleInput? No.
            // Let's modify WeaponStateMachine to allow states to trigger transitions if
            // needed?
            // Or simpler: handleInput with a special TICK input type?
            return;
            // Actually, the SM design I proposed has void onUpdate.
            // This is a flaw for "Animation Complete".
            // We need a way to signal completion.
        }

        elapsed++;

        // Render Animation
        if (animStats != null) {
            int frameIndex = (int) ((elapsed / 20.0) * animStats.fps);
            if (frameIndex >= animStats.frameCount) {
                frameIndex = animStats.frameCount - 1;
            }
            ctx.applyModel(animStats, frameIndex);
        }

        // Render Bar
        displayReloadBar(ctx, elapsed, totalTicks);

        // Completion
        if (elapsed >= totalTicks) {
            completeReload(ctx);
            isReloadPossible = false; // Mark complete
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
                return null; // Ignore
            case SPRINT_START:
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

        // Eject leftover
        int leftover = pdc.getOrDefault(PDCKeys.AMMO, PDCKeys.INTEGER, 0);
        String oldAmmoId = pdc.get(PDCKeys.CURRENT_AMMO_ID, PDCKeys.STRING);

        if (leftover > 0 && oldAmmoId != null) {
            ItemStack ejected = ItemFactory.create(oldAmmoId);
            if (ejected != null) {
                ejected.setAmount(leftover);
                ctx.getPlayer().getInventory().addItem(ejected).forEach(
                        (i, drop) -> ctx.getPlayer().getWorld().dropItemNaturally(ctx.getPlayer().getLocation(), drop));
            }
        }

        // Consume & Load
        int magSize = ctx.getStats().magSize;
        int needed = magSize;
        int loaded = 0;

        for (ItemStack ammoStack : finalAmmoStacks) {
            if (needed <= 0)
                break;
            if (ammoStack == null || ammoStack.getType() == Material.AIR)
                continue;

            int amount = ammoStack.getAmount();
            int take = Math.min(amount, needed);

            ammoStack.setAmount(amount - take);
            loaded += take;
            needed -= take;
        }

        pdc.set(PDCKeys.AMMO, PDCKeys.INTEGER, loaded);
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
