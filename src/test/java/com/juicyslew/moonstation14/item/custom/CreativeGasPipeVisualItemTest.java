package com.juicyslew.moonstation14.item.custom;

import net.minecraft.world.item.BlockItem;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreativeGasPipeVisualItemTest {
    @Test
    void fixtureIsAPlaceableBlockItemRatherThanAnAtmosphereDevice() {
        assertTrue(BlockItem.class.isAssignableFrom(CreativeGasPipeVisualItem.class));
    }

    @Test
    void constructorAcceptsOnlyIndividualLanes() {
        assertEquals(1, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL.get().getLane());
        assertEquals(2, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_SECONDARY.get().getLane());
        assertEquals(4, com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_TERTIARY.get().getLane());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CreativeGasPipeVisualItem(com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get(),
                        new net.minecraft.world.item.Item.Properties(), 3));
    }

    @Test
    void inventoryNamesAreDistinctForAddedLanes() {
        assertEquals("block.moonstation14.gas_pipe_visual", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL.get().getDescriptionId());
        assertEquals("item.moonstation14.gas_pipe_visual_secondary", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_SECONDARY.get().getDescriptionId());
        assertEquals("item.moonstation14.gas_pipe_visual_tertiary", com.juicyslew.moonstation14.item.ModItems.GAS_PIPE_VISUAL_TERTIARY.get().getDescriptionId());
    }

    @Test
    void translationsDescribeFunctionalBodyPlacementAndVisualCreativePath() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("assets/moonstation14/lang/en_us.json")) {
            assertTrue(stream != null, "missing en_us translation resource");
            JsonObject translations = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Gas Pipe", translations.get("block.moonstation14.gas_pipe_visual").getAsString());
            assertEquals("Secondary Gas Pipe", translations.get("item.moonstation14.gas_pipe_visual_secondary").getAsString());
            assertEquals("Tertiary Gas Pipe", translations.get("item.moonstation14.gas_pipe_visual_tertiary").getAsString());
            assertFalse(translations.toString().contains("Visual Test"));
            assertTrue(translations.get("item.moonstation14.gas_pipe_visual.tooltip.visual_only").getAsString()
                    .contains("while controlling a character body creates a functional gas pipe"));
            assertTrue(translations.get("item.moonstation14.gas_pipe_visual.tooltip.visual_only").getAsString()
                    .contains("Creative inventory use places a visual fixture"));
        }
    }
}
