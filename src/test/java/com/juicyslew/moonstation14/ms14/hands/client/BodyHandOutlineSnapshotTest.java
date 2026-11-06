package com.juicyslew.moonstation14.ms14.hands.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import dev.ryanhcode.sable.companion.math.Pose3d;
import org.joml.Vector3d;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BodyHandOutlineSnapshotTest {
    @Test void actualNominationAndDrawShareOneFrameRayAndCapturedCandidateGeometry() throws Exception {
        String client = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        int build = client.indexOf("private static OutlinePileSnapshot buildOutlineCandidates");
        int buildEnd = client.indexOf("private static OutlineCandidate captureOutlineCandidate", build);
        String builder = client.substring(build, buildEnd);
        assertEquals(1, occurrences(builder, "BodyHandRaySnapshot.capture("), "one camera ray per candidate query");
        assertTrue(client.contains("private static final RenderFrameSnapshotCache<OutlinePileSnapshot> OUTLINE_PILE"));
        assertTrue(client.contains("refreshPileUnprofiled(Mob body)"));
        int refresh = client.indexOf("private static void refreshPileUnprofiled");
        int refreshEnd = client.indexOf("private static OutlinePileSnapshot buildOutlineCandidates", refresh);
        assertTrue(client.substring(refresh, refreshEnd).contains("buildOutlineCandidates(body, false)"));
        int cache = client.indexOf("private static OutlinePileSnapshot outlinePileSnapshot");
        int select = client.indexOf("private static boolean dumpOutlinePriority", cache);
        assertTrue(client.substring(cache, select).contains("OUTLINE_PILE.get(outlineRenderSerial, body, body.level(), cameraEpoch"));
        assertTrue(client.substring(cache, select).contains("buildOutlineCandidates(body, true)"));

        int selectedItem = client.indexOf("public static ItemEntity selectedItemForOutline()");
        int selection = client.indexOf("public static OutlineSelection outlineSelection()", selectedItem);
        String selectedMethod = client.substring(selectedItem, selection);
        assertTrue(selectedMethod.contains("return selection == null ? null : selection.entity()"));
        assertFalse(selectedMethod.contains("BodyHandRaySnapshot.capture("));
        String selectionMethod = client.substring(selection, client.indexOf("public static boolean outlineItemPrecedesBlock", selection));
        assertTrue(selectionMethod.contains("outlinePileSnapshot(body)"));
        assertFalse(selectionMethod.contains("BodyHandRaySnapshot.capture("));
        assertTrue(client.contains("if (snapshot.dumpSourcePriority())"));
        assertTrue(client.contains("return selected != null && selected.entity().isAlive() ? selected : null"),
                "a captured dump source retains precedence instead of falling through to another pile item");
        assertTrue(client.contains("ItemEntity captured = looseEntity(source)"), "captured dump source is forced through the same ray/geometry candidate test");
        assertTrue(builder.contains("nearestOutlineBlock(ray)"));
        assertTrue(builder.contains("LooseItemQueryGeometry.maximumProfileRadius(true)"));
        assertTrue(builder.contains("LoadedFrameRayQuery.frameRayContexts("));
        assertTrue(builder.contains("context.frame().logicalPose()"));
        assertTrue(builder.contains("mappedFrameRayQueryBox(context.frame().logicalPose(),"));
        assertTrue(builder.contains("loose.committedRenderFrameId(ray.partialTick())"));
        assertTrue(builder.contains("entitiesById.putIfAbsent"));
        assertTrue(client.contains("geometry.inflated(BodyItemTargetProbe.TARGET_MARGIN).clip(ray.from(), ray.to())"));
        assertTrue(client.contains("geometry.minimumProjection(ray.from(), ray.direction())"),
                "block precedence and physical projection stay on the uninflated profile");
    }

    @Test void outlineConsumersUseCapturedCameraRayAndTheSameCachedGeometryRatherThanBodyLookOrRequery() throws Exception {
        String loose = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/SelectedLooseOutlineRenderer.java");
        assertTrue(loose.contains("BodyHandClient.outlineSelection()"));
        assertTrue(loose.contains("selection.geometry()"));
        assertTrue(loose.contains("selection.ordinaryRenderBounds()"));
        assertTrue(loose.contains("var camera = ray.from()"));
        assertFalse(loose.contains("LooseItemQueryGeometry.forEntity"));
        assertFalse(loose.contains("loose.renderPosition(partialTick)"));

        String block = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyBlockOutlineRenderer.java");
        assertTrue(block.contains("BodyHandClient.outlineSelection()"));
        assertTrue(block.contains("BodyHandClient.outlineRaySnapshot()"));
        assertTrue(block.contains("var start = ray.from()"));
        assertTrue(block.contains("var end = ray.to()"));
        assertFalse(block.contains("body.getEyePosition()"));
        assertFalse(block.contains("body.getLookAngle()"));
        assertTrue(block.contains("BodyHandClient.outlineItemPrecedesBlock(selection, hit.getLocation())"));
    }

    @Test void actualCandidateBuilderUsesCameraRayInsteadOfControlledBodyLookRay() throws Exception {
        String client = read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        int candidate = client.indexOf("private static OutlineCandidate captureOutlineCandidate");
        int next = client.indexOf("private static double candidateDistance", candidate);
        if (next < 0) next = client.indexOf("private static Vec3 interpolatedItemPosition", candidate);
        String narrowphase = client.substring(candidate, next);
        assertTrue(narrowphase.contains("ray.from()"));
        assertTrue(narrowphase.contains("ray.to()"));
        assertFalse(narrowphase.contains("body.getLookAngle()"));
        assertFalse(narrowphase.contains("body.getEyePosition()"));

        Vec3 cameraFrom = Vec3.ZERO, cameraTo = new Vec3(0,0,5);
        Vec3 bodyFrom = new Vec3(5,0,0), bodyTo = new Vec3(5,0,5);
        AABB candidateBox = new AABB(-.25,-.25,1.5,.25,.25,2.5);
        assertNotNull(LooseItemRayGeometry.entry(candidateBox, cameraFrom, cameraTo));
        assertNull(LooseItemRayGeometry.entry(candidateBox, bodyFrom, bodyTo),
                "the regression fixture has distinct camera and actor-look nominations");
    }

    @Test void candidateUsesRenderedLooseProxyAndOrdinaryRenderedBoundsAndNearestWorldOrFrameBlock() {
        Vec3 from = Vec3.ZERO, to = new Vec3(0, 0, 5);
        AABB item = new AABB(-.3, -.3, 1, .3, .3, 1.5);
        Vec3 cameraRayHit = LooseItemRayGeometry.entry(item, from, to);
        Vec3 differentBodyRayHit = LooseItemRayGeometry.entry(item, new Vec3(5, 0, 0), new Vec3(5, 0, 5));
        assertNotNull(cameraRayHit);
        assertNull(differentBodyRayHit, "camera and actor look rays can nominate different entities");

        var renderBounds = BodyHandClient.ordinaryRenderedBounds(item, new Vec3(0,0,1.25), new Vec3(.5,0,1.25));
        assertEquals(item.move(.5, 0, 0), renderBounds, "ordinary ItemEntity proxy matches captured render interpolation");

        UUID frame = UUID.randomUUID();
        var ray = new BodyHandRaySnapshot.Snapshot(from, new Vec3(0,0,1), to, .5f,
                new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Result(
                        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.State.HIT,
                        new BlockHitResult(new Vec3(0,0,4), Direction.NORTH, BlockPos.ZERO, false),
                        new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(
                                BlockPos.ZERO, Direction.NORTH, new Vec3(0,0,2), frame)));
        assertEquals(new Vec3(0,0,2), BodyHandClient.nearestOutlineBlock(ray),
                "frame hit wins when nearer than world hit");
        var worldCloser = new BodyHandRaySnapshot.Snapshot(from, new Vec3(0,0,1), to, .5f,
                new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Result(
                        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.State.HIT,
                        new BlockHitResult(new Vec3(0,0,1), Direction.NORTH, BlockPos.ZERO, false),
                        new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Hit(
                                BlockPos.ZERO, Direction.NORTH, new Vec3(0,0,2), frame)));
        assertEquals(new Vec3(0,0,1), BodyHandClient.nearestOutlineBlock(worldCloser),
                "nearest world/frame occluder remains the blocker");
        assertTrue(OutlinePriority.itemPrecedesBlock(item, from, to, BodyHandClient.nearestOutlineBlock(ray)));
        assertFalse(OutlinePriority.itemPrecedesBlock(item, from, to, BodyHandClient.nearestOutlineBlock(worldCloser)));
    }

    @Test void storageRayEndpointsMapThroughActualTranslatedAndYawedPoseWithConservativeRadius() {
        var pose = new Pose3d();
        pose.position().set(100, 20, -50);
        pose.orientation().rotationY(Math.PI / 2);
        Vec3 localFrom = new Vec3(0, 1, 0), localTo = new Vec3(0, 1, 10);
        AABB mapped = BodyHandClient.mappedFrameRayQueryBox(pose, localFrom, localTo, .75);
        assertNotNull(mapped);
        var worldFrom = pose.transformPosition(new org.joml.Vector3d(0,1,0));
        var worldTo = pose.transformPosition(new org.joml.Vector3d(0,1,10));
        assertTrue(mapped.contains(new Vec3(worldFrom.x, worldFrom.y, worldFrom.z)));
        assertTrue(mapped.contains(new Vec3(worldTo.x, worldTo.y, worldTo.z)));
        assertTrue(mapped.minX <= 100 && mapped.maxX >= 110, "local +Z maps along world +X under yaw");
        assertNull(BodyHandClient.mappedFrameRayQueryBox(pose, localFrom, localTo, Double.NaN));
        assertNull(BodyHandClient.mappedFrameRayQueryBox(pose, localFrom, localTo, -1));
        pose.orientation().set(Double.NaN, 0, 0, 1);
        assertNull(BodyHandClient.mappedFrameRayQueryBox(pose, localFrom, localTo, .75), "invalid pose fails closed");
        pose.orientation().set(0,0,0,0);
        assertNull(BodyHandClient.mappedFrameRayQueryBox(pose, localFrom, localTo, .75), "zero frame rotation fails closed");
    }

    @Test void productionMappedBroadphaseFindsLogicalWorldPointMissedByCameraBoxForTranslationAndYawWhileRenderedShapeStillHits() {
        assertMappedCase(false);
        assertMappedCase(true);
    }

    private static void assertMappedCase(boolean yawDelta) {
        var renderPose = new Pose3d();
        renderPose.position().set(0,0,0);
        renderPose.rotationPoint().set(0,0,0);
        renderPose.orientation().rotationY(.4);
        var logicalPose = new Pose3d();
        logicalPose.position().set(renderPose.position());
        logicalPose.rotationPoint().set(renderPose.rotationPoint());
        logicalPose.orientation().set(renderPose.orientation());
        double radius = com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.TARGET_MARGIN
                + com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry.maximumProfileRadius(true);
        if (yawDelta) logicalPose.orientation().rotateY(.01);
        else logicalPose.position().x = radius + 3;

        var storageAnchor = new Vector3d(20_481_032.375, 128.25, 20_481_032.625);
        var renderCenter = renderPose.transformPosition(new Vector3d(storageAnchor));
        var logicalCenter = logicalPose.transformPosition(new Vector3d(storageAnchor));
        var direction = renderPose.orientation().transform(new Vector3d(0,0,1)).normalize();
        Vec3 from = new Vec3(renderCenter.x,renderCenter.y,renderCenter.z)
                .subtract(new Vec3(direction.x,direction.y,direction.z).scale(3));
        Vec3 to = new Vec3(renderCenter.x,renderCenter.y,renderCenter.z)
                .add(new Vec3(direction.x,direction.y,direction.z).scale(3));
        var localFrom = renderPose.transformPositionInverse(new Vector3d(from.x,from.y,from.z));
        var localTo = renderPose.transformPositionInverse(new Vector3d(to.x,to.y,to.z));
        var cameraWorldQuery = new AABB(from,to).inflate(radius);
        var mappedFrameQuery = BodyHandClient.mappedFrameRayQueryBox(logicalPose,
                new Vec3(localFrom.x,localFrom.y,localFrom.z), new Vec3(localTo.x,localTo.y,localTo.z), radius);
        assertNotNull(mappedFrameQuery);
        var currentEntityWorld = new Vec3(logicalCenter.x,logicalCenter.y,logicalCenter.z);
        assertFalse(cameraWorldQuery.contains(currentEntityWorld), "ordinary camera-world broadphase misses the current logical anchor");
        assertTrue(mappedFrameQuery.contains(currentEntityWorld), "mapped query encloses the frame-logical current anchor");

        var geometry = new com.juicyslew.moonstation14.ms14.loose_objects.LooseItemQueryGeometry(.7,1.4,
                new Vec3(renderCenter.x,renderCenter.y,renderCenter.z),renderPose.orientation());
        assertNotNull(geometry.inflated(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.TARGET_MARGIN)
                .clip(from,to), "rendered physical proxy still intersects the actual camera ray");
        var ray = new BodyHandRaySnapshot.Snapshot(from,
                new Vec3(direction.x,direction.y,direction.z),to,.5f,
                new com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.Result(
                        com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery.State.MISS,null,null));
        Vec3 behind = from.add(ray.direction().scale(5));
        Vec3 before = from.add(ray.direction().scale(1));
        assertTrue(BodyHandClient.outlineGeometryPrecedesBlock(geometry,ray,behind));
        assertFalse(BodyHandClient.outlineGeometryPrecedesBlock(geometry,ray,before));
    }

    @Test void committedLooseFrameGetterRequiresLiveInterpolationAnchorAndDoesNotUseSdkContainment() throws Exception {
        String source = read("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java");
        int getter = source.indexOf("public java.util.UUID committedRenderFrameId(float partialTick)");
        int end = source.indexOf("/** True when Sable's outer renderer", getter);
        String method = source.substring(getter, end);
        assertTrue(method.contains("!level().isClientSide"));
        assertTrue(method.contains("clientPosition.frameProjectionPaused()"));
        assertTrue(method.contains("clientPosition.renderAnchor(partialTick) != null"));
        assertTrue(method.contains("clientPosition.frameId()"));
        assertFalse(method.contains("sable$getTrackingSubLevel"), "SDK containment is not committed presentation-frame identity");
    }

    private static String read(String path) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve(path))) root = root.getParent();
        assertNotNull(root, "source file: " + path);
        return Files.readString(root.resolve(path), StandardCharsets.UTF_8);
    }

    private static int occurrences(String source, String needle) {
        int count=0, at=0;
        while ((at=source.indexOf(needle,at))>=0) { count++; at+=needle.length(); }
        return count;
    }
}
