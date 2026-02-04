package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;

public class SprintingState implements WeaponState {

    @Override
    public void onEnter(WeaponContext ctx) {
        // Sprint start effects
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null)
            return;

        // Increase Sprint Progress
        double sprintStep = 0.2; // Fixed speed for sprint transition
        double current = ctx.getSprintProgress();
        if (current < 1.0) {
            ctx.setSprintProgress(Math.min(1.0, current + sprintStep));
        }

        // Decrease Aim Progress
        double aimStep = calculateAimStep(stats);
        ctx.setAimProgress(Math.max(0.0, ctx.getAimProgress() - aimStep));

        // Render
        GunStats.AnimationStats anim = stats.sprintAnimation;
        if (anim != null) {
            int frame = ctx.getProgressFrameIndex(ctx.getSprintProgress(), anim);
            ctx.applyModel(anim, frame);
        }
    }

    @Override
    public void onExit(WeaponContext ctx) {
        // Sprint end effects
    }

    @Override
    public WeaponState handleInput(WeaponContext ctx, InputType input) {
        switch (input) {
            case SPRINT_END:
                return new IdleState();
            case RELOAD:
                // Can reload while sprinting? Usually no, or stops sprint.
                // Let's assume reload cancels sprint in this game logic.
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
