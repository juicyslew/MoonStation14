package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SmallBotanyFixtureLayoutShowcaseTest {
    @Test void showcaseGeometryIsMainRelativeAndBounded() {
        BlockPos main=new BlockPos(10,64,-20), showcase=main.offset(50,0,0);
        assertEquals(showcase,SmallBotanyFixtureLayout.showcaseOrigin(main));
        assertEquals(java.util.List.of(main,main.offset(25,0,0),showcase),SmallBotanyFixtureLayout.roomOrigins(main));
        assertEquals(SmallBotanyFixtureLayout.stationTray(main,"AO"),SmallBotanyFixtureLayout.showcaseTray(main,0));
        assertEquals(SmallBotanyFixtureLayout.stationTray(main,"AZ"),SmallBotanyFixtureLayout.showcaseTray(main,11));
        assertEquals(SmallBotanyFixtureLayout.showcaseTray(main,0).south(),SmallBotanyFixtureLayout.showcaseSign(main,0));
        assertEquals(showcase.offset(7,3,2),SmallBotanyFixtureLayout.showcaseLamp(main));
        assertThrows(IllegalArgumentException.class,()->SmallBotanyFixtureLayout.showcaseTray(main,12));
        var trays=java.util.stream.IntStream.range(0,12).mapToObj(i->SmallBotanyFixtureLayout.showcaseTray(main,i)).toList();
        assertEquals(12,trays.stream().distinct().count());
        var footprint=SmallBotanyFixtureLayout.plannedFootprint(main);
        for(var p:trays) assertTrue(footprint.contains(p));
        for(var p:SmallBotanyFixtureLayout.showcaseLightingCablePlan(main).stream().map(SmallBotanyFixtureLayout.CareLightingCableNode::pos).toList()) assertTrue(footprint.contains(p));
        assertTrue(SmallBotanyFixtureLayout.connectorPlan(main).stream().anyMatch(c->c.opening()&&c.pos().equals(main.offset(25,1,5))));
        assertTrue(SmallBotanyFixtureLayout.connectorPlan(main).stream().anyMatch(c->c.opening()&&c.pos().equals(main.offset(50,1,5))));
        var labels=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.labels(main);
        var positions=labels.stream().map(com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.Label::pos).toList();
        assertEquals(positions.size(),positions.stream().distinct().count(),"no duplicate sign positions");
        assertTrue(positions.stream().allMatch(footprint::contains),"all signs lie in planned write footprint");
        assertTrue(labels.stream().allMatch(l->l.trayPos()!=null&&footprint.contains(l.trayPos())),"all sign targets are included trays");
        assertTrue(labels.stream().filter(l->l.title().startsWith("AO ")||l.title().startsWith("AP ")||l.title().startsWith("AQ ")||l.title().startsWith("AR ")||l.title().startsWith("AS ")||l.title().startsWith("AT ")||l.title().startsWith("AU ")||l.title().startsWith("AV ")||l.title().startsWith("AW ")||l.title().startsWith("AX ")||l.title().startsWith("AY ")||l.title().startsWith("AZ ")).count()==12);
    }
}
