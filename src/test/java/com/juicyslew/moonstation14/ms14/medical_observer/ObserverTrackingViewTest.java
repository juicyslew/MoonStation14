package com.juicyslew.moonstation14.ms14.medical_observer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObserverTrackingViewTest {
    @Test void controlledBodyViewUsesValidatedBodyAndMissingContextKeepsCarrier() {
        Vec3 carrier=new Vec3(0,64,0), body=new Vec3(256,70,0);
        assertEquals(body,ObserverTracking.activeBodyOrCarrier(carrier,body));
        assertEquals(carrier,ObserverTracking.activeBodyOrCarrier(carrier,null));
    }
}
