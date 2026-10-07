package com.juicyslew.moonstation14.ms14.disposal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.disposal.domain.*;
import com.juicyslew.moonstation14.ms14.disposal.prototype.*;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeBlock;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasTransfer;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DisposalTubePrototypeTest {
    @Test void idsAcceptNamespacedCatalogKeysWithoutRebasing() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14", "disposal_pipe"), ModDisposals.id("disposal_pipe"));
        assertEquals(ResourceLocation.fromNamespaceAndPath("othermod", "disposal_pipe"), ModDisposals.id("othermod:disposal_pipe"));
    }

    private static final String[] IDS = {"disposal_pipe", "disposal_bend", "disposal_trunk", "disposal_junction", "disposal_junction_flipped", "disposal_y_junction", "disposal_x_junction", "disposal_tagger", "disposal_router", "disposal_router_flipped"};
    private static JsonObject json(String id) {
        var in = Objects.requireNonNull(DisposalTubePrototypeTest.class.getResourceAsStream("/data/moonstation14/moonstation14/disposal_entity/" + id + ".json"));
        try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) { return JsonParser.parseReader(reader).getAsJsonObject(); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private static Map<ResourceLocation, JsonObject> bundled() {
        var out = new LinkedHashMap<ResourceLocation, JsonObject>();
        out.put(ModDisposals.BASE, json("disposal_tube_base"));
        for (String id : List.of("base_structure", "base_machine", "base_machine_powered", "disposal_unit_base", "disposal_unit"))
            out.put(ModDisposals.id(id), json(id));
        out.put(ModDisposals.HOLDER, json("disposal_holder"));
        for (String id : IDS) out.put(ModDisposals.id(id), json(id));
        return out;
    }
    @Test void holderCompositionResolvesFiniteAirAndTransitDefaults() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var holder = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.HOLDER).holder();
        assertEquals(70, holder.fallbackAirLiters()); assertEquals(.1, holder.segmentSeconds());
        assertEquals("DisposalHolderComponent", holder.containerId());
    }
    @Test void holderFlushMergeMovesAllSpeciesAndEnthalpyWithoutChangingDestinationVolume() {
        GasMixture holder = new GasMixture(Map.of(GasType.OXYGEN, 2.0, GasType.NITROGEN, 3.0), 250.0);
        GasMixture room = new GasMixture(Map.of(GasType.CARBON_DIOXIDE, 1.0), 300.0);
        var plan = PipeGasTransfer.plan(holder, room, .070, 1.0, holder.totalMoles(), Double.MAX_VALUE,
                EnumSet.allOf(GasType.class)).orElseThrow();
        assertEquals(0, plan.sourceAfter().totalMoles(), 1e-12);
        assertEquals(2, plan.destinationAfter().moles(GasType.OXYGEN), 1e-12);
        assertEquals(3, plan.destinationAfter().moles(GasType.NITROGEN), 1e-12);
        assertEquals(1, plan.destinationAfter().moles(GasType.CARBON_DIOXIDE), 1e-12);
        assertEquals(holder.thermalEnergy() + room.thermalEnergy(), plan.destinationAfter().thermalEnergy(), 1e-8);
    }
    @Test void resolvedRotationAndEveryJunctionShapeRouteByAuditedOrderedDirections() {
        var raw = bundled(); raw.get(ModDisposals.id("disposal_y_junction")).addProperty("rotationQuarters", 1);
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, raw); var c = m.snapshot(ModDisposals.DISPOSAL_TYPE);
        var y = c.get(ModDisposals.id("disposal_y_junction")).tube();
        assertEquals(List.of(DisposalDirection.EAST, DisposalDirection.SOUTH, DisposalDirection.NORTH), y.topology().directions().stream().map(d -> d.rotate(y.rotationQuarters())).toList());
        assertEquals(DisposalDirection.SOUTH, DisposalTubeRouting.selectNextDirection(y, DisposalDirection.EAST, Set.of(), b -> 0));
        var x = c.get(ModDisposals.id("disposal_x_junction")).tube();
        assertEquals(List.of(DisposalDirection.NORTH, DisposalDirection.WEST, DisposalDirection.SOUTH, DisposalDirection.EAST), x.topology().directions());
        assertEquals(DisposalDirection.WEST, DisposalTubeRouting.selectNextDirection(x, DisposalDirection.NORTH, Set.of(), b -> 0));
        var flipped = c.get(ModDisposals.id("disposal_junction_flipped")).tube();
        assertEquals(List.of(DisposalDirection.NORTH, DisposalDirection.EAST, DisposalDirection.SOUTH), flipped.topology().directions());
        assertEquals(DisposalDirection.EAST, DisposalTubeRouting.selectNextDirection(flipped, DisposalDirection.NORTH, Set.of(), b -> 0));
        var normal = c.get(ModDisposals.id("disposal_junction")).tube();
        assertEquals(DisposalDirection.WEST, DisposalTubeRouting.selectNextDirection(normal, DisposalDirection.NORTH, Set.of(), b -> 0));
    }
    @Test void unitCompositionDefaultsDriveTimingKernel() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var unit = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT);
        assertEquals(1000, unit.unit().powerWatts()); assertEquals(2500, unit.unit().airLiters());
        assertEquals(0.5, unit.unit().entrySeconds()); assertEquals(2, unit.unit().draggedEntrySeconds());
        var kernel = new DisposalUnitTimingKernel(unit.unit(), 0);
        kernel.insert(1); assertEquals(600, kernel.nextFlushTick());
        kernel.manualEngage(); assertEquals(40, kernel.nextFlushTick());
        assertEquals(DisposalUnitTimingKernel.FlushResult.FLUSH_REQUESTED, kernel.advanceTo(41, true));
        assertEquals(441, kernel.nextPressurizedTick()); assertEquals(DisposalUnitTimingKernel.State.FLUSHED, kernel.currentState());
    }
    @Test void containedViewCompositionInheritsUnitDefaultAndHolderOverride() {
        var raw = bundled(); var manager = manager(); manager.reload(ModDisposals.DISPOSAL_TYPE, raw);
        var resolved = manager.snapshot(ModDisposals.DISPOSAL_TYPE);
        assertTrue(resolved.get(ModDisposals.UNIT).containedView().cameraCollision());
        assertFalse(resolved.get(ModDisposals.HOLDER).containedView().cameraCollision());

        raw.get(ModDisposals.id("disposal_unit")).add("containedView", JsonParser.parseString("{\"cameraCollision\":false}").getAsJsonObject());
        manager.reload(ModDisposals.DISPOSAL_TYPE, raw);
        assertFalse(manager.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).containedView().cameraCollision());
    }
    @Test void powerEntryTrunkAndExitAttemptsFollowAuditedSchedule() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var kernel = new DisposalUnitTimingKernel(m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit(), 0);
        kernel.insert(2); kernel.manualEngage(); kernel.setPowered(false); assertNull(kernel.nextFlushTick());
        kernel.setPowered(true); assertEquals(40, kernel.nextFlushTick());
        assertEquals(DisposalUnitTimingKernel.FlushResult.MISSING_ENTRY_CANCELLED, kernel.advanceTo(41, false));
        assertFalse(kernel.engaged()); assertEquals(2, kernel.contents());
        assertTrue(kernel.exitAttemptAllowed()); assertFalse(kernel.exitAttemptAllowed());
        assertEquals(DisposalUnitTimingKernel.FlushResult.NONE, kernel.advanceTo(51, true));
        assertTrue(kernel.exitAttemptAllowed());
    }
    @Test void unitTimingSnapshotRoundTripsChargingEngagementAndPowerLoss() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var config = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit();
        var original = new DisposalUnitTimingKernel(config, 0);
        original.insert(1); original.manualEngage();
        assertEquals(DisposalUnitTimingKernel.FlushResult.FLUSH_REQUESTED, original.advanceTo(41, true));
        original.manualEngage(); original.setPowered(false);
        var snapshot = original.snapshot();
        var restored = DisposalUnitTimingKernel.restore(config, snapshot);
        assertEquals(snapshot, restored.snapshot());
        assertEquals(DisposalUnitTimingKernel.State.FLUSHED, restored.currentState());
        restored.setPowered(true);
        assertEquals(DisposalUnitTimingKernel.FlushResult.NONE, restored.advanceTo(441, true));
        assertEquals(481, restored.nextFlushTick());
        assertTrue(restored.engaged());
    }
    @Test void missingEntryAdvancedDeadlineSurvivesRestoreAndInvalidSnapshotIsRejected() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var config = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit();
        var original = new DisposalUnitTimingKernel(config, 0); original.insert(1); original.manualEngage();
        assertEquals(DisposalUnitTimingKernel.FlushResult.MISSING_ENTRY_CANCELLED, original.advanceTo(41, false));
        var snapshot = original.snapshot(); assertEquals(640, snapshot.nextFlushTick());
        assertEquals(snapshot, DisposalUnitTimingKernel.restore(config, snapshot).snapshot());
        var invalid = new DisposalUnitTimingKernel.StateSnapshot(20, true, true, true, -1, 10L, 5, null);
        assertThrows(IllegalArgumentException.class, () -> DisposalUnitTimingKernel.restore(config, invalid));
        assertEquals(snapshot, original.snapshot());
    }
    @Test void persistedFutureClockRebasesRemainingDeadlinesWithoutWeakeningMonotonicAdvance() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var config = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.UNIT).unit();
        var original = new DisposalUnitTimingKernel(config, 1000);
        assertTrue(original.exitAttemptAllowed());
        original.insert(1); original.manualEngage();
        original.advanceTo(1020, true);
        original.manualEngage();
        var saved = original.snapshot();
        var restored = DisposalUnitTimingKernel.restore(config, saved);
        restored.rebaseClock(0);
        assertEquals(0, restored.nowTick());
        assertEquals(saved.nextFlushTick() - saved.logicalTick(), restored.nextFlushTick());
        assertEquals(Math.max(0, saved.pressureDeadlineTick() - saved.logicalTick()), restored.nextPressurizedTick());
        assertEquals(DisposalUnitTimingKernel.FlushResult.NONE, restored.advanceTo(0, true));
        assertThrows(IllegalArgumentException.class, () -> restored.advanceTo(-1, true));
        assertEquals(saved.engaged(), restored.engaged());
        assertEquals(0, restored.contents()); // payload count belongs to the adapter, not timing snapshot.

        var exitBefore = saved.lastExitAttemptTick();
        assertNotNull(exitBefore);
        assertNull(restored.snapshot().lastExitAttemptTick());
        assertTrue(restored.exitAttemptAllowed());
        assertEquals(restored.snapshot(), DisposalUnitTimingKernel.restore(config, restored.snapshot()).snapshot());

        var flushed = new DisposalUnitTimingKernel(config, 0);
        flushed.insert(1); flushed.manualEngage(); flushed.advanceTo(41, true);
        var flushedSave = flushed.snapshot();
        var flushedRestored = DisposalUnitTimingKernel.restore(config, flushedSave);
        flushedRestored.rebaseClock(0);
        assertEquals(flushedSave.visualDeadlineTick() - flushedSave.logicalTick(), flushedRestored.snapshot().visualDeadlineTick());
        assertEquals(DisposalUnitTimingKernel.State.FLUSHED, flushedRestored.currentState());
    }
    private static PrototypeManager manager() { var m = new PrototypeManager(); m.register(ModDisposals.DISPOSAL_TYPE); return m; }
    @Test void resolvedCompositionInheritsTubeDefaultsAndRoutesFromCatalog() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var c = m.snapshot(ModDisposals.DISPOSAL_TYPE);
        assertNull(c.get(ModDisposals.BASE));
        var straight = c.get(ModDisposals.id("disposal_pipe"));
        assertTrue(straight.anchored()); assertEquals(.1, straight.tube().segmentSeconds()); assertEquals(0, straight.tube().turnDamage());
        var rightBend = c.get(ModDisposals.id("disposal_bend"));
        assertEquals(Set.of(DisposalDirection.NORTH, DisposalDirection.WEST), DisposalTubeRouting.connections(rightBend.tube()));
        assertNotEquals(DisposalTubeRouting.selectNextDirection(straight.tube(), DisposalDirection.NORTH, Set.of(), bound -> 0),
                DisposalTubeRouting.selectNextDirection(rightBend.tube(), DisposalDirection.NORTH, Set.of(), bound -> 0));
        assertEquals(DisposalDirection.NORTH, DisposalTubeRouting.selectNextDirection(c.get(ModDisposals.id("disposal_trunk")).tube(), null, Set.of(), b -> 0));
    }
    @Test void tubeKindsUseCorrectRandomTagAndConnectionRules() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled()); var c = m.snapshot(ModDisposals.DISPOSAL_TYPE);
        var junction = c.get(ModDisposals.id("disposal_junction")).tube();
        assertEquals(DisposalDirection.WEST, DisposalTubeRouting.selectNextDirection(junction, DisposalDirection.NORTH, Set.of(), b -> 0));
        assertEquals(DisposalDirection.SOUTH, DisposalTubeRouting.selectNextDirection(junction, DisposalDirection.NORTH, Set.of(), b -> 1));
        var flipped = c.get(ModDisposals.id("disposal_junction_flipped")).tube();
        assertEquals(DisposalDirection.EAST, DisposalTubeRouting.selectNextDirection(flipped, DisposalDirection.NORTH, Set.of(), b -> 0));
        var router = c.get(ModDisposals.id("disposal_router")).tube();
        assertEquals(DisposalDirection.WEST, DisposalTubeRouting.selectNextDirection(router, DisposalDirection.EAST, Set.of("parcel"), b -> 0));
        var tags = DisposalTubeRouting.appendTag(Set.of("existing"), "new");
        assertEquals(Set.of("existing", "new"), tags);
        assertTrue(DisposalTubeRouting.validTagExpression("parcel, fragile")); assertFalse(DisposalTubeRouting.validTagExpression("parcel!"));
        assertTrue(DisposalTubeRouting.validTaggerInput("fragile 2")); assertFalse(DisposalTubeRouting.validTaggerInput("fragile,"));
        assertTrue(DisposalTubeRouting.mutuallyConnected(straight(c).tube(), true, DisposalDirection.SOUTH,
                c.get(ModDisposals.id("disposal_bend")).tube(), true));
        assertFalse(DisposalTubeRouting.mutuallyConnected(straight(c).tube(), false, DisposalDirection.SOUTH,
                c.get(ModDisposals.id("disposal_bend")).tube(), true));
    }
    @Test void bendRoutesFromEitherConnectedSideToTheOtherAtEveryRotation() {
        var m = manager(); m.reload(ModDisposals.DISPOSAL_TYPE, bundled());
        var base = m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_bend")).tube();
        for (int rotation = 0; rotation < 4; rotation++) {
            var bend = new DisposalEntityData.Tube(base.kind(), base.topology(), rotation,
                    base.segmentSeconds(), base.turnDamage(), base.tag(), base.routerTags());
            var forward = base.topology().directions().get(0).rotate(rotation);
            var side = base.topology().directions().get(1).rotate(rotation);
            assertEquals(side, DisposalTubeRouting.selectNextDirection(bend, forward, Set.of(), b -> 0));
            assertEquals(forward, DisposalTubeRouting.selectNextDirection(bend, side, Set.of(), b -> 0));
            assertNotEquals(forward, DisposalTubeRouting.selectNextDirection(bend, forward, Set.of(), b -> 0), "must not bounce to incoming neighbor");
            assertEquals(forward, DisposalTubeRouting.selectNextDirection(bend, null, Set.of(), b -> 0));
        }
    }
    @Test void everyOrthogonalFrameMapsSixPortsBijectivelyAndReciprocally() {
        var seen = new HashSet<String>();
        for (var forward : net.minecraft.core.Direction.values()) for (var up : net.minecraft.core.Direction.values()) {
            if (forward.getAxis()==up.getAxis()) continue;
            var frame=new DisposalOrientation(forward,up);
            assertEquals(6, Arrays.stream(DisposalDirection.values()).map(frame::world).distinct().count());
            for (var local : DisposalDirection.values()) assertEquals(local, frame.local(frame.world(local)), "direction conversion");
            for (var local : DisposalDirection.values()) assertEquals(frame.world(local).getOpposite(), frame.world(local.opposite()));
            seen.add(forward+":"+up);
        }
        assertEquals(24,seen.size());
        var identity=DisposalOrientation.identity();
        for(var d:DisposalDirection.values()) assertEquals(d.toMC(),identity.world(d));
        for(var d:DisposalDirection.values()) assertEquals(d,d.rotate(4));
        assertEquals(DisposalDirection.UP,DisposalDirection.UP.rotate(1));
        assertEquals(DisposalDirection.DOWN,DisposalDirection.DOWN.rotate(-1));
        for(var face:net.minecraft.core.Direction.values()) {
            var placement=DisposalOrientation.placement(face, face.getOpposite());
            assertEquals(face,placement.forward());
        }
    }
    @Test void worldRoutingUsesTheSameFrameForConnectionsAndDirectionalRoles() {
        var m=manager(); m.reload(ModDisposals.DISPOSAL_TYPE,bundled());
        var cat=m.snapshot(ModDisposals.DISPOSAL_TYPE);
        var bend=cat.get(ModDisposals.id("disposal_bend")).tube();
        for(var forward:net.minecraft.core.Direction.values()) for(var up:net.minecraft.core.Direction.values()) {
            if(forward.getAxis()==up.getAxis()) continue;
            var frame=new DisposalOrientation(forward,up);
            var ports=DisposalTubeRouting.worldConnections(bend,frame);
            var f=DisposalDirection.fromMC(frame.world(DisposalDirection.NORTH));
            var side=DisposalDirection.fromMC(frame.world(DisposalDirection.WEST));
            assertEquals(Set.of(f,side),ports);
            assertEquals(side,DisposalTubeRouting.selectNextDirection(bend,frame,f,Set.of(),b->0));
            assertEquals(f,DisposalTubeRouting.selectNextDirection(bend,frame,side,Set.of(),b->0));
        }
    }
    @Test void tubeGeometryInheritsAndCustomResolvedDiameterChangesCoreAndArms() {
        var raw=bundled(); var m=manager(); m.reload(ModDisposals.DISPOSAL_TYPE,raw);
        var inherited=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        assertEquals(14.0/16.0,inherited.geometry().diameter());
        raw=bundled(); raw.get(ModDisposals.BASE).addProperty("tubeDiameter",.5);
        m.reload(ModDisposals.DISPOSAL_TYPE,raw);
        var custom=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        assertEquals(.5,custom.geometry().diameter());
        var boxes=custom.geometry().boxes(custom.tube(),DisposalOrientation.identity());
        assertTrue(boxes.size()>3);
        assertTrue(boxes.stream().allMatch(box->box.getXsize()<.5));
        assertTrue(boxes.stream().anyMatch(box->box.maxZ==1));
        assertThrows(IllegalArgumentException.class,()->new DisposalEntityData.TubeGeometry(0));
    }
    @Test void trunkGeometryAddsPhysicalUpInterfaceWithoutAddingLogicalRoutePort() {
        var m=manager(); m.reload(ModDisposals.DISPOSAL_TYPE,bundled());
        var catalog=m.snapshot(ModDisposals.DISPOSAL_TYPE);
        var trunk=catalog.get(ModDisposals.id("disposal_trunk")).tube();
        var frame=new DisposalOrientation(net.minecraft.core.Direction.DOWN,net.minecraft.core.Direction.NORTH);
        assertEquals(Set.of(DisposalDirection.DOWN),DisposalTubeRouting.worldConnections(trunk,frame));
        var boxes=catalog.get(ModDisposals.id("disposal_trunk")).geometry().boxes(trunk,frame);
        assertTrue(boxes.size()>3); // rounded hub, logical DOWN port, physical unit interface UP
        assertTrue(boxes.stream().anyMatch(box->box.minY==0));
        assertTrue(boxes.stream().anyMatch(box->box.maxY==1));

        var upright=new DisposalOrientation(net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.EAST);
        var rolled=catalog.get(ModDisposals.id("disposal_trunk")).geometry().boxes(trunk,upright);
        assertTrue(rolled.stream().anyMatch(box->box.maxY==1));
        assertEquals(Set.of(DisposalDirection.NORTH),DisposalTubeRouting.worldConnections(trunk,upright));
    }
    @Test void liveBlockShapeConsumesResolvedCustomDiameterAndFramePorts() {
        var raw=bundled(); raw.get(ModDisposals.BASE).addProperty("tubeDiameter",.5);
        var m=manager(); m.reload(ModDisposals.DISPOSAL_TYPE,raw);
        var entity=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        var record=new DisposalTubeRecord(UUID.randomUUID(),new net.minecraft.core.BlockPos(0,0,0),
                ModDisposals.id("disposal_pipe").toString(),DisposalOrientation.identity(),true,"","",0,
                DisposalTubeRecord.HostKind.EXPOSED,"",null);
        var shape=DisposalTubeBlock.shapeFor(entity,record);
        assertEquals(.25,shape.bounds().minY,1e-9);
        assertEquals(.75,shape.bounds().maxY,1e-9);
        assertEquals(0,shape.bounds().minZ,1e-9);
        assertEquals(1,shape.bounds().maxZ,1e-9);
        assertTrue(occupied(shape,.5,.5,.9));
        assertFalse(occupied(shape,.1,.1,.9));
        assertFalse(occupied(shape,.01,.01,.01));
    }
    @Test void collisionAndMeshProductsAreSharedByPhysicalConfiguration() {
        var m=manager();m.reload(ModDisposals.DISPOSAL_TYPE,bundled());
        var entity=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        var frame=DisposalOrientation.identity();
        var first=com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(entity.geometry(),entity.tube(),frame);
        var second=com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(entity.geometry(),entity.tube(),frame);
        assertSame(first,second);
        assertSame(first.shape(),DisposalTubeBlock.shapeFor(entity,new DisposalTubeRecord(UUID.randomUUID(),new net.minecraft.core.BlockPos(0,0,0),
                ModDisposals.id("disposal_pipe").toString(),frame,true,"","",0,DisposalTubeRecord.HostKind.EXPOSED,"",null)));
        assertSame(first.mesh(),entity.geometry().mesh(entity.tube(),frame));
        var custom=new DisposalEntityData.TubeGeometry(.53125);
        var cold=com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(custom,entity.tube(),frame);
        assertNotSame(first,cold);
        for(var axis:net.minecraft.core.Direction.Axis.values()) assertTrue(cold.shape().getCoords(axis).size()<=17);
    }
    @Test void coldTrunkAndJunctionCollisionGridsAreBoundedForEveryFrame() {
        var m=manager();m.reload(ModDisposals.DISPOSAL_TYPE,bundled());var catalog=m.snapshot(ModDisposals.DISPOSAL_TYPE);
        int sample=0;
        for(String id:List.of("disposal_trunk","disposal_junction","disposal_x_junction")) {
            var entity=catalog.get(ModDisposals.id(id));
            for(var forward:net.minecraft.core.Direction.values()) for(var up:net.minecraft.core.Direction.values()) if(forward.getAxis()!=up.getAxis()) {
                var frame=new DisposalOrientation(forward,up);
                // A unique non-lattice diameter forces genuine cold construction for each test configuration.
                var geometry=new DisposalEntityData.TubeGeometry(.6+sample++/2048.0);
                long coldStart=System.nanoTime();
                var products=com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(geometry,entity.tube(),frame);
                long coldNanos=System.nanoTime()-coldStart;
                for(var axis:net.minecraft.core.Direction.Axis.values()) assertTrue(products.shape().getCoords(axis).size()<=17,id+" "+frame+" "+axis);
                long warmStart=System.nanoTime();
                for(int i=0;i<1000;i++) assertSame(products,com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(geometry,entity.tube(),frame));
                long warmNanos=System.nanoTime()-warmStart;
                System.out.printf("tube geometry %s %s cold=%.3fms warm1000=%.3fms%n",id,frame,coldNanos/1_000_000.0,warmNanos/1_000_000.0);
            }
        }
    }
    @Test void roundedPortCrossSectionsFollowAllSixAxesAndResolvedDiameter() {
        var raw=bundled();var m=manager();m.reload(ModDisposals.DISPOSAL_TYPE,raw);
        var parent=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        raw=bundled();raw.get(ModDisposals.BASE).addProperty("tubeDiameter",.5);m.reload(ModDisposals.DISPOSAL_TYPE,raw);
        var child=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        assertEquals(14.0/16,parent.geometry().diameter());assertEquals(.5,child.geometry().diameter());
        assertEquals(.0625,parent.geometry().mesh(parent.tube(),DisposalOrientation.identity()).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(DisposalMeshGeometry.Vertex::x).min().orElseThrow(),1e-9);
        assertEquals(.25,child.geometry().mesh(child.tube(),DisposalOrientation.identity()).stream().flatMap(q->java.util.stream.Stream.of(q.a(),q.b(),q.c(),q.d())).mapToDouble(DisposalMeshGeometry.Vertex::x).min().orElseThrow(),1e-9);
        for(var forward:net.minecraft.core.Direction.values())for(var up:net.minecraft.core.Direction.values())if(forward.getAxis()!=up.getAxis()){
            var frame=new DisposalOrientation(forward,up);var tube=parent.tube();var a=parent.geometry().boxes(tube,frame);var b=child.geometry().boxes(tube,frame);
            for(var port:DisposalTubeRouting.worldConnections(tube,frame)){
                var d=port.toMC();double[] p={.5,.5,.5};int axial=d.getAxis()==net.minecraft.core.Direction.Axis.X?0:d.getAxis()==net.minecraft.core.Direction.Axis.Y?1:2;p[axial]=d.getStepX()+d.getStepY()+d.getStepZ()>0?.9:.1;
                int transverse1=d.getAxis()==net.minecraft.core.Direction.Axis.X?1:0,transverse2=d.getAxis()==net.minecraft.core.Direction.Axis.Z?1:2;p[transverse1]=.8;
                assertTrue(occupied(a,p[0],p[1],p[2]));assertFalse(occupied(b,p[0],p[1],p[2]));
                double[] corner={.1,.1,.1};corner[axial]=p[axial];
                corner[transverse1]=.1;corner[transverse2]=.1;assertFalse(occupied(a,corner[0],corner[1],corner[2]));
            }
        }
    }
    @Test void straightCollisionHasNoUnrenderedHubBulgeOnAnyAxis(){
        var m=manager();m.reload(ModDisposals.DISPOSAL_TYPE,bundled());var straight=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_pipe"));
        var cases=java.util.List.of(new Object[]{DisposalOrientation.identity(),.85,.85,.5},new Object[]{new DisposalOrientation(net.minecraft.core.Direction.EAST,net.minecraft.core.Direction.UP),.5,.85,.85},new Object[]{new DisposalOrientation(net.minecraft.core.Direction.UP,net.minecraft.core.Direction.NORTH),.85,.5,.85});
        for(var test:cases){var frame=(DisposalOrientation)test[0];var shape=straight.geometry().boxes(straight.tube(),frame);assertFalse(occupied(shape,(double)test[1],(double)test[2],(double)test[3]));
            var port=DisposalTubeRouting.worldConnections(straight.tube(),frame).iterator().next().toMC();double[] center={.5,.5,.5};int axis=port.getAxis()==net.minecraft.core.Direction.Axis.X?0:port.getAxis()==net.minecraft.core.Direction.Axis.Y?1:2;center[axis]=port.getStepX()+port.getStepY()+port.getStepZ()>0?.9:.1;
            assertTrue(occupied(shape,center[0],center[1],center[2]));
        }
    }
    @Test void resolvedBendUsesTheSameClosedJoinInsteadOfTwoCutArms(){
        var m=manager();m.reload(ModDisposals.DISPOSAL_TYPE,bundled());var bend=m.snapshot(ModDisposals.DISPOSAL_TYPE).get(ModDisposals.id("disposal_bend"));
        var frame=DisposalOrientation.identity();var shape=bend.geometry().boxes(bend.tube(),frame);
        assertTrue(occupied(shape,.78,.5,.78));
        var ports=DisposalTubeRouting.worldConnections(bend.tube(),frame).stream().map(DisposalDirection::toMC).collect(java.util.stream.Collectors.toSet());
        assertTrue(DisposalMeshGeometry.needsHub(ports));assertTrue(bend.geometry().mesh(bend.tube(),frame).stream().anyMatch(q->!q.dark()&&q.a().y()<.5&&q.b().y()<.5));
        assertFalse(occupied(shape,.5,.9,.46));
        assertTrue(occupied(shape,.5,.9,.3));assertTrue(occupied(shape,.5,.5,.5));
    }
    private static boolean occupied(net.minecraft.world.phys.shapes.VoxelShape shape,double x,double y,double z){return shape.toAabbs().stream().anyMatch(b->x>=b.minX-1e-9&&x<=b.maxX+1e-9&&y>=b.minY-1e-9&&y<=b.maxY+1e-9&&z>=b.minZ-1e-9&&z<=b.maxZ+1e-9);}
    private static boolean occupied(java.util.List<net.minecraft.world.phys.AABB> boxes,double x,double y,double z){return boxes.stream().anyMatch(b->x>=b.minX-1e-9&&x<=b.maxX+1e-9&&y>=b.minY-1e-9&&y<=b.maxY+1e-9&&z>=b.minZ-1e-9&&z<=b.maxZ+1e-9);}
    private static DisposalEntityData straight(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<DisposalEntityData> c) { return c.get(ModDisposals.id("disposal_pipe")); }
}
