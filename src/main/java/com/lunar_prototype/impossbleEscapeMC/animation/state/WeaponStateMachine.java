package com.lunar_prototype.impossbleEscapeMC.animation.state;

public class WeaponStateMachine {
    private final WeaponContext context;
    private WeaponState currentState;
    private WeaponState globalState; // Optional: for handling global events if needed

    public WeaponStateMachine(WeaponContext context, WeaponState initialState) {
        this.context = context;
        this.context.setStateMachine(this);
        this.currentState = initialState;
        if (this.currentState != null) {
            this.currentState.onEnter(context);
        }
    }

    public void update() {
        // 第一に独立アニメーション（非同期/重畳レイヤー）の進行を管理
        if (context != null) {
            context.updateIndependentAnimation();
        }
        
        // メインステートの更新処理
        if (currentState != null) {
            currentState.onUpdate(context);
        }
    }

    public void handleInput(InputType input) {
        if (currentState != null) {
            WeaponState next = currentState.handleInput(context, input);
            if (next != null) {
                transitionTo(next);
            }
        }
    }

    public void reset() {
        if (currentState != null) {
            currentState.onExit(context);
        }
        context.resetProgress();
        currentState = new IdleState();
        if (currentState != null) {
            currentState.onEnter(context);
        }
    }

    public void transitionTo(WeaponState nextState) {
        if (currentState != null) {
            currentState.onExit(context);
        }
        currentState = nextState;
        if (currentState != null) {
            currentState.onEnter(context);
        }
    }

    public WeaponState getCurrentState() {
        return currentState;
    }

    public WeaponContext getContext() {
        return context;
    }
}
