package com.juicyslew.moonstation14.ms14.ui.preview;

import com.juicyslew.moonstation14.ms14.atmos.machine.fixture.AtmosMachineryFixtureProofSignFormatter;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryFixtureProofSignFormatterTest {
    @Test void manifoldReceiptNamesTheActualGasSpecies() {
        var lines=AtmosMachineryFixtureProofSignFormatter.manifold(Component.literal("Manifold"),true,
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,50,
                com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,50);
        assertEquals("ONE MIX/3 LANES",lines.status());
        assertEquals("PLASMA 50.00%",lines.first());
        assertEquals("N2 50.00%",lines.second());
    }
    @Test void roomProofShowsPhysicalSpeciesAndSignedStockAndTemperatureChanges() {
        var source=AtmosMachineryFixtureProofSignFormatter.room(Component.literal("Press pump SRC"),"SOURCE","PLASMA",120,-2.5,293.15,7);
        assertEquals("PL120.00mol",source.status());
        assertEquals("dPL -2.50 mol",source.first());
        assertEquals("293.15 K",source.second());
        assertTrue(source.first().length()<=15&&source.second().length()<=15);
        var exhaust=AtmosMachineryFixtureProofSignFormatter.room(Component.literal("Filter EXHAUST"),"EXHAUST","NITROGEN",80,5,293,0);
        assertEquals("dN2 +5.00 mol",exhaust.first());
        var thermal=AtmosMachineryFixtureProofSignFormatter.room(Component.literal("Freezer ROOM"),"THERMAL_ROOM","PLASMA",60,0,293.15,1.25);
        assertEquals("dT +1.25K",thermal.first());
        assertEquals("293.15 K",thermal.second());
        var nitrogen=AtmosMachineryFixtureProofSignFormatter.room(Component.literal("Freezer ROOM"),"RECEIVER","NITROGEN",12.5,12.5,293.15,0);
        assertEquals("dN2 +12.50 mol",nitrogen.first());
        assertTrue(nitrogen.first().length()<=15);
        for(var lines:List.of(source,exhaust,thermal,nitrogen))
            for(String line:List.of(lines.title().getString(),lines.status(),lines.first(),lines.second()))
                assertTrue(line.length()<=15,"room proof line exceeds conservative ASCII bound: "+line);
    }

    @Test void fourKindProofLinesUseRealMinecraftBitmapFontAndStayWithinSignWidth() throws Exception {
        Path root=Path.of(System.getProperty("moonstation14.projectDir", ".")).toAbsolutePath().normalize();
        Path jar=ProductionUiPreviewTest.configuredGameResources(root);
        var font=MinecraftBitmapFont.load(jar);
        var language=PreviewEnglishLanguage.load(root,jar);
        try {
            var titles=List.of(language.component("block.moonstation14.pressure_pump","Pressure pump"),
                    language.component("block.moonstation14.volume_pump","Volume pump"),
                    language.component("block.moonstation14.heater","Heater"),
                    language.component("block.moonstation14.freezer","Freezer"));
            var cases=List.of(
                    AtmosMachineryFixtureProofSignFormatter.pump(titles.get(0),true,1.2,-1.2),
                    AtmosMachineryFixtureProofSignFormatter.pump(titles.get(1),false,Double.NaN,1e300),
                    AtmosMachineryFixtureProofSignFormatter.thermal(titles.get(2),true,true,290.123,350.456,0),
                     AtmosMachineryFixtureProofSignFormatter.thermal(titles.get(3),true,false,262144,0.001,-1.2e300),
                     AtmosMachineryFixtureProofSignFormatter.thermal(titles.get(2),false,true,Double.NaN,290,Double.POSITIVE_INFINITY),
                    AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),true,21.5,78.5),
                     AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),false,Double.NaN,Double.POSITIVE_INFINITY),
                      AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),true,com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,50,com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,50),
                    AtmosMachineryFixtureProofSignFormatter.filter(titles.get(1),true,"OXYGEN",1.25,8.75),
                    AtmosMachineryFixtureProofSignFormatter.filter(titles.get(1),false,"OXYGEN",Double.NaN,0),
                    AtmosMachineryFixtureProofSignFormatter.valve(titles.get(0),true,true,-1.25,1.25),
                    AtmosMachineryFixtureProofSignFormatter.valve(titles.get(0),false,true,0,0),
                    AtmosMachineryFixtureProofSignFormatter.exchanger(titles.get(2),true,-1200000,1200000),
                    AtmosMachineryFixtureProofSignFormatter.exchanger(titles.get(2),false,Double.NaN,Double.NaN),
                    AtmosMachineryFixtureProofSignFormatter.manifold(titles.get(0),true,21.5,78.5),
                    AtmosMachineryFixtureProofSignFormatter.manifold(titles.get(0),false,Double.NaN,Double.NaN),
                    AtmosMachineryFixtureProofSignFormatter.miner(titles.get(0),true,12.5,250),
                    AtmosMachineryFixtureProofSignFormatter.miner(titles.get(0),false,Double.NaN,Double.NaN),
                    AtmosMachineryFixtureProofSignFormatter.scrubber(titles.get(1),"CAPTURED","OXYGEN",2.5,-2.5),
                    AtmosMachineryFixtureProofSignFormatter.scrubber(titles.get(1),"UNKNOWN","CARBON DIOXIDE",Double.NaN,Double.NaN),
                    AtmosMachineryFixtureProofSignFormatter.vent(titles.get(1),"TRANSFERRED",2.5,2.5),
                     AtmosMachineryFixtureProofSignFormatter.vent(titles.get(1),"UNKNOWN",Double.NaN,Double.NaN));
            var normalPlasmaMix=AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),true,
                    com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,50,
                    com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,50);
            var caseTitles=List.of(titles.get(0),titles.get(1),titles.get(2),titles.get(3),titles.get(2),
                     titles.get(0),titles.get(0),titles.get(0),titles.get(1),titles.get(1),titles.get(0),titles.get(0),
                    titles.get(2),titles.get(2),titles.get(0),titles.get(0),titles.get(0),titles.get(0),
                    titles.get(1),titles.get(1),titles.get(1),titles.get(1));
            var wideCases=List.of(
                    AtmosMachineryFixtureProofSignFormatter.pump(titles.get(1),false,Double.NaN,1e308),
                    AtmosMachineryFixtureProofSignFormatter.thermal(titles.get(3),true,true,0.001,262144,1e308),
                     AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),false,-1e308,1e308),
                      AtmosMachineryFixtureProofSignFormatter.mixer(titles.get(0),true,com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA,-1e308,com.juicyslew.moonstation14.ms14.atmos.core.GasType.WATER_VAPOR,1e308),
                    AtmosMachineryFixtureProofSignFormatter.filter(titles.get(1),false,"CARBON DIOXIDE",1e308,1e-300),
                    AtmosMachineryFixtureProofSignFormatter.valve(titles.get(0),false,true,1e308,-1e308),
                    AtmosMachineryFixtureProofSignFormatter.exchanger(titles.get(2),true,-1e308,1e308),
                    AtmosMachineryFixtureProofSignFormatter.manifold(titles.get(0),false,Double.NaN,Double.POSITIVE_INFINITY),
                    AtmosMachineryFixtureProofSignFormatter.miner(titles.get(1),false,1e308,1e308),
                    AtmosMachineryFixtureProofSignFormatter.scrubber(titles.get(1),"NO CAPTURE","CARBON DIOXIDE",1e308,-1e308),
                    AtmosMachineryFixtureProofSignFormatter.vent(titles.get(1),"NO TRANSFER",1e308,1.0));
            for(int i=0;i<cases.size();i++) {
                var lines=cases.get(i);
                assertTrue(lines.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents);
                assertSame(caseTitles.get(i),lines.title(),"formatter must retain original machine-name Component");
                assertTrue(font.width(lines.title().getString())<=90,"title exceeds 90px: "+lines.title().getString());
                for(String line:List.of(lines.status(),lines.first(),lines.second())) {
                    assertTrue(font.width(line)<=90,"line exceeds 90px: "+line);
                    assertFalse(line.contains("NaN")||line.contains("Infinity"),"non-finite number leaked: "+line);
                }
            }
            for(var lines:wideCases) {
                assertTrue(font.width(lines.title().getString())<=90,"live machine title exceeds 90px: "+lines.title().getString());
                for(String line:List.of(lines.status(),lines.first(),lines.second()))
                    assertTrue(font.width(line)<=90,"wide live projection exceeds 90px: "+line+" ("+font.width(line)+"px)");
            }
            for (var gas : com.juicyslew.moonstation14.ms14.atmos.core.GasType.values()) {
                assertFalse(AtmosMachineryFixtureProofSignFormatter.operatorGasLabel(gas).contains(gas.name()),
                        "operator receipt must use the shared human-facing gas label");
                var lines=AtmosMachineryFixtureProofSignFormatter.filter(titles.get(1),true,gas,1e308,1e-300);
                for(String line:List.of(lines.status(),lines.first(),lines.second()))
                    assertTrue(font.width(line)<=90,"typed gas label exceeds 90px: "+line);
                assertFalse(lines.first().contains(gas.name()),"enum implementation name leaked");
            }
            var bypass=AtmosMachineryFixtureProofSignFormatter.unconfiguredFilter(titles.get(1));
            assertEquals("NO FILTER GAS",bypass.status());
            for(String line:List.of(bypass.status(),bypass.first(),bypass.second()))
                assertTrue(font.width(line)<=90,"unconfigured filter line exceeds 90px: "+line);
            assertEquals("PAID COOLING",cases.get(3).status());
            assertEquals("WAIT / UNVER.",cases.get(4).status());
            assertTrue(cases.get(3).first().contains("→"));
            assertTrue(cases.get(3).second().contains("-"));
            assertTrue(cases.get(1).second().contains("e"),"large counter must use compact SI notation");
            assertTrue(cases.get(2).first().matches(".*\\.\\d{1,2}→.*\\.\\d{1,2} K"));
            assertEquals("ONE MIX/3 LANES",cases.stream().filter(c -> c.status().equals("ONE MIX/3 LANES")).findFirst().orElseThrow().status());
            assertEquals("PLASMA 50.00%",normalPlasmaMix.first());
            assertEquals("H2O 50.00%",normalPlasmaMix.second());
            assertTrue(cases.get(12).first().contains(" "));
            assertTrue(cases.get(20).first().startsWith("Moved +"));
        } finally {
            language.restore();
        }
    }
}
