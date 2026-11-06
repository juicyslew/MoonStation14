package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.examine.FrameBoundsTransform;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;

class HullTerrainGeometryTest {
    private final LevelReusedVectors scratch=new LevelReusedVectors();

    @Test void fixtureChunkTicketAcquisitionAndEveryCleanupForceEntityTicking() throws Exception {
        var fixtureType = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.class;
        var leaseType = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.Lease.class;
        var classes = java.util.stream.Stream.of(fixtureType, leaseType).map(type -> {
            var node = new ClassNode();
            try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(input, "missing compiled class " + type.getName());
                new ClassReader(input).accept(node, 0);
            } catch (java.io.IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
            return node;
        }).toList();
        var calls = classes.stream().flatMap(node -> node.methods.stream()).flatMap(method -> {
            var found = new java.util.ArrayList<MethodInsnNode>();
            for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
                if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ServerChunkCache")
                        && (call.name.equals("addRegionTicket") || call.name.equals("removeRegionTicket")))
                    found.add(call);
            return found.stream();
        }).toList();
        assertEquals(3, calls.size());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("addRegionTicket")).count());
        assertEquals(2, calls.stream().filter(call -> call.name.equals("removeRegionTicket")).count());
        String forcedTicketDescriptor = "(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;Z)V";
        assertTrue(calls.stream().allMatch(call -> call.desc.equals(forcedTicketDescriptor)),
                "acquisition, partial-acquisition rollback, and lease close must use the force-ticks overload");
        for (var call : calls)
            assertEquals(Opcodes.ICONST_1, call.getPrevious().getOpcode(), "every ticket operation must pass forceTicks=true");
    }

    @Test void fixtureTicketWarmupLoadsFullRadiusTwoAfterOwningOnlyFootprintTickets() throws Exception {
        var type = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.class;
        var node = new ClassNode();
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        var acquire = node.methods.stream().filter(method -> method.name.equals("acquire")).findFirst().orElseThrow();
        int ticketAdd = -1, chunkLoadIndex = -1, index = 0, chunkLoads = 0;
        for (var instruction = acquire.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode call && call.name.equals("addRegionTicket")) ticketAdd = index;
            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ServerLevel")
                    && call.name.equals("getChunk") && call.desc.equals("(II)Lnet/minecraft/world/level/chunk/LevelChunk;")) {
                chunkLoads++; chunkLoadIndex = index;
            }
            index++;
        }
        assertTrue(ticketAdd >= 0, "footprint tickets are acquired");
        assertEquals(1, chunkLoads, "a single nested loop uses the normal synchronous getChunk pipeline for the union ring");
        assertTrue(chunkLoadIndex > ticketAdd, "all owned tickets are registered before halo FULL warmup starts");
        assertEquals(1, node.fields.stream().filter(field -> field.name.equals("FULL_NEIGHBOR_RADIUS")
                && Integer.valueOf(2).equals(field.value)).count());
        assertEquals(1, node.fields.stream().filter(field -> field.name.equals("TYPE")).count(),
                "only the footprint set owns fixture tickets; no halo ticket type/set is introduced");
    }

    @Test void fixtureTicketTimeoutSummaryIsBoundedAndIdentifiesActualFailureCause() throws Exception {
        var method = com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets.class
                .getDeclaredMethod("timeoutSummary", int.class, BlockPos.class, String.class, boolean.class);
        method.setAccessible(true);
        String positionCause = (String) method.invoke(null, 40, new BlockPos(163, 188, 188), "ignored", false);
        assertTrue(positionCause.contains("unticked=" + new BlockPos(163, 188, 188)));
        assertFalse(positionCause.contains("additionalReady"));
        String conditionCause = (String) method.invoke(null, 40, null, "physical prerequisite false", false);
        assertTrue(conditionCause.contains("additionalReady=physical prerequisite false:false"));
        String huge = (String) method.invoke(null, 40, null, "x".repeat(5000), false);
        assertTrue(huge.length() <= 600);
        assertTrue(huge.endsWith("..."));
    }

    @Test void broadphaseCornerOverlapDoesNotCountAsOrientedCollision() {
        var pose=new Matrix4d().rotateY(Math.PI/4);
        var thinLongHull=new AABB(-1,-.1,-.1,1,.1,.1);
        var cornerCell=new AABB(.68,-.05,.68,.78,.05,.78);
        var broadphase=HullTerrainGeometry.transformedBounds(thinLongHull,pose);
        assertTrue(broadphase.intersects(cornerCell),"transformed broadphase deliberately includes a corner gap");
        var contact=HullTerrainGeometry.againstTerrain(thinLongHull,pose,cornerCell,scratch);
        assertFalse(contact.overlaps(),"only the SDK OBB SAT result decides actual overlap");
        assertEquals(0d,contact.penetrationDepth(),1e-6);
        assertTrue(Double.isFinite(contact.penetrationDepth()));
    }

    @Test void touchingSupportPlaneHasNoPenetrationButUprightOverlapHasKnownDepth() {
        var hull=new AABB(-.5,-.5,-.5,.5,.5,.5);
        var terrain=new AABB(-2,-2,-2,2,0,2);
        var touching=new Matrix4d().translation(0,.5,0);
        var touch=HullTerrainGeometry.againstTerrain(hull,touching,terrain,scratch);
        assertFalse(touch.overlaps());
        assertEquals(0d,touch.penetrationDepth(),1e-6);

        var penetrating=new Matrix4d().translation(0,.4,0);
        var overlap=HullTerrainGeometry.againstTerrain(hull,penetrating,terrain,scratch);
        assertTrue(overlap.overlaps());
        assertEquals(.1,overlap.penetrationDepth(),1e-6);
    }

    @Test void pitchedAndRolledActualHullBoxReportsKnownFloorPenetration() {
        var hull=new AABB(-.5,-.5,-.5,.5,.5,.5);
        var pose=new Matrix4d().translation(0,.846446609406726,0).rotateZ(Math.PI/4).rotateX(Math.PI/4);
        var terrain=new AABB(-2,-2,-2,2,0,2);
        var contact=HullTerrainGeometry.againstTerrain(hull,pose,terrain,scratch);
        assertTrue(contact.overlaps());
        assertEquals(.0071067811865476,contact.penetrationDepth(),1e-6);
        assertTrue(contact.worldBounds().minY<0,"the transformed physical hull, not plot envelope, crosses terrain");
    }

    @Test void posePivotAtLargeStorageCoordinatesProjectsSmallWorldGeometryPrecisely() {
        double storage=20_481_032.375;
        var pose=new Pose3d();
        pose.rotationPoint().set(storage,storage,storage);
        pose.position().set(3600.5,-51.25,3600.5);
        var localHull=new AABB(storage-.5,storage-.5,storage-.5,storage+.5,storage+.5,storage+.5);
        var matrix=pose.bakeIntoMatrix(new Matrix4d());
        var actual=HullTerrainGeometry.againstTerrain(localHull,matrix,
                new AABB(3599,-52,3599,3602,-51.5,3602),scratch);
        assertEquals(3600,actual.worldBounds().minX,1e-9);
        assertEquals(-51.75,actual.worldBounds().minY,1e-9);
        assertEquals(3601,actual.worldBounds().maxX,1e-9);
        assertTrue(actual.overlaps());
        assertEquals(.25,actual.penetrationDepth(),1e-6);
        assertTrue(new Vector3d(actual.worldBounds().getCenter().x,actual.worldBounds().getCenter().y,
                actual.worldBounds().getCenter().z).distance(3600.5,-51.25,3600.5)<1e-9);
    }

    @Test void farStorageLintelSatUsesTransformedNearWorldBodyBounds() {
        double storage = 20_481_032d;
        var pose = new Pose3d();
        pose.rotationPoint().set(storage, storage, storage);
        pose.position().set(3600, 20, 3600);
        var matrix = pose.bakeIntoMatrix(new Matrix4d());
        var lintel = new AABB(storage + 12, storage + 4, storage,
                storage + 13, storage + 5, storage + 1);
        var below = HullTerrainGeometry.againstTerrain(lintel, matrix,
                new AABB(3612.1, 23, 3600.1, 3612.8, 23.99, 3600.8), scratch);
        assertFalse(below.overlaps(), "body wholly below the transformed lintel must not penetrate");
        assertEquals(0d, below.penetrationDepth(), 1e-9);

        var intersecting = HullTerrainGeometry.againstTerrain(lintel, matrix,
                new AABB(3612.1, 23.9, 3600.1, 3612.8, 24.1, 3600.8), scratch);
        assertTrue(intersecting.overlaps(), "near-world body overlapping far-storage lintel must be detected by SAT");
        assertEquals(.1d, intersecting.penetrationDepth(), 1e-6);
    }

    @Test void actualHullExtentsBoundPadAndFullSlabEscapeCannotPassAsSatContact() {
        var longHull=new AABB(-1,-.25,-.2,1,.25,.2);
        var rotated=new Matrix4d().translation(4.5,0,4.5).rotateY(Math.PI/4);
        var worldHull=FrameBoundsTransform.transformedBounds(longHull,rotated);
        var pad=new AABB(4,-10,4,5,10,5);
        assertFalse(HullTerrainGeometry.withinHorizontalPad(worldHull,pad),
                "rotated shape extrema, not cabin origin, determine pad containment");

        var fullyBelow=new AABB(3,-5,3,4,-3,4);
        assertTrue(HullTerrainGeometry.fullyBelowSupportSlab(fullyBelow,-2));
        var escapeWithNoTerrainOverlap=HullTerrainGeometry.againstTerrain(fullyBelow,
                new Matrix4d().translation(0,-20,0),new AABB(0,-2,0,8,0,8),scratch);
        assertFalse(escapeWithNoTerrainOverlap.overlaps());
        assertTrue(HullTerrainGeometry.fullyBelowSupportSlab(escapeWithNoTerrainOverlap.worldBounds(),-2),
                "clearing an entire thick slab is a separate escape failure, never a SAT non-overlap pass");
    }

    @Test void impactProbeUsesAuthoredFourLayerPadsTopSurfaceRatherThanBottomBlockY() {
        var origin=new BlockPos(3600,-47,3600);
        int top=SableFixtureScene.supportSurfaceY(origin);
        int bottom=SableFixtureScene.supportBottomY(origin,4);
        assertEquals(-47,top);
        assertEquals(-51,bottom);
        assertEquals(-48,SableFixtureScene.supportCell(origin,0,1,0).getY());
        assertEquals(-51,SableFixtureScene.supportCell(origin,0,4,0).getY());

        // Captured production-hull minima were about 3e-6 from the stone top at rest.
        var groundedHull=new AABB(3600,top+3.0e-6,3600,3608,top+7,3613);
        assertTrue(HullTerrainGeometry.nearSupportSurface(groundedHull,top,.10,.10));
        assertFalse(HullTerrainGeometry.nearSupportSurface(groundedHull,top-1,.10,.10),
                "using support block Y instead of the exposed top must fail pre-launch validation");
    }

    @Test void initialPlacementGuardAllowsNativeIdentityNoiseButRejectsMaterialOrInvalidRotations() {
        assertTrue(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(2.638e-8,5.705e-9,-3.438e-7,1)));
        assertTrue(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(0,0,0,1)));
        assertTrue(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(0,0,0,-1)));
        assertFalse(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(1e-4,0,0,1)));
        assertFalse(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(Double.NaN,0,0,1)));
        assertFalse(HullTerrainGeometry.nearIdentityOrientation(new Quaterniond(0,Double.POSITIVE_INFINITY,0,1)));
    }

    @Test void sceneBoundsRejectOverlapButAllowAdjacentAndDifferentLevels() {
        var a=new BlockPos(0,0,0); var b=new BlockPos(2,2,2);
        assertTrue(SableFixtureScene.overlaps(a,b,new BlockPos(2,1,1),new BlockPos(4,3,3)));
        assertFalse(SableFixtureScene.overlaps(a,b,new BlockPos(3,0,0),new BlockPos(5,2,2)));
        assertFalse(SableFixtureScene.overlaps(a,b,new BlockPos(0,3,0),new BlockPos(2,5,2)));
    }

    @Test void cleanupLedgerOnlyAuthorizesSuccessfullyRecordedCellsAndIsIdempotent() {
        var owned=new HashSet<BlockPos>();
        var foreignStone=new BlockPos(1,2,3); var authored=new BlockPos(4,5,6);
        assertFalse(SableFixtureScene.forgetAuthoredCell(owned,foreignStone),"never-authored cells cannot be cleared");
        owned.add(authored);
        assertTrue(SableFixtureScene.forgetAuthoredCell(owned,authored),"successful authored cell is claimed for cleanup");
        assertFalse(SableFixtureScene.forgetAuthoredCell(owned,authored),"repeated cleanup has no cell to clear");
        // A failed later placement never enters the ledger; earlier successful placements remain removable.
        owned.add(authored);
        var partialFailureCell=new BlockPos(7,8,9);
        assertFalse(SableFixtureScene.forgetAuthoredCell(owned,partialFailureCell));
        assertTrue(SableFixtureScene.forgetAuthoredCell(owned,authored));
    }

    @Test void activeSceneAdmissionRejectsConflictsAndReleaseAllowsExactReacquisition() {
        var first=scene(0,0,0,2,2,2,"first");
        var overlap=scene(1,1,1,3,3,3,"overlap");
        var adjacent=scene(4,0,0,6,2,2,"adjacent");
        SableFixtureScene.admit(null,first);
        assertThrows(IllegalStateException.class,()->SableFixtureScene.admit(null,overlap));
        SableFixtureScene.release(null,first);
        SableFixtureScene.admit(null,overlap);
        SableFixtureScene.admit(null,adjacent);
        SableFixtureScene.release(null,overlap);
        SableFixtureScene.release(null,adjacent);
        SableFixtureScene.admit(null,first);
        SableFixtureScene.release(null,first);
    }

    @Test void relocatedNativeFixturesAvoidKnownConcurrentSceneBounds() {
        var cargoOrigin = new BlockPos(2000, 300, 3300);
        var cargoSceneMin = cargoOrigin.offset(-16, -1, -16);
        var cargoSceneMax = cargoOrigin.offset(21, 0, 21);
        var boardingMin = new BlockPos(1992, 299, 2992);
        var boardingMax = new BlockPos(2013, 299, 3013);
        assertFalse(SableFixtureScene.overlaps(cargoSceneMin, cargoSceneMax, boardingMin, boardingMax),
                "registered cargo scene retains its 16-block corridor and deck-relative placement, away from boarding");

        var pigOrigin = SableOwnedBodyCarryGameTests.ROTATING_PIG_IMPACT_ORIGIN;
        var pigSceneMin = pigOrigin.offset(-8, -1, -8);
        var pigSceneMax = pigOrigin.offset(13, 0, 13);
        var impulseMin = new BlockPos(1500, 299, 640);
        var impulseMax = new BlockPos(1595, 311, 671);
        assertFalse(SableFixtureScene.overlaps(pigSceneMin, pigSceneMax, impulseMin, impulseMax),
                "rotating pig scene clears the strong-impulse furnished cabin bounds");
    }

    @Test void strongCargoStressCabinUsesRelocatedSceneDisjointFromSelectedPigFixture() {
        int sceneWidth = SableOwnedBodyCarryGameTests.strongCargoSceneWidth();
        assertEquals(96, sceneWidth, "stress corridor retains the same derived 96-block width");
        var cargoOrigin = SableOwnedBodyCarryGameTests.STRONG_CARGO_ORIGIN;
        var cargoMin = cargoOrigin.below();
        var cargoMax = cargoOrigin.offset(sceneWidth - 1, 11, 31);

        var oldOrigin = new BlockPos(1500, 300, 640);
        var oldMin = oldOrigin.below();
        var oldMax = oldOrigin.offset(sceneWidth - 1, 11, 31);
        var pigOrigin = SableOwnedBodyCarryGameTests.TRANSLATING_PIG_IMPACT_ORIGIN;
        var pigSceneMin = pigOrigin.offset(-8, -1, -8);
        var pigSceneMax = pigOrigin.offset(13, 0, 13);

        assertTrue(SableFixtureScene.overlaps(oldMin, oldMax, pigSceneMin, pigSceneMax),
                "prior strong-cargo furnished-cabin footprint reproduces the actual selected pig-scene collision");
        assertFalse(SableFixtureScene.overlaps(cargoMin, cargoMax, pigSceneMin, pigSceneMax),
                "relocated strong-cargo footprint clears the same selected pig fixture");
    }

    @Test void pigImpactFixturesWireTheRotatingOriginOnlyToRotatingConsumer() throws Exception {
        var type = SableOwnedBodyCarryGameTests.class;
        var node = new ClassNode();
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input, "missing compiled fixture class");
            new ClassReader(input).accept(node, 0);
        }
        var rotating = node.methods.stream().filter(method -> method.name.equals("rotatingHullSideImpactLaunchesVanillaPig")).findFirst().orElseThrow();
        var translating = node.methods.stream().filter(method -> method.name.equals("translatingHullSideImpactLaunchesVanillaPig")).findFirst().orElseThrow();
        assertTrue(usesRotatingOrigin(type, rotating), "rotating pig fixture must consume ROTATING_PIG_IMPACT_ORIGIN");
        assertFalse(usesRotatingOrigin(type, translating), "translating pig fixture must retain its original separate origin");
    }

    @Test void stationaryHumanCarryFixturesWirePadFeedbackAndPreFinalizationHullAuthoring() throws Exception {
        var type=SableOwnedBodyCarryGameTests.class;
        var node=new ClassNode();
        try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")){
            assertNotNull(input,"missing compiled carry fixture class");
            new ClassReader(input).accept(node,0);
        }
        var deck=node.methods.stream().filter(m->m.name.equals("stationaryDeckWallContactKeepsSupportedHumanValid")).findFirst().orElseThrow();
        assertTrue(calls(deck,"com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene","acquire"),
                "stationary wall test leases its physical corridor scene");
        boolean eightBlockMargin=false, widenedScene=false;
        for(var instruction:deck.instructions)if(instruction instanceof IntInsnNode value){
            eightBlockMargin|=value.operand==-8;
            widenedScene|=value.operand==22;
        }
        assertTrue(eightBlockMargin&&widenedScene,"stationary wall lease uses 8-block margins on both sides");
        assertTrue(node.methods.stream().filter(m->m.name.startsWith("lambda$stationaryDeckWallContactKeepsSupportedHumanValid"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixtureScene","authorStoneSupportPad")
                                &&calls(m,"com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixturePlot","allocateAndAuthor")),
                "named wall test authors the physical pad and floor/wall geometry before fixture mass finalization");

        var moving=node.methods.stream().filter(m->m.name.equals("boundHumanStandsAndWalksRelativeToTranslatingNativeDeck")).findFirst().orElseThrow();
        assertTrue(node.methods.stream().filter(m->m.name.contains("boundHumanStandsAndWalksRelativeToTranslatingNativeDeck"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureMotionService","start")),
                "moving human test starts the existing physical feedback controller");
        assertTrue(node.methods.stream().anyMatch(m->m.name.contains("awaitMeasuredFixtureTransport")
                        &&calls(m,"com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureMotionService","snapshot")),
                "moving human test waits on measured controller transport");
        assertTrue(node.methods.stream().anyMatch(m->m.name.contains("awaitMeasuredFixtureTransport")
                        &&calls(m,"com/juicyslew/moonstation14/ms14/player_body_control/character/GroundedHarnessWorldStep","step")),
                "each scheduled transport-warmup tick advances the real owned human motor");
        assertTrue(node.methods.stream().filter(m->m.name.contains("boundHumanStandsAndWalksRelativeToTranslatingNativeDeck"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyCarryGameTests","attachDiagnosticCleanup")),
                "owned moving fixture attaches its existing terminal cleanup before controller start");
        assertTrue(node.methods.stream().filter(m->m.name.startsWith("lambda$boundHumanStandsAndWalksRelativeToTranslatingNativeDeck"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureMotionService","stop")),
                "attached terminal cleanup stops the physical controller");

        var cabin=node.methods.stream().filter(m->m.name.equals("stationaryCabinWallDoesNotBecomeHumanFloorSupport")).findFirst().orElseThrow();
        assertTrue(node.methods.stream().filter(m->m.name.startsWith("lambda$stationaryCabinWallDoesNotBecomeHumanFloorSupport"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/sable/SableFixturePlot","allocateAndAuthor")),
                "cabin fixture allocates through the author-before-finalization entry point");
        assertTrue(node.methods.stream().filter(m->m.name.startsWith("lambda$stationaryCabinWallDoesNotBecomeHumanFloorSupport"))
                        .anyMatch(m->calls(m,"com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleCabinFixture","buildHull")),
                "whole cabin hull is authored inside allocateAndAuthor callback before mass finalization");
    }

    private static boolean calls(org.objectweb.asm.tree.MethodNode method,String owner,String name){
        for(var instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals(name))return true;
        return false;
    }

    private static boolean usesRotatingOrigin(Class<?> type, org.objectweb.asm.tree.MethodNode method) {
        for (var instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && field.owner.equals(type.getName().replace('.', '/')) && field.name.equals("ROTATING_PIG_IMPACT_ORIGIN"))
                return true;
        return false;
    }

    private static SableFixtureScene.Region scene(int x,int y,int z,int maxX,int maxY,int maxZ,String owner) {
        var min=new BlockPos(x,y,z); var max=new BlockPos(maxX,maxY,maxZ);
        var region=new SableFixtureScene.Region(min,min,max,maxX-x+1,maxY-y+1,maxZ-z+1,1);
        region.owner=owner;
        return region;
    }
}
