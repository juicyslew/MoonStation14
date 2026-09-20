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

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;

public class ReagentSystem {
    public static SystemLink<ReagentAttachment, ReagentComponent> bridge = MS14Bridges.REAGENT;

    // --- CORE LOGIC --- //
    public static void handleTransfer(TraitHandler<IReagentTrait> source, TraitHandler<IReagentTrait> target, Level level, float amount) {
        if (level.isClientSide) return;

        ReagentAttachment srcCont = MS14Provider.get(source.holder(), bridge);
        ReagentAttachment dstCont = MS14Provider.get(target.holder(), bridge);

        transfer(level, srcCont, dstCont, amount, target.trait().getCapacity());

        MS14Provider.update(source.holder(), bridge, srcCont);
        MS14Provider.update(target.holder(), bridge, dstCont);
    }

    static void transfer(Level level, ReagentAttachment srcCont, ReagentAttachment dstCont, float amount, float target_capacity) {
        // TODO: ensure that no reagent goes negative in value.
        float availableSpace = target_capacity - getTotal(dstCont.getMap());
        float toMove = Math.min(Math.min(amount, getTotal(srcCont.getMap())), availableSpace);
        var removed = srcCont.naiveRemove(toMove);
        dstCont.mergeAdd(removed, target_capacity);
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
        ReagentAttachment srcCont = MS14Provider.get(source.holder(), bridge);
        if (puddleTargetState.is(ModBlocks.PUDDLE.get())) {
            // Add to existing puddle logic
            if (level.getBlockEntity(targetPos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                transfer(level, srcCont, dstCont, amount, target.getCapacity());
                MS14Provider.update(source.holder(), bridge, srcCont);
                MS14Provider.update(target, bridge, dstCont);
            }
        }else if (aboveState.is(ModBlocks.PUDDLE.get())) {
            // for thrown jugs, and also like, if you clicked the block below somehow.
            // Add to existing puddle logic
            if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                transfer(level, srcCont, dstCont, amount, target.getCapacity());
                MS14Provider.update(source.holder(), bridge, srcCont);
                MS14Provider.update(target, bridge, dstCont);
            }
        } else if (aboveState.canBeReplaced()) {
            // Interface Check
            if (!level.isClientSide) {
                if (!srcCont.isEmpty()) {
                    // Create the puddle
                    level.setBlock(abovePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);

                    // Transfer data to the new BlockEntity
                    if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                        ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                        transfer(level, srcCont, dstCont, amount, target.getCapacity());

                        MS14Provider.update(source.holder(), bridge, srcCont);
                        MS14Provider.update(target, bridge, dstCont);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    public static void handleSpillSolution(ReagentAttachment solution, Level level, BlockPos targetPos) {
        // TODO: Make this less item-use-centric Should be possible to spill from a player (vomitting) or even a jug (breaking with melee)

        BlockState puddleTargetState = level.getBlockState(targetPos);
        BlockPos abovePos = targetPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (puddleTargetState.is(ModBlocks.PUDDLE.get())) {
            // Add to existing puddle logic
            if (level.getBlockEntity(targetPos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                MS14Provider.update(target, bridge, dstCont);
            }
        }else if (aboveState.is(ModBlocks.PUDDLE.get())) {
            // for thrown jugs, and also like, if you clicked the block below somehow.
            // Add to existing puddle logic
            if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                MS14Provider.update(target, bridge, dstCont);
            }
        } else if (aboveState.canBeReplaced()) {
            // Interface Check
            if (!level.isClientSide) {
                if (!solution.isEmpty()) {
                    // Create the puddle
                    level.setBlock(abovePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);

                    // Transfer data to the new BlockEntity
                    if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                        ReagentAttachment dstCont = MS14Provider.get(target, bridge);
                        transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                        MS14Provider.update(target, bridge, dstCont);
                    }
                }
            }
        }
    }
}
