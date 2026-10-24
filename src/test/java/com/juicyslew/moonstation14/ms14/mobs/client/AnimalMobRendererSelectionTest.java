package com.juicyslew.moonstation14.ms14.mobs.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.juicyslew.moonstation14.ms14.mobs.client.AnimalModelAdapters;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;

class AnimalMobRendererSelectionTest {
    @Test void synchronizedVariantSelectsItsOwnRigForEntityAndPortraitSnapshot() {
        String liveAppearance = AnimalMobRenderer.selectAppearanceKey("chicken_1", "chicken_0");
        String capturedSnapshotAppearance = AnimalMobRenderer.selectAppearanceKey("chicken_1", "chicken_0");
        assertEquals("chicken_1", liveAppearance);
        assertEquals(liveAppearance, capturedSnapshotAppearance);
        assertEquals("chicken_2", AnimalMobRenderer.selectAppearanceKey("chicken_2", "chicken_0"));
    }

    @Test void onlyEmptyInitialSyncUsesRegisteredCatalogRig() {
        assertEquals("chicken_0", AnimalMobRenderer.selectAppearanceKey("", "chicken_0"));
    }

    @Test void everyRegisteredRigSelectsOnlyItsAuthoredClipsAcrossMovementAndState() {
        for (var entry : AnimalModelAdapters.bindings().entrySet()) {
            var clips = entry.getValue().supportedClips();
            assertTrue(clips.contains("down_hold"), entry.getKey());
            assertTrue(clips.contains("idle_breathe"), entry.getKey());
            assertEquals("down_hold", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.CRITICAL, false, true, true, false), entry.getKey());
            assertEquals("down_hold", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.DEAD, false, false, false, false), entry.getKey());
            assertEquals("idle_breathe", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, false, false, false), entry.getKey());
            String groundMove = clips.contains("walk") ? "walk" : "idle_breathe";
            String groundSprint = clips.contains("run") ? "run" : "idle_breathe";
            assertEquals(groundMove, AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, true, false, false), entry.getKey());
            assertEquals(groundSprint, AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, true, true, false), entry.getKey());
            assertEquals(clips.contains("fly") ? "fly" : groundMove,
                    AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, true, true, false, false), entry.getKey());
            if (clips.contains("sleep_hold"))
                assertEquals("sleep_hold", AnimalModelAdapters.selectClip(clips, MobStateComponent.State.ALIVE, false, true, true, true), entry.getKey());
        }
    }

    @Test void portableAnimalDescriptorsResolveNativeHeldAdaptersAndStateClips() {
        for (String key : java.util.List.of("hamster", "bee", "butterfly", "cockroach", "mothroach", "moproach")) {
            var adapter = AnimalModelAdapters.resolve(key);
            assertTrue(adapter.supportsPortablePresentation(), key + " has typed portable model applicator");
            assertEquals("idle_breathe", AnimalModelAdapters.selectPortableClip(adapter.supportedClips(),
                    com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE), key);
            assertEquals("down_hold", AnimalModelAdapters.selectPortableClip(adapter.supportedClips(),
                    com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.CRITICAL), key);
            assertEquals("down_hold", AnimalModelAdapters.selectPortableClip(adapter.supportedClips(),
                    com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.DEAD), key);
        }
        assertTrue(AnimalModelAdapters.bindings().values().stream()
                .filter(AnimalModelAdapters.Adapter::supportsPortablePresentation).count() >= 5);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                com.juicyslew.moonstation14.ms14.mob.mouse.MouseAppearanceTextures.heldPresentation(
                        new com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance(
                                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "bee"), "Bee",
                                com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.Coat.NONE,
                                com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearance.State.ALIVE, "bee")));
    }
}
