package com.lunar_prototype.impossbleEscapeMC.animation.state;

import com.lunar_prototype.impossbleEscapeMC.item.GunStats;
import org.bukkit.plugin.Plugin;

public class EquippingState implements WeaponState {

    private final Plugin plugin;
    private int elapsed = 0;
    private int totalTicks = 0;
    private GunStats.AnimationStats animStats = null;

    public EquippingState(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onEnter(WeaponContext ctx) {
        GunStats stats = ctx.getStats();
        if (stats == null) return;

        totalTicks = Math.max(1, stats.equipTimeMs / 50);
        animStats = stats.equipAnimation;

        if (animStats != null) {
            ctx.applyLayeredModel(animStats, 0, ctx.getIndependentFrameToRender());
        }
        
        ctx.setAimProgress(0.0);
        ctx.setSprintProgress(0.0);
    }

    @Override
    public void onUpdate(WeaponContext ctx) {
        elapsed++;

        if (animStats != null) {
            int frameIndex = (int) ((elapsed / 20.0) * animStats.fps * animStats.playbackSpeed);
            if (frameIndex >= animStats.frameCount) {
                frameIndex = animStats.frameCount - 1;
            }
            ctx.applyLayeredModel(animStats, frameIndex, ctx.getIndependentFrameToRender());
        } else {
            // アニメーションが未設定の場合はIdleをフォールバックとして描画
            GunStats stats = ctx.getStats();
            if (stats != null && stats.idleAnimation != null) {
                ctx.applyLayeredModel(stats.idleAnimation, 0, ctx.getIndependentFrameToRender());
            }
        }

        if (elapsed >= totalTicks) {
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
        // 持ち替え中は射撃・ADS・手動リロードなどの入力をすべてブロックする
        return null;
    }
}
