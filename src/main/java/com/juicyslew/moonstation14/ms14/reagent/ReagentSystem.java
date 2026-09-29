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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ReagentSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReagentSystem.class);
    public static SystemLink<ReagentAttachment, ReagentComponent> bridge = MS14Bridges.REAGENT;

    // --- CORE LOGIC --- //
    public static void handleTransfer(TraitHandler<IReagentTrait> source, TraitHandler<IReagentTrait> target, Level level, float amount) {
        if (source.holder() == target.holder()) return;
        if (level.isClientSide) return;

        SystemLink<ReagentAttachment, ReagentComponent> sourceBridge = bridgeForHolder(source.holder());
        SystemLink<ReagentAttachment, ReagentComponent> targetBridge = bridgeForHolder(target.holder());
        // Living entities are not generic reagent holders. If prototype-owned bloodstream
        // eligibility is absent or legacy migration is conflicted, preserve both endpoints.
        if (sourceBridge == null || targetBridge == null) return;
        float targetCapacity = target.trait().getCapacity();
        Long policyCapacity = null;
        if (target.holder() instanceof net.minecraft.world.entity.LivingEntity living) {
            var policy = com.juicyslew.moonstation14.ms14.blood.BloodSystem.resolvePolicy(living);
            if (policy.isEmpty() || !com.juicyslew.moonstation14.ms14.blood.BloodSystem.reconcile(living)
                    || com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(living).isEmpty()) return;
            try {
                policyCapacity = com.juicyslew.moonstation14.ms14.blood.BloodReducer.capacity(policy.get());
            } catch (RuntimeException invalidCapacity) { return; }
        }
        ReagentAttachment srcCont = MS14Provider.getDetached(source.holder(), sourceBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(srcCont.getMap(), level,
                "transfer source " + source.holder().getClass().getSimpleName())) return;
        ReagentAttachment dstCont = MS14Provider.getDetached(target.holder(), targetBridge);
        if (!ReagentCatalogValidation.hasOnlyKnownPositiveReagents(dstCont.getMap(), level,
                "transfer destination " + target.holder().getClass().getSimpleName())) return;
        var srcBefore = MS14Provider.snapshot(srcCont);
        var dstBefore = MS14Provider.snapshot(dstCont);

        // Re-check the actual destination before either detached endpoint can be committed.
        long targetCapacityCents;
        try { targetCapacityCents = policyCapacity == null
                ? ReagentUnits.fromFloat(targetCapacity) : policyCapacity; }
        catch (RuntimeException invalidCapacity) { return; }
        if (dstCont.totalUnits() > targetCapacityCents) return;
        transfer(level, srcCont, dstCont, amount, targetCapacityCents);

        MS14Provider.updateIfChanged(source.holder(), sourceBridge, srcBefore, srcCont);
        MS14Provider.updateIfChanged(target.holder(), targetBridge, dstBefore, dstCont);
    }

    /** Living reagent traits use only the prototype-enrolled bloodstream compartment. */
    public static SystemLink<ReagentAttachment, ReagentComponent> bridgeForHolder(Object holder) {
        if (!(holder instanceof net.minecraft.world.entity.LivingEntity living)) return bridge;
        if (com.juicyslew.moonstation14.ms14.blood.BloodSystem.resolvePolicy(living).isEmpty()
                || !com.juicyslew.moonstation14.ms14.blood.BloodSystem.reconcile(living)
                || com.juicyslew.moonstation14.ms14.blood.BloodSystem.state(living).isEmpty()) return null;
        return MS14Bridges.BLOODSTREAM;
    }

    static void transfer(Level level, ReagentAttachment srcCont, ReagentAttachment dstCont, float amount, float target_capacity) {
        transfer(level, srcCont, dstCont, amount, ReagentUnits.fromFloat(target_capacity));
    }

    private static void transfer(Level level, ReagentAttachment srcCont, ReagentAttachment dstCont,
                                 float amount, long capacity) {
        // Float APIs bound requested flow to cent precision; once admitted the paired
        // source/destination mutation is exact and capacity-conserving.
        // Spill-solution callers use Float.MAX_VALUE as an "all available" sentinel.
        long requested = amount == Float.MAX_VALUE ? ReagentUnits.MAX_CENTS
                : ReagentUnits.fromFloat(Math.max(0f, amount));
        float target_capacity = ReagentUnits.toFloat(capacity);
        if (dstCont.totalUnits() > capacity) {
            LOGGER.error("Skipping reagent transfer into legacy over-capacity destination: stored {} cents " +
                    "exceeds {} cents; contents are preserved for repair", dstCont.totalUnits(), capacity);
            return;
        }
        long admitted = ReagentAttachment.transferUnits(srcCont, dstCont, requested, capacity);
        if (admitted == 0) return;
        dstCont.recursiveReaction(level, target_capacity);
    }

    public static InteractionResult handleSpill(TraitHandler<IReagentTrait> source, Level level, BlockPos targetPos, float amount) {
        // Living sources require an explicit bleed/vomit solution path; never spill their bloodstream generically.
        if (source.holder() instanceof net.minecraft.world.entity.LivingEntity) return InteractionResult.PASS;

        if (amount < .001f){
            return InteractionResult.PASS;
        }
        BlockState puddleTargetState = level.getBlockState(targetPos);
        BlockPos abovePos = targetPos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        SystemLink<ReagentAttachment, ReagentComponent> sourceBridge = bridgeForHolder(source.holder());
        if (sourceBridge == null) return InteractionResult.PASS;
        ReagentAttachment srcCont = MS14Provider.getDetached(source.holder(), sourceBridge);
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
                MS14Provider.updateIfChanged(source.holder(), sourceBridge, srcBefore, srcCont);
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
                MS14Provider.updateIfChanged(source.holder(), sourceBridge, srcBefore, srcCont);
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

                        MS14Provider.updateIfChanged(source.holder(), sourceBridge, srcBefore, srcCont);
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

        if (level.isClientSide || solution == null || solution.isEmpty()
                || !level.hasChunkAt(targetPos) || !level.hasChunkAt(targetPos.above())) return;
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
        } else if (aboveState.canBeReplaced()
                && ModBlocks.PUDDLE.get().defaultBlockState().canSurvive(level, abovePos)) {
            // Interface Check
            if (!level.isClientSide) {
                if (!solution.isEmpty()) {
                    // Create the puddle
                    if (!level.setBlock(abovePos, ModBlocks.PUDDLE.get().defaultBlockState(), 3)) return;

                    // Transfer data to the new BlockEntity
                    if (level.getBlockEntity(abovePos) instanceof PuddleBlockEntity target) {
                        ReagentAttachment dstCont = MS14Provider.getDetached(target, bridge);
                        var dstBefore = MS14Provider.snapshot(dstCont);
                        transfer(level, solution, dstCont, Float.MAX_VALUE, target.getCapacity());
                        MS14Provider.updateIfChanged(target, bridge, dstBefore, dstCont);
                    } else if (level.getBlockState(abovePos).is(ModBlocks.PUDDLE.get())) {
                        level.removeBlock(abovePos, false);
                    }
                }
            }
        }
    }
}
