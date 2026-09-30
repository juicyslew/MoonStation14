package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.HotspotKernel;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereReactionPersistenceTest {
    private static final BlockPos CELL = new BlockPos(3, -20, 5);
    private static final GasMixture HOT_GAS = new GasMixture(
            Map.of(GasType.TRITIUM, 2.0, GasType.OXYGEN, 4.0), 900);
    private static final PrototypeCatalog<GasReactionData> FIRE = new PrototypeCatalog<>(Map.of(
            ResourceLocation.parse("moonstation14:tritium_fire"),
            new GasReactionData(Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, .01),
                    373.15, 2000, 0, -1,
                    List.of(new GasReactionData.Effect(GasReactionData.EffectType.TRITIUM_FIRE)))));

    @Test
    void savedReactiveOverrideAndFiniteClaimContainNoTransientFireState() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        assertTrue(data.claimFinite(3, -20, 5));
        assertTrue(data.put(3, -20, 5, HOT_GAS, GasMixture.vacuum()));
        var saved = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        assertEquals(2, saved.getAsJsonObject().get("version").getAsInt());
        assertEquals(1, saved.getAsJsonObject().getAsJsonArray("cells").size());
        assertEquals(1, saved.getAsJsonObject().getAsJsonArray("finite_cells").size());
        assertEquals(3, saved.getAsJsonObject().size(), "no hotspot or fire-state persistence");

        AtmosphereChunkData restored = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, saved)
                .result().orElseThrow();
        assertTrue(restored.isFiniteClaimed(3, -20, 5));
        assertEquals(1, restored.claimCount());
        assertEquals(1, restored.size());
        assertEquals(HOT_GAS.gasMoles(), restored.get(3, -20, 5).gasMoles());
        assertEquals(HOT_GAS.temperatureKelvin(), restored.get(3, -20, 5).temperatureKelvin());
        assertEquals(new AtmosphereChunkData.CellPosition(3, -20, 5),
                restored.nextAfter(null).orElseThrow().getKey());
        assertTrue(restored.nextAfter(new AtmosphereChunkData.CellPosition(3, -20, 5)).isEmpty());
    }

    @Test
    void unloadDropsHotspotButPreservesSavedGasAndNextCandidateSeedsFreshFraction() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        data.claimFinite(3, -20, 5);
        data.put(3, -20, 5, HOT_GAS, GasMixture.vacuum());
        ReactionWorkQueue work = new ReactionWorkQueue();
        ChunkPos chunk = new ChunkPos(CELL);
        work.startLoad(chunk);
        work.offer(CELL);
        var grown = new HotspotKernel.HotspotState(.8, 900);
        work.setHotspot(CELL, Optional.of(grown));
        assertTrue(HotspotKernel.evaluate(HOT_GAS, FIRE, grown).totalFireExtentMoles()
                > HotspotKernel.evaluate(HOT_GAS, FIRE, null).totalFireExtentMoles());

        work.removeChunk(chunk);
        assertNull(work.hotspot(CELL));
        assertNull(work.pollLoad());
        assertNull(work.poll());
        var saved = AtmosphereChunkData.CODEC.encodeStart(JsonOps.INSTANCE, data).result().orElseThrow();
        AtmosphereChunkData loaded = AtmosphereChunkData.CODEC.parse(JsonOps.INSTANCE, saved)
                .result().orElseThrow();
        work.startLoad(chunk);
        assertNull(work.cursor(chunk));
        assertEquals(1, work.drainLoads(1, 1, loadedChunk -> {
            assertEquals(chunk, loadedChunk);
            var next = loaded.nextAfter(work.cursor(loadedChunk)).orElseThrow();
            assertEquals(new AtmosphereChunkData.CellPosition(3, -20, 5), next.getKey());
            work.continueLoad(loadedChunk, next.getKey());
            work.offer(CELL);
            return true;
        }));
        assertEquals(1, work.drainLoads(1, 1, loadedChunk -> {
            assertEquals(chunk, loadedChunk);
            return loaded.nextAfter(work.cursor(loadedChunk)).isPresent();
        }));
        assertNull(work.pollLoad());
        assertEquals(CELL, work.poll());
        assertNull(work.poll());
        GasMixture candidate = loaded.nextAfter(null).orElseThrow().getValue();
        assertTrue(loaded.isFiniteClaimed(3, -20, 5));
        assertEquals(HOT_GAS.gasMoles(), candidate.gasMoles());
        var fresh = HotspotKernel.evaluate(candidate, FIRE, work.hotspot(CELL));
        assertEquals(.1 + 40 * fresh.totalFireExtentMoles(),
                fresh.nextState().orElseThrow().fraction(), 1e-12);
    }

    @Test
    void missingSnapshotPreservesHotspotButVerifiedSolidQuenchRemovesTransientVisualState() {
        ReactionWorkQueue work = new ReactionWorkQueue();
        var hotspot = new HotspotKernel.HotspotState(.8, 900);
        work.setHotspot(CELL, Optional.of(hotspot));
        work.setBurning(CELL, 204);

        assertFalse(AtmosphereService.schedulerReactionEligible(work, CELL, null, FIRE.asMap().values()));
        assertSame(hotspot, work.hotspot(CELL), "unknown or unloaded snapshots must not quench");
        assertEquals(204, work.fireIntensity(CELL));

        assertTrue(work.quenchIfPresent(CELL), "the loaded-solid topology path explicitly quenchs");
        assertNull(work.hotspot(CELL));
        assertEquals(0, work.fireIntensity(CELL));
        assertFalse(work.quenchIfPresent(CELL), "an already clear hotspot does not republish a visual change");
    }
}
