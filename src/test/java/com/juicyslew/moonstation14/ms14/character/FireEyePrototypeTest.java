package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.CharacterSchemaAudit;
import com.juicyslew.moonstation14.ms14.character.components.BlindablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.FlammablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FireEyePrototypeTest {
    private static final ResourceLocation PARENT = ResourceLocation.parse("moonstation14:fire_eye_parent");
    private static final ResourceLocation CHILD = ResourceLocation.parse("moonstation14:fire_eye_child");
    private static final ResourceLocation HOST = ResourceLocation.parse("minecraft:villager");

    private static JsonObject json(String source) { return JsonParser.parseString(source).getAsJsonObject(); }

    @Test
    void shippedHumanAndPigEachOptIntoBothWithoutImplicitEnrollment() throws Exception {
        for (String species : new String[]{"human", "pig"}) {
            String path = "data/moonstation14/moonstation14/character/" + species + ".json";
            try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(input, path);
                CharacterData data = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))).getOrThrow();
                assertTrue(data.component(FlammablePrototypeComponent.class).isPresent(), species);
                assertTrue(data.component(BlindablePrototypeComponent.class).isPresent(), species);
            }
        }
        assertTrue(new CharacterData().component(FlammablePrototypeComponent.class).isEmpty());
        assertTrue(new CharacterData().component(BlindablePrototypeComponent.class).isEmpty());
    }

    @Test
    void markersAreIndependentStrictInCompleteAndFragmentCodecs() {
        for (String type : new String[]{"Flammable", "Blindable"}) {
            assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    json("{\"components\":[{\"type\":\"" + type + "\"}]}")).result().isPresent());
            for (String extra : new String[]{"\"config\":1", "\"config\":null", "\"type\":null"}) {
                JsonObject object = json(extra.equals("\"type\":null") ? "{\"type\":null}"
                        : "{\"type\":\"" + type + "\"," + extra + "}");
                assertThrows(IllegalArgumentException.class,
                        () -> CharacterSchemaAudit.auditComponentFragment(object, "$.components[0]"));
                assertTrue(CharacterData.CODEC.parse(JsonOps.INSTANCE,
                        json("{\"components\":[" + object + "]}")).result().isEmpty());
            }
        }
        CharacterData fireOnly = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Flammable\"}]}")).getOrThrow();
        CharacterData eyesOnly = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                json("{\"components\":[{\"type\":\"Blindable\"}]}")).getOrThrow();
        assertTrue(fireOnly.component(FlammablePrototypeComponent.class).isPresent());
        assertTrue(fireOnly.component(BlindablePrototypeComponent.class).isEmpty());
        assertTrue(eyesOnly.component(BlindablePrototypeComponent.class).isPresent());
        assertTrue(eyesOnly.component(FlammablePrototypeComponent.class).isEmpty());
    }

    @Test
    void inheritedMarkersAndCurrentSnapshotHostOwnershipFailClosedOnReload() {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        JsonObject parent = json("{\"abstract\":true,\"components\":[{\"type\":\"Flammable\"},{\"type\":\"Blindable\"}]}");
        JsonObject child = json("{\"parent\":\"fire_eye_parent\",\"host_entity_types\":[\"minecraft:villager\"]}");
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(PARENT, parent, CHILD, child));
        var old = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        var inherited = CharacterIdentitySystem.resolveForHost(old, CHILD, HOST).orElseThrow();
        assertTrue(inherited.component(FlammablePrototypeComponent.class).isPresent());
        assertTrue(inherited.component(BlindablePrototypeComponent.class).isPresent());
        assertEquals(inherited, CharacterData.CODEC.parse(JsonOps.INSTANCE,
                manager.encodePublishedCatalogs().get(ModCharacters.CHARACTER_TYPE.typeId()).get(CHILD)).getOrThrow());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, CHILD, ResourceLocation.parse("minecraft:pig")).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, PARENT, HOST).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(old, null, HOST).isEmpty());

        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(CHILD,
                json("{\"host_entity_types\":[\"minecraft:villager\"],\"components\":[{\"type\":\"Blindable\"}]}")));
        var current = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertTrue(CharacterIdentitySystem.resolveForHost(current, CHILD, HOST).orElseThrow()
                .component(FlammablePrototypeComponent.class).isEmpty());
        assertTrue(CharacterIdentitySystem.resolveForHost(current, CHILD, HOST).orElseThrow()
                .component(BlindablePrototypeComponent.class).isPresent());
        assertTrue(inherited.component(FlammablePrototypeComponent.class).isPresent(), "old snapshot remains immutable");
    }
}
