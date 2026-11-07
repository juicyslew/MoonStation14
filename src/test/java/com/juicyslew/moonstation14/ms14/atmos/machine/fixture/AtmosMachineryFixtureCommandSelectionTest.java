package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineryFixtureCommandSelectionTest {
    @Test void explicitCommandLiteralsMapToOneMachineAndBareIsNotASelection() {
        var tokens=java.util.Arrays.stream(AtmosMachineryFixturePlan.Kind.values()).map(AtmosMachineryFixtureCommands::demoLiteral).toList();
        assertEquals(9,tokens.size());
        assertEquals(9,Set.copyOf(tokens).size(),"each machine has exactly one canonical tab/label literal");
        for(var kind:AtmosMachineryFixturePlan.Kind.values()) {
            String token=AtmosMachineryFixtureCommands.demoLiteral(kind);
            assertEquals(kind,AtmosMachineryFixtureCommands.parseDemo(token).orElseThrow());
            assertTrue(token.length()<=14,"station label token exceeds line width: "+token);
        }
        assertEquals("heat_exchanger",AtmosMachineryFixtureCommands.demoLiteral(AtmosMachineryFixturePlan.Kind.PASSIVE_EXCHANGER));
        assertTrue(AtmosMachineryFixtureCommands.parseDemo("").isEmpty());
        assertTrue(AtmosMachineryFixtureCommands.parseDemo("all").isEmpty());
    }

    @Test void productionBrigadierTreeHasSiblingChoicesAndConsumesCoordinateForms() {
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
        dispatcher.register(net.minecraft.commands.Commands.literal("ms14").then(AtmosMachineryFixtureCommands.atmosFixtureCommand()));
        var fixture=dispatcher.getRoot().getChild("ms14").getChild("atmos_fixture");
        assertEquals(Set.of("build","status","start"),fixture.getChildren().stream()
                .map(com.mojang.brigadier.tree.CommandNode::getName).collect(java.util.stream.Collectors.toSet()));
        var start=fixture.getChild("start");
        var bare=dispatcher.parse("ms14 atmos_fixture start",null);
        assertTrue(bare.getExceptions().isEmpty());assertFalse(bare.getReader().canRead());
        var expected=new java.util.HashSet<>(java.util.Arrays.stream(AtmosMachineryFixturePlan.Kind.values())
                .map(AtmosMachineryFixtureCommands::demoLiteral).toList());expected.add("all");
        assertEquals(expected,start.getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName)
                .collect(java.util.stream.Collectors.toSet()),"all machine literals must be siblings immediately under start");
        for(var kind:AtmosMachineryFixturePlan.Kind.values()) {
            String token=AtmosMachineryFixtureCommands.demoLiteral(kind);
            var noCoordinates=dispatcher.parse("ms14 atmos_fixture start "+token,null);
            assertTrue(noCoordinates.getExceptions().isEmpty(),token+" parses as a direct start choice");
            assertFalse(noCoordinates.getReader().canRead(),"literal command is fully consumed: "+token);
            var coordinates=dispatcher.parse("ms14 atmos_fixture start "+token+" 11 64 23",null);
            assertTrue(coordinates.getExceptions().isEmpty(),token+" accepts optional block coordinates");
            assertFalse(coordinates.getReader().canRead(),"coordinate form is fully consumed: "+token);
        }
        var all=dispatcher.parse("ms14 atmos_fixture start all 11 64 23",null);
        assertTrue(all.getExceptions().isEmpty());assertFalse(all.getReader().canRead());
    }
}
