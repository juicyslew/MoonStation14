package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.ms14.activity.EntityActivity;

/** Activity routing policy for a player acting only as an experimental-session carrier. */
public final class CarrierActivityPolicy {
    private CarrierActivityPolicy() { }

    /** Carrier transports do not tick character-body work; environmental exposure remains eligible. */
    public static boolean shouldRun(EntityActivity activity, boolean carrier) {
        if (!carrier) return true;
        return switch (activity) {
            case STATUS_EFFECT, ALERT, REAGENT_METABOLISM, FIRE_DRYING, HUNGER, THIRST -> false;
            case BODY_TEMPERATURE, RESPIRATION_EXPOSURE -> true;
        };
    }
}
