package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class AimingState implements WeaponState {

    @Override
    public void onEnter(WeaponContext ctx) {
        // Play aim sound
        ctx.getPlayer().playSound(ctx.getPlayer().getLocation(), "minecraft:scope.in", 1.0f, 1.0f);
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null)
            return;

        // Increase Aim Progress
        double aimStep = calculateAimStep(stats);
        double current = ctx.getAimProgress();
        if (current < 1.0) {
            ctx.setAimProgress(Math.min(1.0, current + aimStep));
        }

        // Apply Zoom via Packet
        if (stats.scope != null && stats.scope.zoom > 1.0) {
            Player player = ctx.getPlayer();
            float baseWalkSpeed = player.getWalkSpeed() / 2.0f; // Bukkit default 0.2 -> Packet default 0.1
            
            float targetZoom = (float) stats.scope.zoom;
            float currentZoom = 1.0f + (targetZoom - 1.0f) * (float) ctx.getAimProgress();
            
            // Multiply by currentZoom to decrease FOV (Zoom IN)
            float newWalkSpeed = baseWalkSpeed * currentZoom;

            // Only send if significant change to avoid packet spam
            if (Math.abs(ctx.getLastSentWalkSpeed() - newWalkSpeed) > 0.0001f) {
                sendAbilitiesPacket(player, newWalkSpeed);
                ctx.setLastSentWalkSpeed(newWalkSpeed);
            }
        }

        // Decrease Sprint Progress (Force decay in aim)
        ctx.setSprintProgress(Math.max(0.0, ctx.getSprintProgress() - 0.2));

        // Render
        GunStats.AnimationStats anim = stats.aimAnimation;
        if (anim != null) {
            int frame = ctx.getProgressFrameIndex(ctx.getAimProgress(), anim);
            ctx.applyModel(anim, frame);
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        // Reset zoom
        Player player = ctx.getPlayer();
        float originalWalkSpeed = player.getWalkSpeed() / 2.0f;
        sendAbilitiesPacket(player, originalWalkSpeed);
        ctx.setLastSentWalkSpeed(-1.0f);

        // Play exit sound
        player.playSound(player.getLocation(), "minecraft:scope.out", 1.0f, 1.0f);
    }

    private void sendAbilitiesPacket(Player player, float walkSpeed) {
        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
        boolean isSpectator = player.getGameMode() == GameMode.SPECTATOR;
        
        WrapperPlayServerPlayerAbilities packet = new WrapperPlayServerPlayerAbilities(
                isCreative || isSpectator, // invulnerable
                player.isFlying(),
                player.getAllowFlight(),
                isCreative, // creative
                player.getFlySpeed(),
                walkSpeed
        );
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        switch (input) {
            case RIGHT_CLICK_END:
            case LEFT_CLICK: // Toggle off
                return new IdleState();
            case SPRINT_START:
                return new SprintingState();
            case RELOAD:
                return new ReloadingState();
            default:
                return null;
        }
    }

    private double calculateAimStep(GunStats stats) {
        double aimTimeTicks = (stats.adsTime > 0) ? (stats.adsTime / 50.0) : 1.0;
        return 1.0 / aimTimeTicks;
    }
}
