package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.piping.AtmosMachineryFixturePower;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryFixturePlanTest {
    private static final String[] RESOURCE_IDS = {"atmos_machine_base", "atmos_machine_base_powered",
            "constructible_machine_base", "gas_pipe_sans_layers", "gas_pipe_base", "gas_binary_base",
            "gas_trinary_base", "gas_pressure_pump", "gas_volume_pump", "gas_mixer", "gas_filter", "gas_valve",
            "gas_thermo_base", "gas_thermo_freezer", "gas_thermo_heater", "passive_heat_exchanger", "gas_pipe_manifold"};

    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/atmos_machine/" + name + ".json";
        try (var in = AtmosMachineryFixturePlanTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void finiteRoomBaselineReadinessIsScopedToPendingDemos() {
        var complete = new LinkedHashMap<String,Double>();
        String mixer="room.mixer.source";
        complete.put(mixer+".moles",240d); complete.put(mixer+".plasmaMoles",240d);
        complete.put(mixer+".nitrogenMoles",0d); complete.put(mixer+".temperatureK",293.15);
        complete.put(mixer+".thermalJ",0d); complete.put(AtmosMachineryFixtureService.roomSpeciesMeasurementKey(mixer,
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR),0d);
        assertTrue(AtmosMachineryFixtureService.finiteRoomBaselineReady(complete,mixer));
        assertFalse(AtmosMachineryFixtureService.finiteRoomBaselineReady(complete,"room.manifold.a"));
        var missing=new LinkedHashMap<>(complete); missing.remove(mixer+".thermalJ");
        assertFalse(AtmosMachineryFixtureService.finiteRoomBaselineReady(missing,mixer));
        var nonFinite=new LinkedHashMap<>(complete); nonFinite.put(mixer+".temperatureK",Double.NaN);
        assertFalse(AtmosMachineryFixtureService.finiteRoomBaselineReady(nonFinite,mixer));
    }

    @Test
    void unselectedRoomSnapshotComparisonPreservesUnknownAndKnownObservations() {
        var manager=new PrototypeManager(); manager.register(ModAtmosMachines.TYPE);
        var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(String name:RESOURCE_IDS)raw.put(ModAtmosMachines.id(name),resource(name));
        manager.reload(ModAtmosMachines.TYPE,raw);
        var endpoints=AtmosMachineryRoomCircuitPlan.standard(
                AtmosMachineryFixturePlan.standard(manager.snapshot(ModAtmosMachines.TYPE)::get),net.minecraft.core.BlockPos.ZERO).endpoints();
        var mixer=endpoints.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER).findFirst().orElseThrow();
        var manifold=endpoints.stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).findFirst().orElseThrow();
        var selected=java.util.Set.of(AtmosMachineryFixturePlan.Kind.MIXER);
        var unknown=java.util.Map.<String,Double>of();
        assertDoesNotThrow(()->AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(unknown,unknown,endpoints,selected,"snapshot"));
        String manifoldKey=AtmosMachineryFixtureService.roomMeasurementKey(manifold)+".moles";
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->
                AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(unknown,java.util.Map.of(manifoldKey,1d),endpoints,selected,"snapshot"));
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->
                AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(java.util.Map.of(manifoldKey,1d),unknown,endpoints,selected,"snapshot"));
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->
                AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(java.util.Map.of(manifoldKey,1d),java.util.Map.of(manifoldKey,2d),endpoints,selected,"snapshot"));
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY}) assertThrows(
                net.minecraft.gametest.framework.GameTestAssertException.class,()->
                AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(java.util.Map.of(manifoldKey,value),java.util.Map.of(manifoldKey,value),endpoints,selected,"snapshot"));
        assertDoesNotThrow(()->AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(
                java.util.Map.of(manifoldKey,3d),java.util.Map.of(manifoldKey,3d),endpoints,selected,"snapshot"));
        String mixerKey=AtmosMachineryFixtureService.roomMeasurementKey(mixer)+".moles";
        assertDoesNotThrow(()->AtmosMachineryFixtureGameTests.assertUnselectedRoomsUnchanged(unknown,
                java.util.Map.of(mixerKey,42d),endpoints,selected,"snapshot"));
    }

    @Test
    void standardPlanUsesResolvedBundledProfilesAndKeepsAllPortsInBounds() {
        var manager = new PrototypeManager();
        manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : RESOURCE_IDS) raw.put(ModAtmosMachines.id(name), resource(name));
        manager.reload(ModAtmosMachines.TYPE, raw);
        var profiles = manager.snapshot(ModAtmosMachines.TYPE);
        var plan = AtmosMachineryFixturePlan.standard(id -> profiles.get(id));
        var circuits = AtmosMachineryRoomCircuitPlan.standard(plan, net.minecraft.core.BlockPos.ZERO);
        var manifoldFeed=circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.MANIFOLD_IN).findFirst().orElseThrow();
        assertEquals(3,manifoldFeed.pipeSeeds().size());
        assertEquals(1,manifoldFeed.ventHosts().size(),"one real source feed enters the common manifold");
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY,manifoldFeed.pipeSeeds().getFirst().layer());
        var allLabelPositions = new HashSet<net.minecraft.core.BlockPos>();

        assertEquals(9, plan.placements().size());
        assertEquals(9, plan.labels().size());
        assertEquals(new net.minecraft.core.BlockPos(8, 1, 8), plan.station(AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP));
        assertEquals(new net.minecraft.core.BlockPos(23, 1, 8), plan.station(AtmosMachineryFixturePlan.Kind.VOLUME_PUMP));
        var freezer = plan.placements().get(6);
        assertEquals("gas_thermo_freezer", freezer.blockId());
        // Kind order is laid out in three rows of three; FREEZER is row three, column one.
        assertEquals(new net.minecraft.core.BlockPos(8, 1, 38), freezer.position());
        assertEquals(net.minecraft.core.Direction.SOUTH, freezer.facing());
        assertTrue(freezer.inletSeeds().stream().anyMatch(seed -> seed.port() == AtmosMachineProfile.PortName.PIPE));
        assertTrue(inBounds(freezer.position().relative(net.minecraft.core.Direction.EAST)), "freezer east cable contact outside bounds");
        assertEquals("gas_valve", plan.placements().get(4).blockId());
        var seen = new HashSet<net.minecraft.core.BlockPos>();
        for (var placement : plan.placements()) {
            if (placement.kind() == AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP
                    || placement.kind() == AtmosMachineryFixturePlan.Kind.VOLUME_PUMP)
                assertEquals(net.minecraft.core.Direction.SOUTH, placement.facing(), "pump must contact east-host supply cable");
            var profile = profiles.get(ModAtmosMachines.id(placement.blockId()));
            assertEquals(profile.ports().size(), placement.inletSeeds().size() + placement.outletSeeds().size(),
                    placement.blockId() + " must represent every resolved binary/trinary port");
            assertTrue(seen.add(placement.position()), "machine position collision");
            assertTrue(inBounds(placement.position()), "machine outside station bounds");
            for (var seed : placement.inletSeeds()) {
                if (placement.kind() != AtmosMachineryFixturePlan.Kind.MANIFOLD) assertTrue(seen.add(seed.position()), "port position collision");
                assertTrue(inBounds(seed.position()), "inlet outside station bounds");
            }
            for (var seed : placement.outletSeeds()) {
                if (placement.kind() != AtmosMachineryFixturePlan.Kind.MANIFOLD) assertTrue(seen.add(seed.position()), "port position collision");
                assertTrue(inBounds(seed.position()), "outlet outside station bounds");
            }
        }
        for(var label:plan.labels()) {
            assertTrue(allLabelPositions.add(label.position()), "duplicate main sign position: " + label.position());
            assertTrue(label.position().getX()>=0 && label.position().getX()<AtmosMachineryFixturePlan.WIDTH);
            assertTrue(label.position().getZ()>=0 && label.position().getZ()<AtmosMachineryFixturePlan.DEPTH);
            assertEquals(1,label.position().getY());
            assertTrue(label.targetPosition().distManhattan(label.position()) <= 5);
            assertFalse(seen.contains(label.position()),"label overlaps machine or gas port");
            var protectedGeometry = new HashSet<net.minecraft.core.BlockPos>(seen);
            for (var endpoint : circuits.endpoints()) {
                protectedGeometry.addAll(endpoint.room().roomCells());
                protectedGeometry.addAll(endpoint.room().glassPositions());
                protectedGeometry.addAll(endpoint.room().occupiedPositions());
                protectedGeometry.addAll(endpoint.ventHosts());
                protectedGeometry.add(endpoint.machine());
                protectedGeometry.add(endpoint.pipeStub());
                protectedGeometry.add(endpoint.wallCrossing());
                protectedGeometry.addAll(endpoint.additionalWallCrossings());
                protectedGeometry.addAll(endpoint.additionalPipeStubs());
            }
            protectedGeometry.addAll(AtmosMachineryFixtureService.crossingPipePositions());
            protectedGeometry.addAll(AtmosMachineryFixtureService.crossingValvePositions());
            assertFalse(protectedGeometry.contains(label.position()), "main label overlaps protected fixture geometry: " + label.position());
            assertTrue(plan.placements().stream().anyMatch(p->p.kind()==label.kind() && p.position().equals(label.targetPosition())));
            assertFalse(label.text().isBlank());
            if(label.kind()==AtmosMachineryFixturePlan.Kind.MANIFOLD)
                assertEquals("Manifold\n1 mix/6 ports",label.text());
            var commandLines=AtmosMachineryFixtureService.stationLabelLines(label);
            assertEquals(4,commandLines.size(),"operator command occupies the last two physical sign lines");
            assertEquals("start",commandLines.get(2));
            assertEquals(AtmosMachineryFixtureCommands.demoLiteral(label.kind()),commandLines.get(3),"station command literal must not be truncated");
            assertTrue(commandLines.stream().allMatch(line->line.length()<=14),"station sign line exceeds compact readable bound: "+commandLines);
        }
        for (var endpoint : circuits.endpoints())
            assertTrue(allLabelPositions.add(endpoint.room().labelPosition()), "main/room sign position collision: " + endpoint.room().labelPosition());
        for (var miner : AtmosMachineryLaboratoryRooms.miners(net.minecraft.core.BlockPos.ZERO))
            assertTrue(allLabelPositions.add(miner.labelPosition()), "main/miner sign position collision: " + miner.labelPosition());
        for (var pumpKind : new AtmosMachineryFixturePlan.Kind[]{AtmosMachineryFixturePlan.Kind.PRESSURE_PUMP,
                AtmosMachineryFixturePlan.Kind.VOLUME_PUMP}) {
            var pump = plan.station(pumpKind);
            for (var offset : AtmosMachineryFixturePower.occupiedOffsets()) {
                var occupied = pump.offset(offset);
                assertTrue(inBounds(occupied), "power supply outside station bounds: " + occupied);
                assertFalse(seen.contains(occupied), "power supply overlaps machine/pipe: " + occupied);
            }
        }
        for (var anchor : AtmosMachineryFixturePower.laboratoryAnchors(net.minecraft.core.BlockPos.ZERO)) {
            assertTrue(inBounds(anchor), "aisle power anchor outside floor bounds: " + anchor);
            for (var offset : AtmosMachineryFixturePower.occupiedOffsets()) {
                var touched = anchor.offset(offset);
                assertTrue(inBounds(touched), "aisle power geometry outside floor bounds: " + touched);
                assertFalse(touched.getX() <= 44, "aisle power geometry reaches glass station area: " + touched);
            }
        }
        var wall = AtmosMachineryFixturePower.cablePlacements(
                AtmosMachineryFixturePower.laboratoryAnchors(net.minecraft.core.BlockPos.ZERO).getFirst(),
                net.minecraft.core.Direction.WEST, com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC).getFirst();
        var floor = new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(
                wall.pos().relative(net.minecraft.core.Direction.WEST).below(), net.minecraft.core.Direction.UP,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
        assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(
                new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(wall.pos(), wall.face(), wall.tier()), floor).isPresent(),
                "aisle wall APC cable must fold onto floor network by ordinary topology");
        var device = new net.minecraft.core.BlockPos(20, 1, 20);
        for (var face : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var port = new com.juicyslew.moonstation14.ms14.power.topology.DevicePort(device, face,
                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
            var side = new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(
                    device.relative(face), face.getOpposite(), com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
            var turnFloor = new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(
                    side.host().relative(side.face()).below(), net.minecraft.core.Direction.UP,
                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
            assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceAdjacent(port, side));
            assertTrue(com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(side, turnFloor).isPresent(),
                    "side pin must use its real folded edge for " + face);
        }
        var mixer = plan.placements().get(2);
        assertEquals(2, mixer.inletSeeds().size());
        assertTrue(mixer.inletSeeds().stream().anyMatch(p -> p.port() == AtmosMachineProfile.PortName.FILTER));
        var filter = plan.placements().get(3);
        assertTrue(filter.outletSeeds().stream().anyMatch(p -> p.port() == AtmosMachineProfile.PortName.FILTER));
        assertEquals(2, filter.outletSeeds().size());
        var manifold = plan.placements().get(8);
        var manifoldSeeds = java.util.stream.Stream.concat(manifold.inletSeeds().stream(), manifold.outletSeeds().stream()).toList();
        assertEquals(6, manifoldSeeds.size());
        assertEquals(2, manifoldSeeds.stream().map(AtmosMachineryFixturePlan.PipeSeed::position).distinct().count());
        assertEquals(3, manifoldSeeds.stream().map(AtmosMachineryFixturePlan.PipeSeed::layer).distinct().count());
        assertEquals(300.0, profiles.get(ModAtmosMachines.id("gas_pipe_manifold")).ports().stream().mapToDouble(AtmosMachineProfile.Port::volumeM3).sum() * 1000.0, 1e-9);
    }

    @Test
    void roomCircuitFoundationUsesVisibleFiniteInventoriesAndSealedVentCrossings() {
        var manager = new PrototypeManager(); manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String name : RESOURCE_IDS) raw.put(ModAtmosMachines.id(name), resource(name));
        manager.reload(ModAtmosMachines.TYPE, raw);
        var profiles=manager.snapshot(ModAtmosMachines.TYPE);
        var fixture=AtmosMachineryFixturePlan.standard(profiles::get);
        var circuits=AtmosMachineryRoomCircuitPlan.standard(fixture,new net.minecraft.core.BlockPos(0,0,0));
        var translated=AtmosMachineryRoomCircuitPlan.standard(fixture,new net.minecraft.core.BlockPos(100,64,200));
        var translatedVentHosts=AtmosMachineryFixturePower.laboratoryRoomVentHosts(translated);
        assertEquals(translated.endpoints().stream().flatMap(e->e.ventHosts().stream()).toList(),translatedVentHosts,
                "live cable consumer must receive endpoint world positions without translating them again");
        assertTrue(translatedVentHosts.stream().allMatch(p->p.getX()>=100&&p.getZ()>=200&&p.getY()==65),
                "translated receiver hosts must remain within the fixture's world-space floor geometry");
        for(int i=0;i<circuits.endpoints().size();i++) {
            var a=circuits.endpoints().get(i); var b=translated.endpoints().get(i);
            assertEquals(a.machine().offset(100,64,200),b.machine());
            assertEquals(a.ventHosts().stream().map(p->p.offset(100,64,200)).toList(),b.ventHosts());
            assertEquals(a.room().roomCells().stream().map(p->p.offset(100,64,200)).toList(),b.room().roomCells());
        }
        assertEquals(2,circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).count());
        for(var manifold:circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MANIFOLD).toList()) {
            assertEquals(3,manifold.pipeSeeds().size(),"endpoint preserves each resolved physical port/layer seed");
            assertEquals(EnumSet.allOf(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.class),
                    manifold.pipeSeeds().stream().map(AtmosMachineryFixturePlan.PipeSeed::layer).collect(java.util.stream.Collectors.toSet()));
            assertEquals(manifold.ports(),manifold.pipeSeeds().stream().map(AtmosMachineryFixturePlan.PipeSeed::port).toList());
            assertEquals(1,manifold.ventHosts().size(),"existing one powered room receiver is retained; layer pass-through adds no extra loads");
            assertEquals(1,manifold.hostLayers().size());
        }
        var mixerNitrogen=circuitEndpoint(circuits,AtmosMachineryFixturePlan.Kind.MIXER,AtmosMachineryRoomCircuitPlan.Role.SOURCE,
                com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.FILTER);
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,AtmosMachineryFixtureService.roomTracer(mixerNitrogen));
        var exhaust=circuits.endpoints().stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.EXHAUST).findFirst().orElseThrow();
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,AtmosMachineryFixtureService.roomTracer(exhaust));
        assertEquals(java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,0.0),exhaust.initialInventory().moles());
        var mixerOutput=circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.MIXER
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.RECEIVER).findFirst().orElseThrow();
        assertTrue(mixerOutput.initialInventory().moles().containsKey(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR));
        var filterSource=circuits.endpoints().stream().filter(e->e.station()==AtmosMachineryFixturePlan.Kind.FILTER
                &&e.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE).findFirst().orElseThrow();
        assertEquals(java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,120.0,
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,120.0),filterSource.initialInventory().moles());
        assertTrue(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.alphaByte(
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,240d/18d,1d)>0,
                "finite water-vapor stock produces a nonzero real-gas overlay in the 18-cell room");
        assertNotEquals(com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.tint(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA),
                com.juicyslew.moonstation14.ms14.atmos.visual.GasVisibility.tint(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR));
        for(var endpoint:circuits.endpoints().stream().filter(e->e.initialInventory().totalMoles()==0
                &&e.role()!=AtmosMachineryRoomCircuitPlan.Role.EXHAUST).toList()) {
            assertEquals(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,AtmosMachineryFixtureService.roomTracer(endpoint));
            var actual=java.util.Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,0.0,
                    com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,0.0,
                    com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,0.0);
            assertEquals(0.0,actual.get(AtmosMachineryFixtureService.roomTracer(endpoint)));
        }
        assertTrue(circuits.endpoints().stream().anyMatch(e->e.station()==AtmosMachineryFixturePlan.Kind.HEATER
                && e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM && e.ventHosts().size()==2));
        for(var thermal:circuits.endpoints().stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM).toList()) {
            assertEquals(2,thermal.additionalWallCrossings().size());
            assertEquals(2,thermal.additionalPipeStubs().size());
            var outward=net.minecraft.core.Direction.getNearest(thermal.ventHosts().getFirst().getX()-thermal.pipeStub().getX(),0,
                    thermal.ventHosts().getFirst().getZ()-thermal.pipeStub().getZ());
            for(int i=0;i<2;i++) {
                var stub=thermal.additionalPipeStubs().get(i); var crossing=thermal.additionalWallCrossings().get(i);
                assertEquals(crossing,stub.relative(outward));
                assertEquals(stub,crossing.relative(outward.getOpposite()));
                assertEquals(1,thermal.pipeStub().distManhattan(stub));
            }
            assertTrue(thermal.room().glassPositions().containsAll(thermal.additionalWallCrossings()));
            assertTrue(thermal.room().roomCells().containsAll(thermal.ventHosts()));
        }
        assertTrue(circuits.endpoints().stream().anyMatch(e->e.station()==AtmosMachineryFixturePlan.Kind.FILTER
                && e.role()==AtmosMachineryRoomCircuitPlan.Role.FILTERED));
        var ownedCells=new HashSet<net.minecraft.core.BlockPos>();
        var roomKinds=new HashSet<AtmosMachineryFixturePlan.Kind>();
        for(var endpoint:circuits.endpoints()) {
            var room=endpoint.room();
            roomKinds.add(endpoint.station());
            assertEquals(18,room.roomCells().size());
            for(var cell:room.roomCells()) assertTrue(ownedCells.add(cell),"endpoint rooms overlap: "+cell);
            assertTrue(room.roomCells().stream().allMatch(p->p.getY()==1||p.getY()==2));
            if(!endpoint.ventHosts().isEmpty()) {
                assertTrue(room.glassPositions().contains(endpoint.wallCrossing()),"sealed crossing must lie on glass boundary: "+endpoint);
                if(endpoint.role()!=AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM) {
                    var outward=endpoint.outward();
                    assertEquals(endpoint.wallCrossing(),endpoint.ventHosts().getFirst().relative(outward.getOpposite()));
                    assertEquals(endpoint.pipeStub(),endpoint.wallCrossing().relative(outward.getOpposite()));
                }
            }
            assertTrue(room.occupiedPositions().containsAll(endpoint.ventHosts()));
            assertTrue(endpoint.initialInventory().totalMoles()>=0);
            assertTrue(endpoint.label().contains(endpoint.role().name()));
            if(!endpoint.ventHosts().isEmpty()) {
                assertEquals(1,endpoint.machine().distManhattan(endpoint.pipeStub()));
                assertEquals(2,endpoint.machine().distManhattan(endpoint.wallCrossing()));
            }
            assertTrue(Collections.disjoint(room.roomCells(),room.glassPositions()),"room interior intersects its own shell: "+endpoint);
            for(var p:room.roomCells()) assertTrue(p.getX()>=0&&p.getX()<AtmosMachineryFixturePlan.WIDTH
                    &&p.getZ()>=0&&p.getZ()<AtmosMachineryFixturePlan.DEPTH,"room outside floor bounds: "+p);
        }
        var exchanger=circuits.endpoints().stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM).findFirst().orElseThrow();
        assertTrue(exchanger.room().roomCells().contains(exchanger.machine()),"HX host must be inside its finite volume");
        var protectedGeometry=new HashSet<net.minecraft.core.BlockPos>();
        for(var e:circuits.endpoints()) {
            protectedGeometry.addAll(e.room().roomCells()); protectedGeometry.addAll(e.room().glassPositions());
            protectedGeometry.addAll(e.ventHosts()); protectedGeometry.add(e.pipeStub()); protectedGeometry.addAll(e.additionalPipeStubs());
            e.pipeSeeds().forEach(seed->protectedGeometry.add(seed.position()));
        }
        fixture.placements().forEach(placement->protectedGeometry.add(placement.position()));
        var gaugePositions=circuits.endpoints().stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.THERMAL_ROOM
                ||e.role()==AtmosMachineryRoomCircuitPlan.Role.EXCHANGER_ROOM).map(e->e.room().labelPosition().above()).toList();
        assertEquals(3,gaugePositions.stream().distinct().count(),"each thermal demo gets one distinct comparison lamp");
        for(var gauge:gaugePositions)assertFalse(protectedGeometry.contains(gauge),"thermal indicator must stay outside owned finite-room/pipe geometry: "+gauge);
        for(var e:circuits.endpoints()) {
            assertFalse(protectedGeometry.contains(e.room().labelPosition()),"room label collides with protected geometry: "+e.room().labelPosition());
            assertFalse(e.room().roomCells().contains(e.room().labelPosition()),"room label is inside room: "+e.room().labelPosition());
        }
        assertEquals(EnumSet.allOf(AtmosMachineryFixturePlan.Kind.class),roomKinds,
                "all nine station kinds must have planned glass-room geometry");
        var sources=circuits.endpoints().stream().filter(e->e.role()==AtmosMachineryRoomCircuitPlan.Role.SOURCE).toList();
        assertFalse(sources.isEmpty());
        for(var source:sources) {
            if(source.station()==AtmosMachineryFixturePlan.Kind.MIXER) {
                var gas=source.ports().getFirst()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName.INLET
                        ?com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA:com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR;
                assertEquals(java.util.Map.of(gas,240.0),source.initialInventory().moles());
            }
            else if(source.station()==AtmosMachineryFixturePlan.Kind.FILTER) {
                assertEquals(120.0,source.initialInventory().moles().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA),1e-9);
                assertEquals(120.0,source.initialInventory().moles().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR),1e-9);
                assertFalse(source.initialInventory().moles().containsKey(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN));
            }
            else {
                assertEquals(120.0,source.initialInventory().moles().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA),1e-9);
                assertEquals(120.0,source.initialInventory().moles().get(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN),1e-9);
            }
            assertFalse(source.initialInventory().moles().containsKey(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN));
        }
    }

    private static AtmosMachineryRoomCircuitPlan.Endpoint circuitEndpoint(AtmosMachineryRoomCircuitPlan circuits,
            AtmosMachineryFixturePlan.Kind station,AtmosMachineryRoomCircuitPlan.Role role,
            com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PortName port) {
        return circuits.endpoints().stream().filter(e->e.station()==station&&e.role()==role&&e.ports().contains(port)).findFirst().orElseThrow();
    }

    @Test
    void crossingGeometryHasSeparateLayerSpecificValveAndPipeHosts() {
        var center = AtmosMachineryFixtureService.crossingCenter();
        var pipes = AtmosMachineryFixtureService.crossingPipePositions();
        var secondaryOnly = AtmosMachineryFixtureService.crossingSecondaryOnly();
        var valves = AtmosMachineryFixtureService.crossingValvePositions();
        assertEquals(5, pipes.size());
        assertEquals(2, secondaryOnly.size());
        assertEquals(2, valves.size());
        assertTrue(pipes.contains(center));
        assertTrue(java.util.Collections.disjoint(pipes, valves));
        assertTrue(pipes.containsAll(secondaryOnly));
        assertEquals(3, pipes.stream().filter(p -> !secondaryOnly.contains(p)).count());
        for (var p : java.util.stream.Stream.concat(pipes.stream(), valves.stream()).toList())
            assertTrue(inBounds(p), "crossing outside fixture bounds: " + p);
    }

    private static boolean inBounds(net.minecraft.core.BlockPos pos) {
        return pos.getX() >= 0 && pos.getX() < AtmosMachineryFixturePlan.WIDTH
                && pos.getZ() >= 0 && pos.getZ() < AtmosMachineryFixturePlan.DEPTH;
    }
}
