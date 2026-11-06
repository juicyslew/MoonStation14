package com.juicyslew.moonstation14.ms14.shuttle.sable;

public interface FrameAnchorPending {
    FrameAnchorState sable$frameAnchorState();
    default boolean sable$frameAnchorInitializationPending() { return sable$frameAnchorState().pending(); }
    default FrameAnchor sable$pendingFrameAnchor() { return sable$frameAnchorState().pending() ? sable$frameAnchorState().anchor() : null; }
}
