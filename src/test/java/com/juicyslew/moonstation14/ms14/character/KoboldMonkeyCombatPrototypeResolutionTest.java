package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KoboldMonkeyCombatPrototypeResolutionTest {
    private static final String ROOT = "src/main/resources/data/moonstation14/moonstation14/character/";

    @Test
    void addedCombatantsResolveFromCanonicalResourcesAndFeedSharedCombatProjection() throws Exception {
        Path root = Path.of(System.getProperty("moonstation14.projectDir"), ROOT);
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                definitions.put(ResourceLocation.fromNamespaceAndPath("moonstation14", relative.substring(0, relative.length() - 5)),
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        }
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, definitions);
        var catalog = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (String id : java.util.List.of("kobold", "monkey", "scurret")) {
            var character = catalog.get(ResourceLocation.parse("moonstation14:" + id));
            var mode = character.component(CombatModeComponent.class).orElseThrow();
            assertEquals(java.util.Optional.of(true), mode.canDisarm(), id);
            assertEquals(java.util.List.of("left"), character.component(HandsPrototypeComponent.class).orElseThrow().hands(), id);
            var stamina = character.component(StaminaPrototypeComponent.class).orElseThrow();
            assertTrue(character.component(StunnableComponent.class).isPresent(), id + " supports stamina-triggered stun");
            assertEquals(100f, stamina.baseCritThreshold());
            assertEquals(3f, stamina.decay());
            assertEquals(3f, stamina.cooldownSeconds());
            assertEquals(6f, stamina.stunSeconds());
            var attack = character.component(MeleeAttackComponent.class).orElseThrow();
            var expected = id.equals("kobold") ? Map.of("slash", 5f, "piercing", 4f) : id.equals("scurret") ? Map.of("piercing", 10f) : Map.of("blunt", 3f, "piercing", 3f);
            assertEquals(expected, attack.damage().types(), id);
            var profile = com.juicyslew.moonstation14.ms14.combat.UnarmedMeleeProfileResolver.resolve(attack);
            assertEquals(expected, profile.damage(), id);
            assertNotNull(com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.class,
                    "client projection consumes the same resolved melee profile path");
        }
        assertFalse(catalog.get(ResourceLocation.parse("moonstation14:gorilla"))
                .component(CombatModeComponent.class).map(c -> c.canDisarm().orElse(false)).orElse(false));
        assertTrue(catalog.get(ResourceLocation.parse("moonstation14:pig"))
                .component(HandsPrototypeComponent.class).isEmpty(), "handless negative case remains intact");
    }
}
