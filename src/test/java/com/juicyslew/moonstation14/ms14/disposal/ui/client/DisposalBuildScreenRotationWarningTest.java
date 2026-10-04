package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DisposalBuildScreenRotationWarningTest {
    @Test void warnsOnlyForActuallyLostReciprocalConnections() {
        assertEquals(1, DisposalBuildScreen.lostConnectionCount(
                Set.of(DisposalDirection.NORTH, DisposalDirection.EAST), Set.of(DisposalDirection.EAST),
                Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH)));
        assertEquals(0, DisposalBuildScreen.lostConnectionCount(
                Set.of(DisposalDirection.NORTH), Set.of(DisposalDirection.EAST), Set.of(Direction.EAST)));
    }
}
