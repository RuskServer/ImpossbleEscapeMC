package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;

public class IdleState implements WeaponState {

    private int idleTick = 0;

    @Override
    public void onEnter(WeaponContext ctx) {
        // Reset or initialize if needed
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null)
            return;

        // Decrease progress for other states (decay)
        updateProgress(ctx, stats);

        // Render Priority: Sprint Decay -> Aim Decay -> Idle Loop
        if (ctx.getSprintProgress() > 0.001) {
            GunStats.AnimationStats anim = stats.sprintAnimation;
            if (anim != null) {
                int frame = ctx.getProgressFrameIndex(ctx.getSprintProgress(), anim);
                ctx.applyModel(anim, frame);
                return;
            }
        }

        if (ctx.getAimProgress() > 0.001) {
            GunStats.AnimationStats anim = stats.aimAnimation;
            if (anim != null) {
                int frame = ctx.getProgressFrameIndex(ctx.getAimProgress(), anim);
                ctx.applyModel(anim, frame);
                return;
            }
        }

        // Render IDLE animation
        idleTick++;
        GunStats.AnimationStats anim = stats.idleAnimation;
        if (anim != null) {
            int frame = ctx.getLoopFrameIndex(idleTick, anim);
            ctx.applyModel(anim, frame);
        } else {
            // Check legacy render if no animation
            renderLegacy(ctx);
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        // Nothing specific
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        switch (input) {
            case RIGHT_CLICK_START: // For Hold Aim
            case LEFT_CLICK: // For Toggle Aim (Context mapping needed)
                return new AimingState();
            case SPRINT_START:
                return new SprintingState();
            case RELOAD:
                return new ReloadingState();
            default:
                return null;
        }
    }

    private void updateProgress(WeaponContext ctx, GunStats stats) {
        // Aim Decay
        double aimStep = calculateAimStep(stats);
        double currentAim = ctx.getAimProgress();
        if (currentAim > 0) {
            ctx.setAimProgress(Math.max(0.0, currentAim - aimStep));
        }

        // Sprint Decay
        double sprintStep = 0.2;
        double currentSprint = ctx.getSprintProgress();
        if (currentSprint > 0) {
            ctx.setSprintProgress(Math.max(0.0, currentSprint - sprintStep));
        }
    }

    private double calculateAimStep(GunStats stats) {
        double aimTimeTicks = (stats.adsTime > 0) ? (stats.adsTime / 50.0) : 1.0;
        return 1.0 / aimTimeTicks;
    }

    private void renderLegacy(WeaponContext ctx) {
        // Fallback to legacy custom model data logic if needed,
        // but typically applyModel handles the core logic.
        // If we really need legacy behavior (add numbers), we need logic similar to
        // GunListener
        // But for now, we assume animation stats exist or handle gracefully.
        // Actually, let's implement basic legacy reset here just in case.
        GunStats stats = ctx.getStats();
        if (stats.idleAnimation == null && stats.customModelData > 0) {
            // Reset to base model data
            // This requires direct item manipulation or context helper
            // Ideally we shouldn't mix legacy and new, but for safety:
            // ctx.resetToLegacyBase(); // Implementation deferred
        }
    }
}
