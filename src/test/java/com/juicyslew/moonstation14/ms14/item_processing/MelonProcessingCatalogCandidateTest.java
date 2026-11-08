package com.juicyslew.moonstation14.ms14.item_processing;

import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** TEST-only candidate composition for the two distinct melon processing actions. */
class MelonProcessingCatalogCandidateTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    @Test void melonProfilesKeepFiveSliceAndSingleKnifeHatActionsSeparateWithoutPublishing() {
        var raw = BotanyCatalogCandidateTest.resources("item_processing_profile", "watermelon", "holymelon");
        var resolved = PrototypeResolver.resolve(raw, null).asMap();
        var profiles = new LinkedHashMap<ResourceLocation, ItemProcessingProfile>();
        resolved.forEach((key, value) -> profiles.put(key,
                ItemProcessingProfile.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()));

        for (String source : new String[]{"watermelon", "holymelon"}) {
            var profile = profiles.get(id(source));
            assertNotNull(profile, source);
            assertNotNull(profile.sliceableFood());
            assertEquals(id(source.equals("watermelon") ? "watermelon_slice" : "holymelon_slice"),
                    profile.sliceableFood().output());
            assertEquals(5, profile.sliceableFood().count());
            assertEquals(1f, profile.sliceableFood().delaySeconds());
            assertNotNull(profile.butcherable());
            assertEquals(ItemProcessingProfile.Butcherable.Type.KNIFE, profile.butcherable().type());
            assertEquals(8f, profile.butcherable().delaySeconds());
            assertEquals(java.util.List.of(new ItemProcessingProfile.ItemOutput(
                    id(source.equals("watermelon") ? "clothing_head_hat_watermelon" : "clothing_head_hat_holy_watermelon"), 1)),
                    profile.butcherable().outputs());
            assertNull(profile.log());
            assertNull(profile.spawnItemsOnUse());
            BotanyCatalogCandidateTest.assertMainPromotionMatchesCandidate("item_processing_profile", source);
        }

        // This candidate intentionally cannot pass registered-output admission. Do not create
        // placeholder item registrations merely to make a recipe publishable.
        var manager = new PrototypeManager();
        manager.register(ModItemProcessingProfiles.TYPE);
        var before = manager.snapshot();
        var failure = assertThrows(IllegalArgumentException.class,
                () -> manager.stage(Map.of(ModItemProcessingProfiles.TYPE, raw), ignored -> { }));
        assertTrue(failure.getMessage().contains("has no resolved edible solution profile"), failure.getMessage());
        assertSame(before, manager.snapshot());
    }

}
