package com.juicyslew.moonstation14.ms14.player_body_control.movement;

import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.movement.AirMotionReference;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AirMotionReferenceTransitionTest {
    @Test void freshGroundedFrameToWorldDepartureSurvivesNullMotorReference() {
        UUID frame = UUID.randomUUID();
        var before = new LoadedFrameRayQuery.ActorPoint(new Vec3(4, 5, 6), new Vec3(1, 2, 3), frame);
        var after = new LoadedFrameRayQuery.ActorPoint(new Vec3(4, 5, 6), new Vec3(4, 5, 6), null);
        var launch = AirMotionReferenceTransition.departure(true, true, false, before, after,
                new MovementVector(2, 0, -1));
        assertNotNull(launch);
        assertEquals(frame, launch.frameId());
        assertEquals(new MovementVector(2, 0, -1), launch.pointVelocity());
        assertEquals(launch, AirMotionReferenceTransition.afterSuccessfulMotor(false, null, launch));
    }

    @Test void rejectsUnfreshOrNonWorldDeparturesPreservesAirReferenceAndClearsLanding() {
        UUID frame = UUID.randomUUID();
        var point = new LoadedFrameRayQuery.ActorPoint(Vec3.ZERO, Vec3.ZERO, frame);
        assertNull(AirMotionReferenceTransition.departure(false, true, false, point, null, MovementVector.ZERO));
        assertNull(AirMotionReferenceTransition.departure(true, true, false, point, point, MovementVector.ZERO));
        assertNull(AirMotionReferenceTransition.departure(true, false, false, point, null, MovementVector.ZERO));
        var frozen = new AirMotionReference(frame, MovementVector.ZERO);
        assertEquals(frozen, AirMotionReferenceTransition.afterSuccessfulMotor(false, frozen, null));
        assertNull(AirMotionReferenceTransition.afterSuccessfulMotor(true, frozen, null));
    }

    @Test void frozenWorldVelocityIsConvertedToFrameBasisInMetersPerSecondOnAllAxes() {
        var reference = new AirMotionReference(UUID.randomUUID(), new MovementVector(2, 3, -4));
        var framePointVelocity = new MovementVector(1, -2, .5);
        var baseline = AirMotionReferenceTransition.baselineInMotorBasis(reference, framePointVelocity);
        assertEquals(new MovementVector(1, 5, -4.5), baseline);
        // A frame-associated actor and a world-associated actor describe the same physical velocity
        // when the frame contribution is recomposed; this must not introduce a blocks/tick factor.
        assertEquals(reference.pointVelocity(), baseline.add(framePointVelocity));
        assertNull(AirMotionReferenceTransition.baselineInMotorBasis(reference, null),
                "unknown frame basis must pause/recover, not pass a world-space baseline to frame-space motor");
        assertNull(AirMotionReferenceTransition.baselineInMotorBasis(null, framePointVelocity));
    }

    @Test void clientAndServerUseSharedBasisConversionAndServerRejectsMissingFrameVelocity() throws Exception {
        Path root=Path.of("").toAbsolutePath().normalize();
        while(root!=null&&!Files.isRegularFile(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java")))root=root.getParent();
        assertNotNull(root,"project root");
        String server=Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep.java"));
        String client=Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.java"));
        assertTrue(server.contains("baselineInMotorBasis(storedAirReference"));
        assertTrue(server.contains("if (pointVelocity == null) return Optional.empty()"));
        assertFalse(server.contains("pointVelocity.x * 20d"));
        assertTrue(client.contains("baselineInMotorBasis(airReference"));
    }
}
