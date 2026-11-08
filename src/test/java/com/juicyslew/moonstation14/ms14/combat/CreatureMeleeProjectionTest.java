package com.juicyslew.moonstation14.ms14.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.character.components.CombatModeComponent;
import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CreatureMeleeProjectionTest {
    private static final String ROOT = "src/main/resources/data/moonstation14/moonstation14/character/";

    @Test
    void canonicalCreatureMeleeProfilesProjectAndGateDisarmPerCharacter() throws Exception {
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
        var characters = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        var catalog = new PrototypeCatalog<MeleeWeaponProfile>(Map.of());
        UUID body = UUID.randomUUID(), account = UUID.randomUUID();
        var snapshot = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, account, body, 4, 9,
                "left", List.of(new BodyHandStateSnapshot.Hand("left", null, null, 0)), List.of());

        for (String id : List.of("kobold", "monkey")) {
            var character = characters.get(ResourceLocation.parse("moonstation14:" + id));
            var attack = character.component(MeleeAttackComponent.class).orElseThrow();
            var profile = UnarmedMeleeProfileResolver.resolve(attack);
            var selection = ClientMeleeWeaponResolver.project(body, 4, snapshot, catalog, Optional.of(profile)).orElseThrow();
            assertEquals(MeleeWeaponResolver.Kind.UNARMED, selection.kind(), id);
            assertEquals(profile.damage(), selection.weapon().damage(), id);
            assertEquals(profile.audio(), selection.weapon().audio(), id);
            var canDisarm = character.component(CombatModeComponent.class).orElseThrow().canDisarm().orElse(null);
            assertEquals(MeleeAttackRequest.Mode.DISARM,
                    MeleeSecondaryGesture.mode(selection.kind(), selection.weapon().altDisarm(), canDisarm), id);
            assertTrue(MeleeSecondaryGesture.allowsDisarm(selection, canDisarm), id);
        }

        var gorilla = characters.get(ResourceLocation.parse("moonstation14:gorilla"));
        var gorillaProfile = UnarmedMeleeProfileResolver.resolve(gorilla.component(MeleeAttackComponent.class).orElseThrow());
        var handless = MeleeWeaponResolver.choose(body, 0, "", false, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(gorillaProfile)).orElseThrow();
        var gorillaCanDisarm = gorilla.component(CombatModeComponent.class).orElseThrow().canDisarm().orElse(null);
        assertFalse(MeleeSecondaryGesture.allowsDisarm(handless, gorillaCanDisarm));
        assertNull(MeleeSecondaryGesture.mode(handless.kind(), handless.weapon().altDisarm(), gorillaCanDisarm));
    }
}
