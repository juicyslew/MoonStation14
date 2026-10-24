package com.juicyslew.moonstation14.ms14.botany.fixture;

import net.minecraft.core.BlockPos;
import com.juicyslew.moonstation14.ms14.botany.SmallBotanyFixtureLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotanyFixtureSignsTest {
    @Test
    void stationGuideAndPhysicalSignUseTheSameDemonstrationActionsAndResults() {
        var labels = BotanyFixtureSigns.labels(BlockPos.ZERO);
        assertEquals(33, labels.size());
        for (var label : labels) {
            assertNotNull(label.title());
            assertFalse(label.demonstration().isBlank());
            assertFalse(label.action().isBlank());
            assertFalse(label.result().isBlank());
            assertTrue(label.summary().contains(label.demonstration()));
            assertTrue(label.summary().contains(label.action()));
            assertTrue(label.summary().contains(label.result()));
            assertEquals(label.displayTitle(), label.lines()[0]);
            assertEquals(label.trayPos().south(), label.pos());
            assertEquals(0, label.rotation());
            assertEquals(label.classification() != BotanyFixtureSigns.Classification.ORDINARY_TRIGGER
                    ? "PRESET" : "TRIGGER", label.lines()[1]);
            if (!label.title().equals("AL LIGNEOUS")) {
                assertTrue(label.lines()[2].equals(label.action()));
                assertTrue(label.lines()[3].equals(label.result()));
            }
        }
        assertEquals(27, labels.stream().filter(l -> l.classification() != BotanyFixtureSigns.Classification.ORDINARY_TRIGGER).count());
        var w=labels.stream().filter(l->l.title().equals("W YIELD")).findFirst().orElseThrow();
        var x=labels.stream().filter(l->l.title().equals("X POTENCY")).findFirst().orElseThrow();
        assertEquals(SmallBotanyFixtureLayout.yieldTray(BlockPos.ZERO),w.trayPos());
        assertEquals(SmallBotanyFixtureLayout.potencyTray(BlockPos.ZERO),x.trayPos());
        assertTrue(w.demonstration().contains("yield preset 7"));
        assertTrue(x.demonstration().contains("potency preset 50"));
        assertEquals("Packet: Y7 P5",w.lines()[3]);
        assertEquals("Packet: Y3 P50",x.lines()[3]);
        var y=labels.stream().filter(l->l.title().equals("Y WATER")).findFirst().orElseThrow();
        var z=labels.stream().filter(l->l.title().equals("Z NUTRIENT")).findFirst().orElseThrow();
        assertEquals(SmallBotanyFixtureLayout.waterTray(BlockPos.ZERO),y.trayPos());
        assertEquals(SmallBotanyFixtureLayout.nutrientTray(BlockPos.ZERO),z.trayPos());
        assertTrue(y.demonstration().contains("preset 0.9")); assertTrue(z.demonstration().contains("preset 1.2"));
        assertTrue(y.lines()[2].length()<=15 && z.lines()[2].length()<=15);
        var aa=labels.stream().filter(l->l.title().equals("AA FAST STAGES")).findFirst().orElseThrow();
        var ab=labels.stream().filter(l->l.title().equals("AB EARLY FRUIT")).findFirst().orElseThrow();
        assertEquals(SmallBotanyFixtureLayout.fastStageTray(BlockPos.ZERO),aa.trayPos());
        assertEquals(SmallBotanyFixtureLayout.earlyFruitTray(SmallBotanyFixtureLayout.careOrigin(BlockPos.ZERO)),ab.trayPos());
        assertTrue(aa.demonstration().contains("same age as station 11"));
        assertTrue(ab.demonstration().contains("production 1"));
        var ac=labels.stream().filter(l->l.title().equals("AC ENDURANCE")).findFirst().orElseThrow();
        assertEquals(SmallBotanyFixtureLayout.enduranceTray(BlockPos.ZERO),ac.trayPos());
        assertTrue(ac.demonstration().contains("maximum endurance 50"));
        assertEquals("Health warning",ac.lines()[3]);
        var sample = labels.stream().filter(l -> l.title().equals("V SAMPLE")).findFirst().orElseThrow();
        assertEquals(SmallBotanyFixtureLayout.samplingTray(BlockPos.ZERO), sample.trayPos());
        assertEquals(SmallBotanyFixtureLayout.samplingSign(BlockPos.ZERO), sample.pos());
        assertTrue(sample.lines()[2].length() <= 15 && sample.lines()[3].length() <= 15);
        assertTrue(sample.demonstration().contains("CanScream"));
        assertTrue(sample.demonstration().contains("station 08 after its ordinary no-repeat harvest has cleared it"));
        assertTrue(labels.stream().filter(l -> l.title().startsWith("A ") || l.title().startsWith("B ") || l.title().startsWith("C "))
                .allMatch(l -> l.classification() == BotanyFixtureSigns.Classification.PRESET_COMPARISON));
    }

    @Test
    void careShowcaseHasElevenDistinctAdjacentOrdinaryTriggerStations() {
        var wing = SmallBotanyFixtureLayout.careOrigin(BlockPos.ZERO);
        var stations = BotanyFixtureSigns.careLabels(wing);
        assertEquals(11, stations.size());
        assertEquals("K HEALTH", stations.getFirst().title());
        assertEquals("U SEEDS", stations.getLast().title());
        assertEquals(11, stations.stream().map(BotanyFixtureSigns.Label::trayPos).distinct().count());
        for (var station : stations) {
            assertEquals(BotanyFixtureSigns.Classification.ORDINARY_TRIGGER, station.classification());
            assertFalse(station.demonstration().isBlank());
            assertFalse(station.action().isBlank());
            assertFalse(station.result().isBlank());
            assertEquals(1, Math.abs(station.pos().getX()-station.trayPos().getX()) + Math.abs(station.pos().getZ()-station.trayPos().getZ()));
            assertTrue(station.summary().contains(station.action()));
        }
        assertTrue(stations.stream().anyMatch(s -> s.title().equals("T PHALANX") && s.demonstration().contains("4u")));
        assertEquals(java.util.List.of(100L,100L,100L,100L,100L,100L,100L,1000L,1000L,400L,1000L),
                BotanyFixtureSigns.careDoses().stream().map(BotanyFixtureSigns.CareDose::cents).toList());
        assertEquals(stations.stream().map(BotanyFixtureSigns.Label::title).toList(),
                BotanyFixtureSigns.careDoses().stream().map(BotanyFixtureSigns.CareDose::station).toList());
    }

    @Test
    void mutationStationUsesEmptySoilOperatorDoseAndProbabilisticCopy() {
        var label = BotanyFixtureSigns.labels(BlockPos.ZERO).stream().filter(s -> s.title().equals("I MUTAGEN")).findFirst().orElseThrow();
        assertEquals("Dose mutagen", label.action());
        assertTrue(label.demonstration().contains("no guaranteed outcome"));
        assertTrue(label.demonstration().toLowerCase(java.util.Locale.ROOT).contains("modifier-3 preset"));
        assertTrue(label.lines()[2].length() <= 15 && label.lines()[3].length() <= 15);
        assertEquals("unstablemutagen", BotanyFixtureSigns.allFixtureDoses().getFirst().reagent());
        assertEquals(500L, BotanyFixtureSigns.allFixtureDoses().getFirst().cents());
        assertEquals(11, BotanyFixtureSigns.careDoses().size());
        assertEquals(12, BotanyFixtureSigns.allFixtureDoses().size());
    }

    @Test
    void carePhysicalSignsStayShortWhileFullInstructionsAndLimitsRemainInSummary() {
        var stations = BotanyFixtureSigns.careLabels(SmallBotanyFixtureLayout.careOrigin(BlockPos.ZERO));
        for (var station : stations) {
            assertEquals(4, station.lines().length);
            assertTrue(station.lines()[0].length() <= 15);
            assertTrue(station.lines()[2].length() <= 15);
            assertTrue(station.lines()[3].length() <= 15);
            assertTrue(station.summary().contains(station.demonstration()));
        }
        assertTrue(stations.get(0).summary().contains("1u/4u transfers"));
        assertTrue(stations.get(7).summary().contains("10u bottle twice"));
        assertTrue(stations.get(8).result().contains("Rolls not shown"));
        assertTrue(stations.get(8).summary().contains("cannot be verified by ordinary manual inspection"));
        assertTrue(stations.get(10).result().contains("Extract to test"));
        assertTrue(stations.get(10).summary().contains("successful real packet proves restoration"));
        assertTrue(stations.get(10).summary().contains("use a fresh harvest"));
        assertTrue(stations.get(4).summary().contains("no-op on this seeded plant"));
    }

    @Test
    void careWingAndMainFootprintsHaveBoundedConnectedAirwayAndNoStationOverlap() {
        var main=BlockPos.ZERO;
        var wing=SmallBotanyFixtureLayout.careOrigin(main);
        assertEquals(25,wing.getX());
        var stations=BotanyFixtureSigns.careLabels(wing);
        var trays=stations.stream().map(BotanyFixtureSigns.Label::trayPos).collect(java.util.stream.Collectors.toSet());
        var signs=stations.stream().map(BotanyFixtureSigns.Label::pos).collect(java.util.stream.Collectors.toSet());
        assertEquals(11,trays.size());
        assertEquals(11,signs.size());
        assertTrue(java.util.Collections.disjoint(trays,signs));
        var tolerance=BotanyFixtureSigns.toleranceLabels(wing);
        assertEquals(8,tolerance.size());
        assertEquals(8,tolerance.stream().map(BotanyFixtureSigns.Label::trayPos).distinct().count());
        assertTrue(java.util.Collections.disjoint(trays,tolerance.stream().map(BotanyFixtureSigns.Label::trayPos).toList()));
        for(var label:tolerance) {
            assertTrue(label.title().length()<=15); assertTrue(label.lines()[2].length()<=15); assertTrue(label.lines()[3].length()<=15);
            assertEquals(1,Math.abs(label.pos().getX()-label.trayPos().getX())+Math.abs(label.pos().getZ()-label.trayPos().getZ()));
            assertTrue(SmallBotanyFixtureLayout.plannedFootprint(main).contains(label.pos()));
            assertTrue(SmallBotanyFixtureLayout.plannedFootprint(main).contains(label.pos().below()));
            assertTrue(SmallBotanyFixtureLayout.plannedFootprint(main).contains(label.trayPos().above()));
        }
        assertEquals(java.util.List.of("AF TOXIN 4","AG TOXIN 10","AH PEST 5","AI PEST 15","AJ WEED 5","AK WEED 15","AM HEAT 10","AN HEAT 25"),
                tolerance.stream().map(BotanyFixtureSigns.Label::title).toList());
        assertTrue(tolerance.get(6).demonstration().contains("ideal heat 275 K"));
        assertTrue(tolerance.get(6).demonstration().contains("room 293.15 K"));
        assertEquals(BotanyFixtureSigns.Classification.GENETIC_PRESET,tolerance.get(7).classification());
        var mainLabels = BotanyFixtureSigns.labels(main);
        var mainTrays = mainLabels.stream().map(BotanyFixtureSigns.Label::trayPos).collect(java.util.stream.Collectors.toSet());
        assertEquals(33, mainTrays.size());
        assertTrue(mainTrays.contains(SmallBotanyFixtureLayout.samplingTray(main)));
        assertTrue(mainTrays.contains(SmallBotanyFixtureLayout.noRepeatTray(main)));
        assertFalse(mainLabels.stream().filter(l -> !l.title().equals("V SAMPLE")).anyMatch(l -> l.pos().equals(SmallBotanyFixtureLayout.samplingSign(main))));
        for(var p:trays) assertTrue(SmallBotanyFixtureLayout.plannedFootprint(main).contains(p));
        for(var p:signs) assertTrue(SmallBotanyFixtureLayout.plannedFootprint(main).contains(p));
        assertEquals(SmallBotanyFixtureLayout.stationTray(main,"K"),SmallBotanyFixtureLayout.careTray(wing,0));
        assertEquals(SmallBotanyFixtureLayout.stationTray(main,"L"),SmallBotanyFixtureLayout.careTray(wing,1));
        // The rooms' 270-cell interiors plus the eight-cell connector/doorway remain below the 1024-cell proof cap.
        assertEquals(548,2*9*3*10+8);
    }

    @Test
    void preflightPlanCoversEverySignSupportAndConnectorWriteBeforeBuild() {
        var main=BlockPos.ZERO;
        var planned=java.util.Set.copyOf(SmallBotanyFixtureLayout.plannedFootprint(main));
        assertNull(BotanyFixtureSigns.plannedLayoutProblem(main));
        assertNull(BotanyFixtureSigns.layoutProblem(main));
        var guides=BotanyFixtureSigns.guides(main);
        assertEquals(6,guides.size());
        assertEquals(java.util.List.of("01-18","19-40","41-52"),guides.stream().filter(g->g.lines().get(0).startsWith("ROOM ")).map(g->g.lines().get(1)).toList());
        for(var guide:guides) {
            assertTrue(planned.contains(guide.pos()));
            assertTrue(guide.lines().size()==4);
            for(String line:guide.lines()) assertTrue(line.length()<=15,line);
        }
        var startGuide=guides.stream().filter(g->g.lines().contains("Starts paused")).findFirst().orElseThrow();
        assertEquals(java.util.List.of("Starts paused","/ms14","botany_fixture","start core"),startGuide.lines());
        assertEquals(main.offset(10,2,2),startGuide.pos());
        assertTrue(BotanyFixtureSigns.labels(main).stream().filter(l->l.title().equals("F WEEDS")).findFirst().orElseThrow().demonstration().contains("intentionally present"));
        assertTrue(BotanyFixtureSigns.labels(main).stream().filter(l->l.title().equals("G CARE")).findFirst().orElseThrow().demonstration().contains("No nutrients"));
        assertTrue(BotanyFixtureSigns.toleranceLabels(SmallBotanyFixtureLayout.careOrigin(main)).stream().allMatch(l->l.demonstration().contains("Healthy wheat") && l.demonstration().contains("full")));
        assertFalse(guides.get(0).wall());
        var nextRoom=guides.stream().filter(g->g.lines().get(0).startsWith("NEXT ROOM")).findFirst().orElseThrow();
        assertEquals(main.offset(SmallBotanyFixtureLayout.WIDTH-2,2,4),nextRoom.pos());
        assertTrue(nextRoom.wall());
        for(var label:BotanyFixtureSigns.labels(main)) {
            assertTrue(planned.contains(label.pos()));
            assertTrue(planned.contains(label.pos().below()));
        }
        // Every tray addressed by the builder's physical signs is part of its bounded tray placement plan.
        var wing=SmallBotanyFixtureLayout.careOrigin(main);
        var plannedTrays=BotanyFixtureCommands.mutationShowcaseTrays(wing);
        for(var label:BotanyFixtureSigns.labels(main).stream().filter(l -> l.title().equals("AB EARLY FRUIT") || l.title().equals("AL LIGNEOUS")).toList())
            assertTrue(plannedTrays.contains(label.trayPos()),label.title());
        var allMainSigns=BotanyFixtureSigns.labels(main).stream().map(BotanyFixtureSigns.Label::pos).collect(java.util.stream.Collectors.toSet());
        var allMainTrays=BotanyFixtureSigns.labels(main).stream().map(BotanyFixtureSigns.Label::trayPos).collect(java.util.stream.Collectors.toSet());
        assertEquals(33,allMainSigns.size()); assertEquals(33,allMainTrays.size());
        assertTrue(java.util.Collections.disjoint(allMainSigns,allMainTrays));
        var physicalLabels=new java.util.ArrayList<BotanyFixtureSigns.Label>(BotanyFixtureSigns.labels(main));
        physicalLabels.addAll(BotanyFixtureSigns.careLabels(SmallBotanyFixtureLayout.careOrigin(main)));
        physicalLabels.addAll(BotanyFixtureSigns.toleranceLabels(SmallBotanyFixtureLayout.careOrigin(main)));
        for(var label:physicalLabels) for(String line:label.lines()) assertTrue(line.length()<=15,label.title()+": "+line);
        for(var label:physicalLabels) for(String line:label.lines()) {
            assertFalse(line.contains("check status"),label.title());
            assertFalse(line.contains("Gas report"),label.title());
            assertFalse(line.contains("Compare with D"),label.title());
        }
        var wheatControl=BotanyFixtureSigns.labels(main).stream().filter(l->l.title().equals("D WHEAT")).findFirst().orElseThrow();
        assertEquals("Examine; keep",wheatControl.lines()[2]);
        assertEquals("For 14/15/18",wheatControl.lines()[3]);
        assertTrue(physicalLabels.stream().allMatch(l->Character.isDigit(l.lines()[0].charAt(0))));
        for(var label:BotanyFixtureSigns.careLabels(SmallBotanyFixtureLayout.careOrigin(main))) {
            assertTrue(planned.contains(label.pos()));
            assertTrue(planned.contains(label.pos().below()));
        }
        var connector=SmallBotanyFixtureLayout.connectorPlan(main);
        assertEquals(40,connector.size());
        for(var cell:connector) assertTrue(planned.contains(cell.pos()));
        assertTrue(planned.contains(main.offset(11,0,5))); // connector floor
        assertTrue(planned.contains(main.offset(12,3,5))); // connector roof
        assertTrue(planned.contains(main.offset(36,0,5))); // second connector floor
        assertTrue(planned.contains(main.offset(37,3,5))); // second connector roof
        assertEquals(16,connector.stream().filter(SmallBotanyFixtureLayout.ConnectorCell::opening).count());
    }
}
