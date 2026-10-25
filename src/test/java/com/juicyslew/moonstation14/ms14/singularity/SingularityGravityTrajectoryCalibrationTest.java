package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.movement.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Resolved-profile force regression; the motor scenario is intentionally a pure test fixture. */
class SingularityGravityTrajectoryCalibrationTest {
    private static final double DT = .05;

    @Test void prototypeManagerResolvesAbsentGravityFieldsToDefaults() throws Exception {
        var base = bundledPrototype();
        base.remove("gravityStrengthMultiplier");
        base.getAsJsonObject("eventHorizon").remove("playerLethalRadiusScale");
        base.getAsJsonObject("eventHorizon").remove("playerLethalRadiusMax");
        base.remove("gravityRamp");
        var baseId = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_defaults_parent");
        var childId = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_defaults_child");
        var child = new com.google.gson.JsonObject();
        child.addProperty("parent", baseId.toString());
        var manager = manager();
        manager.reload(ModSingularityGameplay.TYPE, Map.of(baseId, base, childId, child));
        var resolved = manager.snapshot(ModSingularityGameplay.TYPE).get(childId);
        assertNotNull(resolved);
        assertEquals(15, resolved.gravityStrengthMultiplier());
        assertEquals(3, resolved.gravityRangeMultiplier());
        assertEquals(2, resolved.gravityRampFullStrengthRadiusScale());
        assertEquals(.35, resolved.eventHorizon().playerLethalRadiusScale());
        assertEquals(.35, resolved.eventHorizon().playerLethalRadiusMax());
        assertEquals(3, resolved.gravityRangeMultiplier());
        assertEquals(2, resolved.gravityRampFullStrengthRadiusScale());
        assertEquals(.35, resolved.eventHorizon().playerLethalRadiusMax());
        assertEquals(10, resolved.gravityAcceleration(), "unmodified parent fields are inherited");
    }

    @Test void expandedGravityRangeHasContinuousWarningEnvelopeAtEveryLevel() throws Exception {
        var profile = loadProfile();
        for (int level = 1; level <= 6; level++) {
            double outer = profile.gravityPullRangeAtLevel(level);
            double inner = profile.gravityFullStrengthRadiusAtLevel(level);
            assertEquals(0, profile.gravityRadialEnvelopeAtLevel(level, outer), 1e-12);
            assertTrue(profile.gravityRadialEnvelopeAtLevel(level, (outer + inner) / 2) > 0);
            assertEquals(1, profile.gravityRadialEnvelopeAtLevel(level, inner), 1e-12);
            assertEquals(0, SingularityGravityPulse.sourceDeltaVelocity(new Vec3(outer, 0, 0), Vec3.ZERO, level, profile, DT).length(), 1e-12);
            double justInsideOuter = profile.gravityRadialEnvelopeAtLevel(level, outer - 1e-4);
            assertTrue(justInsideOuter > 0 && justInsideOuter < 1e-8, "smooth warning band tends continuously to zero at stage " + level);
            double warningDistance = (outer + inner) / 2;
            double warningForce = SingularityGravityPulse.sourceDeltaVelocity(new Vec3(warningDistance, 0, 0), Vec3.ZERO, level, profile, DT).length();
            double closerForce = SingularityGravityPulse.sourceDeltaVelocity(new Vec3(inner + (warningDistance - inner) * .25, 0, 0), Vec3.ZERO, level, profile, DT).length();
            assertTrue(closerForce > warningForce, "closer warning distance has stronger pull at stage " + level);
            assertTrue(profile.gravityPullRangeAtLevel(level) > profile.gravityRangeAtLevel(level));
        }
        assertEquals(12, profile.gravityPullRangeAtLevel(1), 1e-12);
        assertEquals(30, profile.gravityPullRangeAtLevel(4), 1e-12);
    }

    @Test void prototypeManagerOverridesInheritedGravityFieldsAndKernelConsumesThem() throws Exception {
        var base = bundledPrototype();
        base.remove("gravityStrengthMultiplier");
        base.getAsJsonObject("eventHorizon").remove("playerLethalRadiusScale");
        var baseId = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_override_parent");
        var childId = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_override_child");
        var child = new com.google.gson.JsonObject();
        child.addProperty("parent", baseId.toString());
        child.addProperty("gravityStrengthMultiplier", 7.5);
        child.add("gravityRamp", com.google.gson.JsonParser.parseString("{\"rangeMultiplier\":4.0,\"fullStrengthRadiusScale\":1.5}"));
        child.add("eventHorizon", com.google.gson.JsonParser.parseString("{\"playerLethalRadiusScale\":0.2,\"playerLethalRadiusMax\":0.5}"));
        var manager = manager();
        manager.reload(ModSingularityGameplay.TYPE, Map.of(baseId, base, childId, child));
        var resolved = manager.snapshot(ModSingularityGameplay.TYPE).get(childId);
        assertNotNull(resolved);
        assertEquals(7.5, resolved.gravityStrengthMultiplier());
        assertEquals(.2, resolved.eventHorizon().playerLethalRadiusScale());
        assertEquals(.5, resolved.eventHorizon().playerLethalRadiusMax());
        assertEquals(4, resolved.gravityRangeMultiplier());
        assertEquals(1.5, resolved.gravityRampFullStrengthRadiusScale());
        assertEquals(10, resolved.gravityAcceleration(), "nested parent fields remain inherited");
        assertEquals(5.5, resolved.eventHorizonRadiusAtLevel(6), "Minecraft terrain/event-horizon radius is unchanged");
        assertEquals(.5, resolved.playerLethalRadiusAtLevel(6), 1e-12, "player core uses inherited/overridden absolute cap");
        assertEquals(.5, resolved.playerLethalRadiusAtLevel(4), 1e-12, "L4 player core remains capped in blocks");
        assertEquals(3.5, resolved.eventHorizonRadiusAtLevel(4), 1e-12, "NPC/terrain horizon remains unchanged");
        Vec3 force = SingularityGravityPulse.sourceDeltaVelocity(new Vec3(4, 0, 0), Vec3.ZERO, 6, resolved, DT);
        Vec3 defaultForce = SingularityGravityPulse.sourceDeltaVelocity(new Vec3(4, 0, 0), Vec3.ZERO, 6, loadProfile(), DT);
        assertEquals(defaultForce.x * .5, force.x, 1e-12, "gravity kernel consumes resolved 7.5 multiplier at half the bundled 15 multiplier");
        assertEquals(32, resolved.gravityPullRangeAtLevel(3), 1e-12, "live gravity source range consumes inherited override");
        var invalid = bundledPrototype();
        invalid.addProperty("gravityStrengthMultiplier", 0);
        assertThrows(RuntimeException.class, () -> manager.reload(ModSingularityGameplay.TYPE, Map.of(baseId, invalid)),
                "invalid resolved multiplier is rejected by the actual prototype manager");
        var invalidCap = bundledPrototype();
        invalidCap.getAsJsonObject("eventHorizon").addProperty("playerLethalRadiusMax", 0);
        assertThrows(RuntimeException.class, () -> manager.reload(ModSingularityGameplay.TYPE, Map.of(baseId, invalidCap)),
                "invalid player core cap is rejected by the actual prototype manager");
    }
    @Test void selectedFactorFifteenPreservesEscapeInnerCaptureAndOverheadLiftTrajectories() throws Exception {
        SingularityGameplayProfile profile = loadProfile();
        assertEquals(15, profile.gravityStrengthMultiplier());
        assertEquals(.35, profile.eventHorizon().playerLethalRadiusScale());
        CharacterMovementMotor motor = resolvedHumanMotor();
        assertEquals(5.625, motor.policy().sprintSpeedPerSecond(), 1e-12, "resolved Minecraft human sprint policy");
        for (int level = 1; level <= 6; level++) {
            double oldRadius = profile.eventHorizonRadiusAtLevel(level);
            double outsideStart = oldRadius + 1e-4;
            Trajectory outer = simulate(motor, profile, level, outsideStart, true, false, 60);
            assertFalse(outer.captured(), "outside run must remain uncaptured at level " + level);
            assertTrue(outer.state().position().x() - outsideStart > 0,
                    "direct-away sprint gains ground over 3 seconds at level " + level);

            Trajectory inner = simulate(motor, profile, level, oldRadius * .6, true, false, 200);
            assertTrue(inner.captured(), "inner run reaches player core at level " + level);
            assertTrue(inner.ticks() <= 200, "inner capture occurs within 10 seconds at level " + level);

        }
        double overheadDistance = profile.eventHorizonRadiusAtLevel(6) * .8;
        Trajectory overhead = simulate(motor, profile, 6, overheadDistance, false, true, 200);
        assertTrue(overhead.liftTick() > 0, "grounded human body lifts under an overhead source");
        assertTrue(overhead.maxHeight() > 0, "grounded human rises under normal gravity plus overhead pull");
        assertTrue(SingularityGravityPulse.sourceDeltaVelocity(new Vec3(0, overheadDistance, 0), Vec3.ZERO, 6, profile, DT).y > 0,
                "overhead source produces upward delta velocity");
    }

    @Test void warningBandTrajectoryMeasuresFourSecondNeutralDriftThenThreeSecondRunout() throws Exception {
        SingularityGameplayProfile profile = loadProfile();
        CharacterMovementMotor motor = resolvedHumanMotor();
        final int neutralTicks = 80; // Four seconds at the production 20 Hz step.
        final int runoutTicks = 60; // Three seconds at the production 20 Hz step.
        for (int level = 1; level <= 6; level++) {
            double warningStart = profile.gravityPullRangeAtLevel(level) * .995;
            WarningTrajectory trajectory = simulateWarningReaction(motor, profile, level, warningStart, neutralTicks, runoutTicks);
            assertTrue(trajectory.inwardDrift() > .001,
                    "80 neutral ticks (4 s) at 99.5% of warning range should produce measurable modeled inward drift; level=" + level
                            + ", start=" + warningStart + ", drift=" + trajectory.inwardDrift());
            assertTrue(trajectory.escapeProgress() > 0,
                    "60 outward-run ticks (3 s) should gain ground after neutral delay; level=" + level
                            + ", start=" + warningStart + ", progress=" + trajectory.escapeProgress());
        }
    }

    private static CharacterMovementMotor resolvedHumanMotor() throws Exception {
        try (var json = new InputStreamReader(SingularityGravityTrajectoryCalibrationTest.class.getClassLoader()
                .getResourceAsStream("data/moonstation14/moonstation14/character/human.json"), StandardCharsets.UTF_8)) {
            var human = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.parse(
                    com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseReader(json)).getOrThrow(IllegalStateException::new);
            CharacterMovementMotor motor = new CharacterMovementMotor(CharacterMovementPolicy.fromCharacterData(human));
            assertEquals(5.625, motor.policy().sprintSpeedPerSecond(), 1e-12, "resolved Minecraft human sprint policy");
            return motor;
        }
    }

    private record Trajectory(CharacterMovementState state, int ticks, boolean captured, int liftTick, double maxHeight) { }
    private record WarningTrajectory(double inwardDrift, double escapeProgress) { }

    private static WarningTrajectory simulateWarningReaction(CharacterMovementMotor motor, SingularityGameplayProfile profile,
                                                               int level, double startDistance, int warningTicks, int escapeTicks) {
        MovementCollisionResolver floor = (p, requested, grounded) -> {
            double y = requested.y() < 0 && p.y() + requested.y() < 0 ? -p.y() : requested.y();
            boolean landed = requested.y() < 0 && y != requested.y();
            return new MovementCollisionResolver.CollisionResult(new MovementVector(requested.x(), y, requested.z()), landed || (requested.y() == 0 && grounded));
        };
        CharacterMovementState state = new CharacterMovementState(new MovementVector(startDistance, 0, 0), MovementVector.ZERO, true);
        for (int i = 0; i < warningTicks; i++) state = warningTick(motor, profile, level, state, 0, floor);
        double afterWarning = state.position().x();
        for (int i = 0; i < escapeTicks; i++) state = warningTick(motor, profile, level, state, 1, floor);
        return new WarningTrajectory(startDistance - afterWarning, state.position().x() - afterWarning);
    }

    private static CharacterMovementState warningTick(CharacterMovementMotor motor, SingularityGameplayProfile profile,
                                                       int level, CharacterMovementState state, double wish,
                                                       MovementCollisionResolver floor) {
        Vec3 delta = SingularityGravityPulse.sourceDeltaVelocity(Vec3.ZERO,
                new Vec3(state.position().x(), state.position().y(), state.position().z()), level, profile, DT);
        var env = new CharacterMovementEnvironment(DT, 32, 8.4, .98,
                new MovementVector(delta.x, delta.y, delta.z), delta.length(), 0, floor);
        return motor.tick(state, new CharacterMovementCommand(wish, 0, false, true), env, false);
    }

    private static Trajectory simulate(CharacterMovementMotor motor, SingularityGameplayProfile profile, int level, double distance,
                                       boolean horizontal, boolean overhead, int maxTicks) {
        MovementCollisionResolver floor = (p, requested, grounded) -> {
            double y = requested.y() < 0 && p.y() + requested.y() < 0 ? -p.y() : requested.y();
            boolean landed = requested.y() < 0 && y != requested.y();
            boolean stationarySupport = requested.y() == 0 && grounded;
            return new MovementCollisionResolver.CollisionResult(new MovementVector(requested.x(), y, requested.z()), landed || stationarySupport);
        };
        CharacterMovementState state = new CharacterMovementState(
                overhead ? MovementVector.ZERO : new MovementVector(distance, 0, 0), MovementVector.ZERO, true);
        int liftTick = -1;
        double maxHeight = 0;
        for (int i = 0; i < maxTicks; i++) {
            if ((!overhead && Math.abs(state.position().x()) <= profile.playerLethalRadiusAtLevel(level))
                    || (overhead && Math.abs(distance - state.position().y()) <= profile.playerLethalRadiusAtLevel(level)))
                return new Trajectory(state, i, true, liftTick, maxHeight);
            Vec3 center = overhead ? new Vec3(0, distance, 0) : Vec3.ZERO;
            Vec3 target = overhead ? new Vec3(state.position().x(), state.position().y(), state.position().z())
                    : new Vec3(state.position().x(), state.position().y(), state.position().z());
            Vec3 delta = SingularityGravityPulse.sourceDeltaVelocity(center, target, level, profile, DT);
            CharacterMovementEnvironment env = new CharacterMovementEnvironment(DT, 32, 8.4, .98,
                    new MovementVector(delta.x, delta.y, delta.z), delta.length(), 0, floor);
            state = motor.tick(state, new CharacterMovementCommand(horizontal ? 1 : 0, 0, false, true), env, false);
            if (!overhead && i == 0) assertTrue(state.onGround(), "floor collision resolver retains grounded support");
            if (overhead && liftTick < 0 && state.position().y() > 0) liftTick = i + 1;
            if ((!overhead && Math.abs(state.position().x()) <= profile.playerLethalRadiusAtLevel(level))
                    || (overhead && Math.abs(distance - state.position().y()) <= profile.playerLethalRadiusAtLevel(level)))
                return new Trajectory(state, i + 1, true, liftTick, maxHeight);
            maxHeight = Math.max(maxHeight, state.position().y());
        }
        boolean captured = overhead ? Math.abs(distance - state.position().y()) <= profile.playerLethalRadiusAtLevel(level)
                : Math.abs(state.position().x()) <= profile.playerLethalRadiusAtLevel(level);
        return new Trajectory(state, maxTicks, captured, liftTick, maxHeight);
    }

    private static SingularityGameplayProfile loadProfile() throws Exception {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity");
        PrototypeManager manager = manager();
        manager.reload(ModSingularityGameplay.TYPE, Map.of(id, bundledPrototype()));
        return manager.snapshot(ModSingularityGameplay.TYPE).get(id);
    }

    private static PrototypeManager manager() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModSingularityGameplay.TYPE);
        return manager;
    }

    private static com.google.gson.JsonObject bundledPrototype() throws Exception {
        try (var stream = SingularityGravityTrajectoryCalibrationTest.class.getClassLoader()
                .getResourceAsStream("data/moonstation14/singularity_gameplay/singularity.json")) {
            assertNotNull(stream, "bundled singularity gameplay prototype");
            return com.google.gson.JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
