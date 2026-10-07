package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;

/** Shared target rule: only an unoccupied steel host may receive a straight sealed crossing. */
public final class DisposalConstructionTargetPolicy {
    private DisposalConstructionTargetPolicy() { }
    public static boolean sealed(boolean eligibleSteelHost, boolean liveTubeAtHost, DisposalEntityData.Kind kind) {
        return eligibleSteelHost && !liveTubeAtHost && kind == DisposalEntityData.Kind.STRAIGHT;
    }
}
