package com.lunar_prototype.impossbleEscapeMC.animation.state;

public interface WeaponState {
    void onEnter(WeaponContext ctx);

    void onUpdate(WeaponContext ctx);

    void onExit(WeaponContext ctx);

    /**
     * Handle input events.
     * 
     * @param ctx   The context
     * @param input The input type
     * @return The next state to transition to, or null if no transition takes
     *         place.
     */
    WeaponState handleInput(WeaponContext ctx, InputType input);
}
