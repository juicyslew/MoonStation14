package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal first-character staging implementation. A successful result names the exact dormant
 * body and staged, disconnected Mind; callers must durably promote before beginning control.
 */
public final class FirstCharacterBodyStager {
    private static final String PROFILE_KEY = "main";
    private static final Map<String, String> DEFAULT_APPEARANCE = Map.of(
            "appearance", "DEFAULT", "bodyShape", "WIDE");

    private FirstCharacterBodyStager() { }

    static ReservationResult reserve(ServerPlayer player, MinecraftServer server) {
        // Gate checks precede all player, context, store, and world access.
        if (!MindGhostStartupGate.enabledForServer()) return ReservationResult.failed("lifecycle master gate is off");
        if (MovementStartupGate.enabledForServer()) return ReservationResult.failed("movement conflict is active");
        if (server == null || player == null || !server.isSameThread())
            return ReservationResult.failed("server thread or player is unavailable");

        ServerLevel level;
        UUID accountId = player.getUUID();
        if (player instanceof FakePlayer || player.isRemoved() || player.level().isClientSide
                || player.getServer() != server || player.connection == null
                || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(accountId) != player
                || !(player.level() instanceof ServerLevel playerLevel))
            return ReservationResult.failed("player is not the exact authenticated connected server player");
        level = playerLevel;

        LifecycleStartupRuntime.StartupSnapshot snapshot = LifecycleStartupRuntime.snapshot(server).orElse(null);
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (snapshot == null || context == null
                || (snapshot.state() != LifecycleStartupRuntime.State.EMPTY
                && snapshot.state() != LifecycleStartupRuntime.State.UNINITIALIZED))
            return ReservationResult.failed("server lifecycle context is not eligible for first enrollment");
        if (context.hasReservedClaim(accountId)) return ReservationResult.failed("account already has a lifecycle claim");
        if (GhostMobHarnessControl.ownsDebugSession(player))
            return ReservationResult.failed("player has an active debug session");

        UUID mindId = UUID.randomUUID();
        UUID bodyId = UUID.randomUUID();
        SavedLifecycleProfile.Location location = new SavedLifecycleProfile.Location(
                player.getX(), player.getY(), player.getZ());
        boolean reserved;
        try {
            reserved = LifecycleStartupRuntime.reserveFirstProfile(server, accountId, PROFILE_KEY,
                    mindId, bodyId, level.dimension().location().toString(), location, DEFAULT_APPEARANCE);
        } catch (Exception failure) {
            return ReservationResult.failed("PREPARING reservation failed; inspect lifecycle state before retrying");
        }
        if (!reserved) return ReservationResult.failed("PREPARING reservation was not confirmed; inspect lifecycle state");
        return ReservationResult.reserved(new ReservedFirstCharacter(server, accountId, mindId, bodyId));
    }

    static Result stageReserved(ReservedFirstCharacter reservation, ServerPlayer player, MinecraftServer server) {
        if (!MindGhostStartupGate.enabledForServer()) return Result.failed("lifecycle master gate is off");
        if (MovementStartupGate.enabledForServer()) return Result.failed("movement conflict is active");
        if (reservation == null || server == null || player == null || reservation.server() != server
                || player.getServer() != server || !reservation.accountUUID().equals(player.getUUID()))
            return Result.failed("reservation does not match this player and server; PREPARING retained");
        if (!server.isSameThread() || player instanceof FakePlayer || player.isRemoved()
                || player.level().isClientSide || player.connection == null || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || !(player.level() instanceof ServerLevel level))
            return Result.failed("player is no longer the exact authenticated connected server player; PREPARING retained");
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null || !context.hasReservedClaim(reservation.accountUUID()))
            return Result.failed("matching PREPARING claim is unavailable; inspect lifecycle state");
        try {
            if (!context.hasCurrentPreparing(reservation.accountUUID(), PROFILE_KEY,
                    reservation.mindUUID(), reservation.bodyUUID()))
                return Result.failed("matching PREPARING claim is not in the current primary; inspect lifecycle state");
        } catch (Exception failure) {
            return Result.failed("current PREPARING claim could not be verified; inspect lifecycle state");
        }

        PlayerCharacterHarnessEntity body = null;
        boolean registered = false;
        boolean mindStaged = false;
        try {
            body = PlayerCharacterHarnessRegistration.getEntityType().create(level);
            if (body == null) return Result.failed("custom character entity could not be created; PREPARING retained");
            body.setUUID(reservation.bodyUUID());
            body.setNoAi(true);
            body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            if (!level.addFreshEntity(body)) return Result.failed("custom character entity was rejected; PREPARING retained");

            if (level.getEntity(reservation.bodyUUID()) != body || body.getType() != PlayerCharacterHarnessRegistration.getEntityType()
                    || body.isRemoved() || !body.isAlive() || !body.isNoAi()
                    || !PlayerCharacterHarnessBinder.bind(body, reservation.accountUUID(), PROFILE_KEY, reservation.mindUUID(), ModCharacters.HUMAN_ID))
                return Result.failed("body identity/binder verification failed; PREPARING retained");

            PlayerCharacterBinding binding = body.playerCharacterBinding();
            CharacterIdentityAttachment identity = body.getExistingDataOrNull(
                    ModDataAttachments.CHARACTER_IDENTITY.get());
            if (binding == null || !binding.equals(new PlayerCharacterBinding(reservation.accountUUID(), PROFILE_KEY, reservation.mindUUID()))
                    || identity == null || !identity.isBound() || !ModCharacters.HUMAN_ID.equals(identity.characterId())
                     || CharacterIdentitySystem.resolveForActor(body).isEmpty())
                return Result.failed("body account/profile/Mind/HUMAN identity did not verify; PREPARING retained");

            MobHarnessId harnessId = new MobHarnessId(reservation.bodyUUID());
            registered = context.lifecycle().registerHarness(new MobHarness(harnessId, MobHarnessKind.CHARACTER));
            if (!registered) return Result.failed("character harness registration failed; PREPARING retained");
            mindStaged = context.stageFirstProfile(reservation.accountUUID(), reservation.mindUUID(), reservation.bodyUUID(), harnessId);
            if (!mindStaged) return Result.failed("disconnected Mind could not be staged; PREPARING retained");
        } catch (Exception failure) {
            return Result.failed("staging failed; PREPARING retained for operator recovery");
        }

        if (!mindStaged) return Result.failed("disconnected Mind could not be staged; PREPARING retained");
        var stagedMind = context.lifecycle().profile(reservation.accountUUID()).orElse(null);
        if (stagedMind == null || stagedMind.state() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry.LifecycleState.PREPARING
                || stagedMind.active()) return Result.failed("staged Mind did not remain disconnected PREPARING");
        return Result.prepared(new PreparedFirstCharacter(body, reservation.accountUUID(), reservation.mindUUID(), reservation.bodyUUID(),
                stagedMind.connectionGeneration()));
    }

    record ReservedFirstCharacter(MinecraftServer server, UUID accountUUID, UUID mindUUID, UUID bodyUUID) {
        ReservedFirstCharacter {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(accountUUID, "accountUUID");
            Objects.requireNonNull(mindUUID, "mindUUID");
            Objects.requireNonNull(bodyUUID, "bodyUUID");
        }
    }

    record ReservationResult(boolean reserved, String diagnostic, ReservedFirstCharacter token) {
        ReservationResult {
            Objects.requireNonNull(diagnostic, "diagnostic");
            if (reserved != (token != null)) throw new IllegalArgumentException("reservation/token mismatch");
        }
        static ReservationResult failed(String diagnostic) { return new ReservationResult(false, diagnostic, null); }
        static ReservationResult reserved(ReservedFirstCharacter token) { return new ReservationResult(true, "reserved", token); }
    }

    public record PreparedFirstCharacter(PlayerCharacterHarnessEntity body, UUID accountUUID,
                                         UUID mindUUID, UUID bodyUUID, long generation) {
        public PreparedFirstCharacter {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(accountUUID, "accountUUID");
            Objects.requireNonNull(bodyUUID, "bodyUUID");
            Objects.requireNonNull(mindUUID, "mindUUID");
            if (generation < 0) throw new IllegalArgumentException("invalid generation");
        }
    }

    public record Result(Outcome outcome, String diagnostic, PreparedFirstCharacter prepared) {
        public Result {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
        static Result failed(String diagnostic) { return new Result(Outcome.FAILED, diagnostic, null); }
        static Result prepared(PreparedFirstCharacter prepared) { return new Result(Outcome.PREPARED, "staged", prepared); }
    }

    public enum Outcome { FAILED, PREPARED }

}
