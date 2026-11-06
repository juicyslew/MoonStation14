package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AirlockContactCaptureGateTest {
    @Test void observationGateRequiresExactArmedEpochEntityAndBody(){
        UUID body=UUID.randomUUID();
        assertFalse(AirlockContactCaptureGate.matches(0,8,body,8,body));
        assertTrue(AirlockContactCaptureGate.matches(4,8,body,8,body));
        assertFalse(AirlockContactCaptureGate.matches(4,8,body,9,body));
        assertFalse(AirlockContactCaptureGate.matches(4,8,body,8,UUID.randomUUID()));
        assertFalse(AirlockContactCaptureGate.matches(4,8,null,8,body));
    }
}
