package com.juicyslew.moonstation14.ms14.stripping.client;

import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StripTargetBindingTest {
    @Test void targetIdentitySurvivesContentlessViewAndRejectsIncarnationOrDimensionReuse() {
        UUID uuid=UUID.randomUUID();
        ResourceKey<Level> overworld=ResourceKey.create(Registries.DIMENSION,ResourceLocation.withDefaultNamespace("overworld"));
        ResourceKey<Level> nether=ResourceKey.create(Registries.DIMENSION,ResourceLocation.withDefaultNamespace("the_nether"));
        var binding=new StripTargetBinding(uuid,41,overworld);
        // View contents may be absent while connecting or read-only; binding retains only identity.
        assertTrue(binding.matches(uuid,41,overworld));
        assertFalse(binding.matches(UUID.randomUUID(),41,overworld));
        assertFalse(binding.matches(uuid,42,overworld));
        assertFalse(binding.matches(uuid,41,nether));
    }
}
