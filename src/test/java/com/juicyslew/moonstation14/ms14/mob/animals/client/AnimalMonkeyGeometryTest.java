package com.juicyslew.moonstation14.ms14.mob.animals.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimalMonkeyGeometryTest {
    @Test
    void diaperUvLayersHaveSeparatedXZBounds() throws Exception {
        String source = Files.readString(Path.of(System.getProperty("moonstation14.projectDir"),
            "src/main/java/com/juicyslew/moonstation14/ms14/mob/animals/client/AnimalMonkeyModel.java"));
        assertTrue(source.contains(".texOffs(65, 1).addBox(-3.25F, 2.99399399F, -1.85515873F, 6.5F, 2.0F, 5.0F)"));
        assertTrue(source.contains(".texOffs(21, 13).addBox(-3.24F, 2.93693694F, -1.84515873F, 6.48F, 2.0F, 4.98F)"));
        assertTrue(-3.24F > -3.25F && -3.24F + 6.48F < -3.25F + 6.5F);
        assertTrue(-1.84515873F > -1.85515873F && -1.84515873F + 4.98F < -1.85515873F + 5.0F);
    }
}
