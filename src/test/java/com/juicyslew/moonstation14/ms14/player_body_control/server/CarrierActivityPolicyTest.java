package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarrierActivityPolicyTest {
    @Test
    void onlyCharacterBodyActivitiesAreSuppressedForCarriers() {
        for (EntityActivity activity : EntityActivity.values()) {
            boolean characterBodyActivity = switch (activity) {
                case STATUS_EFFECT, ALERT, REAGENT_METABOLISM, FIRE_DRYING, HUNGER, THIRST -> true;
                case BODY_TEMPERATURE, RESPIRATION_EXPOSURE -> false;
            };

            assertEquals(!characterBodyActivity, CarrierActivityPolicy.shouldRun(activity, true),
                    "unexpected carrier routing for " + activity);
            assertEquals(true, CarrierActivityPolicy.shouldRun(activity, false),
                    "legacy routing changed for " + activity);
        }
    }
}
