package com.lunar_prototype.impossbleEscapeMC.animation.state;

public class WeaponStateMachine {
    private final WeaponContext context;
    private WeaponState currentState;
    private WeaponState globalState; // Optional: for handling global events if needed

    public WeaponStateMachine(WeaponContext context, WeaponState initialState) {
        this.context = context;
        this.currentState = initialState;
        if (this.currentState != null) {
            this.currentState.onEnter(context);
        }
    }

    public void update() {
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
