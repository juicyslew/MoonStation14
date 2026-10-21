package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.block.custom.EmitterBlock;
import com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock;
import com.juicyslew.moonstation14.block.custom.ParticleAcceleratorPartBlock;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.block.custom.SingularityGeneratorBlock;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.entities.GasCanisterEntity;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityConsumptionSafetyTest {
    @Test void lowYieldTerrainTagIsPackagedAtCanonicalMinecraftTagPath() throws Exception {
        try (var stream = SingularityConsumptionSafetyTest.class.getResourceAsStream(
                "/data/moonstation14/tags/block/singularity_low_yield_terrain.json")) {
            assertNotNull(stream, "Minecraft 1.21.1 singular block-tag resource must be packaged");
            var tag = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(tag.get("replace").getAsBoolean());
            var values = tag.getAsJsonArray("values").asList().stream().map(value -> value.getAsString()).toList();
            assertEquals(java.util.List.of("minecraft:dirt", "minecraft:grass_block"), values);
        }
    }

    @Test void plainLoosePayloadIsEligibleButOwnedContainerPayloadIsNot() {
        assertFalse(SingularityConsumptionSafety.hasOwnedPayload(new ItemStack(Items.STONE)));
        assertTrue(SingularityConsumptionSafety.hasOwnedPayload(new ItemStack(Items.SHULKER_BOX)));
        assertFalse(SingularityConsumptionSafety.hasOwnedPayload(ItemStack.EMPTY));
    }

    @Test void auditedOwnerRemovalTargetsOptInWithoutUsingPowerRuntimeNodeAsEligibility() {
        for (Class<?> owner : new Class<?>[]{PowerDeviceBlock.class, EmitterBlock.class,
                ParticleAcceleratorPartBlock.class, FieldGeneratorBlock.class,
                SingularityGeneratorBlock.class, RadiationCollectorBlock.class, LooseItemEntity.class, GasCanisterEntity.class})
            assertTrue(SingularityConsumable.class.isAssignableFrom(owner), owner.getSimpleName());
        assertTrue(SingularityConsumable.class.isAssignableFrom(SingularityFood.class),
                "food block entities inherit the safe-removal capability");
    }
}
