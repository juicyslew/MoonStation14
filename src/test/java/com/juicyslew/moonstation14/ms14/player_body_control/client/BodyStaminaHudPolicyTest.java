package com.juicyslew.moonstation14.ms14.player_body_control.client;

import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BodyStaminaHudPolicyTest {
    private static StaminaPrototypeComponent capability(float capacity) {
        var d = StaminaPrototypeComponent.DEFAULT;
        return new StaminaPrototypeComponent(capacity, d.decay(), d.cooldownSeconds(), d.stunSeconds(),
                d.afterCritDecayMultiplier(), d.slowdownThresholds());
    }

    private static StaminaState state(float damage, boolean critical, boolean recovering) {
        return new StaminaState(damage, critical, recovering, 0);
    }

    @Test void capableBodyWithMissingAttachmentUsesFreshFullStateWithoutMaterialization() {
        // Callers supply EMPTY for a capable entity whose synchronized attachment is absent;
        // this pure projection cannot create or mutate an attachment.
        var view = BodyStaminaHudPolicy.project(capability(100), StaminaState.EMPTY);
        assertNotNull(view);
        assertEquals(100, view.remaining());
        assertEquals(100, view.capacity());
    }

    @Test void projectsHalfDamageAndNonDefaultCapacityFromSnapshot() {
        var half = BodyStaminaHudPolicy.project(capability(100), state(50, false, false));
        assertEquals(50, half.remaining());
        var larger = BodyStaminaHudPolicy.project(capability(200), state(50, false, false));
        assertEquals(150, larger.remaining());
        assertEquals(200, larger.capacity());
    }

    @Test void preservesCriticalAndAfterCriticalStatusAtZeroAndDuringRecovery() {
        var critical = BodyStaminaHudPolicy.project(capability(100), state(100, true, false));
        assertEquals(0, critical.remaining());
        assertTrue(critical.critical());
        var recovering = BodyStaminaHudPolicy.project(capability(100), state(75, false, true));
        assertEquals(25, recovering.remaining());
        assertTrue(recovering.afterCritical());
    }

    @Test void unsupportedOrInvalidCapacityDoesNotProject() {
        assertNull(BodyStaminaHudPolicy.project(null, StaminaState.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> capability(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> capability(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> capability(0));
    }
}
