package com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
import com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.UUID;

/** Server-owned correlation and authority for aim-and-release ChemMaster dumping. */
public final class ChemMasterDumpService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final ChemMasterDumpCaptureState<Capture> CAPTURES = new ChemMasterDumpCaptureState<>();
    private static final java.util.Map<UUID, Long> SEQUENCES = new java.util.HashMap<>();
    private ChemMasterDumpService() { }
    private record Capture(ChemMasterDumpCorrelation.Owner owner, LooseItemEntity source,
                           ChemMasterWorldDump.ExpectedSource expected, ServerLevel level) { }

    public static void handle(ServerPlayer player, ChemMasterDumpRequest request) {
        long previousSequence = SEQUENCES.getOrDefault(player.getUUID(), -1L);
        if (!ChemMasterDumpCorrelation.newer(request.requestSequence(), previousSequence)) {
            reply(player, request, false, "stale request sequence"); return;
        }
        if (request.phase() == ChemMasterDumpRequest.Phase.CANCEL) {
            Capture current = CAPTURES.get(player.getUUID());
            boolean removed = CAPTURES.removeIf(player.getUUID(), c -> ChemMasterDumpCorrelation.owns(c.owner(), request.gestureId(), request.sourceId(),
                    c.owner().body(), request.bodyEpoch(), request.dimension(), request.requestSequence()));
            LOGGER.debug("ChemMaster dump capture phase=CANCEL gesture={} seq={} removed={} owned={}", token(request), request.requestSequence(), removed, current != null && current.owner().gesture().equals(request.gestureId()));
            if (removed) { reply(player, request, true, null);
                SEQUENCES.put(player.getUUID(), request.requestSequence());
            } else { LOGGER.debug("ChemMaster dump capture phase=CANCEL gesture={} seq={} removed=false reason=not_owned", token(request), request.requestSequence());
                reply(player, request, false, "gesture not owned"); }
            return;
        }
        var authority = BodyActionAuthority.resolve(player).orElse(null);
        if (authority == null || authority.identity().epoch() != request.bodyEpoch()
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().location().equals(request.dimension())
                || !BodyActionAuthority.eligibleForComplexInteraction(player, authority)) {
            if (request.phase() == ChemMasterDumpRequest.Phase.RELEASE) {
                CAPTURES.removeIf(player.getUUID(), current -> current.owner().gesture().equals(request.gestureId())
                        && current.owner().source().equals(request.sourceId()));
            }
            reply(player, request, false, "body authority or context changed"); return;
        }
        if (request.phase() == ChemMasterDumpRequest.Phase.BEGIN) {
            var expected = ChemMasterWorldDump.captureEligibleSource(level, request.sourceId());
            var entity = level.getEntity(request.sourceId());
            if (expected == null || !(entity instanceof LooseItemEntity item)) {
                LOGGER.debug("ChemMaster dump capture phase=BEGIN gesture={} seq={} accepted=false reason=source_not_dumpable", token(request), request.requestSequence());
                reply(player, request, false, "source is not dumpable"); return;
            }
            boolean replaced = CAPTURES.get(player.getUUID()) != null;
            CAPTURES.replace(player.getUUID(), new Capture(new ChemMasterDumpCorrelation.Owner(request.gestureId(),
                    request.sourceId(), authority.body().getUUID(), request.bodyEpoch(), request.dimension(),
                    request.requestSequence()), item, expected, level));
            LOGGER.debug("ChemMaster dump capture phase=BEGIN gesture={} seq={} accepted=true replaced={}", token(request), request.requestSequence(), replaced);
            SEQUENCES.put(player.getUUID(), request.requestSequence());
            reply(player, request, true, null); return;
        }
        Capture capture = CAPTURES.get(player.getUUID());
        if (capture == null) {
            LOGGER.debug("ChemMaster dump rejected phase={} seq={} captureExists=false reason=missing", request.phase(), request.requestSequence());
            reply(player, request, false, "no active dump capture"); return;
        }
        boolean gestureMatches = capture.owner().gesture().equals(request.gestureId());
        boolean sourceMatches = capture.owner().source().equals(request.sourceId());
        boolean bodyMatches = capture.owner().body().equals(authority.body().getUUID());
        boolean epochMatches = capture.owner().epoch() == request.bodyEpoch();
        boolean dimensionMatches = capture.owner().dimension().equals(request.dimension()) && capture.level() == level;
        boolean sequenceNewer = ChemMasterDumpCorrelation.newer(request.requestSequence(), capture.owner().sequence());
        if (!gestureMatches || !sourceMatches || !bodyMatches || !epochMatches || !dimensionMatches || !sequenceNewer) {
            LOGGER.debug("ChemMaster dump rejected phase={} seq={} captureExists=true gestureMatch={} sourceMatch={} bodyMatch={} epochMatch={} dimensionMatch={} sequenceNewer={}",
                    request.phase(), request.requestSequence(), gestureMatches, sourceMatches, bodyMatches, epochMatches, dimensionMatches, sequenceNewer);
            reply(player, request, false, "dump capture context or sequence mismatch"); return;
        }
        CAPTURES.removeIf(player.getUUID(), c -> c == capture);
        LOGGER.debug("ChemMaster dump capture phase=RELEASE gesture={} seq={} removed=true reason=consumed", token(request), request.requestSequence());
        SEQUENCES.put(player.getUUID(), request.requestSequence());
        if (level.getEntity(capture.owner().source()) != capture.source()) {
            LOGGER.debug("ChemMaster dump capture phase=RELEASE gesture={} seq={} reason=source_changed", token(request), request.requestSequence());
            reply(player, request, false, "source changed"); return;
        }
        BlockPos pos = request.target().orElseThrow();
        if (!level.hasChunkAt(pos)) { reply(player, request, false, "target unavailable"); return; }
        ChemMasterBlockEntity machine = nominatedChemMaster(level, pos);
        if (machine == null) {
            reply(player, request, false, "not aimed at a ChemMaster"); return;
        }
        var result = ChemMasterWorldDump.transfer(player, capture.expected(), pos, machine.placementIdentity(),
                machine.stateRevision(), machine.bufferRevision());
        if (result == ChemMasterWorldDump.Result.SUCCESS) {
            com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterSessionService.machineChanged(machine);
            com.juicyslew.moonstation14.ms14.chemistry.ChemMasterDumpAudio.playAccepted(level, pos);
        }
        reply(player, request, result == ChemMasterWorldDump.Result.SUCCESS,
                result == ChemMasterWorldDump.Result.SUCCESS ? null : "dump rejected");
    }

    /** Read-only release target nomination; the transaction remains the source/target authority. */
    public static boolean nominatesChemMaster(ServerLevel level, BlockPos pos) {
        return nominatedChemMaster(level, pos) != null;
    }

    private static ChemMasterBlockEntity nominatedChemMaster(ServerLevel level, BlockPos pos) {
        // Client position is only a nomination. Exact loaded BE identity is resolved here; WorldDump
        // independently enforces actor-position range and LOS to both source and target at commit.
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return null;
        var be = chunk.getBlockEntities().get(pos);
        return be instanceof ChemMasterBlockEntity machine && machine.isLoadedValid() ? machine : null;
    }

    private static void reply(ServerPlayer player, ChemMasterDumpRequest request, boolean accepted, String reason) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ChemMasterDumpResult(request.gestureId(), request.phase(), accepted,
                        reason == null ? java.util.Optional.empty() : java.util.Optional.of(Component.literal(reason))));
    }
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Capture capture = CAPTURES.get(p.getUUID());
            if (capture != null) LOGGER.debug("ChemMaster dump capture phase=LOGOUT gesture={} seq={} reason=logout", token(capture.owner().gesture()), capture.owner().sequence());
            CAPTURES.removeIf(p.getUUID(), c -> true); SEQUENCES.remove(p.getUUID());
        }
    }
    public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        var server = event.getServer();
        CAPTURES.removeInvalid((playerId, capture) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) return "PLAYER_OFFLINE";
            if (player.level() != capture.level()) return "LEVEL_CHANGED";
            if (capture.level().getEntity(capture.owner().source()) != capture.source()) return "SOURCE_REPLACED_OR_REMOVED";
            var authority = BodyActionAuthority.resolve(player).orElse(null);
            if (authority == null) return "NO_BODY_AUTHORITY";
            if (authority.identity().epoch() != capture.owner().epoch()) return "EPOCH_CHANGED";
            if (!authority.body().getUUID().equals(capture.owner().body())) return "BODY_CHANGED";
            if (!BodyActionAuthority.eligibleForComplexInteraction(player, authority)) return "COMPLEX_INELIGIBLE";
            return null;
        }, (playerId, capture, reason) -> LOGGER.debug("ChemMaster dump capture phase=TICK_REMOVE gesture={} seq={} reason={}", token(capture.owner().gesture()), capture.owner().sequence(), reason));
    }
    public static void serverStopped(ServerStoppedEvent event) {
        // State has no iteration API; cleanup remains unconditional and capture owners do not survive shutdown.
        CAPTURES.clear(); SEQUENCES.clear();
    }
    private static String token(ChemMasterDumpRequest request) { return token(request.gestureId()); }
    private static String token(UUID gesture) { return gesture.toString().substring(0, 8); }
}
