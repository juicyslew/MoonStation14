package com.juicyslew.moonstation14.ms14.disposal.unit;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.piping.BodyPlacementCollision;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/** Server-authoritative placement of the catalog disposal-unit item by the active body hand. */
public final class FunctionalDisposalUnitPlacement {
    private FunctionalDisposalUnitPlacement() { }
    public enum Result { INSTALLED, DENIED, RECOVERY_REQUIRED }

    public static Result place(ServerPlayer actor, BodyBlockUseRequest request,
                               BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null) return Result.DENIED;
        var authority = admission.authority();
        var hands = admission.hands();
        var hit = admission.hit();
        var body = authority.body();
        if (!(body.level() instanceof ServerLevel level) || !HandActorAuthority.revalidate(actor, authority)) return Result.DENIED;
        ItemStack held = hands.stackCopy(request.handId()).orElse(null);
        if (!isUnitStack(held) || !request.handId().equals(hands.activeHand())) return Result.DENIED;
        BlockPos clicked = hit.pos();
        long x = (long) clicked.getX() + hit.face().getStepX(), y = (long) clicked.getY() + hit.face().getStepY(), z = (long) clicked.getZ() + hit.face().getStepZ();
        if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || y < Integer.MIN_VALUE || y > Integer.MAX_VALUE
                || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) return Result.DENIED;
        BlockPos destination = new BlockPos((int) x, (int) y, (int) z);
        if (!positionsAllowed(level, actor, clicked, destination) || !handsValid(actor, authority, hands, request, held)) return Result.DENIED;
        BlockState clickedBefore = level.getBlockState(clicked), original = level.getBlockState(destination);
        BlockEntity clickedEntityBefore = level.getBlockEntity(clicked), originalEntity = level.getBlockEntity(destination);
        if (!destinationAvailable(original, originalEntity != null, level.getFluidState(destination).isEmpty())) return Result.DENIED;
        var resolvedPrototype = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverDisposals().get(
                com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.UNIT);
        if (resolvedPrototype == null || resolvedPrototype.unit() == null) return Result.DENIED;
        BlockState placement = ModBlocks.DISPOSAL_UNIT.get().defaultBlockState();
        if (BodyPlacementCollision.blockIntersects(level, body, destination, placement)
                || intersectsOtherEntities(level, body, destination, placement)) return Result.DENIED;
        if (!positionsAllowed(level, actor, clicked, destination) || !handsValid(actor, authority, hands, request, held)
                || level.getBlockState(clicked) != clickedBefore || level.getBlockEntity(clicked) != clickedEntityBefore
                || level.getBlockState(destination) != original || level.getBlockEntity(destination) != originalEntity) return Result.DENIED;
        try { if (!level.setBlock(destination, placement, 3)) return Result.DENIED; }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        BlockEntity created = level.getBlockEntity(destination);
        if (!(created instanceof DisposalUnitBlockEntity unit) || !resolvedPrototype.unit().equals(unit.prototype())
                || !level.getBlockState(destination).is(ModBlocks.DISPOSAL_UNIT.get())) return Result.RECOVERY_REQUIRED;
        var consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority, request.handId(), request.expectedToken(), request.expectedRevision(), held);
        if (consumed == BodyHandItemTransfer.Result.SUCCESS) {
            BodyHandStateService.requireFreshQuery(actor);
            return Result.INSTALLED;
        }
        if (consumed == BodyHandItemTransfer.Result.RECOVERY_REQUIRED) return Result.RECOVERY_REQUIRED;
        if (level.getBlockEntity(destination) != created || !level.getBlockState(destination).is(ModBlocks.DISPOSAL_UNIT.get())) return Result.RECOVERY_REQUIRED;
        try {
            if (!level.setBlock(destination, original, 3) || level.getBlockEntity(destination) != originalEntity
                    || level.getBlockState(destination) != original) return Result.RECOVERY_REQUIRED;
        } catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return Result.DENIED;
    }

    static boolean isUnitStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.DISPOSAL_UNIT.get()) && stack.getCount() == 1;
    }

    static boolean destinationAvailable(BlockState state, boolean hasBlockEntity, boolean emptyFluid) {
        return state != null && state.isAir() && !hasBlockEntity && emptyFluid;
    }

    private static boolean intersectsOtherEntities(ServerLevel level, net.minecraft.world.entity.LivingEntity body,
                                                    BlockPos pos, BlockState placement) {
        var shape = placement.getCollisionShape(level, pos, net.minecraft.world.phys.shapes.CollisionContext.of(body));
        for (AABB local : shape.toAabbs()) {
            AABB world = local.move(pos);
            if (!level.getEntities(body, world, FunctionalDisposalUnitPlacement::isPlacementOccupant).isEmpty()) return true;
        }
        return false;
    }

    static boolean isPlacementOccupant(net.minecraft.world.entity.Entity entity) {
        return entity != null && entity.isAlive() && !entity.isSpectator() && !entity.noPhysics
                && (entity instanceof net.minecraft.world.entity.LivingEntity
                || entity instanceof net.minecraft.world.entity.vehicle.VehicleEntity);
    }

    private static boolean handsValid(ServerPlayer actor, HandActorAuthority.Snapshot authority,
            com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands, BodyBlockUseRequest request, ItemStack expected) {
        if (!HandActorAuthority.revalidate(actor, authority) || authority.body().getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(authority.body()) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand()) || hands.token(request.handId()).filter(request.expectedToken()::equals).isEmpty()) return false;
        ItemStack actual = hands.stackCopy(request.handId()).orElse(null);
        return isUnitStack(actual) && actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static boolean positionsAllowed(ServerLevel level, ServerPlayer actor, BlockPos clicked, BlockPos destination) {
        return full(level, clicked) && full(level, destination) && level.getWorldBorder().isWithinBounds(clicked)
                && level.getWorldBorder().isWithinBounds(destination) && level.isInWorldBounds(clicked)
                && level.isInWorldBounds(destination) && !level.isOutsideBuildHeight(clicked)
                && !level.isOutsideBuildHeight(destination) && level.mayInteract(actor, clicked) && level.mayInteract(actor, destination);
    }
    private static boolean full(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) instanceof LevelChunk;
    }
}

