package com.juicyslew.moonstation14.ms14.construction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionLegacyRecipeTest {
    private final ClassLoader resources = getClass().getClassLoader();

    @Test
    void legacyVanillaRecipesDoNotBypassTheConstructionGraph() {
        assertNull(resources.getResource("data/moonstation14/recipe/steel_wall_girder_block.json"));
        assertNull(resources.getResource("data/moonstation14/recipe/steel_wall_block.json"));
        assertNull(resources.getResource("data/moonstation14/recipe/steel_from_steel_wall_girder.json"));
    }

    @Test
    void sharedGirderSteelWallAndPlasticWallConstructionRecipesRemainAvailable() {
        assertNotNull(resources.getResource("data/moonstation14/moonstation14/construction_recipe/girder.json"));
        assertNotNull(resources.getResource("data/moonstation14/moonstation14/construction_recipe/steel_wall.json"));
        assertNotNull(resources.getResource("data/moonstation14/moonstation14/construction_recipe/plastic_wall.json"));
    }
}
