package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NativeSideCollisionWitnessTest {
    @Test void geometryOnlyFirstNormalDoesNotBlockLaterSingleOwnProjection() {
        for (int substeps = 1; substeps <= 10; substeps++) {
            var request = new MovementVector(.125, .25, .3125);
            var frame = UUID.randomUUID();
            var rows = coherentTwoCubeCornerRows(request, substeps, frame);
            assertEquals(2, rows.size(), "one geometry-only A push and one final B own projection n=" + substeps);
            assertTrue(rows.getFirst().selectedX() > 0d && rows.getFirst().selectedZ() == 0d,
                    "A depenetrates only along +X while own motion is away from A");
            assertTrue(rows.getLast().selectedZ() < 0d && rows.getLast().selectedX() == 0d,
                    "B supplies the only inward own projection, along -Z");
            assertEquals(substeps == 1 ? 1 : substeps, rows.getLast().substep());

            var selected = rows.stream().map(r -> new MovementVector(r.selectedX(), r.selectedY(), r.selectedZ())).toList();
            var replay = NativeSideContactProof.replayOwnResponsesAt(request, substeps, rows, selected);
            assertEquals(1, replay.projections().size(), "only B clips the own request");
            var bounded = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.accepted(request,
                    request.add(replay.aggregate()));
            var first = rows.getFirst();
            double firstLength = Math.sqrt(first.selectedX()*first.selectedX() + first.selectedY()*first.selectedY()
                    + first.selectedZ()*first.selectedZ());
            assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validate(request, bounded,
                    first.selectedX()/firstLength, first.selectedY()/firstLength, first.selectedZ()/firstLength).isEmpty(),
                    "the first SAT normal (+X) correctly rejects movement-away evidence");
            assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                    request, bounded, substeps, replay.projections()).isPresent(),
                    "the one actual B projection independently passes the existing strict ordered validator");

            var responses = rows.stream().mapToDouble(CurrentFrameCollisionScope.NativeCollisionTrace::responseX).sum();
            var responseY = rows.stream().mapToDouble(CurrentFrameCollisionScope.NativeCollisionTrace::responseY).sum();
            var responseZ = rows.stream().mapToDouble(CurrentFrameCollisionScope.NativeCollisionTrace::responseZ).sum();
            var nativeMotion = new MovementVector(request.x()+responses, request.y()+responseY, request.z()+responseZ);
            var diagnostic = NativeSideContactProof.validateDetailed(request, nativeMotion, nativeMotion, frame, rows,
                    row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.NONE, diagnostic.reason(),
                    "coherent SAT proof should accept; observed reason=" + diagnostic.reason() + " row="
                            + diagnostic.rowIndex() + " projections=" + replay.projections().size()
                            + " firstNormalRejected=true orderedAccepted=true n=" + substeps);
            var proof = diagnostic.result().orElseThrow();
            assertEquals(request.x(), proof.boundedOwn().x(), 0d);
            assertEquals(request.y(), proof.boundedOwn().y(), 0d);
            assertEquals(request.z()+rows.getLast().selectedZ(), proof.boundedOwn().z(), 1e-12);
            assertEquals(rows.getFirst().selectedX(), proof.geometryCorrection().x(), 1e-6);
            assertEquals(0d, proof.geometryCorrection().y(), 1e-12);
            assertEquals(0d, proof.geometryCorrection().z(), 1e-12);
            assertNotNull(proof.horizontalContact());
            assertTrue(proof.horizontalContact().matches(request, proof.boundedOwn()));

            var position = com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence.validateSide(
                    request, proof.boundedOwn(), proof.geometryCorrection(), proof.horizontalContact()).orElseThrow();
            assertTrue(position.matches(request, proof.boundedOwn(), false));

            var firstOnly = List.of(rows.getFirst());
            var firstOnlyNative = request.add(new MovementVector(rows.getFirst().responseX(),
                    rows.getFirst().responseY(), rows.getFirst().responseZ()));
            var awayOnly = NativeSideContactProof.validateDetailed(request, firstOnlyNative, firstOnlyNative, frame,
                    firstOnly, row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.NONE, awayOnly.reason());
            assertEquals(request, awayOnly.result().orElseThrow().boundedOwn(),
                    "a geometry-only contact moving away cannot clip its normal component");
            assertNull(awayOnly.result().orElseThrow().horizontalContact(), "no actual own projection, no contact plane");

            var wrongRequest = NativeSideContactProof.validateDetailed(request.add(new MovementVector(.01,0,0)),
                    nativeMotion, nativeMotion, frame, rows,
                    row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.REQUEST_ACCUMULATION_MISMATCH, wrongRequest.reason());
            var wrongNative = NativeSideContactProof.validateDetailed(request,
                    nativeMotion.add(new MovementVector(.01,0,0)), nativeMotion.add(new MovementVector(.01,0,0)),
                    frame, rows, row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.FINAL_TRACE_RECONSTRUCTION_MISMATCH, wrongNative.reason());
            var wrongMeasured = NativeSideContactProof.validateDetailed(request, nativeMotion,
                    nativeMotion.add(new MovementVector(0,1e-4,0)), frame, rows,
                    row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            assertEquals(NativeSideContactProof.Failure.UNEXPLAINED_Y, wrongMeasured.reason());
            var wrongFrameRows = List.of(copyTrace(rows.getFirst(), UUID.randomUUID(), rows.getFirst().geometry()), rows.getLast());
            var wrongFrame = NativeSideContactProof.validateDetailed(request, nativeMotion, nativeMotion, frame,
                    wrongFrameRows, row -> true);
            assertEquals(NativeSideContactProof.Failure.UNKNOWN_DATA_OR_FRAME, wrongFrame.reason());
            var staleOrder = NativeSideContactProof.validateDetailed(request, nativeMotion, nativeMotion, frame,
                    List.of(rows.getLast(), rows.getFirst()), row -> true);
            assertNotEquals(NativeSideContactProof.Failure.NONE, staleOrder.reason(),
                    "stale reordered trace must not certify movement n=" + substeps);
            assertTrue(staleOrder.result().isEmpty());
            var unknownShape = NativeSideContactProof.validateDetailed(request, nativeMotion, nativeMotion, frame,
                    rows, row -> false);
            assertEquals(NativeSideContactProof.Failure.UNKNOWN_DATA_OR_FRAME, unknownShape.reason());
            var last = rows.getLast();
            var alteredGeometry = new NativeCollisionGeometry(
                    last.geometry().entityBoundsCenter().add(new MovementVector(0,0,.01)),
                    last.geometry().entityBoundsDimensions(), last.geometry().entityBoundsOrientation(),
                    last.geometry().subLevelPose(), last.geometry().selectedStorageBounds(),
                    last.geometry().maxMtv(), last.geometry().collisionMotion(), last.geometry().iteration(),
                    last.geometry().substeps());
            var tamperedGeometry = new java.util.ArrayList<>(rows);
            tamperedGeometry.set(tamperedGeometry.size()-1, copyTrace(last, frame, alteredGeometry));
            var wrongGeometry = NativeSideContactProof.validateDetailed(request, nativeMotion, nativeMotion, frame,
                    tamperedGeometry, row -> true);
            assertNotEquals(NativeSideContactProof.Failure.NONE, wrongGeometry.reason());
            assertTrue(wrongGeometry.result().isEmpty());

            var resolver = (com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver)
                    (p, requested, wasGrounded) -> {
                        assertEquals(request, requested);
                        return new com.juicyslew.moonstation14.ms14.movement.MovementCollisionResolver.CollisionResult(
                                proof.boundedOwn(), false, proof.horizontalContact(), position);
                    };
            var environment = new com.juicyslew.moonstation14.ms14.movement.CharacterMovementEnvironment(
                    1d, 0d, 0d, 1d, MovementVector.ZERO, 0d, 0d, resolver);
            var motor = new com.juicyslew.moonstation14.ms14.movement.CharacterMovementMotor(
                    new com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy(0,0,0,0,0,0));
            var stepped = motor.tick(new com.juicyslew.moonstation14.ms14.movement.CharacterMovementState(
                    MovementVector.ZERO, request, false),
                    new com.juicyslew.moonstation14.ms14.movement.CharacterMovementCommand(0,0,false,false),
                    environment, false);
            assertFalse(stepped.onGround(), "side geometry cannot mint floor support");
            assertEquals(proof.boundedOwn().x()+proof.geometryCorrection().x(), stepped.position().x(), 1e-12,
                    "A correction is applied once to position");
            assertEquals(proof.boundedOwn().y(), stepped.position().y(), 1e-12);
            assertEquals(proof.boundedOwn().z()+proof.geometryCorrection().z(), stepped.position().z(), 1e-12);
            assertEquals(request.x(), stepped.velocity().x(), 1e-12, "A's away normal does not clip X velocity");
            assertEquals(request.y(), stepped.velocity().y(), 1e-12, "side response preserves Y velocity");
            assertEquals(0d, stepped.velocity().z(), 1e-12, "B's actual -Z plane clips only inward Z velocity");
        }
    }

    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> coherentTwoCubeCornerRows(
            MovementVector request, int substeps, UUID frame) {
        var dimensions = new MovementVector((double) .6f, (double) 1.8f, (double) .6f);
        var orientation = new NativeCollisionGeometry.QuaternionValue(0d,0d,0d,1d);
        var pose = new NativeFrameInterval.PoseValue(new org.joml.Matrix4d().get(new double[16]));
        var halfWidth = dimensions.x() * .5d;
        var initial = new MovementVector(.875d, 1d,
                1d - halfWidth - request.z() * (1d - .5d / substeps));
        var walls = List.of(
                new CornerWall(net.minecraft.core.BlockPos.ZERO, new AABB(0,0,0,1,1,1)),
                new CornerWall(new net.minecraft.core.BlockPos(1,0,1), new AABB(1,0,1,2,1,2)));
        var witness = new NativeSideCollisionWitness();
        var result = new java.util.ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        var responses = MovementVector.ZERO;
        for (int step = 1; step <= substeps; step++) {
            int iteration = 0;
            for (var wall : walls) {
                var before = request.scale(step / (double) substeps).add(responses);
                var center = initial.add(before);
                var template = new NativeCollisionGeometry(center, dimensions, orientation, pose, wall.bounds(),
                        MovementVector.ZERO, before, iteration, substeps);
                var selected = witness.separation(template);
                boolean expectedContact = (step == 1 && wall.pos().equals(net.minecraft.core.BlockPos.ZERO))
                        || (step == substeps && wall.pos().equals(new net.minecraft.core.BlockPos(1,0,1)));
                double length = selected == null ? Double.NaN
                        : Math.sqrt(selected.x()*selected.x()+selected.y()*selected.y()+selected.z()*selected.z());
                if (expectedContact) {
                    assertNotNull(selected, "SDK SAT for scheduled block " + wall.pos() + " n=" + substeps);
                    assertTrue(length > com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                            "scheduled SAT contact is nonzero and above existing slop: " + selected + " n=" + substeps);
                    var geometry = new NativeCollisionGeometry(center, dimensions, orientation, pose, wall.bounds(),
                            selected, before, iteration, substeps);
                    result.add(new CurrentFrameCollisionScope.NativeCollisionTrace(frame, step, substeps, iteration,
                            wall.pos(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), "stone", "stone",
                            selected.x(), selected.y(), selected.z(), before.x(), before.y(), before.z(),
                            selected.x(), selected.y(), selected.z(), selected.x(), selected.y(), selected.z(), geometry, false));
                    responses = responses.add(selected);
                    iteration++;
                } else {
                    assertNotNull(selected, "SDK SAT candidate for unscheduled block " + wall.pos());
                    assertTrue(length <= com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                            "no additional candidate contact at step " + step + " block " + wall.pos() + ": " + selected);
                }
            }
        }
        var finalBefore = request.add(responses);
        var finalCenter = initial.add(finalBefore);
        for (var wall : walls) {
            var finalGeometry = new NativeCollisionGeometry(finalCenter, dimensions, orientation, pose, wall.bounds(),
                    MovementVector.ZERO, finalBefore, 0, substeps);
            var remaining = witness.separation(finalGeometry);
            assertNotNull(remaining);
            assertEquals(MovementVector.ZERO, remaining, "final full body is clear of " + wall.pos() + " n=" + substeps);
        }
        return List.copyOf(result);
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace copyTrace(
            CurrentFrameCollisionScope.NativeCollisionTrace row, UUID frame, NativeCollisionGeometry geometry) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, row.substep(), row.substeps(), row.iteration(),
                row.selectedPos(), row.selectedState(), row.block(), row.blockState(), row.selectedX(), row.selectedY(),
                row.selectedZ(), row.collisionMotionBeforeX(), row.collisionMotionBeforeY(), row.collisionMotionBeforeZ(),
                row.appliedX(), row.appliedY(), row.appliedZ(), row.responseX(), row.responseY(), row.responseZ(),
                geometry, row.verticalCollision());
    }

    private record CornerWall(net.minecraft.core.BlockPos pos, AABB bounds) { }

    @Test void capturedDynamicYawCornerCompoundReplaysCompleteNativeProof() {
        var root = OwnerTraceFixture.load("owner-first-pure-air-compound.json");
        var frame = UUID.fromString(root.get("frameId").getAsString());
        var request = OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion = OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured = OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows = OwnerTraceFixture.rows(root.getAsJsonArray("rows"), frame);
        var expectedSteps = List.of(1, 1, 7, 8, 9, 9, 10, 10);
        var expectedIterations = List.of(0, 1, 0, 0, 0, 1, 0, 1);
        assertEquals(8, rows.size());
        assertEquals(10, root.getAsJsonArray("yawSamples").size());
        assertTrue(root.getAsJsonObject("source").getAsJsonObject("captureCompleteness")
                .get("yawSamplesComplete").getAsBoolean());
        for (var sample : root.getAsJsonArray("yawSamples"))
            assertFalse(sample.getAsJsonObject().get("currentGeometry").getAsBoolean(), "captured stock interval yaw sample");

        var witness = new NativeSideCollisionWitness();
        MovementVector previousResponses = MovementVector.ZERO;
        MovementVector origin = null;
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var geometry = row.geometry();
            assertEquals(expectedSteps.get(i), row.substep(), "captured substep row " + i);
            assertEquals(expectedIterations.get(i), row.iteration(), "captured iteration row " + i);
            assertEquals(10, row.substeps());
            assertEquals("moonstation14:steel_wall_block",
                    root.getAsJsonArray("rows").get(i).getAsJsonObject().get("blockId").getAsString());
            var yawSample = root.getAsJsonArray("yawSamples").get(row.substep() - 1).getAsJsonObject();
            var capturedFramePose = row.substep() == 1
                    ? root.getAsJsonArray("yawSamples").get(1).getAsJsonObject().getAsJsonArray("sdkSourcePose")
                    : yawSample.getAsJsonArray("selectedBodyPose");
            assertArrayEquals(jsonDoubles(capturedFramePose), geometry.subLevelPose().matrix(), 0d,
                    "captured per-step frame pose row " + i);
            double selectedYaw = yawSample.get("selectedBodyYaw").getAsDouble();
            assertEquals(Math.sin(selectedYaw * .5), geometry.entityBoundsOrientation().y(), 1e-12,
                    "captured dynamic body yaw quaternion Y row " + i);
            assertEquals(Math.cos(selectedYaw * .5), geometry.entityBoundsOrientation().w(), 1e-12,
                    "captured dynamic body yaw quaternion W row " + i);

            var rowOrigin = new MovementVector(geometry.entityBoundsCenter().x() - geometry.collisionMotion().x(),
                    geometry.entityBoundsCenter().y() - geometry.collisionMotion().y(),
                    geometry.entityBoundsCenter().z() - geometry.collisionMotion().z());
            if (origin == null) origin = rowOrigin;
            assertEquals(origin, rowOrigin, "constant captured body origin row " + i);
            var expectedBefore = request.scale(row.substep() / (double) row.substeps()).add(previousResponses);
            var actualBefore = geometry.collisionMotion();
            assertUlpNear(expectedBefore.x(), actualBefore.x(), 4, "prefix X row " + i);
            assertUlpNear(expectedBefore.y(), actualBefore.y(), 4, "prefix Y row " + i);
            assertUlpNear(expectedBefore.z(), actualBefore.z(), 4, "prefix Z row " + i);

            var replayed = witness.separation(geometry);
            assertNotNull(replayed, "SDK SAT witness row " + i);
            // Native captures retain f32-side values; use the production witness's existing residual budget.
            assertEquals(row.selectedX(), replayed.x(), com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                    "SAT MTV X row " + i);
            assertEquals(row.selectedY(), replayed.y(), com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                    "SAT MTV Y row " + i);
            assertEquals(row.selectedZ(), replayed.z(), com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.MAX_RESIDUAL,
                    "SAT MTV Z row " + i);
            assertEquals(row.selectedX(), row.appliedX(), 0d, "selected/applied X row " + i);
            assertEquals(row.selectedY(), row.appliedY(), 0d, "selected/applied Y row " + i);
            assertEquals(row.selectedZ(), row.appliedZ(), 0d, "selected/applied Z row " + i);
            assertEquals(row.appliedX(), row.responseX(), 1e-6, "applied/response X row " + i);
            assertEquals(row.appliedY(), row.responseY(), 1e-6, "applied/response Y row " + i);
            assertEquals(row.appliedZ(), row.responseZ(), 1e-6, "applied/response Z row " + i);
            previousResponses = previousResponses.add(new MovementVector(row.responseX(), row.responseY(), row.responseZ()));
        }
        assertEquals(OwnerTraceFixture.vector(root.getAsJsonObject("source").getAsJsonObject("bodyCenterOrigin")
                .getAsJsonArray("first")), origin);

        var proof = NativeSideContactProof.validateDetailed(request, nativeMotion, measured, frame, rows,
                row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertEquals(NativeSideContactProof.Failure.NONE, proof.reason(), "row=" + proof.rowIndex());
        var result = proof.result().orElseThrow();
        assertEquals(OwnerTraceFixture.vector(root.getAsJsonArray("boundedOwn")), result.boundedOwn());
        assertEquals(OwnerTraceFixture.vector(root.getAsJsonArray("geometryCorrection")), result.geometryCorrection());
        assertEquals(OwnerTraceFixture.vector(root.getAsJsonObject("horizontalContactEvidence").getAsJsonArray("accepted")),
                result.boundedOwn());
        assertNotNull(result.horizontalContact());
        assertTrue(result.horizontalContact().matches(request, result.boundedOwn()));

        var first = rows.getFirst();
        double firstLength = Math.sqrt(first.selectedX()*first.selectedX() + first.selectedY()*first.selectedY()
                + first.selectedZ()*first.selectedZ());
        var firstPlane = com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validate(
                request, result.boundedOwn(), first.selectedX()/firstLength, first.selectedY()/firstLength,
                first.selectedZ()/firstLength);
        var selected = rows.stream().map(row -> new MovementVector(row.selectedX(), row.selectedY(), row.selectedZ())).toList();
        var replay = NativeSideContactProof.replayOwnResponsesAt(request, 10, rows, selected);
        assertTrue(replay.projections().size() > 1, "captured proof exercises its ordered fallback domain");
        var ordered = com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                request, result.boundedOwn(), 10, replay.projections());
        assertTrue(ordered.isPresent(), "captured ordered projection replay is accepted");
        assertTrue(firstPlane.isEmpty(), "first captured plane alone does not certify the final compound response");

        var position = com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence.validateSide(
                request, result.boundedOwn(), result.geometryCorrection(), result.horizontalContact()).orElseThrow();
        assertTrue(position.matches(request, result.boundedOwn(), false));
    }

    private static void assertUlpNear(double expected, double actual, int ulps, String message) {
        double limit = ulps * Math.max(Math.ulp(expected), Math.ulp(actual));
        assertTrue(Math.abs(expected - actual) <= limit,
                message + " expected=" + expected + " actual=" + actual + " ulpLimit=" + limit);
    }

    private static double[] jsonDoubles(com.google.gson.JsonArray values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i).getAsDouble();
        return result;
    }

    @Test void actualSatProjectionIsSnappedBeforeOrderedExactOutputComparison() {
        // Minimal deterministic candidate found by seed 20261103's bounded SAT search. This is a
        // numerical SAT-linked regression, not a replay of the owner's missing corner trace.
        var center = new MovementVector(.02015124831930573, 1.156746791636065, .31590066011345874);
        var dimensions = new MovementVector(.6, 1.8, .6);
        var orientation = new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1);
        double[] matrix = { .7342028536973959, 3.836648810901661e-5, -.6789301644135521, 0,
                -2.816878507671925e-5, .9999999992640063, 2.6048166098996524e-5, 0,
                .6789301649132404, 0, .7342028542377645, 0, 0, 0, 0, 1 };
        var pose = new NativeFrameInterval.PoseValue(matrix);
        var bounds = new AABB(0, 0, 0, 1, 1, 1);
        var request = new MovementVector(-.21794275190199694, .25, -.2356857873995437);
        var template = new NativeCollisionGeometry(center, dimensions, orientation, pose, bounds,
                MovementVector.ZERO, request, 0, 1);
        var witness = new NativeSideCollisionWitness();
        var mtv = witness.separation(template);
        assertNotNull(mtv);
        assertEquals(-.16468088450532747, mtv.x(), 0d);
        assertEquals(0d, mtv.y(), 0d);
        assertEquals(.15228328169785724, mtv.z(), 0d);

        UUID frame = UUID.randomUUID();
        var geometry1 = new NativeCollisionGeometry(center, dimensions, orientation, pose, bounds,
                mtv, request, 0, 1);
        var rows = List.of(traceRow(frame, mtv, request, geometry1, 0));
        assertEquals(mtv, witness.separation(geometry1), "stored typed geometry replays through SDK SAT");
        var replay = NativeSideContactProof.replayOwnResponsesAt(request, 1, rows, List.of(mtv));
        assertEquals(1, replay.projections().size());
        var accepted = request.add(replay.aggregate());
        var normalized = com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop.accepted(request, accepted);
        assertEquals(request.x(), normalized.x(), 0d,
                "existing endpoint slop snaps this one-ULP farther-inward X projection to the request");
        assertEquals(accepted.z(), normalized.z(), 0d,
                "the tangent Z coordinate is not what causes this rejection");
        assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                request, accepted, 1, replay.projections()).isEmpty(),
                "raw SAT producer output alone is rejected; this is not the production caller path");
        assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                request, normalized, 1, replay.projections()).isPresent(),
                "production proof normalizes accepted displacement before ordered contact validation");
    }

    private static CurrentFrameCollisionScope.NativeCollisionTrace traceRow(UUID frame, MovementVector mtv,
            MovementVector before, NativeCollisionGeometry geometry, int iteration) {
        return new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, iteration,
                net.minecraft.core.BlockPos.ZERO, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                "stone", "stone", mtv.x(), mtv.y(), mtv.z(), before.x(), before.y(), before.z(),
                mtv.x(), mtv.y(), mtv.z(), mtv.x(), mtv.y(), mtv.z(), geometry, false);
    }

    @Test void replaysAllR34Tick57NativeSubstepSnapshots() {
        List<R34NativeTraceFixture.Row> rows = R34NativeTraceFixture.rows();
        assertEquals(13, rows.size());
        var witness = new NativeSideCollisionWitness();
        double rx=0, ry=0, rz=0;
        for (var row : rows) {
            MovementVector replay = witness.separation(row.geometry());
            assertNotNull(replay, "substep " + row.substep());
            assertEquals(row.selected().x(), replay.x(), 1e-6, "substep " + row.substep() + " x");
            assertEquals(row.selected().y(), replay.y(), 1e-6, "substep " + row.substep() + " y");
            assertEquals(row.selected().z(), replay.z(), 1e-6, "substep " + row.substep() + " z");
            assertEquals(row.selected(), row.applied(), "selected/applied substep " + row.substep());
            rx += row.response().x(); ry += row.response().y(); rz += row.response().z();
        }
        // The native trace response accumulator starts from the original requested movement.
        assertEquals(-0.0036279771586692246, 0.0 + rx, 1e-6);
        assertEquals(0.0026618433468801107, -0.001 + ry, 1e-6);
        assertEquals(-1.6328211113529987e-6, 0.0 + rz, 1e-6);
    }

    private static NativeCollisionGeometry geometry(MovementVector center, MovementVector size, double[] matrix, AABB bounds) {
        return new NativeCollisionGeometry(center, size, new NativeCollisionGeometry.QuaternionValue(0,0,0,1),
                new NativeFrameInterval.PoseValue(matrix), bounds, MovementVector.ZERO, MovementVector.ZERO, 0, 1);
    }

    @Test void reportsSdkMtvFromFrozenBodyAndSelectedStorageBounds() {
        var witness = new NativeSideCollisionWitness();
        var result = witness.separation(geometry(new MovementVector(1.2,.9,.5), new MovementVector(.6,1.8,.6),
                new Pose3d().bakeIntoMatrix(new org.joml.Matrix4d()).get(new double[16]), new AABB(0,0,0,1,1,1)));
        assertNotNull(result);
        assertEquals(.1, result.x(), 1e-6);
        assertEquals(0, result.y(), 1e-6);
        assertEquals(0, result.z(), 1e-6);
    }

    @Test void disjointBoxesProduceValidZeroWitness() {
        var result = new NativeSideCollisionWitness().separation(geometry(new MovementVector(4,.5,.5),
                new MovementVector(.6,1.8,.6), new org.joml.Matrix4d().get(new double[16]), new AABB(0,0,0,1,1,1)));
        assertEquals(MovementVector.ZERO, result);
    }

    @Test void rejectsNonRigidPoseAndDoesNotMutateSnapshot() {
        double[] matrix = new org.joml.Matrix4d().scale(2,1,1).get(new double[16]);
        var frozen = geometry(new MovementVector(4,.5,.5), new MovementVector(.6,1.8,.6), matrix, new AABB(0,0,0,1,1,1));
        double[] before = frozen.subLevelPose().matrix();
        assertNull(new NativeSideCollisionWitness().separation(frozen));
        assertArrayEquals(before, frozen.subLevelPose().matrix());
    }

    @Test void ceilingOnlyProofAcceptsPositiveNativeYAfterWitnessedUpwardCeilingClip() {
        var request = new MovementVector(0, .1, 0);
        var ceiling = new MovementVector(0, -.05, 0);
        var nativeMotion = request.add(ceiling);
        var ceilingBounds = new AABB(-1, 1.85, -1, 1, 2.85, 1);
        var geometry = new NativeCollisionGeometry(new MovementVector(0, 1, 0), new MovementVector(.6, 1.8, .6),
                new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1),
                new NativeFrameInterval.PoseValue(new org.joml.Matrix4d().get(new double[16])),
                ceilingBounds, ceiling, request, 0, 1);
        var witness = new NativeSideCollisionWitness().separation(geometry);
        assertNotNull(witness);
        assertEquals(ceiling.x(), witness.x(), 1e-12);
        assertEquals(ceiling.y(), witness.y(), 1e-12);
        assertEquals(ceiling.z(), witness.z(), 1e-12);
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(), 1, 1, 0,
                new net.minecraft.core.BlockPos(0, 1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                "stone", "stone", ceiling.x(), ceiling.y(), ceiling.z(), request.x(), request.y(), request.z(),
                ceiling.x(), ceiling.y(), ceiling.z(), ceiling.x(), ceiling.y(), ceiling.z(), geometry, true);
        var nativeFrame = row.frameId();
        var clip = NativeSideContactProofTest.worldClipForTest(nativeMotion,
                new net.minecraft.world.phys.Vec3(0, 0, 0), new AABB(-.3, 0, -.3, .3, 1.8, .3));

        assertTrue(nativeMotion.y() > 0d);
        var accepted = NativeSideContactProof.validateCeilingOnlyDetailed(request, nativeMotion, nativeMotion,
                nativeFrame, List.of(row), candidate -> candidate == row
                        && candidate.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), clip);
        assertEquals(NativeSideContactProof.Failure.NONE, accepted.reason());
        assertEquals(nativeMotion, accepted.result().orElseThrow().boundedOwn());
        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request, nativeMotion, nativeMotion,
                nativeFrame, List.of(row), candidate -> false, clip).result().isEmpty(), "unknown shape remains rejected");
        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request,
                nativeMotion.add(new MovementVector(0, .001, 0)), nativeMotion, nativeFrame, List.of(row),
                candidate -> true, clip).result().isEmpty(), "mismatched native/context remains rejected");
    }

    @Test void ceilingOnlyProofKeepsTiltedCeilingLateralMtvPositionOnly() {
        var frame = UUID.randomUUID();
        var request = new MovementVector(0, .1, 0);
        var bounds = new AABB(-1, 1.85, -1, 1, 2.85, 1);
        double angle = 1e-4;
        double c = Math.cos(angle), s = Math.sin(angle);
        double[] pose = {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        var geometryTemplate = new NativeCollisionGeometry(new MovementVector(0, 1, 0),
                new MovementVector(.6, 1.8, .6), new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1),
                new NativeFrameInterval.PoseValue(pose), bounds, MovementVector.ZERO, request, 0, 1);
        var selected = new NativeSideCollisionWitness().separation(geometryTemplate);
        assertNotNull(selected);
        assertTrue(selected.y() < -Math.hypot(selected.x(), selected.z()), "selected vector must be ceiling-only");
        assertTrue(Math.hypot(selected.x(), selected.z()) > 1e-6,
                "tilted roof MTV must exceed generic own-axis allowance");
        var geometry = new NativeCollisionGeometry(geometryTemplate.entityBoundsCenter(),
                geometryTemplate.entityBoundsDimensions(), geometryTemplate.entityBoundsOrientation(),
                geometryTemplate.subLevelPose(), geometryTemplate.selectedStorageBounds(), selected, request, 0, 1);
        assertEquals(selected, new NativeSideCollisionWitness().separation(geometry));
        var nativeMotion = request.add(selected);
        var row = new CurrentFrameCollisionScope.NativeCollisionTrace(frame, 1, 1, 0,
                new net.minecraft.core.BlockPos(0, 1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                "stone", "stone", selected.x(), selected.y(), selected.z(), request.x(), request.y(), request.z(),
                selected.x(), selected.y(), selected.z(), selected.x(), selected.y(), selected.z(), geometry, true);
        var before = new net.minecraft.world.phys.Vec3(0, 0, 0);
        var bodyBounds = new AABB(-.3, 0, -.3, .3, 1.8, .3);
        var clip = NativeSideContactProofTest.worldClipForTest(nativeMotion, before, bodyBounds);

        var accepted = NativeSideContactProof.validateCeilingOnlyDetailed(request, nativeMotion, nativeMotion,
                frame, List.of(row), candidate -> candidate == row, clip);
        assertEquals(NativeSideContactProof.Failure.NONE, accepted.reason());
        var proof = accepted.result().orElseThrow();
        assertEquals(request.x(), proof.boundedOwn().x(), 0d);
        assertEquals(request.z(), proof.boundedOwn().z(), 0d);
        assertEquals(nativeMotion.x() - request.x(), proof.geometryCorrection().x(), 0d);
        assertEquals(nativeMotion.z() - request.z(), proof.geometryCorrection().z(), 0d);
        assertNull(proof.horizontalContact(), "vertical ceiling MTV must never project horizontal velocity");

        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request, nativeMotion, nativeMotion,
                frame, List.of(row), candidate -> false, clip).result().isEmpty(), "unknown shape rejected");
        var changedResponse = new MovementVector(nativeMotion.x() + 1e-4, nativeMotion.y(), nativeMotion.z());
        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request, changedResponse, changedResponse,
                frame, List.of(row), candidate -> true, clip).result().isEmpty(), "tampered response rejected");
        var nonidentityClip = OwnerCeilingOnlyTraceRegressionTest.observe(
                new net.minecraft.world.phys.Vec3(nativeMotion.x(), nativeMotion.y(), nativeMotion.z()),
                new net.minecraft.world.phys.Vec3(nativeMotion.x() + 1e-4, nativeMotion.y(), nativeMotion.z()),
                before, bodyBounds, true);
        assertTrue(NativeSideContactProof.validateCeilingOnlyDetailed(request, nativeMotion, nativeMotion,
                frame, List.of(row), candidate -> true, nonidentityClip).result().isEmpty(),
                "nonidentity world clip rejected");
    }

    @Test void tenSubstepSideProofKeepsSubSlopSatNormalsForOrderedProjection() {
        var frame = UUID.randomUUID();
        var request = new MovementVector(0, .3332, .15625);
        double angle = 8.2129518e-6;
        double c = Math.cos(angle), s = Math.sin(-angle);
        double[] matrix = {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1};
        var pose = new NativeFrameInterval.PoseValue(matrix);
        var dimensions = new MovementVector(.6, 1.8, .6);
        var orientation = new NativeCollisionGeometry.QuaternionValue(0, 0, 0, 1);
        var storageBounds = new AABB(1, 0, 0, 2, 1, 1);
        // Start with a small, SAT-resolvable overlap, then let each substep add a sub-slop
        // tangent-plane approach. The initial overlap is independently represented by SAT MTvs.
        var tangentX = (1.5 - (.5 + .3 * (c + Math.abs(s))) - Math.abs(s) * .5) / c + 3e-7;
        var initialCenter = new MovementVector(tangentX, .9, .5);
        var witness = new NativeSideCollisionWitness();
        var rows = new java.util.ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        MovementVector responses = MovementVector.ZERO;
        for (int step = 1; step <= 10; step++) {
            MovementVector before = request.scale(step / 10d).add(responses);
            var template = new NativeCollisionGeometry(initialCenter.add(before), dimensions, orientation, pose,
                    storageBounds, MovementVector.ZERO, before, 0, 10);
            MovementVector selected = witness.separation(template);
            assertNotNull(selected, "SAT witness at substep " + step);
            double selectedLength = Math.sqrt(selected.x() * selected.x() + selected.y() * selected.y()
                    + selected.z() * selected.z());
            assertTrue(selectedLength > 0d && selectedLength <= 1e-6,
                    "each individually sub-slop SAT MTV: " + selected);
            var geometry = new NativeCollisionGeometry(template.entityBoundsCenter(), dimensions, orientation,
                    pose, storageBounds, selected, before, 0, 10);
            var replayed = witness.separation(geometry);
            assertNotNull(replayed, "stored geometry replays at substep " + step);
            assertEquals(selected.x(), replayed.x(), 1e-12, "SAT X replay at substep " + step);
            assertEquals(selected.y(), replayed.y(), 1e-12, "SAT Y replay at substep " + step);
            assertEquals(selected.z(), replayed.z(), 1e-12, "SAT Z replay at substep " + step);
            rows.add(new CurrentFrameCollisionScope.NativeCollisionTrace(frame, step, 10, 0,
                    new net.minecraft.core.BlockPos(1, 0, 0),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), "stone", "stone",
                    selected.x(), selected.y(), selected.z(), before.x(), before.y(), before.z(),
                    selected.x(), selected.y(), selected.z(), selected.x(), selected.y(), selected.z(), geometry, false));
            responses = responses.add(selected);
        }
        assertTrue(Math.hypot(responses.x(), responses.z()) > 1e-6,
                "cumulative SAT push exceeds the own-axis tolerance: " + responses);
        var nativeMotion = request.add(responses);
        var measured = nativeMotion;
        var clip = NativeSideContactProofTest.worldClipForTest(nativeMotion,
                new net.minecraft.world.phys.Vec3(0, 0, 0), new AABB(-.3, 0, -.3, .3, 1.8, .3));

        var accepted = NativeSideContactProof.validateDetailed(request, nativeMotion, measured, frame, rows,
                row -> row.selectedState() == net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        assertEquals(NativeSideContactProof.Failure.NONE, accepted.reason());
        var proof = accepted.result().orElseThrow();
        var replay = NativeSideContactProof.replayOwnResponsesAt(request, 10, rows,
                rows.stream().map(row -> new MovementVector(row.selectedX(), row.selectedY(), row.selectedZ())).toList());
        assertEquals(request.x() + replay.aggregate().x(), proof.boundedOwn().x(), 0d);
        assertEquals(request.z() + replay.aggregate().z(), proof.boundedOwn().z(), 0d);
        assertNotNull(proof.horizontalContact(), "validated ordered side projection is retained");
        assertTrue(proof.horizontalContact().matches(request, proof.boundedOwn()));
        assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validateOrdered(
                request, proof.boundedOwn(), 10, replay.projections()).isPresent(),
                "shared ordered projection validator independently accepts replay");
        assertEquals(measured.x() - proof.boundedOwn().x(), proof.geometryCorrection().x(), 0d);
        assertEquals(measured.z() - proof.boundedOwn().z(), proof.geometryCorrection().z(), 0d);

        assertTrue(NativeSideContactProof.validateDetailed(request, nativeMotion, measured, frame, rows,
                row -> false).result().isEmpty(), "unknown native shape rejected");
        var last = rows.getLast();
        var tamperedLast = new CurrentFrameCollisionScope.NativeCollisionTrace(last.frameId(), last.substep(),
                last.substeps(), last.iteration(), last.selectedPos(), last.selectedState(), last.block(),
                last.blockState(), last.selectedX(), last.selectedY(), last.selectedZ(), last.collisionMotionBeforeX(),
                last.collisionMotionBeforeY(), last.collisionMotionBeforeZ(), last.appliedX(), last.appliedY(),
                last.appliedZ(), last.responseX() + 1e-4, last.responseY(), last.responseZ(), last.geometry(),
                last.verticalCollision());
        var tamperedRows = new java.util.ArrayList<>(rows);
        tamperedRows.set(tamperedRows.size() - 1, tamperedLast);
        assertTrue(NativeSideContactProof.validateDetailed(request, nativeMotion, measured, frame, tamperedRows,
                row -> true).result().isEmpty(), "tampered native response rejected");
        assertTrue(com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence.validate(request,
                proof.boundedOwn(), 1e-7, -1d, 0d).isEmpty(), "near-vertical normal stays outside side evidence");
    }
}
