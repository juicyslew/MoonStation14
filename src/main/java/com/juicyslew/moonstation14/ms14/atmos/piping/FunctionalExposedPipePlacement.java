package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.player_body_control.action.CharacterAppliedSneak;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.function.BooleanSupplier;

/** Server-authoritative first-time functional placement of an exposed pipe in air. */
public final class FunctionalExposedPipePlacement {
    private FunctionalExposedPipePlacement() { }

    public enum Result { INSTALLED, DENIED, RECOVERY_REQUIRED }

    public static Result place(ServerPlayer actor, BodyBlockUseRequest request,
                                BodyHandRequestService.BlockUseAdmission admission) {
        var authority = admission.authority();
        var hands = admission.hands();
        var hit = admission.hit();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level)) return Result.DENIED;
        var stack = hands.stackCopy(request.handId()).orElse(null);
        int lane = stack == null ? 0 : BodyPipePlacementPolicy.selectedLayer(stack).orElse(0);
        if (lane == 0 || !request.handId().equals(hands.activeHand())) return Result.DENIED;
        if (!(body instanceof net.minecraft.world.entity.Mob mob)) return Result.DENIED;
        BlockPos clicked = hit.pos();
        long x = (long) clicked.getX() + hit.face().getStepX();
        long y = (long) clicked.getY() + hit.face().getStepY();
        long z = (long) clicked.getZ() + hit.face().getStepZ();
        BlockPos destination = null;
        if (x >= Integer.MIN_VALUE && x <= Integer.MAX_VALUE && y >= Integer.MIN_VALUE && y <= Integer.MAX_VALUE
                && z >= Integer.MIN_VALUE && z <= Integer.MAX_VALUE) destination = new BlockPos((int) x, (int) y, (int) z);
        boolean clickedLoaded = level.getWorldBorder().isWithinBounds(clicked) && level.isInWorldBounds(clicked)
                && !level.isOutsideBuildHeight(clicked) && fullChunk(level, clicked);
        boolean destinationLoaded = destination != null && level.getWorldBorder().isWithinBounds(destination)
                && level.isInWorldBounds(destination) && !level.isOutsideBuildHeight(destination) && fullChunk(level, destination);
        if (!clickedLoaded) return Result.DENIED;
        BlockState clickedBefore = level.getBlockState(clicked);
        BlockEntity clickedEntityBefore = level.getBlockEntity(clicked);
        boolean wall = SealedWallHostEligibility.defaults().eligible(level, clicked) && clickedEntityBefore == null;
        boolean pipeMissing = clickedBefore.is(ModBlocks.GAS_PIPE_VISUAL.get())
                && clickedEntityBefore instanceof VisualPipeBlockEntity visual && !visual.hasLayer(lane);
        BlockState original = null;
        BlockEntity originalEntity = null;
        boolean air = false;
        if (destinationLoaded) {
            original = level.getBlockState(destination);
            originalEntity = level.getBlockEntity(destination);
            air = original.isAir() && level.getFluidState(destination).isEmpty() && originalEntity == null;
        }
        boolean appliedSneak = CharacterAppliedSneak.isApplied(actor, mob, authority.epoch());
        var intent = PipePlacementIntentPolicy.choose(request.sneaking(), appliedSneak, wall, pipeMissing, air);
        if (intent == PipePlacementIntentPolicy.Intent.SEALED_WALL)
            return addSealedWallSegment(actor, request, admission, level, lane, stack);
        if (intent == PipePlacementIntentPolicy.Intent.CROSSING)
            return addCrossingLane(actor, request, admission, level, lane, stack);
        if (intent != PipePlacementIntentPolicy.Intent.EXPOSED) return Result.DENIED;
        var policy = BodyPipePlacementPolicy.check(actor, authority, hands, request.handId(),
                request.expectedToken(), request.expectedRevision(), stack, clicked, hit.face(), destination);
        if (!policy.allowed()) return Result.DENIED;
        if (!fullChunk(level, clicked) || !fullChunk(level, destination)
                || level.getBlockState(clicked) != clickedBefore || level.getBlockEntity(clicked) != clickedEntityBefore
                || level.getBlockState(destination) != original || level.getBlockEntity(destination) != originalEntity)
            return Result.DENIED;
        var block = ModBlocks.GAS_PIPE_VISUAL.get();
        BlockState placement = block.defaultBlockState();
        if (!placement.canSurvive(level, destination)) return Result.DENIED;
        if (BodyPlacementCollision.pipeIntersects(level, body, destination, lane)) return Result.DENIED;
        // The destination was preflighted as loaded AIR above. Retire only an orphaned
        // persisted owner there, before a replacement block/entity can be installed.
        if (!LoadedPipeGasOwner.retireOrphanAtAir(level, destination, lane)) return Result.RECOVERY_REQUIRED;
        try { if (!level.setBlock(destination, placement, 3)) return Result.DENIED; }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        BlockEntity created = level.getBlockEntity(destination);
        if (!(created instanceof VisualPipeBlockEntity)) return Result.RECOVERY_REQUIRED;
        boolean configured = created instanceof VisualPipeBlockEntity visual && visual.setLayersForPlacement(lane);
        var identity = configured ? LoadedPipeGasOwner.createVacuumLane(level, destination, lane) : java.util.Optional.<java.util.UUID>empty();
        if (identity.isEmpty()) {
            if (created instanceof VisualPipeBlockEntity visual && level.getBlockEntity(destination) == created
                    && level.getBlockState(destination).is(block) && visual.functionalIdentity(lane).isEmpty()) {
                try { level.setBlock(destination, original, 3); } catch (RuntimeException ignored) { }
            }
            return Result.DENIED;
        }
        if (level.getBlockEntity(destination) != created || !level.getBlockState(destination).is(block)
                || LoadedPipeGasOwner.activeSnapshot(level, destination, lane)
                .filter(entry -> entry.incarnation().equals(identity.orElseThrow())).isEmpty())
            return Result.RECOVERY_REQUIRED;
        BodyHandItemTransfer.Result consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority,
                request.handId(), request.expectedToken(), request.expectedRevision(), stack);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        boolean rolledBack = LoadedPipeGasOwner.rollbackFreshVacuumLane(level, destination, lane, identity.orElseThrow());
        if (rolledBack && level.getBlockEntity(destination) == created && level.getBlockState(destination).is(block)
                && original.isAir() && originalEntity == null) {
            BlockState before = original;
            BlockEntity beforeEntity = originalEntity;
            BlockPos restorePos = destination;
            return restoreAfterFailedConsumption(level, restorePos, before, beforeEntity,
                    () -> level.setBlock(restorePos, before, 3));
        }
        return Result.RECOVERY_REQUIRED;
    }

    /** DENIED is safe only when the exact pre-placement block and entity have been restored. */
    static Result restoreAfterFailedConsumption(ServerLevel level, BlockPos pos, BlockState original,
                                                BlockEntity originalEntity, BooleanSupplier restore) {
        try {
            if (!restore.getAsBoolean()) return Result.RECOVERY_REQUIRED;
            return level.getBlockState(pos) == original && level.getBlockEntity(pos) == originalEntity
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) {
            return Result.RECOVERY_REQUIRED;
        }
    }

    private static Result addSealedWallSegment(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, ServerLevel level, int laneId,
            net.minecraft.world.item.ItemStack stack) {
        var hit = admission.hit();
        BlockPos clicked = hit.pos();
        if (!level.getWorldBorder().isWithinBounds(clicked) || !level.isInWorldBounds(clicked)
                || level.isOutsideBuildHeight(clicked) || !fullChunk(level, clicked)
                || !SealedWallHostEligibility.defaults().eligible(level, clicked)
                || level.getBlockEntity(clicked) != null) return Result.DENIED;
        BlockState before = level.getBlockState(clicked);
        PipeTopology.Layer lane = switch (laneId) {
            case VisualPipeBlockEntity.PRIMARY -> PipeTopology.Layer.PRIMARY;
            case VisualPipeBlockEntity.SECONDARY -> PipeTopology.Layer.SECONDARY;
            case VisualPipeBlockEntity.TERTIARY -> PipeTopology.Layer.TERTIARY;
            default -> null;
        };
        if (lane == null) return Result.DENIED;
        if (BodyPlacementCollision.concealedArmIntersects(level, admission.authority().body(), clicked,
                hit.face(), laneId)) return Result.DENIED;
        var policy = BodyPipePlacementPolicy.check(actor, admission.authority(), admission.hands(), request.handId(),
                request.expectedToken(), request.expectedRevision(), stack, clicked, hit.face(), clicked);
        if (!policy.allowed() || !fullChunk(level, clicked) || !SealedWallHostEligibility.defaults().eligible(level, clicked)
                || (level.getBlockState(clicked) != before && !level.getBlockState(clicked).equals(before))
                || level.getBlockEntity(clicked) != null) return Result.DENIED;
        java.util.Optional<PipeChunkData.Record> prepared;
        try { prepared = LoadedSealedWallSegment.prepareOne(level, clicked, hit.face(), lane, java.util.UUID.randomUUID()); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        if (prepared.isEmpty()) return Result.DENIED;
        var record = prepared.orElseThrow();
        java.util.UUID id = record.id();
        java.util.UUID generation;
        try { generation = java.util.UUID.fromString(record.hostIncarnation()); }
        catch (IllegalArgumentException malformed) { return Result.RECOVERY_REQUIRED; }
        if ((level.getBlockState(clicked) != before && !level.getBlockState(clicked).equals(before))
                || level.getBlockEntity(clicked) != null) return Result.RECOVERY_REQUIRED;
        if (!LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level, clicked, lane)) {
            return rollbackWallAdmission(level, clicked, lane, id, generation, before)
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if ((level.getBlockState(clicked) != before && !level.getBlockState(clicked).equals(before))
                || level.getBlockEntity(clicked) != null
                || LoadedSealedWallGasOwner.activeSnapshot(level, clicked, lane)
                .filter(entry -> entry.incarnation().equals(id)).isEmpty()) return Result.RECOVERY_REQUIRED;
        var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, admission.authority(), request.handId(),
                request.expectedToken(), request.expectedRevision(), stack);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            VisualPipeBlockEntity.refreshLoadedAdjacent(level, clicked);
            actor.sendSystemMessage(net.minecraft.network.chat.Component.translatable(switch (laneId) {
                case VisualPipeBlockEntity.PRIMARY -> "message.moonstation14.pipe.installed_through_steel_wall.primary";
                case VisualPipeBlockEntity.SECONDARY -> "message.moonstation14.pipe.installed_through_steel_wall.secondary";
                case VisualPipeBlockEntity.TERTIARY -> "message.moonstation14.pipe.installed_through_steel_wall.tertiary";
                default -> throw new IllegalStateException("Validated wall lane changed");
            }));
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        return rollbackWallAdmission(level, clicked, lane, id, generation, before)
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean rollbackWallAdmission(ServerLevel level, BlockPos pos, PipeTopology.Layer lane,
                                                 java.util.UUID id, java.util.UUID generation, BlockState host) {
        if ((level.getBlockState(pos) != host && !level.getBlockState(pos).equals(host))
                || level.getBlockEntity(pos) != null) return false;
        if (LoadedSealedWallGasOwner.rollbackFreshVacuumOwner(level, pos, lane, id)
                != LoadedSealedWallGasOwner.RollbackResult.ROLLED_BACK) return false;
        return LoadedSealedWallSegment.rollbackFreshRecord(level, pos, lane, id, generation)
                == LoadedSealedWallSegment.RollbackResult.ROLLED_BACK;
    }

    private static Result addCrossingLane(ServerPlayer actor, BodyBlockUseRequest request,
            BodyHandRequestService.BlockUseAdmission admission, ServerLevel level, int lane,
            net.minecraft.world.item.ItemStack stack) {
        var hit = admission.hit();
        BlockPos clicked = hit.pos();
        if (!level.getWorldBorder().isWithinBounds(clicked) || !level.isInWorldBounds(clicked)
                || level.isOutsideBuildHeight(clicked) || !fullChunk(level, clicked)
                || !level.getBlockState(clicked).is(ModBlocks.GAS_PIPE_VISUAL.get())
                || !(level.getBlockEntity(clicked) instanceof VisualPipeBlockEntity visual)
                || visual.hasLayer(lane)) return Result.DENIED;
        int originalMask = visual.getLayerMask();
        var oldIdentities = visual.functionalIdentitiesSnapshot();
        boolean trustedExistingLane = false;
        for (int existing : new int[]{VisualPipeBlockEntity.PRIMARY, VisualPipeBlockEntity.SECONDARY,
                VisualPipeBlockEntity.TERTIARY}) {
            if ((originalMask & existing) == 0 || visual.functionalIdentity(existing).isEmpty()) continue;
            if (LoadedPipeGasOwner.activeSnapshot(level, clicked, existing).isPresent()) {
                trustedExistingLane = true;
                break;
            }
        }
        if (!trustedExistingLane) return Result.DENIED;
        var policy = BodyPipePlacementPolicy.check(actor, admission.authority(), admission.hands(), request.handId(),
                request.expectedToken(), request.expectedRevision(), stack, clicked, hit.face(), clicked);
        if (!policy.allowed() || level.getBlockEntity(clicked) != visual || visual.getLayerMask() != originalMask
                || !visual.functionalIdentitiesSnapshot().equals(oldIdentities)) return Result.DENIED;
        if (BodyPlacementCollision.pipeIntersects(level, admission.authority().body(), clicked, lane)) return Result.DENIED;
        if (!visual.addFunctionalPlacementLayer(lane)) return Result.DENIED;
        var identity = LoadedPipeGasOwner.createVacuumLane(level, clicked, lane);
        if (identity.isEmpty()) {
            return visual.rollbackAddedFunctionalLayer(lane, originalMask | lane, oldIdentities)
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        if (level.getBlockEntity(clicked) != visual || !level.getBlockState(clicked).is(ModBlocks.GAS_PIPE_VISUAL.get())
                || LoadedPipeGasOwner.activeSnapshot(level, clicked, lane)
                .filter(entry -> entry.incarnation().equals(identity.orElseThrow())).isEmpty())
            return Result.RECOVERY_REQUIRED;
        var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, admission.authority(), request.handId(),
                request.expectedToken(), request.expectedRevision(), stack);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        boolean identityRollback = LoadedPipeGasOwner.rollbackFreshVacuumLane(level, clicked, lane, identity.orElseThrow());
        boolean maskRollback = identityRollback && level.getBlockEntity(clicked) == visual
                && visual.rollbackAddedFunctionalLayer(lane, originalMask | lane, oldIdentities);
        return maskRollback ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean fullChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                ChunkStatus.FULL, false) instanceof LevelChunk;
    }
}
