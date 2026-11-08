package com.juicyslew.moonstation14.ms14.botany.fixture;

import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BotanyFixtureSupplyLayoutTest {
    @Test void fixtureDoseMetadataNamesResolvableStations() {
        for(var dose:BotanyFixtureSigns.allFixtureDoses()) {
            String id=dose.station().split(" ")[0];
            assertNotNull(BotanyFixtureStations.lookup(id),dose.station());
            assertEquals(id,BotanyFixtureStations.lookup(id).legacyId());
        }
    }
    @Test void typedPlanPreservesStationSlotsAndDistinctPositions() {
        var definitions=BotanyFixtureSupplies.descriptors();
        var planned=BotanyFixtureSupplies.plan(BlockPos.ZERO,definitions);
        assertEquals(definitions.size(),planned.size());
        assertEquals(definitions.stream().map(BotanyFixtureSupplies.Descriptor::label).toList(),planned.stream().map(BotanyFixtureSupplies.PlannedSupply::label).toList());
        assertEquals(definitions.stream().map(BotanyFixtureSupplies.Descriptor::stationId).toList(),planned.stream().map(BotanyFixtureSupplies.PlannedSupply::stationId).toList());
        assertEquals(definitions.size(),planned.stream().map(BotanyFixtureSupplies.PlannedSupply::position).distinct().count());
        for(int i=0;i<planned.size();i++) { var supply=planned.get(i); assertEquals(SmallBotanyFixtureLayout.supplyPositionNear(SmallBotanyFixtureLayout.stationTray(BlockPos.ZERO,supply.stationId()),supply.slot()),supply.position()); }
        var reserved=new HashSet<BlockPos>();
        for(var station:BotanyFixtureStations.all()) { var tray=SmallBotanyFixtureLayout.stationTray(BlockPos.ZERO,station.legacyId()); reserved.add(tray); reserved.add(tray.south()); reserved.add(SmallBotanyFixtureLayout.stationTorch(BlockPos.ZERO,station.legacyId())); }
        for(var guide:BotanyFixtureSigns.guides(BlockPos.ZERO)) reserved.add(guide.pos());
        reserved.addAll(List.of(SmallBotanyFixtureLayout.grinder(BlockPos.ZERO),SmallBotanyFixtureLayout.extractor(BlockPos.ZERO),SmallBotanyFixtureLayout.apc(BlockPos.ZERO),SmallBotanyFixtureLayout.lamp(BlockPos.ZERO)));
        for(var supply:planned) assertFalse(reserved.contains(supply.position()),supply.label()+" overlaps fixture reservation");
        assertEquals(5,definitions.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.SEED).count());
        assertEquals(4,definitions.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.TOOL).count());
        assertEquals(1,definitions.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.EMPTY_BEAKER).count());
         assertEquals(BotanyFixtureSigns.allFixtureDoses().size(),definitions.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.REAGENT&&d.value()!=null&&!d.label().startsWith("MUTATION RESERVE")&&!d.label().equals("water")&&!d.label().equals("eznutrient")).count());
         assertEquals(4,definitions.stream().filter(d->d.kind()==BotanyFixtureSupplies.Kind.REAGENT&&d.label().startsWith("MUTATION RESERVE")).count());
        assertEquals("G",definitions.stream().filter(d->d.label().equals("water")).findFirst().orElseThrow().stationId());
        assertEquals("G",definitions.stream().filter(d->d.label().equals("eznutrient")).findFirst().orElseThrow().stationId());
        assertEquals("I",definitions.stream().filter(d->d.label().equals("knife")).findFirst().orElseThrow().stationId());
         for(var dose:BotanyFixtureSigns.allFixtureDoses()) { String expected=dose.displayStationName()+" "+dose.cents()/100+"u "+dose.reagent(); assertTrue(definitions.stream().anyMatch(d->expected.equals(d.label())&&d.stationId().equals(dose.station().split(" ")[0]))); }
         for(int i=1;i<=4;i++) { String expected="MUTATION RESERVE "+i+" 5u unstablemutagen"; assertTrue(definitions.stream().anyMatch(d->expected.equals(d.label()))); }
    }
    @Test void stationLocalPositionsAreBoundedDistinctAndInsideFixtureRooms() {
        BlockPos origin=BlockPos.ZERO;
        for(var station:BotanyFixtureStations.all()) {
            BlockPos tray=SmallBotanyFixtureLayout.stationTray(origin,station.legacyId());
            Set<BlockPos> cells=new HashSet<>();
            for(int i=0;i<6;i++) {
                BlockPos p=SmallBotanyFixtureLayout.supplyPositionNear(tray,i);
                assertTrue(cells.add(p)); assertNotEquals(tray,p);
                assertTrue(SmallBotanyFixtureLayout.roomOrigins(origin).stream().anyMatch(r->p.getX()>=r.getX()+1&&p.getX()<=r.getX()+21&&p.getZ()>=r.getZ()+1&&p.getZ()<=r.getZ()+27));
            }
            assertThrows(IllegalArgumentException.class,()->SmallBotanyFixtureLayout.supplyPositionNear(tray,6));
        }
    }
}
