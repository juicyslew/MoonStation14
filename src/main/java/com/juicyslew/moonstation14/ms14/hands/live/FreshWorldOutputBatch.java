package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;

/** Publishes newly-created biological outputs as world owners before committing their source. */
public final class FreshWorldOutputBatch {
    private FreshWorldOutputBatch() { }

    public static BodyHandItemTransfer.Result publish(ServerLevel level, LivingEntity body,
                                                       List<ItemStack> stacks, BooleanSupplier sourceCurrent,
                                                       Runnable sourceCommit) {
        return publish(level, body, stacks, sourceCurrent, sourceCommit, null, null, false);
    }

    /** Body-harvest outputs use SS14's per-output horizontal manual-harvest scatter (Minecraft map units). */
    public static BodyHandItemTransfer.Result publishBodyHarvest(ServerLevel level, LivingEntity body,
            List<ItemStack> stacks, BooleanSupplier sourceCurrent, Runnable sourceCommit) {
        return publish(level, body, stacks, sourceCurrent, sourceCommit, null, null, true);
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
        var staged = new ArrayList<LooseItemEntity>(stacks.size());
        var expected = new ArrayList<ItemStack>(stacks.size());
        try {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) return BodyHandItemTransfer.Result.DENIED;
                var entity = machine == null ? scatter
                        ? BodyHandItemTransfer.createStagedHarvestDropWithFallback(body, stack,
                            level.random.nextDouble() * .5 - .25, level.random.nextDouble() * .5 - .25)
                        : BodyHandItemTransfer.createStagedDrop(body, stack)
                        : stageAtMachine(level, machine, stack);
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
            if (!sourceCurrent.getAsBoolean()) {
                cleanup(level, staged, expected);
                return BodyHandItemTransfer.Result.DENIED;
            }
            sourceCommit.run();
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
