package com.juicyslew.moonstation14.ms14.player_body_control.server;

import com.juicyslew.moonstation14.ms14.player_body_control.BodyControlRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GhostRecoveryTest {
    @Test
    void detachedInvalidCharacterCanRecoverToPreservedGhostAtCurrentEpoch() {
        BodyControlRegistry registry = new BodyControlRegistry();
        UUID session = UUID.randomUUID();
        MobHarnessId ghost = new MobHarnessId(UUID.randomUUID());
        MobHarnessId character = new MobHarnessId(UUID.randomUUID());
        registry.registerHarness(new MobHarness(ghost, MobHarnessKind.GHOST));
        registry.registerHarness(new MobHarness(character, MobHarnessKind.CHARACTER));
        var initial = registry.createMind(session, ghost, target -> true).orElseThrow();
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.transfer(session, character, initial.epoch(), target -> true));

        var characterOwned = registry.mind(session).orElseThrow();
        // Eligibility checks revoke the now-invalid body and advance the epoch before recovery.
        assertEquals(false, registry.authorizes(session, character, characterOwned.epoch(), target -> false));
        var detached = registry.mind(session).orElseThrow();
        assertNull(detached.harnessId());
        assertEquals(BodyControlRegistry.OperationResult.CHANGED,
                registry.attach(session, ghost, detached.epoch(), target -> target.kind() == MobHarnessKind.GHOST));
        assertEquals(ghost, registry.mind(session).orElseThrow().harnessId());
    }
}
