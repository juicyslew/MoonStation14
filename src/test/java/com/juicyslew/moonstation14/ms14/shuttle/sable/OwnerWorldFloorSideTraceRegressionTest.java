package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Geometric regression for owner seq11309; stone is a neutral full-cube stand-in,
 * not a claim that the owner's selected steel wall was reproduced. */
class OwnerWorldFloorSideTraceRegressionTest {
    private static final UUID FRAME = UUID.fromString("0fb05421-6f59-44d1-b337-ee121d2b1539");
    private static final String FIXTURE = "/fixtures/shuttle/owner-seq11309.json";

    @Test void frozenThirteenRowTraceProvesWorldFloorSideAndKeepsGeometryCorrectionPositionOnly() {
        JsonObject root = OwnerTraceFixture.load("owner-seq11309.json");
        MovementVector request = OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        MovementVector nativeMotion = OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        MovementVector measured = OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        List<CurrentFrameCollisionScope.NativeCollisionTrace> rows = OwnerTraceFixture.rows(root.getAsJsonArray("rows"), FRAME);
        assertEquals(13, rows.size());

        var diagnostic = NativeSideContactProof.validateWorldFloorSideDetailed(request, nativeMotion, measured,
                FRAME, rows, row -> row.selectedState() == Blocks.STONE.defaultBlockState(), OwnerTraceFixture.worldClip(root));
        assertEquals(NativeSideContactProof.Failure.NONE, diagnostic.reason(), "row=" + diagnostic.rowIndex());
        var proof = diagnostic.result().orElseThrow();
        assertEquals(-8.08856839e-7, proof.boundedOwn().x(), 2e-12);
        assertEquals(-.14086618580446952, proof.boundedOwn().y(), 0d);
        assertEquals(-.154325444959176, proof.boundedOwn().z(), 2e-12);
        assertEquals(.00239661538129, proof.geometryCorrection().x(), 2e-12);
        assertEquals(0d, proof.geometryCorrection().y(), 0d);
        assertNotNull(proof.horizontalContact(), "ordered side contact preserves tangent velocity");
        assertTrue(proof.horizontalContact().matches(request, proof.boundedOwn()));
        assertEquals(measured.z(), proof.boundedOwn().z(), 2e-12,
                "world-floor side evidence must not zero the accepted tangent component");
        for (var row : rows) {
            assertNotNull(row.geometry());
            assertEquals(16, row.geometry().subLevelPose().matrix().length);
        }
    }

    @Test void missingGeometryOrWrongRequestCannotReuseFrozenTraceProof() {
        JsonObject root = OwnerTraceFixture.load("owner-seq11309.json");
        var request = OwnerTraceFixture.vector(root.getAsJsonArray("request"));
        var nativeMotion = OwnerTraceFixture.vector(root.getAsJsonArray("nativeMotion"));
        var measured = OwnerTraceFixture.vector(root.getAsJsonArray("measured"));
        var rows = OwnerTraceFixture.rows(root.getAsJsonArray("rows"), FRAME);
        var clip = OwnerTraceFixture.worldClip(root);
        assertNotEquals(NativeSideContactProof.Failure.NONE,
                NativeSideContactProof.validateWorldFloorSideDetailed(request.add(new MovementVector(.01,0,0)),
                        nativeMotion, measured, FRAME, rows, ignored -> true, clip).reason());
        var missing = new ArrayList<>(rows);
        missing.set(12, new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, 10, 10, 0,
                BlockPos.ZERO, Blocks.STONE.defaultBlockState(), "stone", "stone", 0,0,0,
                0,0,0, 0,0,0, 0,0,0, null, false));
        assertNotEquals(NativeSideContactProof.Failure.NONE,
                NativeSideContactProof.validateWorldFloorSideDetailed(request, nativeMotion, measured,
                        FRAME, missing, ignored -> true, clip).reason());
    }

    private static JsonObject fixture() {
        try (var in = OwnerWorldFloorSideTraceRegressionTest.class.getResourceAsStream(FIXTURE)) {
            if (in == null) throw new AssertionError("missing " + FIXTURE);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }

    private static MovementVector vector(JsonArray a) {
        return new MovementVector(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> rows(JsonArray source) {
        var result = new ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for (var item : source) {
            JsonObject r = item.getAsJsonObject(), g = r;
            JsonArray pose = g.getAsJsonArray("pose"), bounds = g.getAsJsonArray("bounds");
            double[] matrix = new double[pose.size()];
            for (int i=0;i<matrix.length;i++) matrix[i]=pose.get(i).getAsDouble();
            var geometry = new NativeCollisionGeometry(vector(g.getAsJsonArray("center")),
                    vector(g.getAsJsonArray("dimensions")), new NativeCollisionGeometry.QuaternionValue(
                    g.getAsJsonArray("orientation").get(0).getAsDouble(), g.getAsJsonArray("orientation").get(1).getAsDouble(),
                    g.getAsJsonArray("orientation").get(2).getAsDouble(), g.getAsJsonArray("orientation").get(3).getAsDouble()),
                    new NativeFrameInterval.PoseValue(matrix), new AABB(bounds.get(0).getAsJsonArray().get(0).getAsDouble(),
                    bounds.get(0).getAsJsonArray().get(1).getAsDouble(), bounds.get(0).getAsJsonArray().get(2).getAsDouble(),
                    bounds.get(1).getAsJsonArray().get(0).getAsDouble(), bounds.get(1).getAsJsonArray().get(1).getAsDouble(),
                    bounds.get(1).getAsJsonArray().get(2).getAsDouble()),
                    vector(g.getAsJsonArray("mtv")), vector(g.getAsJsonArray("motion")), r.get("iteration").getAsInt(), r.get("substeps").getAsInt());
            result.add(new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, r.get("substep").getAsInt(),
                    r.get("substeps").getAsInt(), r.get("iteration").getAsInt(), new BlockPos(
                    r.getAsJsonArray("selectedPos").get(0).getAsInt(), r.getAsJsonArray("selectedPos").get(1).getAsInt(),
                    r.getAsJsonArray("selectedPos").get(2).getAsInt()), Blocks.STONE.defaultBlockState(), "stone", "stone",
                    vector(r.getAsJsonArray("selected")).x(), vector(r.getAsJsonArray("selected")).y(), vector(r.getAsJsonArray("selected")).z(),
                    vector(r.getAsJsonArray("before")).x(), vector(r.getAsJsonArray("before")).y(), vector(r.getAsJsonArray("before")).z(),
                    vector(r.getAsJsonArray("applied")).x(), vector(r.getAsJsonArray("applied")).y(), vector(r.getAsJsonArray("applied")).z(),
                    vector(r.getAsJsonArray("response")).x(), vector(r.getAsJsonArray("response")).y(), vector(r.getAsJsonArray("response")).z(),
                    geometry, r.get("vertical").getAsBoolean()));
        }
        return List.copyOf(result);
    }

    private static CurrentFrameCollisionScope.WorldClipObservation worldClip(JsonObject root, MovementVector input, MovementVector output) {
        var entity=new net.minecraft.world.entity.Marker(net.minecraft.world.entity.EntityType.MARKER, null);
        JsonObject clip=root.getAsJsonObject("worldClip");
        Vec3 in=jsonVec(clip.getAsJsonArray("input")), out=jsonVec(clip.getAsJsonArray("output"));
        JsonArray p=clip.getAsJsonArray("positionBefore"), b=clip.getAsJsonArray("boundsBefore");
        Vec3 position=new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
        AABB bounds=new AABB(b.get(0).getAsJsonArray().get(0).getAsDouble(),b.get(0).getAsJsonArray().get(1).getAsDouble(),
                b.get(0).getAsJsonArray().get(2).getAsDouble(), b.get(1).getAsJsonArray().get(0).getAsDouble(),
                b.get(1).getAsJsonArray().get(1).getAsDouble(),b.get(1).getAsJsonArray().get(2).getAsDouble());
        return CurrentFrameCollisionScope.withTrackedCharacterPose(entity,false,()->{
            CurrentFrameCollisionScope.recordVanillaWorldCollisionInput(entity,in,position,bounds);
            CurrentFrameCollisionScope.recordVanillaWorldCollisionOutput(entity,out); return null;
        }).worldClipObservation();
    }

    private static Vec3 jsonVec(JsonArray a) {
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }
}
