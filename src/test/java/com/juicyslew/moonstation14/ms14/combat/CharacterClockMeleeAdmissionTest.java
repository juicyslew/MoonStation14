package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CharacterClockMeleeAdmissionTest {
    @Test void onlyMatchingConfirmedSessionClockGrantsHandlessServerTime() {
        UUID body = UUID.randomUUID();
        var context = new GhostControlClient.CharacterSessionClockContext(body, 8, "minecraft:overworld", 1234);
        assertEquals(1234, CharacterClockMeleeAdmission.estimate(body, 8, "minecraft:overworld", context).orElseThrow());
        assertTrue(CharacterClockMeleeAdmission.estimate(body, 9, "minecraft:overworld", context).isEmpty());
        assertTrue(CharacterClockMeleeAdmission.estimate(UUID.randomUUID(), 8, "minecraft:overworld", context).isEmpty());
        assertTrue(CharacterClockMeleeAdmission.estimate(body, 8, "minecraft:the_nether", context).isEmpty());
        assertTrue(CharacterClockMeleeAdmission.estimate(body, 8, "minecraft:overworld", null).isEmpty());
    }
}
