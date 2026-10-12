package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Verifies the production return ticket pins its one exact saved chunk across server ticks. */
@GameTestTags({"lifecycle", "character", "carrier"})
@PrefixGameTestTemplate(false)
public final class ExactBodyReturnChunkTicketGameTests {
    private ExactBodyReturnChunkTicketGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14")
    public static void exactReturnChunkTicketRetainsOnlyRequestedChunk(GameTestHelper helper) {
        var level = helper.getLevel();
        ChunkPos exact = new ChunkPos(512, -512);
        UUID key = UUID.randomUUID();
        level.getChunkSource().getChunk(exact.x, exact.z, ChunkStatus.FULL, true);
        LifecycleExistingBodyReconnect.retainReturnChunk(level, exact, key);
        helper.runAfterDelay(2, () -> {
            try {
                require(level.getChunkSource().getChunkNow(exact.x, exact.z) != null,
                        "saved FULL chunk remains available under exact return ticket");
                require(level.getChunkSource().getChunkNow(exact.x + 4, exact.z) == null,
                        "return ticket does not request a neighboring chunk");
            } finally {
                LifecycleExistingBodyReconnect.releaseReturnChunk(level, exact, key);
            }
            helper.succeed();
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
