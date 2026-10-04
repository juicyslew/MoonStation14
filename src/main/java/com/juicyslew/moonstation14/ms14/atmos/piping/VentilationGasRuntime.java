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

/** Runs ventilation only against the one-use authorization from a completed paid power solve. */
public final class VentilationGasRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger(VentilationGasRuntime.class);
    // Values must not reference ServerLevel: block entities strongly reference their level.
    // Keep a positional tombstone until level unload so replacement/reloaded BEs cannot
    // resume flow while the failed transaction's orphaned gas still requires manual recovery.
    private static final Map<ServerLevel, Set<BlockPos>> QUARANTINED = new WeakHashMap<>();

    private VentilationGasRuntime() { }

    public static void onCompletedPaidSolve(ServerLevel level, VentilationDeviceBlockEntity device) {
        onCompletedPaidSolveWithServiceForTest(level, device, AtmosphereService.INSTANCE);
    }

    /** Same paid-solve path with an isolated atmosphere policy for package-local integration tests. */
    static void onCompletedPaidSolveWithServiceForTest(ServerLevel level, VentilationDeviceBlockEntity device,
            AtmosphereService atmosphere) {
        if (level == null || device == null || atmosphere == null || !atmosphere.isEnabled()
                || QUARANTINED.getOrDefault(level, Set.of()).contains(device.getBlockPos())) return;
        try {
            PowerRuntime.performCompletedVentilationWork(level, device, seconds -> {
                // This is conservative internal pipe distribution covered by the paid interval;
                // it may remain even if room-side transfer preflight subsequently fails.
                LoadedPipeEqualization.equalize(level, device);
                return FiniteVentilationTransfer.executeTrustedAfterPaidInterval(
                        level, atmosphere, device, seconds).moved();
            });
        } catch (PipeGasTransaction.RecoveryRequiredException recoveryRequired) {
            QUARANTINED.computeIfAbsent(level, ignored -> new HashSet<>()).add(device.getBlockPos().immutable());
            LOGGER.error("Ventilation device quarantined after gas transaction recovery failure; manual recovery is required and flow will not resume until the level unloads");
        }
    }

    /** Preserve the quarantine tombstone: the failed gas may still belong to an orphan owner. */
    public static void forgetDevice(ServerLevel level, VentilationDeviceBlockEntity device) {
        // No BE/level reference is retained by QUARANTINED; position tombstones are intentional.
    }

    public static void forgetLevel(ServerLevel level) { QUARANTINED.remove(level); }

    public static void clear() { QUARANTINED.clear(); }
}
