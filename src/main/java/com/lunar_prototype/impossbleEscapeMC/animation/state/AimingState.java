package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;

public class AimingState implements WeaponState {

    @Override
    public void onEnter(WeaponContext ctx) {
        // Play aim sound if needed
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
        // Play aim exit sound if needed
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
