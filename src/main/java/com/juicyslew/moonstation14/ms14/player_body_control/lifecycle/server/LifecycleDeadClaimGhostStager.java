package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.ms14.movement.MovementStartupGate;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarness;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessId;
import com.juicyslew.moonstation14.ms14.player_body_control.MobHarnessKind;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.ghost.GhostMobHarnessRegistration;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.PlayerLifecycleRegistry;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterBinding;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessBinder;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.persistence.SavedLifecycleProfile;
import com.juicyslew.moonstation14.ms14.player_body_control.server.GhostMobHarnessControl;
import com.juicyslew.moonstation14.ms14.player_body_control.server.MindGhostStartupGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Explicit DEAD_CLAIM to durable GHOST staging boundary for trusted login and confirmed-death callers. */
public final class LifecycleDeadClaimGhostStager {
    private LifecycleDeadClaimGhostStager() { }

    public static Result stage(ServerPlayer player, MinecraftServer server) {
        return resolveAndStage(player, server, null, false);
    }

    /** Connected-death handoff requires the retained, exact corpse to still be loaded. */
    static Result stageConnectedDeath(ServerPlayer player, MinecraftServer server) {
        return resolveAndStage(player, server, null, true);
    }

    private static Result resolveAndStage(ServerPlayer player, MinecraftServer server, SavedLifecycleProfile supplied,
                                          boolean requireLoadedCorpse) {
        if (!MindGhostStartupGate.enabledForServer()) return Result.failed("lifecycle master gate is off");
        if (MovementStartupGate.enabledForServer()) return Result.failed("movement conflict is active");
        if (player == null || server == null) return Result.failed("server thread or player is unavailable");
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return Result.failed("initialized lifecycle context is unavailable");
        final SavedLifecycleProfile saved;
        try { saved = supplied == null ? context.currentDeadClaim(player.getUUID()).orElse(null) : supplied; }
        catch (Exception failure) { return Result.failed("current-primary DEAD_CLAIM could not be validated; inspect lifecycle state"); }
        return prepare(player, server, saved, requireLoadedCorpse);
    }

    static Result stage(ServerPlayer player, MinecraftServer server, SavedLifecycleProfile saved) {
        return prepare(player, server, saved, false);
    }

    private static Result prepare(ServerPlayer player, MinecraftServer server, SavedLifecycleProfile saved,
                                  boolean requireLoadedCorpse) {
        // These global gates precede player, context, storage, and world access.
        if (!MindGhostStartupGate.enabledForServer()) return Result.failed("lifecycle master gate is off");
        if (MovementStartupGate.enabledForServer()) return Result.failed("movement conflict is active");
        if (player == null || server == null || !server.isSameThread())
            return Result.failed("server thread or player is unavailable");
        if (player instanceof FakePlayer || player.isRemoved() || !player.isAlive()
                || player.level().isClientSide || player.getServer() != server || player.connection == null
                || !player.connection.isAcceptingMessages()
                || server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                || !(player.level() instanceof ServerLevel))
            return Result.failed("player is not the exact authenticated connected spectator");

        UUID accountId = player.getUUID();
        if (GhostMobHarnessControl.ownsDebugSession(player)) return Result.failed("player has an active debug session");
        if (LifecycleGhostSessionControl.ownsAny(player))
            return Result.failed("lifecycle ghost session already exists");
        LifecycleServerContext context = LifecycleStartupRuntime.contextFor(server).orElse(null);
        if (context == null) return Result.failed("initialized lifecycle context is unavailable");
        var memory = context.lifecycle().profile(accountId).orElse(null);
        if (memory != null && (memory.active() || memory.state() != PlayerLifecycleRegistry.LifecycleState.DEAD_CLAIM))
            return Result.failed("account already has an active or conflicting lifecycle profile");

        if (saved == null || saved.state() != SavedLifecycleProfile.State.DEAD_CLAIM)
            return Result.failed("no unique current-primary DEAD_CLAIM for this account");
        saved = validatedCurrentDeadClaim(accountId, saved, context::currentDeadClaim).orElse(null);
        if (saved == null) return Result.failed("no matching unique current-primary DEAD_CLAIM for this account");

        ServerLevel targetLevel = null;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        boolean exactLoadedCorpse = false;
        try {
            ResourceLocation dimension = ResourceLocation.parse(saved.dimension());
            ServerLevel savedLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
            if (savedLevel != null) {
                Entity corpse = savedLevel.getEntity(saved.bodyId());
                if (corpse != null) {
                    PlayerCharacterBinding expectedBinding = new PlayerCharacterBinding(saved.accountId(), saved.profileKey(), saved.mindId());
                    if (corpse.isRemoved() || !(corpse instanceof PlayerCharacterHarnessEntity)
                            || corpse.getType() != com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration.getEntityType()
                            || !expectedBinding.equals(PlayerCharacterHarnessBinder.binding(corpse)))
                        return Result.failed("loaded saved corpse identity is invalid");
                    targetLevel = savedLevel;
                    x = corpse.getX(); y = corpse.getY(); z = corpse.getZ();
                    exactLoadedCorpse = true;
                } else {
                    BlockPos savedPos = BlockPos.containing(saved.location().x(), saved.location().y(), saved.location().z());
                    if (savedLevel.hasChunkAt(savedPos)) {
                        targetLevel = savedLevel;
                        x = saved.location().x(); y = saved.location().y(); z = saved.location().z();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // An invalid/unavailable saved location is not death evidence and never blocks safe login spawn.
        }
        if (requireLoadedCorpse && !exactLoadedCorpse)
            return Result.failed("exact retained corpse is not loaded in its saved dimension; DEAD_CLAIM retained");
        if (targetLevel == null) targetLevel = (ServerLevel) player.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!targetLevel.hasChunkAt(pos))
            return Result.failed("neither saved corpse area nor carrier login location is already loaded; refusing to force-load");

        GhostMobHarnessEntity ghost = null;
        boolean registered = false;
        boolean keepGhost = false;
        try {
            ghost = GhostMobHarnessRegistration.getEntityType().create(targetLevel);
            if (ghost == null) return Result.failed("registered ghost entity could not be created");
            ghost.setNoAi(true);
            ghost.setNoGravity(true);
            ghost.noPhysics = true;
            ghost.moveTo(x, y, z, 0, 0);
            if (!targetLevel.addFreshEntity(ghost)) return Result.failed("new ghost was rejected by the world");
            if (!exactFreshGhost(targetLevel, ghost)) return Result.failed("new ghost failed exact world verification");

            MobHarnessId ghostId = new MobHarnessId(ghost.getUUID());
            registered = context.lifecycle().registerHarness(new MobHarness(ghostId, MobHarnessKind.GHOST));
            if (!registered) return Result.failed("new ghost harness registration failed");
            var activated = context.activateDeadClaimGhost(saved, ghostId);
            if (activated.isEmpty()) {
                var current = context.lifecycle().profile(accountId).orElse(null);
                if (current != null && current.bodyId().equals(ghostId) && !current.active()) {
                    keepGhost = true;
                    return retainStagedRecovery(ghost,
                            "durable activation was rejected (" + current.state()
                                    + "); staged claim retained for explicit recovery");
                }
                return Result.failed("DEAD_CLAIM staging was rejected; new ghost discarded");
            }
            var active = activated.orElseThrow();
            if (!active.active() || active.state() != PlayerLifecycleRegistry.LifecycleState.GHOST
                    || !active.deadClaim() || !active.bodyId().equals(ghostId)
                    || !active.mindId().value().equals(saved.mindId())
                    || !context.lifecycle().authorizes(accountId, active.connectionGeneration(), ghostId,
                    harness -> harness.id().equals(ghostId) && harness.kind() == MobHarnessKind.GHOST))
                return retainStagedRecovery(ghost, "post-CAS ghost authority did not verify; explicit recovery required");
            keepGhost = true;
            return Result.prepared(new PreparedGhost(ghost, accountId, active.mindId().value(), saved.bodyId(),
                    active.connectionGeneration()));
        } catch (Exception failure) {
            var stagedProfile = context.lifecycle().profile(accountId).orElse(null);
            if (registered && ghost != null && stagedProfile != null
                    && stagedProfile.bodyId().equals(new MobHarnessId(ghost.getUUID()))
                    && !stagedProfile.active()) {
                keepGhost = true;
                return retainStagedRecovery(ghost, "ghost staged but durable activation failed; explicit recovery required");
            }
            return Result.failed("ghost preparation failed before staging; DEAD_CLAIM retained");
        } finally {
            if (!keepGhost) cleanup(context, accountId, ghost, registered);
        }
    }

    @FunctionalInterface
    interface DeadClaimReader {
        Optional<SavedLifecycleProfile> read(UUID accountId) throws IOException;
    }

    /** Re-read before world mutation: a caller-supplied row alone is never staging authority. */
    static Optional<SavedLifecycleProfile> validatedCurrentDeadClaim(UUID accountId, SavedLifecycleProfile supplied,
                                                                       DeadClaimReader reader) {
        if (accountId == null || supplied == null || supplied.state() != SavedLifecycleProfile.State.DEAD_CLAIM
                || !accountId.equals(supplied.accountId()) || reader == null) return Optional.empty();
        try {
            var current = reader.read(accountId);
            return current.filter(row -> row.state() == SavedLifecycleProfile.State.DEAD_CLAIM
                    && accountId.equals(row.accountId()) && row.equals(supplied));
        } catch (IOException | RuntimeException failure) {
            return Optional.empty();
        }
    }

    /** Safely undo post-CAS activation if handshake startup did not take ownership. */
    static boolean returnPreparedToDeadClaim(LifecycleServerContext context, PreparedGhost prepared) {
        var ghost = prepared.ghost();
        MobHarnessId ghostId = new MobHarnessId(ghost.getUUID());
        var returned = context.lifecycle().returnGhostToDeadClaim(prepared.accountUUID(),
                new com.juicyslew.moonstation14.ms14.player_body_control.MindId(prepared.mindUUID()),
                ghostId, new MobHarnessId(prepared.corpseUUID()), prepared.generation());
        if (returned.isEmpty()) return false;
        cleanup(context, prepared.accountUUID(), ghost, true);
        return true;
    }

    static void suspendAndDiscardPrepared(LifecycleServerContext context, PreparedGhost prepared) {
        var profile = context.lifecycle().profile(prepared.accountUUID()).orElse(null);
        if (profile != null && !profile.active()
                && profile.state() == PlayerLifecycleRegistry.LifecycleState.RECOVERY_REQUIRED)
            cleanup(context, prepared.accountUUID(), prepared.ghost(), true);
    }

    private static Result retainStagedRecovery(GhostMobHarnessEntity ghost, String diagnostic) {
        return Result.failed(diagnostic + " (transient ghost retained; no session or packets started)");
    }

    private static void cleanup(LifecycleServerContext context, UUID accountId, GhostMobHarnessEntity ghost,
                                boolean registered) {
        if (registered && ghost != null) {
            MobHarnessId ghostId = new MobHarnessId(ghost.getUUID());
            if (!context.lifecycle().unregisterTransientGhost(accountId, ghostId))
                context.ownership().unregisterHarness(ghostId);
        }
        if (ghost != null && !ghost.isRemoved()) ghost.discard();
    }

    private static boolean exactFreshGhost(ServerLevel level, GhostMobHarnessEntity ghost) {
        return ghost != null && !ghost.isRemoved() && ghost.isAlive() && ghost.isAddedToLevel()
                && ghost.level() == level && level.getEntity(ghost.getUUID()) == ghost
                && ghost.getType() == GhostMobHarnessRegistration.getEntityType() && ghost.isNoAi()
                && ghost.isNoGravity() && ghost.noPhysics;
    }

    public record PreparedGhost(GhostMobHarnessEntity ghost, UUID accountUUID, UUID mindUUID,
                                UUID corpseUUID, long generation) {
        public PreparedGhost {
            Objects.requireNonNull(ghost, "ghost"); Objects.requireNonNull(accountUUID, "accountUUID");
            Objects.requireNonNull(mindUUID, "mindUUID"); Objects.requireNonNull(corpseUUID, "corpseUUID");
            if (generation <= 0) throw new IllegalArgumentException("invalid generation");
        }
    }

    public record Result(Outcome outcome, String diagnostic, PreparedGhost prepared) {
        public Result {
            Objects.requireNonNull(outcome, "outcome"); Objects.requireNonNull(diagnostic, "diagnostic");
            if ((outcome == Outcome.PREPARED) != (prepared != null))
                throw new IllegalArgumentException("prepared result mismatch");
        }
        static Result failed(String diagnostic) { return new Result(Outcome.FAILED, diagnostic, null); }
        static Result prepared(PreparedGhost prepared) { return new Result(Outcome.PREPARED, "durably active ghost", prepared); }
    }

    public enum Outcome { FAILED, PREPARED }
}
