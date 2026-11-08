package com.juicyslew.moonstation14.ms14.botany.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class GrinderProcessingAudioPolicyTest {
    private static final ResourceLocation DIM = ResourceLocation.withDefaultNamespace("overworld");
    private static final BlockPos POS = new BlockPos(3, 4, 5);
    private static final UUID PLACEMENT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test void exactStartKeyIsIdempotentButSeparateMachinesAndJobsAreIndependent() {
        var original = new GrinderProcessingAudio.Key(DIM, POS, PLACEMENT, 12);
        assertEquals(original, new GrinderProcessingAudio.Key(DIM, POS, PLACEMENT, 12));
        assertNotEquals(original, new GrinderProcessingAudio.Key(DIM, new BlockPos(4, 4, 5), PLACEMENT, 12));
        assertNotEquals(original, new GrinderProcessingAudio.Key(ResourceLocation.withDefaultNamespace("the_nether"), POS, PLACEMENT, 12));
        assertNotEquals(original, new GrinderProcessingAudio.Key(DIM, POS, UUID.fromString("00000000-0000-0000-0000-000000000002"), 12));
        assertNotEquals(original, new GrinderProcessingAudio.Key(DIM, POS, PLACEMENT, 13));
    }
}
