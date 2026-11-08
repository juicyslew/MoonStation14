package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere", "atmos-machine-cadence", "atmos-machinery-runtime"})
public final class AtmosMachineCadenceGameTests {
    private AtmosMachineCadenceGameTests() { }

    @GameTest(template = "empty", batch = "atmos_machine_cadence_isolated", timeoutTicks = 500, templateNamespace = "moonstation14")
    public static void registeredHeaterCompletesFivePaidEventsDuringSteadyThreeSeconds(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Fixture fixture = new Fixture(helper, level);
        fixture.install();
        helper.runAfterDelay(3, () -> {
            try {
                var host = fixture.machine();
                host.setControl("enabled", true);
                if (!host.applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,
                        new AtmosMachineSettings.NumericValue(593.15)))
                    fixture.fail("resolved heater rejected its bounded live thermostat target");
                fixture.seedFinitePort();
                var cable = new CableFaceNode(fixture.apcCableHost(), Direction.WEST, CableTier.APC);
                var electricalPort = host.powerRuntimePorts().stream()
                        .filter(port -> port.tier() == CableTier.APC).findFirst().orElse(null);
                if (!PowerTopology.deviceAdjacent(electricalPort, cable))
                    fixture.fail("heater APC port must oppose the owned cable on its actual host face");
                PowerFixtureReadiness.await(helper, level, 220,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, cable)
                                && PowerRuntime.apcPowerMeter(level, host).known()
                                && PowerRuntime.apcPowerMeter(level, host).deliveredWatts() >= 5000
                                && host.apcAllocationKnown()
                                && host.apcAllocatedWatts() >= host.resolvedProfile().configuredReceiverLoadWatts(),
                        () -> "heaterApcCallback={known=" + host.apcAllocationKnown()
                                + ", watts=" + host.apcAllocatedWatts() + ", sample=" + host.apcSampleTick()
                                + ", demand=" + host.resolvedProfile().configuredReceiverLoadWatts()
                                + "}, APCport=" + PowerGraphService.state(level, cable)
                                + ", diagnostic=" + PowerRuntime.lastSolveDiagnostic(level)
                                + ", graph=" + PowerGraphService.diagnosticSnapshot(level),
                        () -> fixture.awaitFirstAcceptedHeat(host));
            } catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", batch = "atmos_machine_cadence_isolated", timeoutTicks = 600, templateNamespace = "moonstation14")
    public static void registeredFreezerCoolsFiniteGasUsingPaidRuntime(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Fixture fixture = new Fixture(helper, level, true);
        fixture.install();
        helper.runAfterDelay(3, () -> {
            try {
                var host = fixture.machine();
                host.setControl("enabled", true);
                if (!host.applySettings(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,
                        new AtmosMachineSettings.NumericValue(250)))
                    fixture.fail("resolved freezer rejected its bounded live thermostat target");
                fixture.seedFinitePort();
                var cable = new CableFaceNode(fixture.apcCableHost(), Direction.WEST, CableTier.APC);
                var electricalPort = host.powerRuntimePorts().stream().filter(port -> port.tier() == CableTier.APC).findFirst().orElse(null);
                if (!PowerTopology.deviceAdjacent(electricalPort, cable))
                    fixture.fail("freezer APC port must oppose fixture cable on its actual host face");
                var seededPipe = fixture.entry(AtmosMachineProfile.PortName.PIPE);
                if (fixture.gasRevision(seededPipe) != fixture.seededVersion
                        || fixture.gas(seededPipe).moles(GasType.NITROGEN) != 100d
                        || fixture.gas(seededPipe).temperatureKelvin() != 300d)
                    fixture.fail("freezer fixture must begin with its seeded finite 100 mol, 300 K PIPE mixture");
                PowerFixtureReadiness.await(helper, level, 220,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, cable)
                                && validAllocation(level, host),
                        () -> "freezer allocation=" + host.apcAllocatedWatts() + ", known=" + host.apcAllocationKnown(),
                        () -> pollFirstCooling(fixture, host, fixture.seededVersion, fixture.seededEnergy, 0));
            } catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", batch = "atmos_machine_cadence_isolated", timeoutTicks = 300, templateNamespace = "moonstation14")
    public static void registeredPressurePumpMovesFiniteGasUntilTargetPressure(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Fixture fixture = new Fixture(helper, level, ModBlocks.GAS_PRESSURE_PUMP.get());
        fixture.install();
        helper.runAfterDelay(3, () -> {
            try {
                var host = fixture.machine();
                host.setControl("enabled", true);
                if (!host.applySettings(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                        new AtmosMachineSettings.NumericValue(101.325)))
                    fixture.fail("resolved pressure pump rejected target pressure");
                fixture.seedFinitePort(AtmosMachineProfile.PortName.INLET,
                        new GasMixture(Map.of(GasType.OXYGEN, 100d), 300, fixture.port(AtmosMachineProfile.PortName.INLET).volumeM3()));
                UUID inletIncarnation = fixture.entry(AtmosMachineProfile.PortName.INLET).incarnation();
                UUID outletIncarnation = fixture.entry(AtmosMachineProfile.PortName.OUTLET).incarnation();
                var cable = new CableFaceNode(fixture.apcCableHost(), Direction.WEST, CableTier.APC);
                PowerFixtureReadiness.await(helper, level, 220,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, cable)
                                && host.apcAllocationKnown() && host.apcAllocatedWatts() >= 200
                                && PowerRuntime.apcPowerMeter(level, host).known()
                                && PowerRuntime.apcPowerMeter(level, host).deliveredWatts() >= 200,
                        () -> "pressure pump allocation=" + host.apcAllocatedWatts(),
                        () -> pollPressurePump(fixture, host, fixture.seededVersion, inletIncarnation, outletIncarnation, 0));
            } catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        });
    }

    @GameTest(template = "empty", batch = "atmos_machine_cadence_isolated", timeoutTicks = 600, templateNamespace = "moonstation14")
    public static void registeredVolumePumpTransfersFiniteGasWhenPaidAndStopsWhenDisabled(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        Fixture fixture = new Fixture(helper, level, ModBlocks.GAS_VOLUME_PUMP.get());
        fixture.install();
        helper.runAfterDelay(3, () -> {
            try {
                var host = fixture.machine();
                host.setControl("enabled", true);
                if (!host.applySettings(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,
                         new AtmosMachineSettings.NumericValue(.001)))
                    fixture.fail("resolved volume pump rejected its bounded 1 L/s transfer rate");
                fixture.seedFinitePort(AtmosMachineProfile.PortName.INLET,
                        new GasMixture(Map.of(GasType.OXYGEN, 100d), 300, fixture.port(AtmosMachineProfile.PortName.INLET).volumeM3()));
                fixture.seedFinitePort(AtmosMachineProfile.PortName.OUTLET,
                        new GasMixture(Map.of(), 2.5, fixture.port(AtmosMachineProfile.PortName.OUTLET).volumeM3()));
                var cable = new CableFaceNode(fixture.apcCableHost(), Direction.WEST, CableTier.APC);
                var electricalPort = host.powerRuntimePorts().stream().filter(port -> port.tier() == CableTier.APC).findFirst().orElse(null);
                if (!PowerTopology.deviceAdjacent(electricalPort, cable))
                    fixture.fail("volume pump APC port must oppose fixture cable on its actual host face");
                UUID inletIncarnation = fixture.entry(AtmosMachineProfile.PortName.INLET).incarnation();
                UUID outletIncarnation = fixture.entry(AtmosMachineProfile.PortName.OUTLET).incarnation();
                PowerFixtureReadiness.await(helper, level, 220,
                        () -> PowerFixtureReadiness.topologyReadyWithKnownPorts(level, cable)
                                && host.apcAllocationKnown() && host.apcAllocatedWatts() >= 200
                                && PowerRuntime.apcPowerMeter(level, host).known()
                                && PowerRuntime.apcPowerMeter(level, host).deliveredWatts() >= 200,
                        () -> "volume pump allocation=" + host.apcAllocatedWatts(),
                         () -> pollVolumePump(fixture, host, inletIncarnation, outletIncarnation, 0));
            } catch (RuntimeException | Error failure) {
                fixture.cleanup();
                throw failure;
            }
        });
    }

    private static void pollVolumePump(Fixture fixture, AtmosMachineBlockEntity host, UUID inletIncarnation, UUID outletIncarnation, int waited) {
        var inlet = fixture.entry(AtmosMachineProfile.PortName.INLET);
        var outlet = fixture.entry(AtmosMachineProfile.PortName.OUTLET);
        if (!inlet.incarnation().equals(inletIncarnation) || !outlet.incarnation().equals(outletIncarnation))
            fixture.fail("volume pump port incarnation changed during accounting");
        if (fixture.gas(outlet).totalMoles() > 0) {
            double sourceOxygen = fixture.gas(inlet).moles(GasType.OXYGEN);
            double outputOxygen = fixture.gas(outlet).moles(GasType.OXYGEN);
            if (sourceOxygen >= 100 || !PipeGasLedger.conservedSpecies(100, sourceOxygen + outputOxygen))
                fixture.fail("paid volume pump must move finite oxygen without creating or losing it");
            double movedFraction = (100 - sourceOxygen) / 100;
            if (!(sourceOxygen > 1) || movedFraction > .1
                    || fixture.gas(inlet).pressureKpa(fixture.gas(inlet).volumeCubicMeters()) <= fixture.machine().resolvedProfile().pump().lowerThresholdKpa().orElseThrow()
                    || fixture.gas(outlet).pressureKpa(fixture.gas(outlet).volumeCubicMeters()) >= fixture.machine().resolvedProfile().pump().higherThresholdKpa().orElseThrow())
                fixture.fail("volume pump must disable with substantial inlet gas and pressure head remaining");
            if (!host.applySettings(AtmosMachineSettings.Operation.SET_ENABLED, new AtmosMachineSettings.BooleanValue(false)))
                fixture.fail("volume pump must accept disable after a paid transfer");
            var disabledInlet = fixture.entry(AtmosMachineProfile.PortName.INLET);
            var disabledOutlet = fixture.entry(AtmosMachineProfile.PortName.OUTLET);
            long inletVersion = fixture.gasRevision(disabledInlet), outletVersion = fixture.gasRevision(disabledOutlet);
            double inletEnergy = fixture.gas(disabledInlet).thermalEnergy(), outletEnergy = fixture.gas(disabledOutlet).thermalEnergy();
            double inletOxygen = fixture.gas(disabledInlet).moles(GasType.OXYGEN), outletOxygen = fixture.gas(disabledOutlet).moles(GasType.OXYGEN);
            fixture.helper.runAfterDelay(60, () -> {
                var afterInlet = fixture.entry(AtmosMachineProfile.PortName.INLET);
                var afterOutlet = fixture.entry(AtmosMachineProfile.PortName.OUTLET);
                if (!afterInlet.incarnation().equals(inletIncarnation) || !afterOutlet.incarnation().equals(outletIncarnation)
                        || fixture.gasRevision(afterInlet) != inletVersion || fixture.gasRevision(afterOutlet) != outletVersion
                        || fixture.gas(afterInlet).thermalEnergy() != inletEnergy || fixture.gas(afterOutlet).thermalEnergy() != outletEnergy
                        || fixture.gas(afterInlet).moles(GasType.OXYGEN) != inletOxygen
                        || fixture.gas(afterOutlet).moles(GasType.OXYGEN) != outletOxygen)
                    fixture.fail("disabled volume pump must stop transfer, energy, and oxygen changes for three cadence intervals");
                fixture.cleanup();
                fixture.helper.succeed();
            });
            return;
        }
        if (waited >= 180) fixture.fail("registered volume pump did not move finite inlet gas");
        fixture.helper.runAfterDelay(1, () -> pollVolumePump(fixture, host, inletIncarnation, outletIncarnation, waited + 1));
    }

    private static void pollPressurePump(Fixture fixture, AtmosMachineBlockEntity host, long inletVersion, UUID inletIncarnation, UUID outletIncarnation, int waited) {
        var inlet = fixture.entry(AtmosMachineProfile.PortName.INLET);
        var outlet = fixture.entry(AtmosMachineProfile.PortName.OUTLET);
        if (!inlet.incarnation().equals(inletIncarnation) || !outlet.incarnation().equals(outletIncarnation))
            fixture.fail("pressure pump port incarnation changed during accounting");
        if (fixture.gas(outlet).totalMoles() > 0) {
            if (fixture.gasRevision(inlet) <= inletVersion || fixture.gas(inlet).moles(GasType.OXYGEN) >= 100
                    || !PipeGasLedger.conservedSpecies(100, fixture.gas(inlet).moles(GasType.OXYGEN) + fixture.gas(outlet).moles(GasType.OXYGEN)))
                fixture.fail("powered pressure pump must transfer finite oxygen without creating or losing it");
            double pressure = fixture.gas(outlet).pressureKpa(fixture.gas(outlet).volumeCubicMeters());
            if (Math.abs(pressure - 101.325) > 1e-3) {
                if (waited >= 180) fixture.fail("registered pressure pump did not reach target within polling bound");
                fixture.helper.runAfterDelay(1, () -> pollPressurePump(fixture, host, inletVersion, inletIncarnation, outletIncarnation, waited + 1));
            } else {
                double completedOxygen = fixture.gas(outlet).moles(GasType.OXYGEN);
                fixture.helper.runAfterDelay(PowerRuntime.TICKS_PER_SOLVE * 3, () -> {
                    var later = fixture.entry(AtmosMachineProfile.PortName.OUTLET);
                    if (!later.incarnation().equals(outletIncarnation)
                            || !fixture.entry(AtmosMachineProfile.PortName.INLET).incarnation().equals(inletIncarnation)
                            || !PipeGasLedger.conservedSpecies(completedOxygen, fixture.gas(later).moles(GasType.OXYGEN))
                            || Math.abs(fixture.gas(later).pressureKpa(fixture.gas(later).volumeCubicMeters()) - 101.325) > 1e-3)
                        fixture.fail("pressure pump target remained unchanged across subsequent cadence intervals");
                    fixture.cleanup();
                    fixture.helper.succeed();
                });
            }
            return;
        }
        if (waited >= 180) fixture.fail("registered pressure pump did not move finite inlet gas");
        fixture.helper.runAfterDelay(1, () -> pollPressurePump(fixture, host, inletVersion, inletIncarnation, outletIncarnation, waited + 1));
    }

    private static void pollFirstCooling(Fixture fixture, AtmosMachineBlockEntity host, long initialVersion,
                                         double initialEnergy, int waited) {
        fixture.trace(host, "freezer-first-wait-" + waited);
        var entry = fixture.entry();
        if(!fixture.gasNetworkId(entry).equals(fixture.seededNetworkId))fixture.fail("freezer pipe network identity changed during paid-work observation");
        if (fixture.gasRevision(entry) > initialVersion) {
            if (!(fixture.gas(entry).temperatureKelvin() < 300)
                    || !(fixture.gas(entry).thermalEnergy() < initialEnergy))
                fixture.fail("registered paid freezer event must lower finite gas temperature and thermal energy");
            if (host.deviceRuntimeState().lastActualPaidInputJoules() <= 0
                    || host.deviceRuntimeState().lastWorkStatus() != com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime.WorkStatus.COMMITTED)
                fixture.fail("committed freezer cooling must account positive paid work");
            fixture.cleanup();
            fixture.helper.succeed();
            return;
        }
        if (waited >= 180) fixture.fail("registered freezer did not produce a first accepted powered cooling event");
        fixture.helper.runAfterDelay(1, () -> pollFirstCooling(fixture, host, initialVersion, initialEnergy, waited + 1));
    }

    /** This probe observes only committed inventory output; the registered BE ticker is the sole consumer. */
    private static void pollFirstHeat(Fixture fixture, AtmosMachineBlockEntity host,
                                       long initialVersion, double initialEnergy, int waited) {
        fixture.trace(host, "first-wait-" + waited);
        long now = PowerRuntime.currentSampleTick(fixture.level);
        var entry = fixture.entry();
        if (!fixture.gasNetworkId(entry).equals(fixture.seededNetworkId))
            fixture.fail("heater pipe network identity changed during paid-work observation");
        if (fixture.gasRevision(entry) > initialVersion) {
            double delta = fixture.gas(entry).thermalEnergy() - initialEnergy;
            if (Math.abs(delta - 2850) > .5)
                fixture.fail("first live heater event must apply exactly one 3000 J paid input at .95 COP; delta=" + delta);
            fixture.awaitSteadyAdmission(host, fixture.level.getGameTime(), host.apcSampleTick(), 0);
            return;
        }
        if (waited >= 180) fixture.fail("registered heater did not produce a first accepted powered event");
        fixture.helper.runAfterDelay(1, () -> pollFirstHeat(fixture, host, initialVersion, initialEnergy, waited + 1));
    }

    private static final class Measurement {
        final long startSample;
        final long deadline;
        long lastSample;
        long previousGrantSample;
        long startingVersion;
        long observedVersion;
        UUID networkId;
        double startingEnergy;
        double observedEnergy;
        double observedDebitJoules;
        int observedEvents;
        int grantIntervals;
        long lastGrantSample;
        long lastEventSample;
        Measurement(long start, long version, UUID networkId, double energy, long lastSample) {
            startSample = start;
            deadline = start + 60;
            startingVersion = version;
            startingEnergy = energy;
            observedVersion = version;
            this.networkId=networkId;
            observedEnergy = energy;
            this.lastSample = lastSample;
            previousGrantSample = lastSample;
            this.lastGrantSample = lastSample;
            lastEventSample = lastSample;
        }
    }

    private static void pollMeasuredWindow(Fixture fixture, AtmosMachineBlockEntity host, Measurement m) {
        fixture.trace(host, "measure");
        long now = PowerRuntime.currentSampleTick(fixture.level);
        var currentEntry = fixture.entry();
        if(!fixture.gasNetworkId(currentEntry).equals(m.networkId))fixture.fail("heater gas network identity changed during measured window");
        long versionAdvance = fixture.gasRevision(currentEntry) - m.observedVersion;
        if (versionAdvance < 0 || versionAdvance > 1)
            fixture.fail("heater event observation expected at most one new network gas revision per tick; advance=" + versionAdvance);
        if (versionAdvance == 1) {
            double thermalDelta = fixture.gas(currentEntry).thermalEnergy() - m.observedEnergy;
            if (Math.abs(thermalDelta - 2850) > .5)
                fixture.fail("new heater network gas revision must include exactly one 2850 J thermal event; delta=" + thermalDelta);
            double debit = host.deviceRuntimeState().lastActualPaidInputJoules();
            if (Math.abs(debit - 3000) > .001)
                fixture.fail("new heater event must report its 3000 J live credit debit; debit=" + debit);
            m.observedEvents++;
            m.observedDebitJoules += debit;
            m.observedVersion = fixture.gasRevision(currentEntry);
            m.observedEnergy = fixture.gas(currentEntry).thermalEnergy();
        }
        long sample = host.apcSampleTick();
        if (sample > m.lastSample) {
            if (!validAllocation(fixture.level, host) || sample - m.lastGrantSample != PowerRuntime.TICKS_PER_SOLVE)
                fixture.fail("measurement allocation became unknown/underpowered/irregular at sample=" + sample);
            m.grantIntervals++;
            m.previousGrantSample = sample;
            m.lastGrantSample = sample;
            m.lastSample = sample;
        } else if (!validAllocation(fixture.level, host)) {
            fixture.fail("measurement allocation invalid between callbacks at sample=" + sample);
        }
        if (now >= m.deadline) {
            var end = fixture.entry();
            long versions = fixture.gasRevision(end) - m.startingVersion;
            double joules = fixture.gas(end).thermalEnergy() - m.startingEnergy;
            if (versions != 5)
                fixture.fail("steady (start,end] 60-tick window expected exactly 5 committed BE heat events; versions=" + versions);
            if (m.grantIntervals != 3)
                fixture.fail("steady window expected exactly 3 unique completed 20-tick allocations; grants=" + m.grantIntervals);
            double required = versions * 3000d;
            double paidWithMaximumCarry = m.grantIntervals * 5000d + 3000d;
            if (required > paidWithMaximumCarry)
                fixture.fail("event debit exceeded completed grants plus one-event carry: debit=" + required
                        + ", paid+carry=" + paidWithMaximumCarry);
            if (Math.abs(joules - 14_250) > 2.5)
                fixture.fail("five accepted events must add 14250 J at .95 COP; observed=" + joules);
            if (m.observedEvents != 5 || Math.abs(m.observedDebitJoules - 15_000) > .001)
                fixture.fail("observed committed heater events must account for five 3000 J debits; events="
                        + m.observedEvents + ", debit=" + m.observedDebitJoules);
            fixture.cleanup();
            fixture.helper.succeed();
            return;
        }
        fixture.helper.runAfterDelay(1, () -> pollMeasuredWindow(fixture, host, m));
    }

    private static void pollSteadyAdmission(Fixture fixture, AtmosMachineBlockEntity host, long startedAt,
                                            long previousSample, int consecutiveIntervals) {
        fixture.trace(host, "readiness-" + consecutiveIntervals);
        long sample = host.apcSampleTick();
        boolean valid = validAllocation(fixture.level, host);
        int next = consecutiveIntervals;
        long previous = previousSample;
        if (!valid) next = 0;
        else if (sample > previousSample) {
            if (sample - previousSample == PowerRuntime.TICKS_PER_SOLVE) next++;
            else next = 0;
            previous = sample;
        }
        if (next >= 3) {
            fixture.beginMeasuredWindow(host);
            return;
        }
        if (fixture.level.getGameTime() - startedAt >= 220)
            fixture.fail("steady admission not observed: intervals=" + next + ",sample=" + sample);
        final int count = next;
        final long watermark = previous;
        fixture.helper.runAfterDelay(1,
                () -> pollSteadyAdmission(fixture, host, startedAt, watermark, count));
    }

    private static boolean validAllocation(ServerLevel level, AtmosMachineBlockEntity host) {
        var meter = PowerRuntime.apcPowerMeter(level, host);
        return host.apcAllocationKnown() && host.apcAllocatedWatts() >= 5000
                && meter.known() && meter.deliveredWatts() >= 5000;
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos heater;
        final AtmosMachineBlock machineBlock;
        final String expectedProfile;
        final BlockPos source;
        final BlockPos substation;
        final BlockPos apc;
        final List<BlockPos> ownedWalls = new ArrayList<>();
        final List<Cable> ownedCables = new ArrayList<>();
        final FixtureChunkTickets.Lease lease;
        final AutoCloseable powerGraphTraceLease;
        long seededVersion;
        UUID seededNetworkId;
        double seededEnergy;
        boolean cleaned;
        final List<String> trace = new ArrayList<>();
        long lastKnownGeneration = -1;
        double lastKnownDemand = -1;
        UUID lastKnownIdentity = null;

        Fixture(GameTestHelper helper, ServerLevel level) {
            this(helper, level, ModBlocks.GAS_THERMO_HEATER.get());
        }

        Fixture(GameTestHelper helper, ServerLevel level, boolean freezer) {
            this(helper, level, freezer ? ModBlocks.GAS_THERMO_FREEZER.get() : ModBlocks.GAS_THERMO_HEATER.get());
        }

        Fixture(GameTestHelper helper, ServerLevel level, AtmosMachineBlock machineBlock) {
            this.helper = helper;
            this.level = level;
            this.powerGraphTraceLease = PowerGraphService.acquireDiagnosticTrace(level);
            this.machineBlock = machineBlock;
            this.expectedProfile = machineBlock.profileIdentity();
            heater = helper.absolutePos(new BlockPos(3, 1, 8));
            source = helper.absolutePos(new BlockPos(3, 1, 2));
            substation = helper.absolutePos(new BlockPos(3, 1, 4));
            apc = helper.absolutePos(new BlockPos(3, 1, 6));
            lease = FixtureChunkTickets.around(helper, helper.absolutePos(new BlockPos(2, 1, 2)),
                    helper.absolutePos(new BlockPos(5, 1, 8)), 1);
        }

        void install() {
            for (BlockPos offset : AtmosMachineryFixturePower.supportOffsets()) ownWall(heater.offset(offset));
            if (!AtmosMachineryFixturePower.installPumpSupply(level, heater)) fail("supported power supply installation failed");
            for (CableTier tier : List.of(CableTier.HV, CableTier.MV, CableTier.APC))
                for (var placement : AtmosMachineryFixturePower.cablePlacements(heater, Direction.WEST, tier))
                    ownedCables.add(new Cable(placement.pos(), placement.face(), placement.tier()));
            level.setBlock(heater, machineBlock.defaultBlockState()
                    .setValue(AtmosMachineBlock.FACING, Direction.SOUTH), 3);
            FixtureChunkTickets.awaitEntityTicking(helper, lease, 100, this::machineReady,
                    "cadence machine and real-power fixture hosts ticking", () -> { },
                    heater, source, substation, apc, apcCableHost());
        }

        private boolean machineReady() {
            return level.getBlockEntity(heater) instanceof AtmosMachineBlockEntity
                    && level.getBlockEntity(source) != null && level.getBlockEntity(substation) != null
                    && level.getBlockEntity(apc) != null;
        }

        private void ownWall(BlockPos local) {
            BlockPos pos = helper.absolutePos(local);
            if (!level.getBlockState(pos).isAir()) fail("cadence fixture wall location was occupied: " + pos);
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            ownedWalls.add(pos);
        }

        BlockPos apcCableHost() { return helper.absolutePos(new BlockPos(4, 1, 8)); }

        AtmosMachineBlockEntity machine() {
            if (level.getBlockEntity(heater) instanceof AtmosMachineBlockEntity host
                    && host.profileIdentity().equals(expectedProfile)) return host;
            throw new GameTestAssertException("registered atmos machine block entity missing: " + expectedProfile);
        }

        void seedFinitePort() {
            seedFinitePort(AtmosMachineProfile.PortName.PIPE, new GasMixture(Map.of(GasType.NITROGEN, 100d), 300,
                    port(AtmosMachineProfile.PortName.PIPE).volumeM3()));
        }

        AtmosMachineProfile.Port port(AtmosMachineProfile.PortName name) {
            return machine().resolvedProfile().ports().stream().filter(p -> p.name() == name).findFirst().orElseThrow();
        }

        void seedFinitePort(AtmosMachineProfile.PortName name, GasMixture gas) {
            machine().reconcileLoadedPorts(level);
            var port = machine().gasPorts().stream().filter(p -> p.id().equals(name.name().toLowerCase())).findFirst().orElseThrow();
            var saved = PipeGasInventorySavedData.forMutation(level);
            var before = saved.snapshot();
            var entry = before.get(port.key(heater)).orElseThrow();
            var after = before.withMixture(port.key(heater), entry.incarnation(), entry.version(), gas);
            if (!saved.replace(before.revision(), before, after)) fail("finite machine port gas seed CAS failed");
            if (name == AtmosMachineProfile.PortName.PIPE) {
                var seeded = saved.snapshot().get(port.key(heater)).orElseThrow();
                seededVersion = gasRevision(seeded);
                seededNetworkId = gasNetworkId(seeded);
                seededEnergy = gas(seeded).thermalEnergy();
            }
        }

        PipeGasInventory.Entry entry() {
            return entry(AtmosMachineProfile.PortName.PIPE);
        }

        PipeGasInventory.Entry entry(AtmosMachineProfile.PortName name) {
            machine().reconcileLoadedPorts(level);
            var port = machine().gasPorts().stream().filter(p -> p.id().equals(name.name().toLowerCase())).findFirst().orElseThrow();
            return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().get(port.key(heater)).orElseThrow();
        }

        long gasRevision(PipeGasInventory.Entry entry) {
            return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().network(entry.node()).orElseThrow().gasRevision();
        }
        UUID gasNetworkId(PipeGasInventory.Entry entry) {
            return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().network(entry.node()).orElseThrow().id();
        }
        GasMixture gas(PipeGasInventory.Entry entry) {
            return PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().mixture(entry.node());
        }

        void awaitFirstAcceptedHeat(AtmosMachineBlockEntity host) {
            trace(host, "await-first");
            pollFirstHeat(this, host, seededVersion, seededEnergy, 0);
        }

        void awaitSteadyAdmission(AtmosMachineBlockEntity host, long startedAt, long sample, int intervals) {
            trace(host, "readiness-start");
            pollSteadyAdmission(this, host, startedAt, sample, intervals);
        }

        void trace(AtmosMachineBlockEntity host, String phase) {
            if (trace.size() >= 24) trace.remove(0);
            var entry = entry();
            var meter = PowerRuntime.apcPowerMeter(level, host);
            long generationBeforeConnectionQuery = PowerGraphService.topologyGeneration(level);
            var connection = PowerRuntime.apcConnection(level, host);
            Double currentDemand = host.apcPowerDemandWatts();
            if (meter.known() && currentDemand != null) {
                lastKnownGeneration = generationBeforeConnectionQuery;
                lastKnownDemand = currentDemand;
                lastKnownIdentity = host.apcPowerIdentity();
            }
            trace.add("sample=" + PowerRuntime.currentSampleTick(level) + ",world=" + level.getGameTime()
                    + ",phase=" + phase + ",apcKnown=" + meter.known() + ",apcDelivered=" + meter.deliveredWatts()
                    + ",allocKnown=" + host.apcAllocationKnown() + ",alloc=" + host.apcAllocatedWatts()
                    + ",allocSample=" + host.apcSampleTick() + ",version=" + entry.version()
                     + ",energy=" + gas(entry).thermalEnergy() + ",paid=" + host.deviceRuntimeState().lastActualPaidInputJoules()
                    + ",status=" + host.deviceRuntimeState().lastWorkStatus()
                    + ",topologyReady=" + PowerGraphService.topologyReady(level)
                    + ",topologyGeneration=" + PowerGraphService.topologyGeneration(level)
                    + ",connection=" + connection
                    + ",generationBeforeConnectionQuery=" + generationBeforeConnectionQuery
                    + ",generationAfterConnectionQuery=" + PowerGraphService.topologyGeneration(level)
                    + ",live=" + (!host.isRemoved() && level.getBlockEntity(heater) == host)
                    + ",profile=" + host.profileIdentity()
                    + ",nodeIdentity=" + host.apcPowerIdentity()
                    + ",currentDemand=" + currentDemand
                    + ",lastKnown={generation=" + lastKnownGeneration + ",demand=" + lastKnownDemand
                    + ",identity=" + lastKnownIdentity + "}"
                    + ",diagnostic=" + PowerRuntime.lastSolveDiagnostic(level)
                    + ",graph=" + PowerGraphService.diagnosticSnapshot(level));
        }

        void beginMeasuredWindow(AtmosMachineBlockEntity host) {
            long start = PowerRuntime.currentSampleTick(level);
            var baseline = entry();
            trace(host, "measurement-start");
            pollMeasuredWindow(this, host,
                    new Measurement(start, gasRevision(baseline), gasNetworkId(baseline), gas(baseline).thermalEnergy(), host.apcSampleTick()));
        }

        void fail(String message) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                    "[heater-cadence-trace] batch=atmos_machine_cadence_isolated fixture={} readiness/window={}..{} rows={}\n{}\n{}",
                    heater.toShortString(), traceStart(), PowerRuntime.currentSampleTick(level), trace.size(),
                    String.join("\n", trace), PowerGraphService.diagnosticSnapshot(level));
            cleanup();
            String concise = message.length() > 560 ? message.substring(0, 557) + "..." : message;
            throw new GameTestAssertException(concise + " (see heater-cadence-trace in native log)");
        }

        long traceStart() {
            if (trace.isEmpty()) return PowerRuntime.currentSampleTick(level);
            String first = trace.getFirst();
            int marker = first.indexOf("sample=");
            int end = first.indexOf(',', marker);
            try { return Long.parseLong(first.substring(marker + 7, end)); }
            catch (RuntimeException ignored) { return -1; }
        }

        void cleanup() {
            if (cleaned) return;
            cleaned = true;
            for (Cable cable : ownedCables) {
                if (CableStorage.get(level, cable.pos(), cable.face()) == cable.tier())
                    CableStorage.remove(level, cable.pos(), cable.face());
            }
            if (level.getBlockState(heater).is(machineBlock))
                level.setBlock(heater, Blocks.AIR.defaultBlockState(), 3);
            if (level.getBlockState(source).is(ModBlocks.HV_SOURCE.get()))
                level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
            if (level.getBlockState(substation).is(ModBlocks.HV_MV_SUBSTATION.get()))
                level.setBlock(substation, Blocks.AIR.defaultBlockState(), 3);
            if (level.getBlockState(apc).is(ModBlocks.APC.get()))
                level.setBlock(apc, Blocks.AIR.defaultBlockState(), 3);
            for (BlockPos pos : ownedWalls)
                if (level.getBlockState(pos).is(Blocks.STONE)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            try { lease.close(); } catch (Exception ignored) { }
            try { powerGraphTraceLease.close(); } catch (Exception ignored) { }
        }
    }

    private record Cable(BlockPos pos, Direction face, CableTier tier) { }
}
