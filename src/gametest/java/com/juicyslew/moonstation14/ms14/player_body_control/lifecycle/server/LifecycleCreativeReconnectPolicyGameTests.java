package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierSnapshotProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.UUID;

/** Pure production-policy seams; GameTest fake players cannot claim a connected login. */
@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifecycleCreativeReconnectPolicyGameTests {
    private LifecycleCreativeReconnectPolicyGameTests() { }

    @GameTest(template = "empty")
    public static void creativeUnmarkedParksBeforeCleanGate(GameTestHelper helper) {
        AtomicInteger parks = new AtomicInteger();
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> { parks.incrementAndGet(); return CreativeCarrierTransition.Result.PARKED; },
                () -> parks.get() == 1) == LifecycleExistingBodyReconnect.Admission.NEWLY_PARKED,
                "new Creative carrier must park before its clean gate");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.DENIED, () -> true)
                == LifecycleExistingBodyReconnect.Admission.DENIED, "failed park cannot admit");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.PARKED, () -> false)
                == LifecycleExistingBodyReconnect.Admission.RECOVERY, "dirty after park requires recovery");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void existingMarkerAndNonCreativeNeverRepark(GameTestHelper helper) {
        AtomicInteger parks = new AtomicInteger();
        var park = (java.util.function.Supplier<CreativeCarrierTransition.Result>) () -> {
            parks.incrementAndGet(); return CreativeCarrierTransition.Result.PARKED;
        };
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, true, park, () -> true)
                == LifecycleExistingBodyReconnect.Admission.READY, "valid existing park accepted unchanged");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.SURVIVAL, false, park, () -> false)
                == LifecycleExistingBodyReconnect.Admission.DENIED, "dirty Survival rejected");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.ADVENTURE, false, park, () -> true)
                == LifecycleExistingBodyReconnect.Admission.READY, "empty Adventure baseline accepted");
        require(parks.get() == 0, "only unmarked Creative may park");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void canceledModeSwitchRestoresOnlyNewPark(GameTestHelper helper) {
        AtomicInteger restores = new AtomicInteger();
        AtomicInteger syncs = new AtomicInteger();
        require(!LifecycleExistingBodyReconnect.inertPolicy(true, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "canceled switch cannot activate");
        require(restores.get() == 1 && syncs.get() == 1, "new snapshot restored and synced");
        require(!LifecycleExistingBodyReconnect.inertPolicy(false, () -> false, () -> GameType.CREATIVE,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "preexisting marker cannot be restored by reconnect");
        require(restores.get() == 1, "preexisting snapshot untouched");
        require(LifecycleExistingBodyReconnect.inertPolicy(true, () -> true, () -> GameType.SPECTATOR,
                () -> true, () -> { restores.incrementAndGet(); return CreativeCarrierTransition.Result.RESTORED; },
                syncs::incrementAndGet), "clean actual Spectator accepted");
        require(restores.get() == 1, "successful handoff retains snapshot");
        require(!LifecycleExistingBodyReconnect.inertPolicy(true, () -> true, () -> GameType.SPECTATOR,
                () -> false, () -> CreativeCarrierTransition.Result.RESTORED, syncs::incrementAndGet),
                "dirty Spectator rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void offlineCreativeRefusalIsReadOnlyPolicyNotConnectedAdmission(GameTestHelper helper) {
        var denied = new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.DENIED,
                true, CreativeCarrierSnapshotProbe.Reason.READY);
        require(LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.CREATIVE, false),
                "no-write Creative refusal may request a detached claim without parking or a body");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.CREATIVE, true),
                "marker mismatch denied");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED, denied, GameType.SURVIVAL, false),
                "nonCreative denied");
        require(!LifecycleExistingBodyReconnect.offlineCreativeRefusalEligible(
                LifecycleExistingBodyReconnect.Admission.DENIED,
                new CreativeCarrierTransition.Attempt(CreativeCarrierTransition.Result.DENIED, false,
                        CreativeCarrierSnapshotProbe.Reason.READY), GameType.CREATIVE, false),
                "ambiguous writes denied");
        require(LifecycleExistingBodyReconnect.admitCarrier(GameType.CREATIVE, false,
                () -> CreativeCarrierTransition.Result.PARKED, () -> true)
                == LifecycleExistingBodyReconnect.Admission.NEWLY_PARKED,
                "clean retry still takes ordinary body reconnect route");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pinnedOfflineClaimRejectsChangedRowAndConflictingMemory(GameTestHelper helper) {
        UUID account = UUID.randomUUID(), mind = UUID.randomUUID(), body = UUID.randomUUID();
        var saved = new SavedLifecycleProfile(1, account, "main", mind, body, "minecraft:overworld",
                new SavedLifecycleProfile.Location(0, 64, 0), Map.of(), UUID.randomUUID(), 4,
                SavedLifecycleProfile.State.OFFLINE, 2, 1L);
        require(LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, account, null),
                "exact offline row with no in-memory authority may be pinned");
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, UUID.randomUUID(), null),
                "different account/stale player cannot use the pinned row");
        var changed = new SavedLifecycleProfile(1, account, "main", mind, body, "minecraft:overworld",
                saved.location(), Map.of(), saved.connectionEpoch(), 5,
                SavedLifecycleProfile.State.OFFLINE, 3, 1L);
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(changed, saved, account, null),
                "generation/revision change invalidates claim");
        require(!LifecycleDevelopmentMode.verifiedOfflineCreativeRow(saved, saved, account,
                new com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot(
                        1, account, "main", new com.juicyslew.moonstation14.ms14.player_body_control.MindId(mind),
                        new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(body),
                        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE,
                        4, true, false)), "active memory denies detached claim");
        Object connected = new Object(), staleSameAccount = new Object();
        require(LifecycleDevelopmentMode.offlineClaimOwner(connected, connected),
                "exact owner can return or clear its claim on logout");
        require(!LifecycleDevelopmentMode.offlineClaimOwner(connected, staleSameAccount),
                "stale player cannot return or clear the new player's claim");
        helper.succeed();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
