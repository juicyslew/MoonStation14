package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;

class BlockStructuralContactPrototypeTest {
    @Test void conflictingDuplicateBlockMappingsAreRejected() {
        var manager = new PrototypeManager(); manager.register(ModBlockStructuralContacts.TYPE);
        var mapping = new com.google.gson.JsonObject(); mapping.addProperty("block", "minecraft:stone");
        mapping.add("structuralContact", JsonParser.parseString("{\"flat_reductions\":{\"blunt\":10}}").getAsJsonObject());
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "first"), mapping.deepCopy());
        raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "second"), mapping.deepCopy());
        assertThrows(RuntimeException.class, () -> manager.reload(Map.of(ModBlockStructuralContacts.TYPE, raw)));
    }

    @Test void productionFloorAndAirlockMappingsResolveAndAbsorbMeleeDamage() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModBlockStructuralContacts.TYPE);
        var url = Objects.requireNonNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/block_structural_contact"));
        var raw = new LinkedHashMap<ResourceLocation, com.google.gson.JsonObject>();
        try (var files = Files.list(Path.of(url.toURI()))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList())
                raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", file.getFileName().toString().replaceFirst("\\.json$", "")),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        manager.reload(Map.of(ModBlockStructuralContacts.TYPE, raw));
        var catalog = manager.snapshot(ModBlockStructuralContacts.TYPE);
        assertEquals(2, catalog.size(), "resource discovery loads both concrete block contacts through the registered type");
        var fists = new MeleeWeaponData(Map.of("blunt", 5f), 1, 1.5f, false, false, 1, false);
        for (String id : new String[]{"station_floor", "airlock"}) {
            var contact = catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", id));
            assertNotNull(contact);
            assertEquals("moonstation14:" + id, contact.block());
            assertEquals(ConstructionResult.ZERO_ABSORBED_CONTACT, evaluate(contact, fists));
            assertEquals(ConstructionResult.ZERO_ABSORBED_CONTACT, evaluate(contact,
                    new MeleeWeaponData(Map.of("blunt", 8f), 1, 1.5f, false, false, 1, false)));
            assertEquals(ConstructionResult.ZERO_ABSORBED_CONTACT, evaluate(contact,
                    new MeleeWeaponData(Map.of("blunt", 10f), 1, 1.5f, false, false, 1, false)));
        }
        assertEquals(ConstructionResult.UNSUPPORTED_POSITIVE_DAMAGE,
                evaluate(catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "station_floor")),
                        new MeleeWeaponData(Map.of("blunt", 11f), 1, 1.5f, false, false, 1, false)));
    }
    private static ConstructionResult evaluate(BlockStructuralContactData data, MeleeWeaponData attack) {
        var damage = com.juicyslew.moonstation14.ms14.damage.DamageModifierData.apply(attack.damage(), data.structuralContact());
        var policy = new com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData(
                data.block(), true, false, false, 1, true, java.util.Optional.empty(),
                java.util.Optional.of(data.structuralContact()));
        return policy.evaluateStructuralContact(damage)
                == com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData.StructuralContactResult.ZERO_ABSORBED_CONTACT
                ? ConstructionResult.ZERO_ABSORBED_CONTACT : ConstructionResult.UNSUPPORTED_POSITIVE_DAMAGE;
    }
    private enum ConstructionResult { ZERO_ABSORBED_CONTACT, UNSUPPORTED_POSITIVE_DAMAGE }
}
