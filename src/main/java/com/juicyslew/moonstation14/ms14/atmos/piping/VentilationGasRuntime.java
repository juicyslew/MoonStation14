package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import net.minecraft.nbt.CompoundTag;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineCadence;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.power.runtime.VentilationPaidInterval;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

/** Credits only completed paid solve intervals, then runs gas work on each device's resolved cadence. */
public final class VentilationGasRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(VentilationGasRuntime.class);
    // Values must not reference ServerLevel: block entities strongly reference their level.
    // Keep a positional tombstone until level unload so replacement/reloaded BEs cannot
    // resume flow while the failed transaction's orphaned gas still requires manual recovery.
    private static final Map<ServerLevel, Set<BlockPos>> QUARANTINED = new WeakHashMap<>();

    /** Bounded, current-placement-session export observation; never serialized. */
    public static final class ExportObservation {
        private final EnumMap<GasType, Double> speciesMoles = new EnumMap<>(GasType.class);
        private final EnumMap<AtmosphereService.ExteriorBoundary.Kind, Double> thermalJoules =
                new EnumMap<>(AtmosphereService.ExteriorBoundary.Kind.class);
        public Map<GasType, Double> speciesMoles() { return Map.copyOf(speciesMoles); }
        public Map<AtmosphereService.ExteriorBoundary.Kind, Double> thermalJoulesByBoundary() { return Map.copyOf(thermalJoules); }
        void add(FiniteVentilationTransfer.ExportReceipt receipt) {
            if (receipt == null) return;
            receipt.packet().gasMoles().forEach((gas, amount) -> speciesMoles.merge(gas, amount, FiniteObservationTotal::add));
            thermalJoules.merge(receipt.kind(), receipt.thermalEnergyJoules(), FiniteObservationTotal::add);
        }
    }

    /** Cumulative committed receipt plus one transient last decision; export receipts remain separate. */
    public static final class TransferObservation {
        private final EnumMap<GasType, Double> speciesMoles = new EnumMap<>(GasType.class);
        private double thermalJoules;
        private transient long decisionSequence;
        private transient Decision lastDecision;
        /** paidSeconds is the configured funded-event duration, not the power-solve interval. */
        public record Decision(long sequence, long worldTick, long sampleTick, double paidSeconds,
                               String kind, java.util.UUID deviceId, BlockPos devicePos, BlockPos pipePos,
                               String pipeNodeIdentity,
                               int lane, double pipeVolumeM3, GasMixture pipeBefore, double pipePressureKpa,
                               BlockPos roomPos, AtmosphereService.ExteriorBoundary.Kind boundaryKind,
                               double roomVolumeM3, GasMixture roomBefore, double roomPressureKpa,
                                VentilationDeviceConfig.Settings settings, double pumpPowerFactor,
                                 boolean powerProfilePresent, String pressureChecks,
                                 double liveExternalPressureBoundKpa, double profileExternalPressureBoundKpa,
                                 double internalPressureBoundKpa,
                                  boolean configurationAvailable,
                               boolean planPresent, GasMixture plannedPacket, GasMixture plannedSourceAfter,
                               GasMixture plannedDestinationAfter, GasMixture actualPacket,
                               GasMixture pipeAfter, GasMixture roomAfter, String outcome,
                                double acceptedMoles, String exportReceipt) { }
        public Decision lastDecision() { return lastDecision; }
        void recordDecision(long worldTick, long sampleTick, double paidSeconds, VentilationDeviceBlockEntity device,
                            BlockPos pipePos, String pipeNodeIdentity, int lane, GasMixture pipeBefore, BlockPos roomPos,
                             AtmosphereService.ExteriorBoundary.Kind boundaryKind, GasMixture roomBefore,
                             VentilationDeviceConfig.Settings settings,
                             double pumpPowerFactor, boolean powerProfilePresent,
                             com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData.GasVentPump pumpProfile,
                              boolean configurationAvailable,
                            boolean planPresent, GasMixture plannedPacket, GasMixture plannedSourceAfter,
                            GasMixture plannedDestinationAfter, GasMixture actualPacket, GasMixture pipeAfter,
                            GasMixture roomAfter, String outcome, FiniteVentilationTransfer.ExportReceipt export) {
            decisionSequence = nextSequence(decisionSequence);
            double pipeVolume = pipeBefore == null ? Double.NaN : pipeBefore.volumeCubicMeters();
            double roomVolume = roomBefore == null ? Double.NaN : roomBefore.volumeCubicMeters();
            lastDecision = new Decision(decisionSequence, worldTick, sampleTick, paidSeconds, device.kind().name(),
                    device.deviceId(), device.getBlockPos().immutable(), pipePos.immutable(), pipeNodeIdentity, lane, pipeVolume,
                    pipeBefore, pipeBefore == null ? Double.NaN : pipeBefore.pressureKpa(pipeVolume),
                    roomPos.immutable(), boundaryKind, roomVolume, roomBefore,
                    roomBefore == null ? Double.NaN : roomBefore.pressureKpa(roomVolume), settings,
                    pumpPowerFactor, powerProfilePresent,
                    pumpProfile == null ? "UNAVAILABLE" : pumpProfile.pressureChecks().name(),
                    settings.externalPressureBoundKPa(),
                    pumpProfile == null ? Double.NaN : pumpProfile.externalPressureBoundKPa(),
                    pumpProfile == null ? Double.NaN : pumpProfile.internalPressureBoundKPa(),
                      configurationAvailable, planPresent, plannedPacket,
                    plannedSourceAfter, plannedDestinationAfter, actualPacket, pipeAfter, roomAfter, outcome,
                    outcome.startsWith("UNAVAILABLE") ? Double.NaN : actualPacket == null ? 0 : actualPacket.totalMoles(), export == null ? null
                            : export.kind()+":"+export.packet().gasMoles()+"@"+export.packet().temperatureKelvin()+"K");
        }
        static long nextSequence(long current) { return current == Long.MAX_VALUE ? 1 : current + 1; }
        public Map<GasType, Double> speciesMoles() { return Map.copyOf(speciesMoles); }
        public double thermalJoules() { return thermalJoules; }
        void add(GasMixture packet) {
            if (packet == null) return;
            for (GasType gas : GasType.values()) speciesMoles.merge(gas, packet.moles(gas), FiniteObservationTotal::add);
            thermalJoules = FiniteObservationTotal.add(thermalJoules, packet.thermalEnergy());
        }
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            speciesMoles.forEach((gas, amount) -> tag.putDouble(gas.name(), amount));
            tag.putDouble("ThermalJoules", thermalJoules);
            return tag;
        }
        void load(CompoundTag tag) {
            speciesMoles.clear(); thermalJoules = 0;
            for (GasType gas : GasType.values()) if (tag.contains(gas.name())) {
                double amount = tag.getDouble(gas.name());
                if (Double.isFinite(amount) && amount >= 0) speciesMoles.put(gas, amount);
            }
            double energy = tag.getDouble("ThermalJoules");
            if (Double.isFinite(energy) && energy >= 0) thermalJoules = energy;
        }
    }

    /** Last successful paid gas event, kept only in the live block-entity session. */
    public record PaidEventReceipt(long eventTick, long lastGrantTick, double fundedEventSeconds,
                                   double eventJoules, double lastGrantJoules,
                                   double cumulativeGrantedJoules, double cumulativeSpentJoules) { }

    private VentilationGasRuntime() { }

    /** Transient cadence/accounting state; each instance is owned directly by one block entity. */
    static final class DeviceCadence {
        static final double MAX_BILLING_QUANTUM_SECONDS = PowerRuntime.TICKS_PER_SOLVE / 20.0;
        private final AtmosMachineCadence.Clock clock = new AtmosMachineCadence.Clock();
        private final AtmosMachineCadence.PaidWorkBudget paidWork = new AtmosMachineCadence.PaidWorkBudget();
        private UUID owner;
        private VentilationPaidInterval.ReceiverSignature receiver;
        private long settingsRevision = -1;
        private long intervalBits = Long.MIN_VALUE;
        private long lastGrantTick = -1;
        private double startupPaidSeconds;
        private double lastGrantJoules;
        private double actualGrantedJoules;
        private double actualSpentJoules;
        private PaidEventReceipt lastPaidEventReceipt;

        boolean reconcile(UUID nextOwner, VentilationPaidInterval.ReceiverSignature nextReceiver,
                          long nextSettingsRevision, double intervalSeconds) {
            if (nextOwner == null || nextReceiver == null || nextSettingsRevision < 0
                    || !nextReceiver.known() || !nextReceiver.ready() || !nextReceiver.breakerClosed()
                    || nextReceiver.tick() < 0
                    || !Double.isFinite(intervalSeconds) || intervalSeconds <= 0) {
                clear();
                return false;
            }
            long nextIntervalBits = Double.doubleToLongBits(intervalSeconds);
            if (!nextOwner.equals(owner) || !VentilationPaidInterval.sameReceiver(receiver, nextReceiver)
                    || settingsRevision != nextSettingsRevision || intervalBits != nextIntervalBits) {
                resetFundingPhase();
                owner = nextOwner;
                settingsRevision = nextSettingsRevision;
                intervalBits = nextIntervalBits;
            }
            receiver = nextReceiver;
            return true;
        }

        boolean grant(double paidSeconds, double paidJoules, long sampleTick, double eventCostJoules,
                      double maximumOldCarryJoules) {
            if (owner == null || receiver == null || !Double.isFinite(paidSeconds) || paidSeconds <= 0
                    || !Double.isFinite(paidJoules) || paidJoules <= 0
                    || !Double.isFinite(eventCostJoules) || eventCostJoules <= 0
                    || !Double.isFinite(maximumOldCarryJoules) || maximumOldCarryJoules < 0
                    || sampleTick < 0) {
                clear();
                return false;
            }
            if (lastGrantTick >= 0 && sampleTick > lastGrantTick
                    && sampleTick - lastGrantTick > PowerRuntime.TICKS_PER_SOLVE)
                resetFundingPhase();
            if (!paidWork.accept(owner, paidJoules, sampleTick, eventCostJoules, maximumOldCarryJoules))
                return false;
            lastGrantTick = sampleTick;
            lastGrantJoules = paidJoules;
            actualGrantedJoules = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(
                    actualGrantedJoules, paidJoules);
            startupPaidSeconds = Math.min(MAX_BILLING_QUANTUM_SECONDS, startupPaidSeconds + paidSeconds);
            if (!clock.anchored() && startupPaidSeconds + 1e-12 >= MAX_BILLING_QUANTUM_SECONDS)
                clock.anchor(sampleTick);
            return true;
        }

        boolean due(long serverTick, double intervalSeconds) {
            return clock.anchored() && clock.due(serverTick, AtmosMachineCadence.intervalTicks(intervalSeconds));
        }
        boolean canWork(long serverTick, long currentPowerTick, double eventCostJoules) {
            return owner != null && paidWork.canWork(owner, serverTick, currentPowerTick, eventCostJoules);
        }
        boolean commitWork(long serverTick, long currentPowerTick, double eventCostJoules) {
            if (owner == null || !paidWork.commitWork(owner, serverTick, currentPowerTick, eventCostJoules)) return false;
            actualSpentJoules = com.juicyslew.moonstation14.ms14.atmos.core.FiniteObservationTotal.add(
                    actualSpentJoules, eventCostJoules);
            lastPaidEventReceipt = new PaidEventReceipt(serverTick, lastGrantTick,
                    intervalBits == Long.MIN_VALUE ? Double.NaN : Double.longBitsToDouble(intervalBits),
                    eventCostJoules, lastGrantJoules, actualGrantedJoules, actualSpentJoules);
            return true;
        }
        void attempted() { clock.completed(); }
        double remainingJoules() { return paidWork.remainingJoules(); }
        double startupPaidSeconds() { return startupPaidSeconds; }
        long lastGrantTick() { return lastGrantTick; }
        double lastGrantJoules() { return lastGrantJoules; }
        double actualGrantedJoules() { return actualGrantedJoules; }
        double actualSpentJoules() { return actualSpentJoules; }
        PaidEventReceipt lastPaidEventReceipt() { return lastPaidEventReceipt; }
        private void resetFundingPhase() {
            clock.reset();
            paidWork.unavailable();
            startupPaidSeconds = 0;
        }
        void clear() {
            resetFundingPhase();
            owner = null;
            receiver = null;
            settingsRevision = -1;
            intervalBits = Long.MIN_VALUE;
        }
    }

    /** Receives only completed solve credits and never transfers gas itself. */
    public static void onCompletedPaidSolve(ServerLevel level, VentilationDeviceBlockEntity device) {
        if (level == null || device == null) return;
        if (QUARANTINED.getOrDefault(level, Set.of()).contains(device.getBlockPos())) {
            device.clearGasCadence();
            return;
        }
        VentilationDeviceData profile = device.prototype();
        VentilationDeviceConfig.Settings settings = device.settingsValid() ? device.config().settings() : null;
        UUID owner = device.deviceId();
        var signature = PowerRuntime.ventilationReceiverSignature(level, device).orElse(null);
        double intervalSeconds = profile == null ? Double.NaN : profile.deviceIntervalSeconds();
        double cost = profile == null ? Double.NaN
                : profile.apcPowerReceiver().requestedWatts() * intervalSeconds;
        DeviceCadence cadence = device.gasCadence();
        if (profile == null || settings == null || !settings.configuredEnabled() || owner == null
                || signature == null || signature.ventilationBE() != device
                || !cadence.reconcile(owner, signature, settings.revision(), intervalSeconds)) {
            cadence.clear();
            return;
        }
        if (!Double.isFinite(cost) || cost <= 0) { cadence.clear(); return; }
        PowerRuntime.performCompletedVentilationWork(level, device, actualPaidSeconds -> {
            var paidSignature = PowerRuntime.ventilationReceiverSignature(level, device).orElse(null);
            if (paidSignature == null || !VentilationPaidInterval.sameReceiver(signature, paidSignature)
                    || paidSignature.ventilationBE() != device || !device.settingsValid()
                    || device.config().settings().revision() != settings.revision()
                    || device.prototype() != profile) {
                cadence.clear();
                return false;
            }
            if (!cadence.reconcile(owner, paidSignature, settings.revision(), intervalSeconds)) return false;
            // The callback's actual elapsed interval was consumed by the one-use 5 W authorization.
            double requestedWatts = profile.apcPowerReceiver().requestedWatts();
            double maximumOldCarry = requestedWatts * DeviceCadence.MAX_BILLING_QUANTUM_SECONDS + cost;
            cadence.grant(actualPaidSeconds, requestedWatts * actualPaidSeconds,
                    paidSignature.tick(), cost, maximumOldCarry);
            return false;
        });
    }

    /** Called once per authoritative server tick over a copy of PowerRuntime's indexed vent positions. */
    public static void tickKnownDevices(ServerLevel level, long now, Iterable<BlockPos> indexedVentPositions) {
        if (level == null || indexedVentPositions == null || level.isClientSide || !level.getServer().isSameThread()) return;
        for (BlockPos position : indexedVentPositions) {
            if (position == null) continue;
            BlockPos pos = position.immutable();
            if (!(level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                    ChunkStatus.FULL, false) instanceof LevelChunk chunk)) continue;
            if (!(chunk.getBlockEntity(pos) instanceof VentilationDeviceBlockEntity device)
                    || device.isRemoved() || device.getLevel() != level
                    || !(chunk.getBlockState(pos).getBlock() instanceof VentilationDeviceBlock)) continue;
            if (QUARANTINED.getOrDefault(level, Set.of()).contains(pos)) {
                device.clearGasCadence();
                continue;
            }
            VentilationDeviceData profile = device.prototype();
            VentilationDeviceConfig.Settings settings = device.settingsValid() ? device.config().settings() : null;
            UUID owner = device.deviceId();
            var signature = PowerRuntime.ventilationReceiverSignature(level, device).orElse(null);
            double intervalSeconds = profile == null ? Double.NaN : profile.deviceIntervalSeconds();
            if (profile == null || settings == null || !settings.configuredEnabled() || owner == null
                    || signature == null || signature.ventilationBE() != device
                    || !device.settingsValid()) {
                device.clearGasCadence();
                continue;
            }
            DeviceCadence cadence = device.gasCadence();
            if (!cadence.reconcile(owner, signature, settings.revision(), intervalSeconds)) continue;
            double cost = profile.apcPowerReceiver().requestedWatts() * intervalSeconds;
            if (!Double.isFinite(cost) || cost <= 0) { cadence.clear(); continue; }
            if (!cadence.due(now, intervalSeconds) || !cadence.canWork(now, now, cost)) continue;

            // A funded attempt consumes this cadence event even when the physical plan is NoPlan.
            cadence.attempted();
            try {
                var result = FiniteVentilationTransfer.executeTrustedAfterPaidInterval(
                        level, AtmosphereService.INSTANCE, device, intervalSeconds);
                if (result.moved() && !cadence.commitWork(now, now, cost)) {
                    // Synchronous server-thread execution means this is an invariant violation, not a retry.
                    cadence.clear();
                    QUARANTINED.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos);
                    LOGGER.error("Ventilation gas mutation committed without matching paid-work debit at {}", pos);
                    continue;
                }
                recordCommittedTransfer(device, result);
            } catch (PipeGasTransaction.RecoveryRequiredException recoveryRequired) {
                cadence.clear();
                QUARANTINED.computeIfAbsent(level, ignored -> new HashSet<>()).add(pos);
                LOGGER.error("Ventilation device quarantined after gas transaction recovery failure; manual recovery is required and flow will not resume until the level unloads");
            }
        }
    }

    /** Applies the receipt only for the successful result returned after its gas transaction committed. */
    static void recordCommittedTransfer(VentilationDeviceBlockEntity device, FiniteVentilationTransfer.Result result) {
        if (device == null || result == null || !result.moved()) return;
        result.committedPackets().forEach(device::recordTransfer);
        if (result.export() != null) device.recordExport(result.export());
    }

    /** Preserve the quarantine tombstone: the failed gas may still belong to an orphan owner. */
    public static void forgetDevice(ServerLevel level, VentilationDeviceBlockEntity device) {
        // No BE/level reference is retained by QUARANTINED; position tombstones are intentional.
        if (device != null) device.clearGasCadence();
    }

    public static void forgetLevel(ServerLevel level) { QUARANTINED.remove(level); }

    public static void clear() { QUARANTINED.clear(); }
}

