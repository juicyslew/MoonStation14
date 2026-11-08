package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryFixtureSavedDataTest {
    @Test void roomDeviceIdentityIsPersistedAndOneShot() {
        var data = new AtmosMachineryFixtureSavedData(); var origin = new BlockPos(4, 5, 6);
        assertTrue(data.recordBuilt(origin, List.of()));
        var owners = List.of(new AtmosMachineryFixtureSavedData.RoomDeviceOwner(origin, UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.RoomDeviceOwner(origin.offset(1, 0, 0), UUID.randomUUID()));
        assertTrue(data.recordRoomDevices(origin, owners));
        assertFalse(data.recordRoomDevices(origin, owners));
        var loaded = AtmosMachineryFixtureSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(owners, loaded.entry(origin).orElseThrow().roomDeviceOwners());
        var replaced = List.of(owners.getFirst(), new AtmosMachineryFixtureSavedData.RoomDeviceOwner(origin.offset(1, 0, 0), UUID.randomUUID()));
        assertNotEquals(loaded.entry(origin).orElseThrow().roomDeviceOwners(), replaced);
        assertFalse(loaded.recordRoomDevices(origin, replaced));
    }

    @Test void laboratoryAdmissionAndStartAreOneShotAndPersisted() {
        var data = new AtmosMachineryFixtureSavedData();
        var origin = new BlockPos(1, 2, 3);
        var crossingFirst = List.of(
                new AtmosMachineryFixtureSavedData.PortOwner(origin.offset(1, 0, 0), "crossing", UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.PortOwner(origin, "core", UUID.randomUUID()));
        var coreFirst = List.of(crossingFirst.get(1), crossingFirst.get(0));
        var miners = List.of(
                new AtmosMachineryFixtureSavedData.MinerOwner(origin, UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(1, 0, 0), UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(2, 0, 0), UUID.randomUUID()));
        assertTrue(data.recordBuilt(origin, crossingFirst));
        var replaced = List.of(new AtmosMachineryFixtureSavedData.PortOwner(origin.offset(1, 0, 0), "crossing", UUID.randomUUID()), coreFirst.get(0));
        var duplicate = List.of(coreFirst.get(0), coreFirst.get(1), coreFirst.get(0));
        assertFalse(data.markInitialized(origin, crossingFirst.subList(0, 1)));
        assertFalse(data.markInitialized(origin, replaced));
        assertFalse(data.markInitialized(origin, duplicate));
        assertFalse(data.entry(origin).orElseThrow().initialized());
        assertTrue(data.markInitialized(origin, coreFirst));
        assertFalse(data.markInitialized(origin, crossingFirst));
        assertTrue(data.recordMiners(origin, miners));
        assertFalse(data.recordMiners(origin, miners));
        Map<String, Double> baseline = Map.of("oxygen", 1.25);
        assertFalse(data.markLaboratoryStarted(origin, coreFirst.subList(0, 1), miners, baseline));
        assertFalse(data.markLaboratoryStarted(origin, replaced, miners, baseline));
        assertFalse(data.markLaboratoryStarted(origin, duplicate, miners, baseline));
        var beforeStart = data.entry(origin).orElseThrow();
        assertFalse(beforeStart.laboratoryStarted());
        assertTrue(beforeStart.baseline().isEmpty());
        assertTrue(data.markLaboratoryStarted(origin, coreFirst, miners, baseline));
        assertFalse(data.markLaboratoryStarted(origin, coreFirst, miners, baseline));
        var loaded = AtmosMachineryFixtureSavedData.load(data.save(new CompoundTag(), null), null);
        var entry = loaded.entry(origin).orElseThrow();
        assertTrue(entry.laboratoryStarted());
        assertEquals(miners, entry.minerOwners());
        assertEquals(baseline, entry.baseline());
    }

    @Test void rejectsBadMinerAdmissionAndNonFiniteBaseline() {
        var data = new AtmosMachineryFixtureSavedData();
        var origin = new BlockPos(0, 0, 0);
        data.recordBuilt(origin, List.of());
        assertFalse(data.recordMiners(origin, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AtmosMachineryFixtureSavedData.Entry(
                origin, List.of(), false, List.of(), false, Map.of("gas", Double.NaN)));
    }

    @Test void startupReservationPrecedesOnceOnlyBaselineAndCannotBeRearmed() {
        var data = new AtmosMachineryFixtureSavedData();
        var origin = new BlockPos(8, 2, 1);
        var ports = List.of(new AtmosMachineryFixtureSavedData.PortOwner(origin, "core", UUID.randomUUID()));
        var miners = List.of(new AtmosMachineryFixtureSavedData.MinerOwner(origin, UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(1, 0, 0), UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(2, 0, 0), UUID.randomUUID()));
        assertTrue(data.recordBuilt(origin, ports));
        assertTrue(data.recordMiners(origin, miners));
        assertTrue(data.markLaboratoryStarted(origin, ports, miners, Map.of()));
        assertFalse(data.markLaboratoryStarted(origin, ports, miners, Map.of()));
        assertFalse(data.recordLaboratoryBaseline(origin, ports, List.of(), Map.of("gas", 1d)));
        assertTrue(data.recordLaboratoryBaseline(origin, ports, miners, Map.of("gas", 1d)));
        assertFalse(data.recordLaboratoryBaseline(origin, ports, miners, Map.of("gas", 2d)));
        assertFalse(data.markLaboratoryStarted(origin, ports, miners, Map.of("gas", 2d)));
        var loaded = AtmosMachineryFixtureSavedData.load(data.save(new CompoundTag(), null), null);
        assertEquals(Map.of("gas", 1d), loaded.entry(origin).orElseThrow().baseline());
    }

    @Test void perDemoReservationAndBaselinesRoundTripWithoutOverwritingEarlierStation() {
        var data=new AtmosMachineryFixtureSavedData();var origin=new BlockPos(2,3,4);
        var ports=List.of(new AtmosMachineryFixtureSavedData.PortOwner(origin,"core",UUID.randomUUID()));
        data.recordBuilt(origin,ports);
        assertTrue(data.reserveDemo(origin,"mixer",ports));
        assertFalse(data.reserveDemo(origin,"mixer",ports));
        assertTrue(data.recordDemoBaseline(origin,"mixer",Map.of("mixer.room",480d)));
        assertFalse(data.recordDemoBaseline(origin,"mixer",Map.of("mixer.room",999d)));
        assertTrue(data.reserveDemo(origin,"heater",ports));
        assertTrue(data.recordDemoBaseline(origin,"heater",Map.of("heater.room",100d,"mixer.room",2d)));
        assertTrue(data.markLaboratoryStarted(origin,ports,List.of(),Map.of()));
        assertTrue(data.recordLaboratoryBaseline(origin,ports,List.of(),Map.of("filter.room",240d,"mixer.room",3d)));
        var loaded=AtmosMachineryFixtureSavedData.load(data.save(new CompoundTag(),null),null).entry(origin).orElseThrow();
        assertEquals(java.util.Set.of("mixer","heater"),loaded.reservedDemos());
        assertEquals(480d,loaded.baseline().get("mixer.room"));
        assertEquals(100d,loaded.baseline().get("heater.room"));
        assertEquals(240d,loaded.baseline().get("filter.room"));
        assertEquals(2,loaded.demoBaselines().size());
    }

    @Test void fixtureMinerOwnershipRequiresTheSavedPlacementIdentity() {
        var data=new AtmosMachineryFixtureSavedData();var origin=new BlockPos(5,2,1);
        var ports=List.of(new AtmosMachineryFixtureSavedData.PortOwner(origin,"core",UUID.randomUUID()));
        var minerPos=origin.offset(4,0,0);var identity=UUID.randomUUID();
        data.recordBuilt(origin,ports);
        data.recordMiners(origin,List.of(new AtmosMachineryFixtureSavedData.MinerOwner(minerPos,identity),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(5,0,0),UUID.randomUUID()),
                new AtmosMachineryFixtureSavedData.MinerOwner(origin.offset(6,0,0),UUID.randomUUID())));
        var entry=data.entry(origin).orElseThrow();
        assertTrue(entry.ownsMiner(minerPos,identity));
        assertFalse(entry.ownsMiner(minerPos,UUID.randomUUID()),"replacement miner at the old position is not owned");
        assertFalse(entry.ownsMiner(origin.offset(99,0,0),identity),"unowned miner identity is not gated");
        assertFalse(entry.ownsMiner(minerPos,null));
    }

    @Test void globalAuxiliaryGuardAllowsLateAllOnceButRejectsLegacyAndRepeatedAll() {
        var origin=new BlockPos(30,4,30);var ports=List.of(new AtmosMachineryFixtureSavedData.PortOwner(origin,"machine",UUID.randomUUID()));
        var data=new AtmosMachineryFixtureSavedData();assertTrue(data.recordBuilt(origin,ports));
        var fresh=data.entry(origin).orElseThrow();
        assertFalse(fresh.legacyGlobalStart());assertTrue(fresh.auxiliaryStartupAvailable());
        for(var kind:AtmosMachineryFixturePlan.Kind.values()) {
            String demo=kind.name().toLowerCase(java.util.Locale.ROOT);
            assertTrue(data.reserveDemo(origin,demo,ports));
            assertTrue(data.recordDemoBaseline(origin,demo,Map.of("room."+demo+".baseline",1d)));
        }
        var afterIndividualDemos=data.entry(origin).orElseThrow();
        assertFalse(afterIndividualDemos.laboratoryStarted());
        assertFalse(afterIndividualDemos.legacyGlobalStart());
        assertTrue(afterIndividualDemos.auxiliaryStartupAvailable(),"all nine individual starts still allow the one auxiliary all transition");
        assertTrue(data.markLaboratoryStarted(origin,ports,List.of(),Map.of()));
        assertFalse(data.entry(origin).orElseThrow().auxiliaryStartupAvailable());
        assertEquals(AtmosMachineryFixturePlan.Kind.values().length,data.entry(origin).orElseThrow().reservedDemos().size());

        var legacy=new AtmosMachineryFixtureSavedData();var legacyOrigin=origin.offset(100,0,0);
        assertTrue(legacy.recordBuilt(legacyOrigin,ports));
        assertTrue(legacy.markLaboratoryStarted(legacyOrigin,ports,List.of(),Map.of("legacy.baseline",5d)));
        var tag=legacy.save(new CompoundTag(),null);var rows=tag.getList("fixtures",CompoundTag.TAG_COMPOUND);
        var row=rows.getCompound(0);row.remove("reservedDemos");row.remove("demoBaselines");rows.set(0,row);tag.put("fixtures",rows);
        var loaded=AtmosMachineryFixtureSavedData.load(tag,null).entry(legacyOrigin).orElseThrow();
        assertTrue(loaded.reservedDemos().isEmpty());
        assertTrue(loaded.legacyGlobalStart(),"legacy global guard without the new optional fields is fail-closed");
        assertFalse(loaded.auxiliaryStartupAvailable());
        assertEquals(Map.of("legacy.baseline",5d),loaded.baseline());
    }
}
