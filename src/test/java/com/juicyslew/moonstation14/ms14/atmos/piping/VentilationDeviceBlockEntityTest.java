package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.block.ModBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VentilationDeviceBlockEntityTest {
    @Test
    void appearanceProjectsOnlySupportedVentilationModesAndLayer() {
        VentilationDeviceConfig c = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT);
        var s = c.settings();
        assertEquals("off", VentilationDeviceAppearance.project(VentilationDeviceConfig.Kind.VENT, s, false, true));
        assertEquals("vent_out", VentilationDeviceAppearance.project(VentilationDeviceConfig.Kind.VENT, s, true, true));
        assertEquals("off", VentilationDeviceAppearance.project(VentilationDeviceConfig.Kind.VENT, s, true, false));
        var scrub = new VentilationDeviceConfig.Settings(0, true, 0, 0, 0, 0,
                VentilationDeviceConfig.ScrubberMode.SIPHON, java.util.Set.of(), VentilationDeviceConfig.Layer.TERTIARY);
        assertEquals("scrub_purge", VentilationDeviceAppearance.project(VentilationDeviceConfig.Kind.SCRUBBER, scrub, true, true));
        assertEquals(2, VentilationDeviceAppearance.layer(scrub));
    }

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
        var type = VentilationDeviceBlockEntity.class;
        assertTrue(java.util.Arrays.stream(type.getDeclaredFields()).noneMatch(field ->
                exposesGasAuthority(field.getType())), "device fields must not own physical gas state");
        var cadence = assertDoesNotThrow(() -> type.getDeclaredField("gasCadence"));
        assertTrue(java.lang.reflect.Modifier.isPrivate(cadence.getModifiers()));
        var accessor = assertDoesNotThrow(() -> type.getDeclaredMethod("gasCadence"));
        assertFalse(java.lang.reflect.Modifier.isPublic(accessor.getModifiers()));
        for (var method : type.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertFalse(exposesGasAuthority(method.getReturnType()),
                    () -> "public API exposes physical gas authority: " + method);
            for (var parameter : method.getParameterTypes())
                assertFalse(exposesGasAuthority(parameter),
                        () -> "public API accepts physical gas authority: " + method);
        }
    }

    private static boolean exposesGasAuthority(Class<?> type) {
        return GasMixture.class.isAssignableFrom(type) || PipeGasInventory.class.isAssignableFrom(type)
                || PipeGasInventorySavedData.class.isAssignableFrom(type)
                || PipeGasTransaction.Endpoint.class.isAssignableFrom(type);
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
        }
    }

    @Test
    void verticalMountCapabilityIsConsumedForBothAxes() {
        var disabled = new com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.FixtureGeometry(.75, .75, .125, true, false);
        for (Direction face : Direction.values())
            assertEquals(face.getAxis().isHorizontal(), FunctionalVentilationDevicePlacement.allowsMount(disabled, face));
    }
}
