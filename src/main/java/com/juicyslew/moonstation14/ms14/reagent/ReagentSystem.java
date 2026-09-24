package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.block.custom.PuddleBlock;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.TraitHandler;
import com.juicyslew.moonstation14.util.SystemLink;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


public class ReagentSystem {
    public static SystemLink<ReagentAttachment, ReagentComponent> bridge = MS14Bridges.REAGENT;

    // --- CORE LOGIC --- //
    public static void handleTransfer(TraitHandler<IReagentTrait> source, TraitHandler<IReagentTrait> target, Level level, float amount) {
        if (source.holder() == target.holder()) return;
        if (level.isClientSide) return;

        ReagentAttachment srcCont = MS14Provider.getDetached(source.holder(), bridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(srcCont.getMap(), level,
                "transfer source " + source.holder().getClass().getSimpleName())) return;
        ReagentAttachment dstCont = MS14Provider.getDetached(target.holder(), bridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                "transfer destination " + target.holder().getClass().getSimpleName())) return;
        var srcBefore = MS14Provider.snapshot(srcCont);
        var dstBefore = MS14Provider.snapshot(dstCont);

        transfer(level, srcCont, dstCont, amount, target.trait().getCapacity());

        MS14Provider.updateIfChanged(source.holder(), bridge, srcBefore, srcCont);
        MS14Provider.updateIfChanged(target.holder(), bridge, dstBefore, dstCont);
    }

    static void transfer(Level level, ReagentAttachment srcCont, ReagentAttachment dstCont, float amount, float target_capacity) {
        // Float APIs bound requested flow to cent precision; once admitted the paired
        // source/destination mutation is exact and capacity-conserving.
        // Spill-solution callers use Float.MAX_VALUE as an "all available" sentinel.
        long requested = amount == Float.MAX_VALUE ? ReagentUnits.MAX_CENTS
                : ReagentUnits.fromFloat(Math.max(0f, amount));
        long capacity = ReagentUnits.fromFloat(target_capacity);
        long admitted = ReagentAttachment.transferUnits(srcCont, dstCont, requested, capacity);
        if (admitted == 0) return;
        dstCont.recursiveReaction(level, target_capacity);
    }

    public static InteractionResult handleSpill(TraitHandler<IReagentTrait> source, Level level, BlockPos targetPos, float amount) {
        // TODO: Make this less item-use-centric Should be possible to spill from a player (vomitting) or even a jug (breaking with melee)

        if (amount < .001f){
            return InteractionResult.PASS;
        }
        BlockState puddleTargetState = level.getBlockState(targetPos);
        BlockPos abovePos = targetPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        ReagentAttachment srcCont = MS14Provider.getDetached(source.holder(), bridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(srcCont.getMap(), level,
                "spill source " + source.holder().getClass().getSimpleName())) return InteractionResult.PASS;
        if (puddleTargetState.is(ModBlocks.PUDDLE.get())) {
            // Add to existing puddle logic
            if (level.getBlockEntity(targetPos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                        "spill destination puddle")) return InteractionResult.PASS;
                var srcBefore = MS14Provider.snapshot(srcCont);
                var dstBefore = MS14Provider.snapshot(dstCont);
                transfer(level, srcCont, dstCont, amount, target.getCapacity());
                MS14Provider.updateIfChanged(source.holder(), bridge, srcBefore, srcCont);
                MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
            }
        }else if (aboveState.is(ModBlocks.PUDDLE.get())) {
            // for thrown jugs, and also like, if you clicked the block below somehow.
            // Add to existing puddle logic
            if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                        "spill destination puddle")) return InteractionResult.PASS;
                var srcBefore = MS14Provider.snapshot(srcCont);
                var dstBefore = MS14Provider.snapshot(dstCont);
                transfer(level, srcCont, dstCont, amount, target.getCapacity());
                MS14Provider.updateIfChanged(source.holder(), bridge, srcBefore, srcCont);
                MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
            }
        } else if (aboveState.canBeReplaced()) {
            // Interface Check
            if (!level.isClientSide) {
                if (!srcCont.isEmpty()) {
                    // Create the puddle
                    level.setBlock(abovePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);

                    // Transfer data to the new BlockEntity
                    if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                        ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                        var srcBefore = MS14Provider.snapshot(srcCont);
                        var dstBefore = MS14Provider.snapshot(dstCont);
                        transfer(level, srcCont, dstCont, amount, target.getCapacity());

                        MS14Provider.updateIfChanged(source.holder(), bridge, srcBefore, srcCont);
                        MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    public static void handleSpillSolution(ReagentAttachment solution, Level level, BlockPos targetPos) {
        // TODO: Make this less item-use-centric Should be possible to spill from a player (vomitting) or even a jug (breaking with melee)

        if (solution.isEmpty()) return;
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(solution.getMap(), level, "spill solution")) return;

        BlockState puddleTargetState = level.getBlockState(targetPos);
        BlockPos abovePos = targetPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (puddleTargetState.is(ModBlocks.PUDDLE.get())) {
            // Add to existing puddle logic
            if (level.getBlockEntity(targetPos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                        "spill solution destination puddle")) return;
                var dstBefore = MS14Provider.snapshot(dstCont);
                transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
            }
        }else if (aboveState.is(ModBlocks.PUDDLE.get())) {
            // for thrown jugs, and also like, if you clicked the block below somehow.
            // Add to existing puddle logic
            if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                        "spill solution destination puddle")) return;
                var dstBefore = MS14Provider.snapshot(dstCont);
                transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
            }
        } else if (aboveState.canBeReplaced()) {
            // Interface Check
            if (!level.isClientSide) {
                if (!solution.isEmpty()) {
                    // Create the puddle
                    level.setBlock(abovePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);

                    // Transfer data to the new BlockEntity
                    if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                        ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                        var dstBefore = MS14Provider.snapshot(dstCont);
                        transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                        MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
                    }
                }
            }
        }
    }
}
