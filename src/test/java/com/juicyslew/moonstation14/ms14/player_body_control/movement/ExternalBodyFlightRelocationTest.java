package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExternalBodyFlightRelocationTest {
    @Test
    void locationBoundaryChangesOnlyAcrossChunkOrDimension() {
        var before = ExternalBodyFlight.boundary(Level.OVERWORLD, -0.01, 32.01);
        assertFalse(ExternalBodyFlight.boundaryChanged(before,
                ExternalBodyFlight.boundary(Level.OVERWORLD, -0.5, 32.99)));
        assertEquals(-1, before.chunkX(), "negative positions use floor-based Minecraft chunk coordinates");
        assertTrue(ExternalBodyFlight.boundaryChanged(before,
                ExternalBodyFlight.boundary(Level.OVERWORLD, 0.01, 32.01)));
        assertTrue(ExternalBodyFlight.boundaryChanged(before,
                ExternalBodyFlight.boundary(Level.NETHER, -0.01, 32.01)));
    }

    @Test
    void relocationProofRejectsCanceledOrReplacedFlightAndChangedBinding() {
        Object body = new Object();
        Object activeFlight = new Object();
        Object replacedFlight = new Object();
        var binding = new PlayerCharacterBinding(UUID.randomUUID(), "main", UUID.randomUUID());
        assertTrue(ExternalBodyFlight.ownerProofMatches(body, body, activeFlight, activeFlight,
                binding, binding));
        assertFalse(ExternalBodyFlight.ownerProofMatches(body, body, null, activeFlight,
                binding, binding), "cancelled flight no longer proves custody");
        assertFalse(ExternalBodyFlight.ownerProofMatches(body, body, replacedFlight, activeFlight,
                binding, binding), "replacement flight cannot satisfy stale proof");
        var changedBinding = new PlayerCharacterBinding(binding.accountId(), "other", binding.mindId());
        assertFalse(ExternalBodyFlight.ownerProofMatches(body, body, activeFlight, activeFlight,
                changedBinding, binding), "changed lifecycle binding rejects stale proof");
        assertFalse(ExternalBodyFlight.ownerProofMatches(new Object(), body, activeFlight, activeFlight,
                binding, binding), "replacement entity cannot satisfy exact-body proof");
    }
}
