package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPopulationSpecTest {
    @Test void acceptsEveryBoundedWidthAndCountWithRegisteredSpecies() {
        for(int width=6;width<=16;width++) for(int count=1;count<=200;count++) {
            var spec=new AnimalPopulationSpec(width,AnimalPopulationSpec.HEIGHT,List.of("bat","kobold","parrot","hamster","cow"),count);
            assertEquals(width,spec.width());
            assertEquals(count,spec.count());
            assertEquals("bat",spec.speciesAt(spec.species().size()));
        }
    }

    @Test void showcaseIsCatalogOrderedUniqueAndSpacedWhileLegacyCellsStayFixed() {
        var catalog=com.juicyslew.moonstation14.ms14.animals.AnimalCatalog.entries().stream().map(e->e.id()).toList();
        var spec=AnimalPopulationSpec.showcase();
        var expected = new java.util.ArrayList<>(catalog); expected.add("mouse");
        assertEquals(expected,spec.species()); assertEquals(expected.size(),spec.count()); assertEquals(24,spec.width()); assertEquals(3,spec.cellSpacing());
        assertEquals(expected.size(),new java.util.HashSet<>(spec.species()).size());
        var cells = new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for(int i=0;i<37;i++) { var cell=FixtureSettling.spawnCell(i,spec.width(),spec.cellSpacing()); assertTrue(cell.getX()>=3&&cell.getZ()>=3&&cell.getX()<=21&&cell.getZ()<=21); cells.add(cell); }
        for (int i=0;i<cells.size();i++) for (int j=i+1;j<cells.size();j++)
            assertTrue(Math.abs(cells.get(i).getX()-cells.get(j).getX())>=3 || Math.abs(cells.get(i).getZ()-cells.get(j).getZ())>=3);
        assertEquals(new net.minecraft.core.BlockPos(3,0,3),FixtureSettling.spawnCell(0,16,1));
        assertEquals(new net.minecraft.core.BlockPos(1,0,1),FixtureSettling.spawnCell(0,8,1));
    }

    @Test void rejectsOutOfBoundsAndUnregisteredSpecies() {
        assertThrows(IllegalArgumentException.class,()->new AnimalPopulationSpec(5,4,List.of("kobold"),1));
        assertThrows(IllegalArgumentException.class,()->new AnimalPopulationSpec(8,4,List.of("kobold"),201));
        assertThrows(IllegalArgumentException.class,()->new AnimalPopulationSpec(8,4,List.of("unknown"),1));
    }

    @Test void automatedScenariosResolveToTheirExactPopulation() {
        var mixed=AnimalPopulationSpec.automated(AnimalPopulationDiagnostic.Scenario.MIXED);
        var parrots=AnimalPopulationSpec.automated(AnimalPopulationDiagnostic.Scenario.PARROTS);
        var kobolds=AnimalPopulationSpec.automated(AnimalPopulationDiagnostic.Scenario.KOBOLDS);
        assertEquals(40,mixed.count());
        assertEquals(List.of("bat","kobold","parrot","hamster","cow"),mixed.species());
        assertEquals(100,parrots.count());
        assertEquals(List.of("parrot"),parrots.species());
        assertEquals("parrot",parrots.speciesAt(99));
        assertEquals(100,kobolds.count());
        assertEquals(List.of("kobold"),kobolds.species());
        assertEquals("kobold",kobolds.speciesAt(99));
    }
}
