package com.juicyslew.moonstation14.ms14.disposal.containment;

/** Client presentation marker only; containment authority remains the server service membership. */
public interface DisposalContainedVisual {
    boolean moonstation14$isDisposalContainedVisual();
    void moonstation14$setDisposalContainedVisual(boolean contained);

    static boolean shouldHide(boolean containedMarker) { return containedMarker; }
}
