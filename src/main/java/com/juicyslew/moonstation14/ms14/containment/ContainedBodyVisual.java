package com.juicyslew.moonstation14.ms14.containment;

/** Client presentation marker only; containment authority remains the server service membership. */
public interface ContainedBodyVisual {
    boolean moonstation14$isContainedBodyVisual();
    void moonstation14$setContainedBodyVisual(boolean contained);

    static boolean shouldHide(boolean containedMarker) { return containedMarker; }
}
