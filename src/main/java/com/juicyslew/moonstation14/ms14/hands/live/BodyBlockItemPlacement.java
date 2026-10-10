package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.custom.CreativeGasPipeVisualItem;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/** Ordinary registered project BlockItem placement from the authenticated controlled body's hand. */
public final class BodyBlockItemPlacement {
    private BodyBlockItemPlacement() { }

    public static boolean supports(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)
                || stack.getItem() instanceof JugItem || stack.getItem() instanceof CreativeGasPipeVisualItem) return false;
        Block block = item.getBlock();
        return block == ModBlocks.STEEL_WALL_BLOCK.get() || block == ModBlocks.STATION_FLOOR.get()
                || block == ModBlocks.STEEL_WALL_GIRDER_BLOCK.get() || block == ModBlocks.MAGIC_BLOCK.get()
                || block == ModBlocks.ATMOS_AIR_PRODUCER.get() || block == ModBlocks.ATMOS_OXYGEN_PRODUCER.get()
                || block == ModBlocks.ATMOS_NITROGEN_PRODUCER.get() || block == ModBlocks.ATMOS_CARBON_DIOXIDE_PRODUCER.get()
                || block == ModBlocks.ATMOS_PLASMA_PRODUCER.get() || block == ModBlocks.ATMOS_TRITIUM_PRODUCER.get()
                || block == ModBlocks.ATMOS_WATER_VAPOR_PRODUCER.get() || block == ModBlocks.ATMOS_AMMONIA_PRODUCER.get()
                || block == ModBlocks.ATMOS_NITROUS_OXIDE_PRODUCER.get() || block == ModBlocks.ATMOS_FREZON_PRODUCER.get()
                || block == ModBlocks.ATMOS_GAS_SINK.get() || block == ModBlocks.ATMOS_HEATER.get()
                || block == ModBlocks.ATMOS_COOLER.get() || block == ModBlocks.HV_SOURCE.get()
                || block == ModBlocks.HV_MV_SUBSTATION.get() || block == ModBlocks.APC.get()
                 || block == ModBlocks.POWER_LAMP.get() || block == ModBlocks.HIGH_LOAD_TEST_LAMP.get()
                 || block == ModBlocks.AIRLOCK.get() || block == ModBlocks.CHEM_MASTER.get();
    }

    public static BodyBlockUseResult.Outcome place(ServerPlayer actor, BodyBlockUseRequest request,
                                                    BodyHandRequestService.BlockUseAdmission admission) {
        if (actor == null || request == null || admission == null || request.intent() != BodyBlockUseRequest.Intent.USE)
            return BodyBlockUseResult.Outcome.DENIED;
        var authority = admission.authority();
        var body = authority.body();
        var hands = admission.hands();
        ItemStack stack = hands.stackCopy(request.handId()).orElse(null);
        if (!supports(stack) || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || !hands.compatible(body) || hands.revision() != request.expectedRevision()
                || !request.handId().equals(hands.activeHand())
                || !hands.token(request.handId()).filter(request.expectedToken()::equals).isPresent()
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !level.getServer().isSameThread()) return BodyBlockUseResult.Outcome.DENIED;
        BlockPos clicked = admission.hit().pos();
        if (!level.hasChunkAt(clicked) || !level.getWorldBorder().isWithinBounds(clicked)
                || level.isOutsideBuildHeight(clicked) || !level.mayInteract(actor, clicked)) return BodyBlockUseResult.Outcome.DENIED;
        BlockState oldClicked = level.getBlockState(clicked);
        BlockPos dest = oldClicked.canBeReplaced() ? clicked : clicked.relative(admission.hit().face());
        if (!level.hasChunkAt(dest) || !level.isInWorldBounds(dest) || level.isOutsideBuildHeight(dest)
                || !level.getWorldBorder().isWithinBounds(dest) || !level.mayInteract(actor, dest)) return BodyBlockUseResult.Outcome.DENIED;
        BlockState old = level.getBlockState(dest);
        FluidState fluid = level.getFluidState(dest);
        if (!old.canBeReplaced() || !fluid.isEmpty() || level.getBlockEntity(dest) != null) return BodyBlockUseResult.Outcome.DENIED;
        var hit = new BlockHitResult(admission.hit().location(), admission.hit().face(), clicked, false);
        if (!(body instanceof net.minecraft.world.entity.player.Player bodyPlayer)) return BodyBlockUseResult.Outcome.DENIED;
        var useContext = new net.minecraft.world.item.context.UseOnContext(level, bodyPlayer,
                net.minecraft.world.InteractionHand.MAIN_HAND, stack.copy(), hit) {
            @Override public boolean isSecondaryUseActive() { return request.sneaking(); }
        };
        BlockPlaceContext context = new BlockPlaceContext(useContext) {
            @Override public boolean isSecondaryUseActive() { return request.sneaking(); }
        };
        BlockItem item = (BlockItem) stack.getItem();
        BlockState placed = item.getBlock().getStateForPlacement(context);
        if (placed == null || !placed.canSurvive(level, dest) || !level.isUnobstructed(placed, dest,
                net.minecraft.world.phys.shapes.CollisionContext.of(body))
                || !HandActorAuthority.revalidate(actor, authority)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                || hands.revision() != request.expectedRevision()) return BodyBlockUseResult.Outcome.DENIED;
        if (!level.setBlock(dest, placed, Block.UPDATE_ALL)) return BodyBlockUseResult.Outcome.DENIED;
        var ownedBlockEntity = level.getBlockEntity(dest);
        try {
            item.updateCustomBlockEntityTag(level, bodyPlayer, dest, stack.copy());
            if (ownedBlockEntity instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity chemMaster)
                chemMaster.resetPlacementIdentity();
            item.getBlock().setPlacedBy(level, dest, placed, body, stack.copy());
        } catch (RuntimeException failure) {
            return rollbackPlacement(level, dest, placed, ownedBlockEntity, old)
                    ? BodyBlockUseResult.Outcome.DENIED : BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        }
        try {
            BodyHandItemTransfer.Result consumed = BodyHandItemTransfer.consumeOneForAuthorizedBody(actor, authority,
                    request.handId(), request.expectedToken(), request.expectedRevision(), stack);
            if (consumed != BodyHandItemTransfer.Result.SUCCESS) {
                if (consumed != BodyHandItemTransfer.Result.DENIED)
                    return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
                boolean restored = rollbackPlacement(level, dest, placed, ownedBlockEntity, old);
                return restored
                        ? BodyBlockUseResult.Outcome.DENIED : BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
            }
            // Once consumption commits, never undo the corresponding world placement.
            try {
                level.playSound(null, dest, placed.getSoundType().getPlaceSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                        (placed.getSoundType().getVolume() + 1.0F) / 2.0F, placed.getSoundType().getPitch() * 0.8F);
            } catch (RuntimeException ignored) { }
            return BodyBlockUseResult.Outcome.PLACED;
        } catch (RuntimeException failure) {
            return BodyBlockUseResult.Outcome.RECOVERY_REQUIRED;
        }
    }

    private static boolean rollbackPlacement(net.minecraft.server.level.ServerLevel level, BlockPos pos,
            BlockState placed, net.minecraft.world.level.block.entity.BlockEntity ownedBlockEntity, BlockState old) {
        if (level.getBlockState(pos) != placed || level.getBlockEntity(pos) != ownedBlockEntity) return false;
        try {
            return level.setBlock(pos, old, Block.UPDATE_ALL) && level.getBlockState(pos) == old
                    && level.getBlockEntity(pos) == null;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
