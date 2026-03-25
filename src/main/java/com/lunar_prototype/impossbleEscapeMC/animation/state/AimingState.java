package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerAbilities;
import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class AimingState implements WeaponState {
    // If ADS is triggered while sprintProgress > 0, we do it sequentially:
    // dash cancel (reduce sprintProgress only) -> ADS progress (increase aimProgress).
    private boolean sequentialDashMode = false;
    private boolean inDashCancelPhase = false;

    private int dashCancelElapsedTicks = 0;
    private double dashCancelTargetTicks = 0.0;
    private double sprintCancelStep = 0.0; // per tick

    private double adsAimStep = 0.0; // per tick

    // When sequentialDashMode is active, scope.in/out should be delayed until ADS phase starts.
    private boolean scopeActivated = false;

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null) return;

        double sprintProgressAtEnter = ctx.getSprintProgress(); // 0.0 - 1.0
        sequentialDashMode = sprintProgressAtEnter > 0.001;

        if (sequentialDashMode) {
            inDashCancelPhase = true;
            dashCancelElapsedTicks = 0;

            // k = 0.7 * (1 + ダッシュ解除の進捗(割合))
            double k = 0.7 * (1.0 + sprintProgressAtEnter);

            // Base times (ms) used by the spec
            double dashCancelTimeMsBase = 250.0; // derived from sprint step 0.2/tick => 5 ticks => 250ms
            double adsStartTimeMsBase = (stats.adsTime > 0) ? stats.adsTime : 50.0; // align with current 1tick behavior

            double dashCancelTimeMsScaled = dashCancelTimeMsBase * k;
            double adsStartTimeMsScaled = adsStartTimeMsBase * k;

            dashCancelTargetTicks = dashCancelTimeMsScaled / 50.0;
            double adsTargetTicks = adsStartTimeMsScaled / 50.0;

            // Guard against extreme/degenerate inputs
            double safeDashTicks = Math.max(0.0001, dashCancelTargetTicks);
            double safeAdsTicks = Math.max(0.0001, adsTargetTicks);

            sprintCancelStep = sprintProgressAtEnter / safeDashTicks;
            adsAimStep = 1.0 / safeAdsTicks;

            ctx.setAimProgress(0.0); // Keep at 0 until dash cancel finishes
            scopeActivated = false; // delay scope.in
        } else {
            inDashCancelPhase = false;
            ctx.setAimProgress(0.0);
            scopeActivated = true;
            // Play aim sound immediately (non-dash case)
            ctx.getPlayer().playSound(ctx.getPlayer().getLocation(), "minecraft:scope.in", 1.0f, 1.0f);
        }
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null)
            return;

        // Phase 1: dash cancel (do not progress aimProgress)
        if (sequentialDashMode && inDashCancelPhase) {
            dashCancelElapsedTicks++;

            ctx.setSprintProgress(Math.max(0.0, ctx.getSprintProgress() - sprintCancelStep));

            // Render sprint animation during dash cancel for visual consistency
            GunStats.AnimationStats sprintAnim = stats.sprintAnimation;
            if (sprintAnim != null) {
                int frame = ctx.getProgressFrameIndex(ctx.getSprintProgress(), sprintAnim);
                ctx.applyLayeredModel(sprintAnim, frame, ctx.getIndependentFrameToRender());
            }

            // Finish dash cancel -> start ADS phase
            if (dashCancelElapsedTicks >= dashCancelTargetTicks || ctx.getSprintProgress() <= 0.0) {
                inDashCancelPhase = false;
                ctx.setSprintProgress(0.0);
                ctx.setAimProgress(0.0);

                scopeActivated = true;
                ctx.getPlayer().playSound(ctx.getPlayer().getLocation(), "minecraft:scope.in", 1.0f, 1.0f);
            } else {
                return; // wait until dash cancel finishes
            }
        }

        // Phase 2: ADS progress (increase aimProgress)
        double current = ctx.getAimProgress();
        if (current < 1.0) {
            double aimStep = sequentialDashMode ? adsAimStep : calculateAimStep(stats);
            ctx.setAimProgress(Math.min(1.0, current + aimStep));
        }

        // Apply Zoom via Packet
        if (scopeActivated && stats.scope != null && stats.scope.zoom > 1.0) {
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
            ctx.applyLayeredModel(anim, frame, ctx.getIndependentFrameToRender());
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        if (scopeActivated) {
            // Reset zoom
            Player player = ctx.getPlayer();
            float originalWalkSpeed = player.getWalkSpeed() / 2.0f;
            sendAbilitiesPacket(player, originalWalkSpeed);
            ctx.setLastSentWalkSpeed(-1.0f);

            // Play exit sound
            player.playSound(player.getLocation(), "minecraft:scope.out", 1.0f, 1.0f);
        }

        // Clear phase flags
        sequentialDashMode = false;
        inDashCancelPhase = false;
        dashCancelElapsedTicks = 0;
        dashCancelTargetTicks = 0.0;
        sprintCancelStep = 0.0;
        adsAimStep = 0.0;
        scopeActivated = false;
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
