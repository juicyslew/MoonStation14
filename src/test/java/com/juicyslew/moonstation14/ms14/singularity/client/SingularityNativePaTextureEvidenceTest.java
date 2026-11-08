package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SingularityNativePaTextureEvidenceTest {
    @Test
    void diagnosticBuildsPngAndMetadataIdsFromAtlasSpriteId() {
        var sprite = ResourceLocation.fromNamespaceAndPath("moonstation14", "block/power_collection/pa_control_box/default");

        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14",
                "textures/block/power_collection/pa_control_box/default.png"),
                SingularityVisualPreview.textureResource(sprite));
        assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14",
                "textures/block/power_collection/pa_control_box/default.png.mcmeta"),
                SingularityVisualPreview.textureMetadataResource(sprite));
    }
}
