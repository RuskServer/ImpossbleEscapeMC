package com.lunar_prototype.impossbleEscapeMC.animation.state;

public enum InputType {
    RIGHT_CLICK_START,
    RIGHT_CLICK_END,
    LEFT_CLICK, // Toggle Aim (if needed) or Interact
    SPRINT_START,
    SPRINT_END,
    RELOAD,
    SWAP_HAND // When item is swapped (handled by re-initialization usually, but might be
              // needed)
}
