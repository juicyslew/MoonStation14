package com.juicyslew.moonstation14.ms14.equipment.client;

/** Keeps the locally controlled character body out of its own first-person world pass. */
public final class BodyWorldRenderPolicy {
    private BodyWorldRenderPolicy() { }

    public static boolean allowOrdinaryPass(boolean locallyOwnedBody, boolean cameraBoundToBody,
                                            boolean firstPerson) {
        return !(locallyOwnedBody && cameraBoundToBody && firstPerson);
    }

    /** Allows exactly the separately-owned arms invocation, never an ordinary body render. */
    public static boolean allowOwnedGunArmsPass(boolean ownedCameraBody, boolean firstPerson,
                                                 boolean committedEpochPositive, boolean activeGun) {
        return ownedCameraBody && firstPerson && committedEpochPositive && activeGun;
    }
}
