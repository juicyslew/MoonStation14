package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization.PortSeed;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.function.Consumer;

/** Native loaded-world smoke coverage; callback allocation is injected, not an APC-network billing integration test. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "atmos-device-work", "atmos-thermostat"})
public final class AtmosDeviceWorkGameTests {
    // GasMixture stores species quantities as floats; tolerate a few ULPs while remaining far below this test's expected transfer.
    private static final double FILTER_MOLE_TOLERANCE = 1e-6;
    private AtmosDeviceWorkGameTests() { }

    private static void withInheritedFilterDefaults(AtmosMachineBlockEntity machine, GasType gas,
                                                     double rate, boolean enabled, Runnable work, Runnable cleanup) {
        PrototypeManager manager = PrototypeRuntime.serverManager();
        var originalCatalogs = manager.snapshot();
        var originalAtmos = manager.snapshot(ModAtmosMachines.TYPE).asMap();
        var saved = manager.encodePublishedCatalogs();
        var savedAtmos = saved.get(ModAtmosMachines.TYPE.typeId());
        ResourceLocation baseId = ModAtmosMachines.id("test_filter_defaults_base");
        var original = savedAtmos.get(ModAtmosMachines.id("gas_filter"));
        var originalProfile = AtmosMachineProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, original).getOrThrow();
        if (originalProfile.filter().gas().isPresent() || originalProfile.filter().enabled())
            throw new IllegalStateException("global gas-filter defaults unexpectedly changed");
        JsonObject parent = original.deepCopy();
        parent.addProperty("id", baseId.toString());
        parent.addProperty("abstract", true);
        if (gas == null) parent.getAsJsonObject("filter").remove("gas");
        else parent.getAsJsonObject("filter").addProperty("gas", gas.name().toLowerCase(java.util.Locale.ROOT));
        parent.getAsJsonObject("filter").addProperty("transferRate", rate);
        parent.getAsJsonObject("filter").addProperty("enabled", enabled);
        JsonObject child = new JsonObject();
        child.addProperty("id", ModAtmosMachines.id("gas_filter").toString());
        child.addProperty("parent", baseId.toString());
        var raw = new java.util.LinkedHashMap<ResourceLocation, JsonObject>();
        savedAtmos.forEach((id, value) -> raw.put(id, value.deepCopy()));
        raw.put(baseId, parent);
        raw.put(ModAtmosMachines.id("gas_filter"), child);
        PrototypeManager resolver = new PrototypeManager();
        resolver.register(ModAtmosMachines.TYPE);
        resolver.reload(ModAtmosMachines.TYPE, raw);
        AtmosMachineProfile resolvedReplacement = resolver.snapshot(ModAtmosMachines.TYPE)
                .get(ModAtmosMachines.id("gas_filter"));
        var candidate = new java.util.LinkedHashMap<>(originalAtmos);
        candidate.put(ModAtmosMachines.id("gas_filter"), resolvedReplacement);
        try {
            manager.publishDecoded(ModAtmosMachines.TYPE, candidate);
            var resolved = machine.resolvedProfile();
            if (resolved.kind() != AtmosMachineProfile.Kind.FILTER || resolved.filter().gas().orElse(null) != gas
                    || resolved.filter().transferRate() != rate || resolved.filter().enabled() != enabled)
                throw new IllegalStateException("inherited filter defaults did not resolve");
            var settings = machine.settingsSnapshot();
            var defaults = AtmosMachineSettings.defaults(resolved);
            if (!settings.equals(defaults) || settings.revision() != 0
                    || settings.filterGas().orElse(null) != gas
                    || settings.transferRateM3PerSecond().orElse(Double.NaN) != rate / 1000d
                    || settings.enabled().orElse(!enabled) != enabled)
                throw new IllegalStateException("initialized filter settings differ from inherited profile defaults");
            work.run();
        } catch (RuntimeException | Error failure) {
            try { cleanup.run(); } catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            try { restoreAtmosCatalog(manager, originalCatalogs, originalAtmos); }
            catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
            throw failure;
        }
        try { cleanup.run(); }
        finally {
            restoreAtmosCatalog(manager, originalCatalogs, originalAtmos);
        }
    }

    private static void restoreAtmosCatalog(PrototypeManager manager,
            Map<ResourceLocation, com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<?>> originalCatalogs,
            Map<ResourceLocation, AtmosMachineProfile> originalAtmos) {
        manager.publishDecoded(ModAtmosMachines.TYPE, originalAtmos);
        var restoredCatalogs = manager.snapshot();
        if (!restoredCatalogs.keySet().equals(originalCatalogs.keySet()))
            throw new IllegalStateException("prototype catalog types were not restored");
        for (var typeId : originalCatalogs.keySet()) {
            var before = originalCatalogs.get(typeId);
            var after = restoredCatalogs.get(typeId);
            if (typeId.equals(ModAtmosMachines.TYPE.typeId())) {
                var beforeAtmos = originalAtmos;
                var afterAtmos = manager.snapshot(ModAtmosMachines.TYPE).asMap();
                if (!beforeAtmos.keySet().equals(afterAtmos.keySet()))
                    throw new IllegalStateException("atmos prototype catalog keys were not restored");
                for (var id : beforeAtmos.keySet()) if (beforeAtmos.get(id) != afterAtmos.get(id))
                    throw new IllegalStateException("atmos prototype entry identity was not restored: " + id);
            } else if (before != after) {
                throw new IllegalStateException("unrelated prototype catalog identity changed: " + typeId);
            }
        }
        var restored = manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_filter"));
        if (restored != originalAtmos.get(ModAtmosMachines.id("gas_filter"))
                || restored.filter().gas().isPresent() || restored.filter().enabled())
            throw new IllegalStateException("global gas-filter defaults were not restored");
    }

    private static GasMixture portGas(ServerLevel level, BlockPos pos, String id) {
        var entry = LoadedDevicePortGasOwner.activeSnapshot(level, pos, id).orElseThrow();
        return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().nodeShare(entry.node());
    }

    private record FilterCase(ServerLevel level, AtmosMachineBlockEntity machine, BlockPos pos) { }

    private static void seedFilterPorts(ServerLevel level, BlockPos pos, AtmosMachineBlockEntity machine) {
        var inlet = machine.gasPorts().stream().filter(port -> port.id().equals("inlet")).findFirst().orElseThrow();
        var seeds = java.util.List.of(new PortSeed(pos, inlet.id(), inlet.incarnation(),
                new GasMixture(Map.of(GasType.OXYGEN, 3d, GasType.NITROGEN, 3d), 293.15, inlet.volume())));
        var initialized = TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level, seeds);
        if (!initialized.successful())
            throw new IllegalStateException("filter inlet gas initialization failed: " + initialized.failure() + " " + initialized.detail());
        var snapshot = PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        var ports = machine.gasPorts();
        var inletNetwork = snapshot.network(inlet.key(pos)).orElseThrow();
        double inletVolume = snapshot.get(inlet.key(pos)).orElseThrow().volume();
        for (var port : ports) {
            var entry = snapshot.get(port.key(pos)).orElseThrow();
            var network = snapshot.network(port.key(pos)).orElseThrow();
            double expectedVolume = (double) (float) entry.volume();
            if (Double.compare(network.mixture().volumeCubicMeters(), expectedVolume) != 0)
                throw new IllegalStateException("filter port network volume differs from canonical port volume: " + port.id());
            if (!port.id().equals("inlet")) {
                if (network == inletNetwork || network.mixture().totalMoles() != 0
                        || Double.compare(network.mixture().temperatureKelvin(), (double) (float) 293.15) != 0)
                    throw new IllegalStateException("filter vacuum output did not retain a distinct canonical-volume network: " + port.id());
                for (var other : ports) {
                    if (!other.id().equals(port.id()) && snapshot.network(other.key(pos)).orElseThrow() == network)
                        throw new IllegalStateException("filter output ports unexpectedly share a network: " + port.id());
                }
            }
        }
        if (Double.compare(inletNetwork.mixture().volumeCubicMeters(), (double) (float) inletVolume) != 0)
            throw new IllegalStateException("filter inlet network volume differs from canonical inlet volume");
    }

    private static void runSeededFilterCase(GameTestHelper helper, GasType selected, boolean enabled,
                                            Consumer<FilterCase> assertion) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        var block = ModBlocks.GAS_FILTER.get();
        helper.setBlock(local, block.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || !machine.profileIdentity().equals("gas_filter"))
                    throw new IllegalStateException("registered filter host missing");
                seedFilterPorts(level, pos, machine);
                helper.runAfterDelay(15, () -> {
                    try {
                        withInheritedFilterDefaults(machine, selected, 1, enabled,
                                () -> assertion.accept(new FilterCase(level, machine, pos)), () -> {
                                    if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                                    lease.close();
                                });
                        helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        helper.succeed();
                    } catch (RuntimeException | Error failure) {
                        if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 120)
    public static void registeredHeaterConsumesOneInjectedPaidCallbackInterval(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        var block = ModBlocks.GAS_THERMO_HEATER.get();
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 90, () -> true,
                "registered heater host footprint entity-ticking", () -> {
        helper.setBlock(local, block.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || !machine.profileIdentity().equals("gas_thermo_heater"))
                    throw new IllegalStateException("registered heater host missing");
                var port = machine.gasPorts().stream().filter(p -> p.id().equals("pipe")).findFirst().orElseThrow();
                machine.setControl("enabled", true);
                var key = port.key(pos);
                var saved = PipeGasInventorySavedData.forMutation(level);
                var before = saved.snapshot();
                var entry = before.get(key).orElseThrow();
                GasMixture seeded = new GasMixture(Map.of(GasType.OXYGEN, 1d), 280, port.volume());
                var seededInventory = before.withMixture(key, entry.incarnation(), entry.version(), seeded);
                if (!saved.replace(before.revision(), before, seededInventory))
                    throw new IllegalStateException("finite heater port gas seed CAS failed");

                double demand = machine.apcPowerDemandWatts();
                double paidBefore = machine.committedPaidJoules();
                double thermalBefore = machine.committedThermalJoules();
                double movedBefore = machine.committedMovedMoles();
                long eventsBefore = machine.committedWorkEvents();
                helper.runAfterDelay(24, () -> {
                    try {
                        long completed = PowerRuntime.currentSampleTick(level);
                        if (completed <= 12) throw new IllegalStateException("native power runtime sample clock did not advance");
                        // A full resolved 0.6 s event needs its complete 12-tick actual allocation.
                        // This exercises the production callback/tracker boundary, not APC-network delivery.
                        machine.receiveApcPowerAllocation(demand, true, completed - 12);
                        machine.receiveApcPowerAllocation(demand, true, completed);
                        var state = AtmosDeviceRuntime.tick(level, machine, AtmosDeviceRuntime.State.initial(), level.getGameTime());
                        var afterFirstInventory = saved.snapshot();
                        var afterFirstEntry = afterFirstInventory.get(key).orElseThrow();
                        var afterFirst = afterFirstInventory.mixture(key);
                        if (state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.COMMITTED
                                || state.lastThermalJoules() <= 0 || afterFirst.temperatureKelvin() <= seeded.temperatureKelvin())
                            throw new IllegalStateException("paid live heater step did not heat its registered finite gas port");
                        double expectedPaidJoules = machine.resolvedProfile().thermostat().inputPowerWatts()
                                * machine.resolvedProfile().simulation().deviceIntervalSeconds();
                        if (Double.compare(machine.committedPaidJoules() - paidBefore, expectedPaidJoules) != 0
                                || !(machine.committedThermalJoules() - thermalBefore > 0)
                                || machine.committedMovedMoles() != movedBefore
                                || machine.committedWorkEvents() != eventsBefore + 1)
                            throw new IllegalStateException("heater committed receipt differs from one paid thermal-only event: paid="
                                    + (machine.committedPaidJoules() - paidBefore) + ", expected=" + expectedPaidJoules
                                    + ", thermal=" + (machine.committedThermalJoules() - thermalBefore)
                                    + ", moved=" + (machine.committedMovedMoles() - movedBefore)
                                    + ", events=" + (machine.committedWorkEvents() - eventsBefore));
                        var firstInventory = saved.snapshot();
                        long firstRevision = firstInventory.revision();
                        long firstGasRevision = firstInventory.network(key).orElseThrow().gasRevision();
                        var repeated = AtmosDeviceRuntime.tick(level, machine, state, level.getGameTime());
                        var afterRepeatInventory = saved.snapshot();
                        var afterRepeatEntry = afterRepeatInventory.get(key).orElseThrow();
                        var afterRepeat = afterRepeatInventory.mixture(key);
                        var leftoverPayment = machine.consumePaidPowerInterval();
                        if (Double.compare(afterFirst.temperatureKelvin(), afterRepeat.temperatureKelvin()) != 0
                                || Double.compare(afterFirst.thermalEnergy(), afterRepeat.thermalEnergy()) != 0
                                || Double.compare(afterFirst.volumeCubicMeters(), afterRepeat.volumeCubicMeters()) != 0
                                || !afterFirst.gasMoles().equals(afterRepeat.gasMoles())
                                || firstRevision != afterRepeatInventory.revision()
                                || firstGasRevision != afterRepeatInventory.network(key).orElseThrow().gasRevision() || leftoverPayment != null
                                || Double.compare(machine.committedPaidJoules() - paidBefore, expectedPaidJoules) != 0
                                || Double.compare(machine.committedThermalJoules() - thermalBefore,
                                        state.lastThermalJoules()) != 0
                                || machine.committedMovedMoles() != movedBefore
                                || machine.committedWorkEvents() != eventsBefore + 1) {
                            throw new IllegalStateException("repeat heater callback changed gas or inventory/payment state: energy="
                                    + afterFirst.thermalEnergy() + "->" + afterRepeat.thermalEnergy()
                                    + ", revision=" + firstRevision + "->" + afterRepeatInventory.revision()
                                    + ", gasRevision=" + firstGasRevision + "->" + afterRepeatInventory.network(key).orElseThrow().gasRevision()
                                    + ", leftoverPayment=" + (leftoverPayment != null));
                        }
                        helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        helper.succeed();
                    } catch (RuntimeException | Error failure) {
                        if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
        }, pos);
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void registeredFilterSeparatesGasWithoutApcPower(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos local = new BlockPos(2, 1, 2);
        BlockPos pos = helper.absolutePos(local);
        var lease = FixtureChunkTickets.around(helper, pos, pos, 1);
        var block = ModBlocks.GAS_FILTER.get();
        helper.setBlock(local, block.defaultBlockState());
        helper.runAfterDelay(3, () -> {
            try {
                if (!(level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine)
                        || !machine.profileIdentity().equals("gas_filter"))
                    throw new IllegalStateException("registered filter host missing");
                machine.setControl("enabled", true);
                if (!machine.applySettings(AtmosMachineSettings.Operation.SET_FILTER_GAS,
                        new AtmosMachineSettings.GasValue(GasType.OXYGEN)))
                    throw new IllegalStateException("filter gas setting was rejected");
                if (!machine.applySettings(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,
                        new AtmosMachineSettings.NumericValue(0.001)))
                    throw new IllegalStateException("filter transfer-rate setting was rejected");
                seedFilterPorts(level, pos, machine);
                helper.runAfterDelay(15, () -> {
                    try {
                        AtmosMachineBlockEntity.tick(level, machine);
                        var state = machine.deviceRuntimeState();
                        var inlet = portGas(level, pos, "inlet");
                        var filtered = portGas(level, pos, "filter");
                        var outlet = portGas(level, pos, "outlet");
                        if (state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.COMMITTED
                                || filtered.moles(GasType.OXYGEN) <= 0 || outlet.moles(GasType.NITROGEN) <= 0
                                || outlet.moles(GasType.OXYGEN) != 0 || inlet.totalMoles() >= 6)
                            throw new IllegalStateException("unpowered live filter did not separate its selected gas");
                        if (!machine.ambientEligible()
                                || !machine.getUpdateTag(level.registryAccess()).getBoolean("AmbientEligible")
                                || machine.saveWithoutMetadata(level.registryAccess()).contains("AmbientEligible"))
                            throw new IllegalStateException("accepted real filter transfer was not published as ambient-eligible");
                        var decoded = new AtmosMachineBlockEntity(pos, level.getBlockState(pos));
                        decoded.setLevel(level);
                        decoded.loadWithComponents(machine.getUpdateTag(level.registryAccess()), level.registryAccess());
                        if (!decoded.ambientEligible() || decoded.resolvedAmbientAudio() == null)
                            throw new IllegalStateException("native BE payload decode lost ambient eligibility/cue");
                        if (!machine.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,
                                new AtmosMachineSettings.BooleanValue(false)) || machine.ambientEligible()
                                || machine.getUpdateTag(level.registryAccess()).getBoolean("AmbientEligible"))
                            throw new IllegalStateException("filter disable did not clear/publish ambient eligibility");
                        helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        helper.succeed();
                    } catch (RuntimeException | Error failure) {
                        if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                        lease.close();
                        throw failure;
                    }
                });
            } catch (RuntimeException | Error failure) {
                if (level.getBlockState(pos).getBlock() == block) helper.setBlock(local, Blocks.AIR);
                lease.close();
                throw failure;
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void inheritedSelectedFilterDefaultsDriveRateLimitedTransfer(GameTestHelper helper) {
        runSeededFilterCase(helper, GasType.NITROGEN, true, test -> {
            var state = AtmosDeviceRuntime.tick(test.level(), test.machine(), AtmosDeviceRuntime.State.initial(), test.level().getGameTime());
            var inlet = portGas(test.level(), test.pos(), "inlet");
            var filtered = portGas(test.level(), test.pos(), "filter");
            var outlet = portGas(test.level(), test.pos(), "outlet");
            if (state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.COMMITTED
                    || filtered.moles(GasType.NITROGEN) <= 0 || outlet.moles(GasType.OXYGEN) <= 0
                    || outlet.moles(GasType.NITROGEN) != 0 || inlet.totalMoles() >= 6)
                throw new IllegalStateException("inherited selected-gas filter did not separate gas");
            var profile = test.machine().resolvedProfile();
            var inletPort = test.machine().gasPorts().stream().filter(p -> p.id().equals("inlet")).findFirst().orElseThrow();
            double expectedTotal = 6 * (0.001 * profile.simulation().pumpSpeedup()
                    * profile.simulation().deviceIntervalSeconds() / inletPort.volume());
            double observedTotal = filtered.moles(GasType.NITROGEN) + (3 - inlet.moles(GasType.NITROGEN));
            double expectedPerGas = expectedTotal / 2;
            if (Math.abs(filtered.moles(GasType.NITROGEN) - expectedPerGas) > FILTER_MOLE_TOLERANCE
                    || Math.abs(outlet.moles(GasType.OXYGEN) - expectedPerGas) > FILTER_MOLE_TOLERANCE
                    || Math.abs(observedTotal - expectedTotal) > FILTER_MOLE_TOLERANCE
                    || Math.abs(state.lastMoles() - expectedTotal) > FILTER_MOLE_TOLERANCE
                    || Math.abs(inlet.moles(GasType.NITROGEN) + filtered.moles(GasType.NITROGEN) - 3) > FILTER_MOLE_TOLERANCE
                    || Math.abs(inlet.moles(GasType.OXYGEN) + outlet.moles(GasType.OXYGEN) - 3) > FILTER_MOLE_TOLERANCE)
                throw new IllegalStateException("filter transfer differs from literal 1 L/s cadence bound: expected="
                        + expectedTotal + ", observed=" + observedTotal);
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void inheritedEnabledFilterWithoutSelectionPassesEveryGasToOutlet(GameTestHelper helper) {
        runSeededFilterCase(helper, null, true, test -> {
            var state = AtmosDeviceRuntime.tick(test.level(), test.machine(), AtmosDeviceRuntime.State.initial(), test.level().getGameTime());
            var inlet = portGas(test.level(), test.pos(), "inlet");
            var filtered = portGas(test.level(), test.pos(), "filter");
            var outlet = portGas(test.level(), test.pos(), "outlet");
            if (state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.COMMITTED
                    || filtered.totalMoles() != 0 || outlet.moles(GasType.OXYGEN) <= 0
                    || outlet.moles(GasType.NITROGEN) <= 0 || inlet.totalMoles() >= 6
                    || Math.abs(inlet.totalMoles() + outlet.totalMoles() - 6) > FILTER_MOLE_TOLERANCE
                    || Math.abs(inlet.moles(GasType.OXYGEN) + outlet.moles(GasType.OXYGEN) - 3) > FILTER_MOLE_TOLERANCE
                    || Math.abs(inlet.moles(GasType.NITROGEN) + outlet.moles(GasType.NITROGEN) - 3) > FILTER_MOLE_TOLERANCE)
                throw new IllegalStateException("enabled filter without selected gas did not pass all inlet gases to outlet");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "atmos_large_empty", timeoutTicks = 80)
    public static void inheritedDisabledFilterDoesNotMoveGas(GameTestHelper helper) {
        runSeededFilterCase(helper, GasType.NITROGEN, false, test -> {
            var state = AtmosDeviceRuntime.tick(test.level(), test.machine(), AtmosDeviceRuntime.State.initial(), test.level().getGameTime());
            var inlet = portGas(test.level(), test.pos(), "inlet");
            var filtered = portGas(test.level(), test.pos(), "filter");
            var outlet = portGas(test.level(), test.pos(), "outlet");
            if (state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.IDLE
                    && state.lastWorkStatus() != AtmosDeviceRuntime.WorkStatus.PASSIVE_EQUALIZATION)
                throw new IllegalStateException("unexpected disabled-filter state: " + state.lastWorkStatus());
            if (inlet.moles(GasType.OXYGEN) != 3 || inlet.moles(GasType.NITROGEN) != 3
                    || filtered.totalMoles() != 0 || outlet.totalMoles() != 0)
                throw new IllegalStateException("inherited disabled filter performed an operation");
        });
    }
}
