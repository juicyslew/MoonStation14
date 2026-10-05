package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.block.ModBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VentilationDeviceBlockEntityTest {
    @Test
    void settingsRoundTripAndLegacyDefaultsAreKindSpecific() {
        VentilationDeviceConfig original = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        assertTrue(original.compareAndSet(0, original.settings()));
        VentilationDeviceConfig loaded = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        assertTrue(loaded.restore(original.save()));
        assertEquals(original.settings(), loaded.settings());

        VentilationDeviceConfig legacy = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        assertTrue(legacy.restore(new CompoundTag()));
        assertEquals(VentilationDeviceConfig.Kind.VENT, legacy.kind());
    }

    @Test
    void malformedSettingsResetDisabled() {
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("Version", 99);
        VentilationDeviceConfig config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        assertFalse(config.restore(malformed));
        assertFalse(config.settings().configuredEnabled());
        var attemptedEnable = new VentilationDeviceConfig.Settings(0, true, 0, 0, 0, 0,
                VentilationDeviceConfig.ScrubberMode.FILTER, java.util.Set.of());
        assertFalse(config.compareAndSet(0, attemptedEnable));
        assertFalse(config.settings().configuredEnabled());
        assertFalse(config.settingsValid());
        assertEquals(5.0, config.receiverDemandWatts());
    }

    @Test
    void malformedNumericFilterAndLayerDataFailClosed() {
        for (int invalid : new int[] {0, 1, 2, 3}) {
            VentilationDeviceConfig config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
            CompoundTag tag = config.save();
            if (invalid == 0) tag.putLong("Revision", -1);
            if (invalid == 1) tag.putDouble("VolumeRate", Double.NaN);
            if (invalid == 2) tag.putString("Filters", "not-a-gas");
            if (invalid == 3) tag.putString("Layer", "INVALID");
            assertFalse(config.restore(tag));
            assertFalse(config.settingsValid());
            assertFalse(config.settings().configuredEnabled());
            assertFalse(config.compareAndSet(0, new VentilationDeviceConfig.Settings(0, true, 0, 0, 0, 0,
                    VentilationDeviceConfig.ScrubberMode.FILTER, java.util.Set.of())));
            assertFalse(config.settings().configuredEnabled());
            assertEquals(5.0, config.receiverDemandWatts());
        }
    }

    @Test
    void migratesV1AndPersistsExplicitV2Layer() {
        VentilationDeviceConfig config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        CompoundTag legacy = config.save();
        legacy.putInt("Version", 1);
        legacy.remove("Layer");
        assertTrue(config.restore(legacy));
        assertEquals(VentilationDeviceConfig.Layer.PRIMARY, config.settings().selectedLayer());
        assertEquals(Ss14VentilationDefaults.SCRUBBER_VOLUME_RATE_M3_PER_SECOND, config.settings().volumeRateM3PerSecond());

        var s = config.settings();
        var secondary = new VentilationDeviceConfig.Settings(s.revision(), s.configuredEnabled(), s.externalPressureBoundKPa(),
                s.targetPressureChangeKPaPerSecond(), s.targetPressureKPa(), s.volumeRateM3PerSecond(),
                s.scrubberMode(), s.filteredGases(), VentilationDeviceConfig.Layer.SECONDARY);
        assertTrue(config.compareAndSet(s.revision(), secondary));
        CompoundTag saved = config.save();
        assertEquals(2, saved.getInt("Version"));
        assertEquals("SECONDARY", saved.getString("Layer"));
        VentilationDeviceConfig reloaded = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER);
        assertTrue(reloaded.restore(saved));
        assertEquals(VentilationDeviceConfig.Layer.SECONDARY, reloaded.settings().selectedLayer());
    }

    @Test
    void unknownPersistedLayerFailsClosed() {
        VentilationDeviceConfig config = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        CompoundTag invalid = config.save();
        invalid.putString("Layer", "MAGENTA");
        assertFalse(config.restore(invalid));
        assertFalse(config.settings().configuredEnabled());
    }

    @Test
    void deviceConfigHasNoGasInventoryOrMutationApi() {
        assertTrue(java.util.Arrays.stream(VentilationDeviceBlockEntity.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getSimpleName().toLowerCase().contains("gas")));
    }

    @Test
    void roomAndPipePortsAreDistinctForEveryHorizontalFacing() {
        BlockPos origin = new BlockPos(4, 10, -2);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos room = origin.relative(facing);
            BlockPos pipe = origin.relative(facing.getOpposite());

            assertNotEquals(room, pipe);
            VentilationDeviceBlockEntity entity = new VentilationDeviceBlockEntity(origin,
                    ModBlocks.GAS_VENT.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, facing));
            assertEquals(room, origin.relative(entity.roomFacePos()));
            assertEquals(pipe, entity.pipePortPos());
            assertTrue(java.util.Arrays.stream(VentilationDeviceBlockEntity.class.getDeclaredMethods())
                    .noneMatch(method -> method.getName().toLowerCase().contains("gas")
                            || method.getName().toLowerCase().contains("air")
                            || method.getName().toLowerCase().contains("block")));
        }
    }

    @Test
    void verticalMountCapabilityIsConsumedForBothAxes() {
        var disabled = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.FixtureGeometry(.75, .75, .125, true, false);
        for (Direction face : Direction.values())
            assertEquals(face.getAxis().isHorizontal(), FunctionalVentilationDevicePlacement.allowsMount(disabled, face));
    }
}
