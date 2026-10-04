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
        assertEquals(3,boxes.size());
        assertEquals(.5,boxes.get(0).getXsize(),1e-12);
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
        assertEquals(3,boxes.size()); // core, logical DOWN port, physical unit interface UP
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
    }
    private static DisposalEntityData straight(com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<DisposalEntityData> c) { return c.get(ModDisposals.id("disposal_pipe")); }
}
