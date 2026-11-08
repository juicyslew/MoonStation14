package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NettleDestructionSoundTest {
    @Test void deathNettleCandidateResolvesDestructionSound() {
        var profiles = new LinkedHashMap<>(BotanyCatalogCandidateTest.mainResources("loose_object_profile", "generic"));
        profiles.putAll(BotanyCatalogCandidateTest.resources("loose_object_profile", "death_nettle"));
        var resolved = PrototypeResolver.resolve(profiles, ModLooseObjectProfiles.PROFILE_TYPE.mergeStrategy().orElseThrow());
        var nettle = LooseObjectProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                resolved.get(ResourceLocation.parse("moonstation14:death_nettle"))).getOrThrow();
        assertEquals(ResourceLocation.parse("moonstation14:ss14/voice/diona/salute"), nettle.itemDamage().destructionSound());
        assertEquals(-5f, nettle.itemDamage().destructionSoundVolumeDb());
    }
}
