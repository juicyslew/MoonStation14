package com.juicyslew.moonstation14.ms14.atmos.world;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereOwnershipSchedulerTest {
    @Test
    void unloadedBoundaryParksSeedWhileAnotherRoomProgressesThenLoadWakesIt() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos unloadedRoomSeed = new BlockPos(0, 64, 0);
        BlockPos smallRoomSeed = new BlockPos(32, 64, 0);
        assertTrue(work.offer(unloadedRoomSeed));
        assertTrue(work.offer(smallRoomSeed));

        assertEquals(unloadedRoomSeed, work.startNext());
        var unknown = new AtmosphereOwnershipSearch(unloadedRoomSeed).step(
                pos -> pos.equals(unloadedRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.UNKNOWN_UNLOADED,
                100);
        assertEquals(AtmosphereOwnershipSearch.Status.UNKNOWN, unknown.status());
        work.parkUnknown(unloadedRoomSeed);
        work.finishActive();
        assertEquals(1, work.unknownSeedCount());
        assertEquals(0, work.saturatedChunkCount());

        assertEquals(smallRoomSeed, work.startNext());
        var finiteRoom = new AtmosphereOwnershipSearch(smallRoomSeed).step(
                pos -> pos.equals(smallRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                20);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, finiteRoom.status());
        assertTrue(work.unknownSeedCount() == 1,
                "the unresolved room remains parked rather than blocking or being misclassified");

        work.finishActive();
        work.wakeUnknown(); // Chunk load or topology change makes the paused seed retryable.
        assertEquals(0, work.unknownSeedCount());
        assertEquals(unloadedRoomSeed, work.startNext());
    }

    @Test
    void saturatedSearchParksItsSeedAndDoesNotBlockTheNextRoom() {
        AtmosphereService.OwnershipWork work = new AtmosphereService.OwnershipWork();
        BlockPos saturatedSeed = new BlockPos(0, 64, 0);
        BlockPos smallRoomSeed = new BlockPos(32, 64, 0);
        assertTrue(work.offer(saturatedSeed));
        assertTrue(work.offer(smallRoomSeed));

        assertEquals(saturatedSeed, work.startNext());
        work.parkSaturated(saturatedSeed);
        assertFalse(work.hasActive());
        assertTrue(work.isSaturated(saturatedSeed));
        assertFalse(work.exterior.contains(saturatedSeed), "saturation is unknown, never exterior");

        assertEquals(smallRoomSeed, work.startNext());
        var finiteRoom = new AtmosphereOwnershipSearch(smallRoomSeed).step(
                pos -> pos.equals(smallRoomSeed)
                        ? AtmosphereOwnershipSearch.ProbeResult.OPEN_COVERED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                20);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, finiteRoom.status());
        assertEquals(java.util.List.of(smallRoomSeed), finiteRoom.visitedOpenCells());
        assertTrue(work.isSaturated(saturatedSeed), "the large region remains parked, not retried every tick");

        work.finishActive(); // the completed small-room job releases the single active slot
        work.wakeSaturated(); // a later topology/chunk lifecycle event makes the seed retryable
        assertFalse(work.isSaturated(saturatedSeed));
        assertEquals(saturatedSeed, work.startNext());
    }

    @Test
    void skyExposureDoesNotEraseGasFromAnExistingFiniteClaim() {
        AtmosphereChunkData data = new AtmosphereChunkData();
        BlockPos savedCell = BlockPos.ZERO;
        GasMixture ambient = GasMixture.breathableAir();
        GasMixture savedGas = new GasMixture(Map.of(GasType.OXYGEN, 7.0), 315.0);
        data.claimFinite(0, 0, 0);
        data.put(0, 0, 0, savedGas, ambient);

        // A newly sky-exposed neighboring cell is only a boundary sink. The claimed cell's
        // identity and gas remain authoritative; this classifier never converts it to exterior.
        var result = new AtmosphereOwnershipSearch(savedCell).step(
                pos -> pos.equals(savedCell) ? AtmosphereOwnershipSearch.ProbeResult.FINITE_CLAIMED
                        : pos.equals(new BlockPos(0, 1, 0)) ? AtmosphereOwnershipSearch.ProbeResult.OPEN_SKY_SEED
                        : AtmosphereOwnershipSearch.ProbeResult.BLOCKED,
                1);
        assertEquals(AtmosphereOwnershipSearch.Status.FINITE, result.status());
        assertTrue(data.isFiniteClaimed(0, 0, 0));
        assertEquals(savedGas.gasMoles(), data.get(0, 0, 0).gasMoles());
        assertEquals(savedGas.temperatureKelvin(), data.get(0, 0, 0).temperatureKelvin());
    }
}
