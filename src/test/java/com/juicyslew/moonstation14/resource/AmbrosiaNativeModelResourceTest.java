package com.juicyslew.moonstation14.resource;

import net.minecraft.client.renderer.block.model.BlockModel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmbrosiaNativeModelResourceTest {
    private static final String ROOT = "/assets/moonstation14/";
    private static final String[] SPECIES = {"ambrosia_deus", "ambrosia_vulgaris"};
    private static final String[] STATES = {"stage-1", "stage-2", "stage-3", "stage-4", "stage-5", "stage-6", "harvest", "dead"};

    @Test
    void allAmbrosiaModelsAreAcceptedByMinecraftNativeDeserializer() throws Exception {
        for (String species : SPECIES) {
            for (String state : STATES) {
                String texture = "moonstation14:block/botany/plants/" + species + "/" + state;
                String path = "models/block/botany/plants/" + species + "/" + state + ".json";
                try (var stream = getClass().getResourceAsStream(ROOT + path)) {
                    assertNotNull(stream, "Missing model " + path);
                    BlockModel model = BlockModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    assertNotNull(model, path);
                    assertTrue(model.getElements().size() > 0, "Native model has no elements: " + path);
                    assertTrue(model.hasTexture("atlas"), "Native model has no atlas texture: " + path);
                    assertEquals(texture, model.getMaterial("atlas").texture().toString(), path);
                    assertEquals(texture, model.getMaterial("particle").texture().toString(), path);
                }
                String png = "textures/block/botany/plants/" + species + "/" + state + ".png";
                try (var stream = getClass().getResourceAsStream(ROOT + png)) {
                    assertNotNull(stream, "Missing texture " + png);
                    assertNotNull(ImageIO.read(stream), "Unreadable PNG " + png);
                }
            }
        }
    }
}
