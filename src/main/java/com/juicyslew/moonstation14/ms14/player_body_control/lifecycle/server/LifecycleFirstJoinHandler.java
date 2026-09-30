package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry;
import com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CarrierHandInventoryGate;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeCarrierTransition;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Production first-account and same-body reconnect join transactions. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class LifecycleFirstJoinHandler {
    private static final String RECONNECT = "Your saved character body could not be safely reconnected. No replacement was spawned; contact an administrator or retry when the saved area is available.";
    private static final String FAILED = "Character enrollment could not be completed safely. Your saved body/reservation was retained; contact an administrator.";
    private static final String DIRTY_CARRIER = "Character control requires an empty carrier inventory and a valid parked-inventory marker. Your inventory was left untouched; reconnect after recovery.";

    private LifecycleFirstJoinHandler() { }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (routeDecision(MindGhostStartupGate.enabledForServer(),
                MovementStartupGate.enabledForServer(), false, false) == JoinRoute.VANILLA) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) { disconnect(player, FAILED); return; }
        // Login may be fired before the player list is authoritative. Never infer a fresh account then.
        if (player.isRemoved() || player.level().isClientSide || player.connection == null
                || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || !(player.level() instanceof ServerLevel)) {
            disconnect(player, FAILED);
            return;
        }
        var snapshot = LifecycleStartupRuntime.snapshot(server).orElse(null);
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (snapshot == null || context == null) {
            disconnect(player, FAILED);
            return;
        }
        boolean knownAccount = context.hasReservedClaim(player.getUUID());
        if (knownAccount) {
            final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile saved;
            try { saved = context.currentAccountProfile(player.getUUID()).orElse(null); }
            catch (Exception failure) { disconnect(player, RECONNECT); return; }
            if (saved == null) { disconnect(player, RECONNECT); return; }
            AccountRoute accountRoute = accountRoute(saved.state());
            if (accountRoute == AccountRoute.LIVING_RECONNECT) {
                if (!existingAccountMayReconnect(snapshot.state(), context, player.getUUID(), saved.state())) {
                    disconnect(player, RECONNECT); return;
                }
                LifecycleExistingBodyReconnect.handle(player, server, context, reason -> disconnect(player, reason));
                return;
            }
            if (accountRoute == AccountRoute.GHOST_LOGIN) {
                if (!existingAccountMayReconnect(snapshot.state(), context, player.getUUID(), saved.state())) {
                    disconnect(player, RECONNECT); return;
                }
                if (GhostMobHarnessControl.ownsDebugSession(player) || LifecycleGhostSessionControl.ownsAny(player)
                        || LifecycleCharacterSessionControl.ownsAny(player)) {
                    disconnect(player, "A conflicting character or ghost session already exists; reconnect is blocked safely.");
                    return;
                }
                DeadClaimAdmission admission = admitDeadClaimCarrier(player.gameMode.getGameModeForPlayer(),
                        player.hasData(ModDataAttachments.CREATIVE_PARKED_INVENTORY.get()),
                        () -> CreativeCarrierTransition.park(player), () -> CarrierHandInventoryGate.allows(player));
                if (admission == DeadClaimAdmission.DENIED) { disconnect(player, DIRTY_CARRIER); return; }
                if (admission == DeadClaimAdmission.RECOVERY) { disconnect(player, RECONNECT); return; }
                boolean newlyParked = admission == DeadClaimAdmission.NEWLY_PARKED;
                if (!switchDeadClaimCarrier(newlyParked, () -> player.setGameMode(GameType.SPECTATOR),
                        () -> player.gameMode.getGameModeForPlayer(), () -> CarrierHandInventoryGate.allows(player),
                        () -> {
                            try {
                                var current = context.currentDeadClaim(player.getUUID()).orElse(null);
                                var memory = context.lifecycle().profile(player.getUUID()).orElse(null);
                                return saved.equals(current) && (memory == null || !memory.active());
                            } catch (Exception failure) { return false; }
                        }, () -> CreativeCarrierTransition.restore(player),
                        () -> LifecycleDevelopmentMode.syncCarrier(player))) {
                    disconnect(player, "Ghost login could not make the carrier a spectator; reconnect after recovery.");
                    return;
                }
                // A mode event can alter carrier slots. Do not create or activate a ghost from a dirty carrier.
                if (!CarrierHandInventoryGate.allows(player)) { disconnect(player, DIRTY_CARRIER); return; }
                var staged = LifecycleDeadClaimGhostStager.stage(player, server, saved);
                if (staged.outcome() != LifecycleDeadClaimGhostStager.Outcome.PREPARED || staged.prepared() == null) {
                    MoonStation14.LOGGER.warn("Saved character death could not be prepared for ghost login (account={}): {}",
                            player.getUUID(), staged.diagnostic());
                    disconnect(player, "Your character has died, but ghost entry could not be prepared safely. Log out and reconnect to retry, or contact an administrator.");
                    return;
                }
                var prepared = staged.prepared();
                LifecycleGhostSessionControl.StartResult start;
                try {
                    start = LifecycleGhostSessionControl.beginPrepared(player, prepared.ghost(), context.lifecycle(),
                            prepared.generation(), new com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId(prepared.corpseUUID()));
                } catch (RuntimeException | Error failure) {
                    if (!LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared))
                        LifecycleDeadClaimGhostStager.suspendAndDiscardPrepared(context, prepared);
                    disconnect(player, "Your character has died, but ghost entry could not be started safely. Log out and reconnect to retry, or contact an administrator.");
                    return;
                }
                if (start != LifecycleGhostSessionControl.StartResult.PREPARED) {
                    if (!LifecycleDeadClaimGhostStager.returnPreparedToDeadClaim(context, prepared))
                        LifecycleDeadClaimGhostStager.suspendAndDiscardPrepared(context, prepared);
                    disconnect(player, "Your character has died, but ghost entry could not be started safely. Log out and reconnect to retry, or contact an administrator.");
                }
                return;
            }
            disconnect(player, RECONNECT);
            return;
        }
        JoinRoute route = routeDecision(true, false, true, false);
        if (snapshot.state() != LifecycleStartupRuntime.State.EMPTY
                && snapshot.state() != LifecycleStartupRuntime.State.UNINITIALIZED) {
            disconnect(player, FAILED);
            return;
        }
        if (GhostMobHarnessControl.ownsDebugSession(player)) { disconnect(player, FAILED); return; }
        if (!supportedVanillaMode(player.gameMode.getGameModeForPlayer())) { disconnect(player, FAILED); return; }
        // Login fires after level insertion and inventory-menu initialization. Refuse before reserving
        // the account: an unsafe carrier must not create a durable claim or change game mode.
        if (!CarrierHandInventoryGate.allows(player)) { disconnect(player, DIRTY_CARRIER); return; }

        // Persist PREPARING before changing the carrier: every possible failure after this point
        // retains a durable claim and is intentionally not eligible for a duplicate enrollment.
        FirstCharacterBodyStager.ReservationResult[] reservation = new FirstCharacterBodyStager.ReservationResult[1];
        boolean[] spectator = {false};
        FirstCharacterBodyStager.ReservedFirstCharacter token = reserveBeforeMode(() -> {
            reservation[0] = FirstCharacterBodyStager.reserve(player, server);
            return reservation[0].token();
        }, ignored -> spectator[0] = player.setGameMode(GameType.SPECTATOR)
                && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR);
        if (reservation[0] == null || !reservation[0].reserved() || token == null) {
            String diagnostic = reservation[0] == null ? "reservation did not return a result" : reservation[0].diagnostic();
            disconnect(player, "Character enrollment reservation failed safely: " + diagnostic
                    + ". Your mode was not changed; contact an administrator before retrying.");
            return;
        }

        // Carrier must be inert before any Mind authority can be promoted.
        if (token == null || !spectator[0]) {
            disconnect(player, FAILED);
            return;
        }
        if (!CarrierHandInventoryGate.allows(player)) { disconnect(player, DIRTY_CARRIER); return; }
        FirstCharacterBodyStager.Result staged = FirstCharacterBodyStager.stageReserved(token, player, server);
        if (staged.outcome() != FirstCharacterBodyStager.Outcome.PREPARED || staged.prepared() == null) {
            disconnect(player, FAILED);
            return;
        }
        var prepared = staged.prepared();
        PlayerCharacterHarnessEntity body = prepared.body();
        if (!validPrepared(server, player, context, prepared)) { disconnect(player, FAILED); return; }
        if (!CarrierHandInventoryGate.allows(player)) { disconnect(player, DIRTY_CARRIER); return; }

        // Establish the immutable speaker identity while authority remains PREPARING.
        PlayerCharacterBinding binding = body.playerCharacterBinding();
        try {
            ChatIdentitySavedData.forFirstEnrollment(server.overworld()).allocateCharacterDurably(server.overworld(),
                    new ChatIdentityRegistry.CharacterKey(binding.accountId(), binding.profileKey()));
        } catch (RuntimeException | Error failure) {
            MoonStation14.LOGGER.error("Chat identity persistence failed before ACTIVE first enrollment (account={}, profile={})",
                    binding.accountId(), binding.profileKey(), failure);
            disconnect(player, "Character enrollment was reserved, but chat identity could not be saved safely. Contact an administrator before reconnecting.");
            return;
        }
        final com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.Snapshot active;
        try {
            active = context.promoteFirstCharacter(prepared.accountUUID(), prepared.mindUUID(), prepared.bodyUUID()).orElse(null);
        } catch (Exception failure) {
            disconnect(player, FAILED);
            return;
        }
        if (active == null || active.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.ACTIVE
                || !active.active() || active.connectionGeneration() <= 0) {
            disconnect(player, FAILED);
            return;
        }
        final LifecycleCharacterSessionControl.StartResult result;
        try {
            result = LifecycleCharacterSessionControl.beginPrepared(player, body, context.lifecycle(), active.connectionGeneration());
        } catch (RuntimeException | Error failure) {
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(), active.mindId(), active.bodyId(), active.connectionGeneration());
            disconnect(player, "Character authority was durably reserved but controller startup failed. Reconnect is deferred; contact an administrator.");
            return;
        }
        if (result != LifecycleCharacterSessionControl.StartResult.PREPARED) {
            context.lifecycle().suspendActiveSessionForRecovery(player.getUUID(), active.mindId(), active.bodyId(), active.connectionGeneration());
            disconnect(player, "Character authority was durably reserved but controller startup failed. Reconnect is deferred; contact an administrator.");
        }
    }

    private static boolean validPrepared(MinecraftServer server, ServerPlayer player, LifecycleServerContext context,
                                         FirstCharacterBodyStager.PreparedFirstCharacter prepared) {
        PlayerCharacterHarnessEntity body = prepared.body();
        if (!(player.level() instanceof ServerLevel level) || !server.isSameThread()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || body.isRemoved() || !body.isAlive() || !body.isAddedToLevel() || body.isPassenger()
                || body.level() != level || level.getEntity(prepared.bodyUUID()) != body || !body.isNoAi()
                || !prepared.accountUUID().equals(player.getUUID())
                || !prepared.bodyUUID().equals(body.getUUID())) return false;
        PlayerCharacterBinding binding = body.playerCharacterBinding();
        var identity = body.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        var mind = context.ownership().mind(player.getUUID()).orElse(null);
        var profile = context.lifecycle().profile(player.getUUID()).orElse(null);
        return binding != null && binding.accountId().equals(player.getUUID())
                && binding.profileKey().equals("main") && binding.mindId().equals(prepared.mindUUID())
                && identity != null && identity.isBound() && ModCharacters.HUMAN_ID.equals(identity.characterId())
                && mind != null && mind.id().value().equals(prepared.mindUUID())
                && mind.harnessId().value().equals(prepared.bodyUUID())
                && profile != null && profile.state() == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.PREPARING
                && !profile.active() && profile.connectionGeneration() == prepared.generation()
                && mind.epoch() == prepared.generation();
    }

    private static boolean supportedVanillaMode(GameType mode) {
        return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
    }

    static JoinRoute routeDecision(boolean masterEnabled, boolean movementConflict,
                                   boolean exactListedRealPlayer, boolean hasDurableAccount) {
        if (!masterEnabled || movementConflict) return JoinRoute.VANILLA;
        if (!exactListedRealPlayer) return JoinRoute.INVALID_PLAYER;
        return hasDurableAccount ? JoinRoute.EXISTING_ACCOUNT : JoinRoute.FIRST_ACCOUNT;
    }

    static boolean existingAccountMayReconnect(LifecycleStartupRuntime.State state) {
        return state == LifecycleStartupRuntime.State.DEFERRED;
    }

    /**
     * UNINITIALIZED is accepted only for an account reserved by this live server during enrollment.
     * The caller must also hold the unique current-primary account row; this helper never grants
     * admission based on startup state alone. Startup DEFERRED keeps its existing offline recovery path.
     */
    static boolean existingAccountMayReconnect(LifecycleStartupRuntime.State state, LifecycleServerContext context,
                                               java.util.UUID accountId,
                                               com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State profileState) {
        if (profileState != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE
                && profileState != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM)
            return false;
        return state == LifecycleStartupRuntime.State.DEFERRED
                || state == LifecycleStartupRuntime.State.UNINITIALIZED && context != null
                && context.wasCreatedOnThisServer(accountId);
    }

    static AccountRoute accountRoute(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State state) {
        if (state == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.OFFLINE)
            return AccountRoute.LIVING_RECONNECT;
        if (state == com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile.State.DEAD_CLAIM)
            return AccountRoute.GHOST_LOGIN;
        return AccountRoute.FAIL_CLOSED;
    }

    enum JoinRoute { VANILLA, INVALID_PLAYER, EXISTING_ACCOUNT, FIRST_ACCOUNT }
    enum AccountRoute { LIVING_RECONNECT, GHOST_LOGIN, FAIL_CLOSED }
    enum DeadClaimAdmission { DENIED, RECOVERY, READY, NEWLY_PARKED }

    /** Value-level policy only; production callbacks enforce exact connected-player authority. */
    static DeadClaimAdmission admitDeadClaimCarrier(GameType mode, boolean markerPresent,
                                                    Supplier<CreativeCarrierTransition.Result> park,
                                                    BooleanSupplier clean) {
        try {
            if (mode == GameType.CREATIVE && !markerPresent) {
                var result = park.get();
                if (result == CreativeCarrierTransition.Result.RECOVERY_REQUIRED) return DeadClaimAdmission.RECOVERY;
                if (result != CreativeCarrierTransition.Result.PARKED) return DeadClaimAdmission.DENIED;
                return clean.getAsBoolean() ? DeadClaimAdmission.NEWLY_PARKED : DeadClaimAdmission.RECOVERY;
            }
            return clean.getAsBoolean() ? DeadClaimAdmission.READY : DeadClaimAdmission.DENIED;
        } catch (RuntimeException | Error failure) { return DeadClaimAdmission.RECOVERY; }
    }

    /** A canceled switch restores only this login's newly parked, clean, exact Creative carrier. */
    static boolean switchDeadClaimCarrier(boolean newlyParked, BooleanSupplier switchMode, Supplier<GameType> actual,
                                          BooleanSupplier clean, BooleanSupplier exactClaim,
                                          Supplier<CreativeCarrierTransition.Result> restore, Runnable sync) {
        boolean switched = false;
        try {
            switched = switchMode.getAsBoolean();
        } catch (RuntimeException | Error failure) {
            // Even a throwing mode callback might have left the player in Creative.
        }
        try {
            GameType mode = actual.get();
            if (switched && mode == GameType.SPECTATOR && clean.getAsBoolean()) return true;
            if (newlyParked && mode == GameType.CREATIVE && clean.getAsBoolean()
                    && exactClaim.getAsBoolean()
                    && restore.get() == CreativeCarrierTransition.Result.RESTORED) sync.run();
        } catch (RuntimeException | Error failure) {
            // Ambiguous mode, claim or slots: retain any marker and disconnect.
        }
        return false;
    }

    static <T> T reserveBeforeMode(Supplier<T> reserve, Consumer<T> modeChange) {
        T token = reserve.get();
        if (token != null) modeChange.accept(token);
        return token;
    }

    private static void disconnect(ServerPlayer player, String reason) {
        if (player.connection != null && player.connection.isAcceptingMessages())
            player.connection.disconnect(Component.literal(reason));
    }
}
