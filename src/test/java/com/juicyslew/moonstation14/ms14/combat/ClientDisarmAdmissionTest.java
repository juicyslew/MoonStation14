package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.StatusEffectsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ClientDisarmAdmissionTest {
    private static CharacterData character(Object... components) {
        return new CharacterData(List.of(), java.util.Arrays.stream(components).map(c -> (com.juicyslew.moonstation14.ms14.character.components.CharacterComponent)c).toList());
    }
    @Test void knownHandsOrKnockdownProofAdmitsButUnknownAndStaminaOnlyDoNot() {
        assertTrue(ClientDisarmAdmission.allows(character(new HandsPrototypeComponent(List.of("left"))), true, false, false, false));
        var status = new StatusEffectsPrototypeComponent(java.util.Set.of(StatusEffectsPrototypeComponent.KNOCKDOWN));
        assertTrue(ClientDisarmAdmission.allows(character(status), true, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(null, true, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(character(), true, false, false, false));
    }
    @Test void disallowsNonlivingSelfIncapacitatedOrSpectator() {
        var proof = character(new HandsPrototypeComponent(List.of("left")));
        assertFalse(ClientDisarmAdmission.allows(proof, false, false, false, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, true, false, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, false, true, false));
        assertFalse(ClientDisarmAdmission.allows(proof, true, false, false, true));
    }

    @Test void productionPigAndChickenCompositionsRemainIneligibleForClientDisarm() throws Exception {
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModCharacters.CHARACTER_TYPE);
        manager.reload(ModCharacters.CHARACTER_TYPE, Map.of(
                id("supported_simple_mob"), read("supported_simple_mob"),
                id("supported_animal_core"), read("supported_animal_core"),
                id("supported_animal"), read("supported_animal"),
                id("supported_nonhostile_animal"), read("supported_nonhostile_animal"),
                id("pig"), read("pig"), id("chicken"), read("chicken")));
        var resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        for (String animal : new String[] {"pig", "chicken"}) {
            var composition = resolved.get(id(animal));
            assertTrue(composition.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isEmpty(), animal);
            assertTrue(composition.component(StatusEffectsPrototypeComponent.class).isEmpty(), animal);
            assertFalse(ClientDisarmAdmission.allows(composition, true, false, false, false),
                    animal + " is rejected from its resolved production composition (no hands or knockdown proof)");
        }
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("moonstation14:" + path); }
    private JsonObject read(String name) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream("data/moonstation14/moonstation14/character/" + name + ".json");
        if (stream == null) throw new AssertionError("missing character prototype " + name);
        try (stream) { return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject(); }
    }
}
