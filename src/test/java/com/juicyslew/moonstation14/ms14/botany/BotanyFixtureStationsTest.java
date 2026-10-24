package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureStations;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotanyFixtureStationsTest {
    @Test void catalogAndBayGeometryAreCanonicalAndDistinct() {
        var stations=BotanyFixtureStations.all();
        assertEquals(52,stations.size());
        var origin=new BlockPos(0,64,0); var trays=new java.util.HashSet<BlockPos>();
        for(int i=0;i<stations.size();i++) {
            var s=stations.get(i); assertEquals(i+1,s.number());
            assertSame(s,BotanyFixtureStations.lookup(s.legacyId()));
            var tray=SmallBotanyFixtureLayout.stationTray(origin,s.legacyId());
            assertTrue(trays.add(tray));
            assertEquals(tray.south(),SmallBotanyFixtureLayout.stationSign(origin,s.legacyId()));
            assertTrue(SmallBotanyFixtureLayout.plannedFootprint(origin).contains(tray));
            assertTrue(SmallBotanyFixtureLayout.plannedFootprint(origin).contains(SmallBotanyFixtureLayout.stationTorch(origin,s.legacyId())));
        }
        assertEquals("01 WHEAT",BotanyFixtureStations.displayTitle("D WHEAT"));
        assertEquals("52 GAS",BotanyFixtureStations.displayTitle("AZ GAS"));
    }
}
