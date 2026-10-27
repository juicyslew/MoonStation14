package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Publishes fresh item outputs as world owners before committing their source. */
public final class FreshWorldOutputBatch {
    private FreshWorldOutputBatch() { }

    public static BodyHandItemTransfer.Result publish(ServerLevel level, LivingEntity body,
                                                       List<ItemStack> stacks, BooleanSupplier sourceCurrent,
                                                       Runnable sourceCommit) {
        return publish(level, body, stacks, sourceCurrent, sourceCommit, null, null, false);
    }

    /** Publishes separate source-processing products around the exact loose-source position. */
    public static BodyHandItemTransfer.Result publishAtLooseSource(ServerLevel level, Vec3 origin,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent, Runnable sourceCommit) {
        if (sourceCommit == null) return BodyHandItemTransfer.Result.DENIED;
        return publishAtSourcePosition(level, origin, stacks, sourceCurrent, () -> {
            sourceCommit.run(); return BodyHandItemTransfer.Result.SUCCESS;
        }, null);
    }

    /** Source-position output publication with an explicit transactional source-commit result. */
    public static BodyHandItemTransfer.Result publishAtSourcePosition(ServerLevel level, Vec3 origin,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent,
            Supplier<BodyHandItemTransfer.Result> sourceCommit,
            BiFunction<LooseItemEntity, ItemStack, BodyHandItemTransfer.Result> afterCommit) {
        if (level == null || origin == null || !Double.isFinite(origin.x) || !Double.isFinite(origin.y)
                || !Double.isFinite(origin.z) || !level.getServer().isSameThread()
                || !level.hasChunkAt(BlockPos.containing(origin))) return BodyHandItemTransfer.Result.DENIED;
        return publishStaged(level, stacks, sourceCurrent, sourceCommit, afterCommit,
                stack -> stageHarvestAt(level, origin, stack), () -> true);
    }

    /** Publishes outputs for a source already irreversibly committed; never attempts rollback. */
    public static BodyHandItemTransfer.Result publishAfterSourceCommitAtPosition(ServerLevel level, Vec3 origin,
            List<ItemStack> stacks, BooleanSupplier committedSourceCurrent) {
        if (stacks == null || stacks.isEmpty()) return BodyHandItemTransfer.Result.DENIED;
        return publishAtSourcePosition(level, origin, stacks, committedSourceCurrent,
                () -> BodyHandItemTransfer.Result.SUCCESS, null);
    }

    /** Body-harvest outputs use SS14's per-output horizontal manual-harvest scatter (Minecraft map units). */
    public static BodyHandItemTransfer.Result publishBodyHarvest(ServerLevel level, LivingEntity body,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent, Runnable sourceCommit) {
        return publishBodyHarvest(level, body, stacks, sourceCurrent, sourceCommit, null);
    }

    /** Body-origin biological outputs with a trusted post-source-commit pickup callback. */
    public static BodyHandItemTransfer.Result publishBodyHarvest(ServerLevel level, LivingEntity body,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent, Runnable sourceCommit,
            BiFunction<LooseItemEntity, ItemStack, BodyHandItemTransfer.Result> afterCommit) {
        return publish(level, body, stacks, sourceCurrent, sourceCommit, null, afterCommit, true);
    }

    /**
     * Publishes server-system harvest outputs around the tray's world position (rather than a
     * body); the bounded +/- .25 horizontal footprint preserves SS14's holder-coordinate output.
     */
    public static BodyHandItemTransfer.Result publishSystemHarvest(ServerLevel level, BlockPos tray,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent, Runnable sourceCommit) {
        if (level == null || tray == null || !level.getServer().isSameThread()
                || !level.hasChunkAt(tray)) return BodyHandItemTransfer.Result.DENIED;
        // The 2D holder maps to the tray block in Minecraft, whose partial-solid top occupies
        // the block below this position; mirror machine output's above-block feet-plane adaptation.
        Vec3 origin = new Vec3(tray.getX() + .5, tray.getY() + 1.0, tray.getZ() + .5);
        return publishStaged(level, stacks, sourceCurrent, () -> {
                    sourceCommit.run(); return BodyHandItemTransfer.Result.SUCCESS;
                }, null,
                stack -> stageHarvestAt(level, origin, stack), () -> true);
    }

    /** Machine outputs stage just above the block (Minecraft's small-offset adaptation of SS14 machine coordinates). */
    public static BodyHandItemTransfer.Result publish(ServerLevel level, LivingEntity body, BlockPos machine,
                                                       List<ItemStack> stacks, BooleanSupplier sourceCurrent,
                                                       Runnable sourceCommit,
                                                       BiFunction<LooseItemEntity, ItemStack, BodyHandItemTransfer.Result> afterCommit) {
        return publish(level, body, stacks, sourceCurrent, sourceCommit, machine, afterCommit, false);
    }

    private static BodyHandItemTransfer.Result publish(ServerLevel level, LivingEntity body,
                                                        List<ItemStack> stacks, BooleanSupplier sourceCurrent,
                                                          Runnable sourceCommit, BlockPos machine,
                                                          BiFunction<LooseItemEntity, ItemStack, BodyHandItemTransfer.Result> afterCommit,
                                                          boolean scatter) {
        if (level == null || body == null || stacks == null || stacks.isEmpty()
                || sourceCurrent == null || sourceCommit == null || !level.getServer().isSameThread()
                || body.level() != level || !sourceCurrent.getAsBoolean())
            return BodyHandItemTransfer.Result.DENIED;
        Function<ItemStack, LooseItemEntity> stager = stack -> machine == null ? scatter
                ? BodyHandItemTransfer.createStagedHarvestDropWithFallback(body, stack,
                    level.random.nextDouble() * .5 - .25, level.random.nextDouble() * .5 - .25)
                : BodyHandItemTransfer.createStagedDrop(body, stack)
                : stageAtMachine(level, machine, stack);
        return publishStaged(level, stacks, sourceCurrent, () -> {
                    sourceCommit.run(); return BodyHandItemTransfer.Result.SUCCESS;
                }, afterCommit, stager,
                () -> body.level() == level);
    }

    private static BodyHandItemTransfer.Result publishStaged(ServerLevel level, List<ItemStack> stacks,
            BooleanSupplier sourceCurrent, Supplier<BodyHandItemTransfer.Result> sourceCommit,
            BiFunction<LooseItemEntity, ItemStack, BodyHandItemTransfer.Result> afterCommit,
            Function<ItemStack, LooseItemEntity> stager, BooleanSupplier contextCurrent) {
        if (level == null || stacks == null || stacks.isEmpty() || sourceCurrent == null || sourceCommit == null
                || !level.getServer().isSameThread() || !contextCurrent.getAsBoolean()
                || !sourceCurrent.getAsBoolean()) return BodyHandItemTransfer.Result.DENIED;
        var staged = new ArrayList<LooseItemEntity>(stacks.size());
        var expected = new ArrayList<ItemStack>(stacks.size());
        try {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) return BodyHandItemTransfer.Result.DENIED;
                var entity = stager.apply(stack);
                if (entity == null) return BodyHandItemTransfer.Result.DENIED;
                if (!ItemStack.isSameItemSameComponents(stack, entity.getItem()) || stack.getCount() != entity.getItem().getCount())
                    return BodyHandItemTransfer.Result.DENIED;
                staged.add(entity);
                expected.add(stack.copy());
            }
            for (var entity : staged) {
                if (!level.addFreshEntity(entity) || level.getEntity(entity.getUUID()) != entity
                        || !entity.isAddedToLevel() || entity.isRemoved()) {
                    cleanup(level, staged, expected);
                    return BodyHandItemTransfer.Result.DENIED;
                }
            }
            if (!contextCurrent.getAsBoolean() || !sourceCurrent.getAsBoolean()) {
                cleanup(level, staged, expected);
                return BodyHandItemTransfer.Result.DENIED;
            }
            BodyHandItemTransfer.Result committed = sourceCommit.get();
            if (committed == null) committed = BodyHandItemTransfer.Result.RECOVERY_REQUIRED;
            if (committed != BodyHandItemTransfer.Result.SUCCESS) {
                // DENIED is an explicit compensated result; old source-current proof may
                // be stale after that compensation advanced a hand revision. Uncertain
                // commits retain staged owners so potentially committed outputs are conserved.
                if (committed == BodyHandItemTransfer.Result.DENIED) cleanup(level, staged, expected);
                return committed;
            }
            if (afterCommit != null) {
                for (int i = 0; i < staged.size(); i++)
                    if (afterCommit.apply(staged.get(i), expected.get(i).copy()) == BodyHandItemTransfer.Result.RECOVERY_REQUIRED)
                        return BodyHandItemTransfer.Result.RECOVERY_REQUIRED;
            }
            return BodyHandItemTransfer.Result.SUCCESS;
        } catch (RuntimeException failure) {
            boolean unchanged;
            try { unchanged = sourceCurrent.getAsBoolean(); }
            catch (RuntimeException ignored) { unchanged = false; }
            if (unchanged) cleanup(level, staged, expected);
            return BodyHandItemTransfer.Result.RECOVERY_REQUIRED;
        }
    }

    private static LooseItemEntity stageAtMachine(ServerLevel level, BlockPos machine, ItemStack stack) {
        try {
            LooseObjectProfile profile = LooseObjectProfile.resolveForStack(stack, false);
            // Small Minecraft adaptation: center on machine and lift 0.15 blocks above its top.
            Vec3 position = new Vec3(machine.getX() + .5, machine.getY() + 1.15, machine.getZ() + .5);
            AABB box = AABB.ofSize(position, profile.physicalWidth(), profile.physicalHeight(), profile.physicalWidth());
            if (!level.hasChunkAt(BlockPos.containing(position)) || !level.hasChunkAt(BlockPos.containing(box.minX, box.minY, box.minZ))
                    || !level.hasChunkAt(BlockPos.containing(box.maxX, box.maxY, box.maxZ))) return null;
            LooseItemEntity entity = LooseItemEntity.create(level, position, stack.copy());
            return level.noCollision(entity, entity.getBoundingBox()) ? entity : null;
        } catch (RuntimeException unavailable) { return null; }
    }

    private static LooseItemEntity stageHarvestAt(ServerLevel level, Vec3 origin, ItemStack stack) {
        // Source/system output uses a stable collision-safe horizontal search without RNG draws.
        double[] offsets = {0, -.25, .25};
        for (double x : offsets) for (double z : offsets) {
            LooseItemEntity candidate = BodyHandItemTransfer.createStagedHarvestDrop(level, origin, stack, x, z);
            if (candidate != null) return candidate;
        }
        return null;
    }

    private static void cleanup(ServerLevel level, List<LooseItemEntity> entities, List<ItemStack> expected) {
        for (int i = 0; i < entities.size(); i++) {
            var entity = entities.get(i);
            var stack = entity.getItem();
            var owner = level.getEntity(entity.getUUID());
            if (entity.isAddedToLevel() && owner == entity && !entity.isRemoved()
                    && ItemStack.isSameItemSameComponents(expected.get(i), stack)
                    && expected.get(i).getCount() == stack.getCount()) entity.discard();
        }
    }
}
