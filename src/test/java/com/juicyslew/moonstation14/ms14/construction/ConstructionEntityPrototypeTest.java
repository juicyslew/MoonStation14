package com.juicyslew.moonstation14.ms14.construction;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponData;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ConstructionEntityPrototypeTest {
    @Test void resolvedEntityDefaultsDriveAnchorabilityAndBlockMapping() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModConstruction.ENTITY_TYPE);
        var url = Objects.requireNonNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/construction_entity"));
        var values = new LinkedHashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject>();
        try (var files = Files.list(Path.of(url.toURI()))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList())
                values.put(ModConstruction.id(file.getFileName().toString().replaceFirst("\\.json$", "")),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        manager.reload(Map.of(ModConstruction.ENTITY_TYPE, values));
        var catalog = manager.snapshot(ModConstruction.ENTITY_TYPE);
        ConstructionEntityData girder = catalog.get(ModConstruction.id("girder"));
        ConstructionEntityData wall = catalog.get(ModConstruction.id("wall_solid"));
        ConstructionEntityData plastic = catalog.get(ModConstruction.id("wall_plastic"));
        assertEquals("moonstation14:steel_wall_girder_block", girder.block());
        assertTrue(girder.defaultAnchored()); assertTrue(girder.anchorable()); assertTrue(girder.unanchorable());
        assertEquals(1.0, girder.anchoringSeconds()); assertFalse(girder.airtight());
        assertEquals("moonstation14:steel_wall_block", wall.block()); assertTrue(wall.defaultAnchored());
        assertFalse(wall.anchorable()); assertFalse(wall.unanchorable()); assertEquals(1.0, wall.anchoringSeconds()); assertTrue(wall.airtight());
        assertEquals("moonstation14:plastic_wall", plastic.block()); assertFalse(plastic.anchorable()); assertTrue(plastic.airtight());
        assertEquals(2, wall.radiationBlocker().orElseThrow().resistance());
        assertEquals(2, plastic.radiationBlocker().orElseThrow().resistance());
        assertTrue(girder.radiationBlocker().isEmpty(), "unmapped structures do not infer shielding");
        var fists = new MeleeWeaponData(Map.of("blunt", 5f), 1, 1.5f, false, false, 1, false);
        var crowbar = new MeleeWeaponData(Map.of("blunt", 8f), 1, 1.5f, false, false, 1, false);
        var knife = new MeleeWeaponData(Map.of("slash", 10f), 1, 1.5f, false, false, 1, false);
        for (var entity : java.util.List.of(girder, wall, plastic)) {
            assertEquals(ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT, entity.evaluateStructuralContact(fists));
            assertEquals(ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT, entity.evaluateStructuralContact(crowbar));
            assertEquals(ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT, entity.evaluateStructuralContact(knife));
            assertEquals(ConstructionEntityData.StructuralContactResult.UNSUPPORTED_POSITIVE_DAMAGE,
                    entity.evaluateStructuralContact(new MeleeWeaponData(Map.of("blunt", 11f), 1, 1.5f, false, false, 1, false)));
        }
        for (float blunt : new float[]{5f, 8f, 10f})
            assertEquals(ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT,
                    wall.evaluateStructuralContact(Map.of("blunt", 0f)));
        assertEquals(ConstructionEntityData.StructuralContactResult.UNSUPPORTED_POSITIVE_DAMAGE,
                wall.evaluateStructuralContact(Map.of("blunt", 11f)));
        assertEquals(ConstructionEntityData.StructuralContactResult.UNSUPPORTED_POSITIVE_DAMAGE,
                wall.evaluateStructuralContact(Map.of("blunt", 11f, "slash", 6f)), "event bonus remains positive after composition");
        var ordinary = new ConstructionEntityData("minecraft:stone", true, false, false, 1, false);
        assertEquals(ConstructionEntityData.StructuralContactResult.NOT_OPTED_IN, ordinary.evaluateStructuralContact(fists));
    }
}
