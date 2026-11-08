package com.juicyslew.moonstation14.ms14.botany;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BotanyMachineProfileTest {
    @Test void rejectsInvalidMachineComposition() {
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile(-1, 0, 0, 0, false, false, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile(0, 0, 0, 0, true, true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MachineProfile(0, 0, 0, 0, true, true, 3, 1));
    }

    @Test void resolvedMachineInheritancePreservesExtractorAndGrinderDefaults() {
        var manager = PrototypeRuntime.serverManager();
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : new String[]{"seed_extractor_base", "seed_extractor", "grinder_base", "grinder"}) {
            String path = "data/moonstation14/moonstation14/botany_machine_profile/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                raw.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        manager.reload(ModBotanyMachineProfiles.TYPE, raw);
        var catalog = manager.snapshot(ModBotanyMachineProfiles.TYPE);
        var extractor = catalog.get(id("seed_extractor"));
        assertNotNull(extractor);
        assertTrue(extractor.extractor());
        assertTrue(extractor.powered());
        assertEquals(1000, extractor.powerWatts());
        assertEquals(0, extractor.chamberCapacity());
        assertEquals(1, extractor.minimumPacketYield());
        assertEquals(3, extractor.maximumPacketYield());
        var grinder = catalog.get(id("grinder"));
        assertNotNull(grinder);
        assertTrue(grinder.powered());
        assertFalse(grinder.extractor());
        assertEquals(0, grinder.powerWatts());
        assertEquals(750, grinder.activePowerWatts());
        assertEquals(6, grinder.chamberCapacity());
        assertEquals(70, grinder.startDelayTicks());
        assertEquals(new MachineProfile.SoundCue(id("botany/grinder/blender"), 1f, 1f), grinder.processingSound());
        assertEquals(new MachineProfile.SoundCue(id("botany/grinder/juicer"), 1f, 1f), grinder.juiceSound());
        assertEquals(new MachineProfile.SoundCue(id("chemistry/chemmaster/insert"), 1f, 1f), grinder.beakerInsertSound());
        assertEquals(new MachineProfile.SoundCue(id("chemistry/chemmaster/eject"), 1f, 1f), grinder.beakerEjectSound());
        assertEquals(new MachineProfile.SoundCue(id("apc/switch"), 0.7943282f, 1f), grinder.chamberEjectSound());
        assertNull(extractor.processingSound(), "extractor has no configured processing cue");
        assertNull(extractor.beakerInsertSound(), "extractor has no configured slot cue");
    }

    @Test void resolvedMachineProfileSoundCuesAreRegisteredSoundEvents() {
        var manager = PrototypeRuntime.serverManager();
        Map<ResourceLocation, JsonObject> raw = new LinkedHashMap<>();
        for (String name : new String[]{"grinder_base", "grinder"}) {
            String path = "data/moonstation14/moonstation14/botany_machine_profile/" + name + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                raw.put(id(name), JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        }
        manager.reload(ModBotanyMachineProfiles.TYPE, raw);
        var profile = manager.snapshot(ModBotanyMachineProfiles.TYPE).get(id("grinder"));
        assertNotNull(profile);
        JsonObject eventDefinitions;
        try (var stream = getClass().getResourceAsStream("/assets/moonstation14/sounds.json")) {
            assertNotNull(stream, "sounds.json event definitions");
            eventDefinitions = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
        for (var cue : new MachineProfile.SoundCue[]{profile.processingSound(), profile.juiceSound(), profile.beakerInsertSound(), profile.beakerEjectSound(), profile.chamberEjectSound()}) {
            assertNotNull(cue);
            assertEquals("moonstation14", cue.sound().getNamespace());
            assertTrue(eventDefinitions.has(cue.sound().getPath()), "missing registered sound event " + cue.sound());
        }
    }

    @Test void profileMergeInheritsAudioCuesAndTimingFromParent() {
        var manager = PrototypeRuntime.serverManager();
        JsonObject parent = JsonParser.parseString("""
                {"id":"moonstation14:audio_parent","processing_sound":{"sound":"moonstation14:botany/grinder/blender"},
                 "beaker_insert_sound":{"sound":"moonstation14:chemistry/chemmaster/revolver_magin"},
                 "chamber_eject_sound":{"sound":"moonstation14:apc/switch","volume":0.7943282},"start_delay_ticks":70}
                """).getAsJsonObject();
        JsonObject child = JsonParser.parseString("""
                {"id":"moonstation14:audio_child","parent":"moonstation14:audio_parent"}
                """).getAsJsonObject();
        manager.reload(ModBotanyMachineProfiles.TYPE, Map.of(id("audio_parent"), parent, id("audio_child"), child));
        var resolved = manager.snapshot(ModBotanyMachineProfiles.TYPE).get(id("audio_child"));
        assertNotNull(resolved);
        assertEquals(70, resolved.startDelayTicks());
        assertEquals(new MachineProfile.SoundCue(id("botany/grinder/blender"), 1f, 1f), resolved.processingSound());
        assertEquals(new MachineProfile.SoundCue(id("chemistry/chemmaster/revolver_magin"), 1f, 1f), resolved.beakerInsertSound());
        assertEquals(new MachineProfile.SoundCue(id("apc/switch"), 0.7943282f, 1f), resolved.chamberEjectSound());
    }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("moonstation14", value); }
}
