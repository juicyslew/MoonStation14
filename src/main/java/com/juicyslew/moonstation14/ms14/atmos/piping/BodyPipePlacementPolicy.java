package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/** Read-only host-world protection preflight for body-origin exposed pipe placement. */
public final class BodyPipePlacementPolicy {
    private BodyPipePlacementPolicy() { }

    static java.util.Optional<Integer> selectedLayer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return java.util.Optional.empty();
        if (stack.is(ModItems.GAS_PIPE_SEGMENT.get())) return java.util.Optional.of(VisualPipeBlockEntity.PRIMARY);
        if (stack.is(ModItems.GAS_PIPE_SEGMENT_SECONDARY.get())) return java.util.Optional.of(VisualPipeBlockEntity.SECONDARY);
        if (stack.is(ModItems.GAS_PIPE_SEGMENT_TERTIARY.get())) return java.util.Optional.of(VisualPipeBlockEntity.TERTIARY);
        return java.util.Optional.empty();
    }

    public enum Blocker {
        NONE, INVALID_AUTHORITY, WRONG_BODY, INVALID_STACK, STACK_NOT_BODY_HELD,
        NOT_SERVER_THREAD, UNLOADED_POSITION, OUTSIDE_WORLD, OUTSIDE_BUILD_HEIGHT,
        CLICKED_PROTECTED, DESTINATION_PROTECTED, ITEM_USE_DENIED, NO_CONTROLLER_PERMISSION_CONTRACT
    }

    public record Result(boolean allowed, Blocker blocker) {
        private static Result allow() { return new Result(true, Blocker.NONE); }
        private static Result deny(Blocker reason) { return new Result(false, reason); }
    }

    /**
     * Legacy overload has no hand/token/revision proof and therefore cannot safely admit placement.
     */
    public static Result check(ServerPlayer controller, LivingEntity body, ItemStack bodyStack,
                               BlockPos clicked, Direction face, BlockPos destination) {
        var resolved = HandActorAuthority.resolve(controller);
        if (resolved.isEmpty()) return Result.deny(Blocker.INVALID_AUTHORITY);
        var authority = resolved.orElseThrow();
        if (authority.body() != body) return Result.deny(Blocker.WRONG_BODY);
        if (bodyStack == null || bodyStack.isEmpty()) return Result.deny(Blocker.INVALID_STACK);
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body) || hands.handIds().stream()
                .map(hands::stackCopy).flatMap(java.util.Optional::stream)
                .noneMatch(stack -> stack.getCount() == bodyStack.getCount()
                        && ItemStack.isSameItemSameComponents(stack, bodyStack)))
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return Result.deny(Blocker.NOT_SERVER_THREAD);
        if (clicked == null || face == null || destination == null) return Result.deny(Blocker.OUTSIDE_WORLD);
        if (!fullChunk(level, clicked) || !fullChunk(level, destination))
            return Result.deny(Blocker.UNLOADED_POSITION);
        if (!level.getWorldBorder().isWithinBounds(clicked) || !level.getWorldBorder().isWithinBounds(destination)
                || !level.isInWorldBounds(clicked) || !level.isInWorldBounds(destination))
            return Result.deny(Blocker.OUTSIDE_WORLD);
        if (level.isOutsideBuildHeight(clicked) || level.isOutsideBuildHeight(destination))
            return Result.deny(Blocker.OUTSIDE_BUILD_HEIGHT);
        if (!level.mayInteract(controller, clicked)) return Result.deny(Blocker.CLICKED_PROTECTED);
        if (!level.mayInteract(controller, destination)) return Result.deny(Blocker.DESTINATION_PROTECTED);
        return Result.deny(Blocker.NO_CONTROLLER_PERMISSION_CONTRACT);
    }

    /** Exact live-hand admission; no carrier inventory is consulted. */
    public static Result check(ServerPlayer controller, HandActorAuthority.Snapshot exact,
                               LiveHands exactHands, String handId, String token, long revision,
                               ItemStack bodyStack, BlockPos clicked, Direction face, BlockPos destination) {
        if (controller == null || exact == null || exact.actor() != controller
                || handId == null || token == null
                || !HandActorAuthority.revalidate(controller, exact))
            return Result.deny(Blocker.INVALID_AUTHORITY);
        LivingEntity body = exact.body();
        if (!HandActorAuthority.revalidate(controller, exact)) return Result.deny(Blocker.INVALID_AUTHORITY);
        if (bodyStack == null || bodyStack.isEmpty()) return Result.deny(Blocker.INVALID_STACK);
        if (selectedLayer(bodyStack).isEmpty()) return Result.deny(Blocker.INVALID_STACK);
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (exactHands == null || current != exactHands || !exactHands.compatible(body)
                || !exact.handIds().contains(handId) || !handId.equals(exactHands.activeHand())
                || exactHands.revision() != revision || exactHands.token(handId).filter(token::equals).isEmpty())
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        ItemStack held = exactHands.stackCopy(handId).orElse(null);
        if (held == null || held.getCount() != bodyStack.getCount()
                || !ItemStack.isSameItemSameComponents(held, bodyStack))
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread()) return Result.deny(Blocker.NOT_SERVER_THREAD);
        if (clicked == null || face == null || destination == null) return Result.deny(Blocker.OUTSIDE_WORLD);
        if (!fullChunk(level, clicked) || !fullChunk(level, destination)) return Result.deny(Blocker.UNLOADED_POSITION);
        if (!level.getWorldBorder().isWithinBounds(clicked) || !level.getWorldBorder().isWithinBounds(destination)
                || !level.isInWorldBounds(clicked) || !level.isInWorldBounds(destination))
            return Result.deny(Blocker.OUTSIDE_WORLD);
        if (level.isOutsideBuildHeight(clicked) || level.isOutsideBuildHeight(destination))
            return Result.deny(Blocker.OUTSIDE_BUILD_HEIGHT);
        if (!level.mayInteract(controller, clicked)) return Result.deny(Blocker.CLICKED_PROTECTED);
        if (!level.mayInteract(controller, destination)) return Result.deny(Blocker.DESTINATION_PROTECTED);
        // Narrow spectator exception: authenticated character controller, exact active body hand,
        // and vanilla interaction checks above. Do not use mayUseItemAt: spectator denial there is
        // unrelated to these controller-identity checks. Third-party claims must integrate via event.
        if (!HandActorAuthority.revalidate(controller, exact)
                || exactHands.revision() != revision || !handId.equals(exactHands.activeHand())
                || exactHands.token(handId).filter(token::equals).isEmpty())
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        ItemStack finalHeld = exactHands.stackCopy(handId).orElse(null);
        if (finalHeld == null || finalHeld.getCount() != bodyStack.getCount()
                || !ItemStack.isSameItemSameComponents(finalHeld, bodyStack)
                || selectedLayer(finalHeld).isEmpty())
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        BlockState clickedBefore = level.getBlockState(clicked);
        BlockState destinationBefore = level.getBlockState(destination);
        BlockEntity clickedEntityBefore = level.getBlockEntity(clicked);
        BlockEntity destinationEntityBefore = level.getBlockEntity(destination);
        BodyPipePlacementPermissionEvent event = new BodyPipePlacementPermissionEvent(
                controller, body, bodyStack, clicked, face, destination);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return Result.deny(Blocker.NO_CONTROLLER_PERMISSION_CONTRACT);
        if (!HandActorAuthority.revalidate(controller, exact)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != exactHands
                || !exactHands.compatible(body) || exactHands.revision() != revision
                || !handId.equals(exactHands.activeHand())
                || exactHands.token(handId).filter(token::equals).isEmpty())
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        ItemStack afterEvent = exactHands.stackCopy(handId).orElse(null);
        if (afterEvent == null || selectedLayer(afterEvent).isEmpty()
                || afterEvent.getCount() != bodyStack.getCount()
                || !ItemStack.isSameItemSameComponents(afterEvent, bodyStack))
            return Result.deny(Blocker.STACK_NOT_BODY_HELD);
        if (!fullChunk(level, clicked) || !fullChunk(level, destination)
                || !level.getWorldBorder().isWithinBounds(clicked) || !level.getWorldBorder().isWithinBounds(destination)
                || !level.isInWorldBounds(clicked) || !level.isInWorldBounds(destination)
                || level.isOutsideBuildHeight(clicked) || level.isOutsideBuildHeight(destination)
                || !level.mayInteract(controller, clicked) || !level.mayInteract(controller, destination))
            return Result.deny(Blocker.NO_CONTROLLER_PERMISSION_CONTRACT);
        if (level.getBlockState(clicked) != clickedBefore || level.getBlockState(destination) != destinationBefore
                || level.getBlockEntity(clicked) != clickedEntityBefore
                || level.getBlockEntity(destination) != destinationEntityBefore)
            return Result.deny(Blocker.NO_CONTROLLER_PERMISSION_CONTRACT);
        return Result.allow();
    }

    private static boolean fullChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4,
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) instanceof LevelChunk;
    }
}
