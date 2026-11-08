package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent;

/** Narrow opt-in contract for Tesla lightning targets implemented by supported block entities. */
public interface LightningConductor {
    LightningTargetComponent lightningTarget();

    /** Returns true only when this strike was accepted (for example, by a charged coil). */
    boolean receiveLightningStrike();
}
