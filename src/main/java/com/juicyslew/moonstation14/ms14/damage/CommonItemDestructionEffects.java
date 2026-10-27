package com.juicyslew.moonstation14.ms14.damage;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.FreshWorldOutputBatch;
import com.juicyslew.moonstation14.ms14.item_processing.FreshItemOutputStacks;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Shared best-effort feedback/output phase for destruction of a damaged item. */
public final class CommonItemDestructionEffects {
    private CommonItemDestructionEffects() { }

    public static BlockPos findSpillSurface(ServerLevel server, net.minecraft.world.phys.AABB bounds) {
        int x = BlockPos.containing(bounds.getCenter()).getX();
        int z = BlockPos.containing(bounds.getCenter()).getZ();
        int startY = net.minecraft.util.Mth.floor(bounds.minY - .001);
        int minY = Math.max(server.getMinBuildHeight(), startY - 512);
        for (int y = startY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!server.hasChunkAt(pos)) return null;
            var state = server.getBlockState(pos);
            if (state.is(com.juicyslew.moonstation14.block.ModBlocks.PUDDLE.get())) return pos;
            var shape = state.getCollisionShape(server, pos);
            if (!shape.isEmpty()) {
                for (var box : shape.toAabbs()) if (box.maxY > 0 && box.minY < 1) return pos;
                return null;
            }
        }
        return null;
    }

    public static BodyHandItemTransfer.Result publish(ServerLevel level, Vec3 origin, BlockPos spillSurface,
            ItemStack ownedSourceSnapshot, LooseObjectProfile.ItemDamageComposition composition,
            BooleanSupplier sourceCurrent, Supplier<BodyHandItemTransfer.Result> terminalSourceCommit) {
        if (level == null || origin == null || ownedSourceSnapshot == null || composition == null
                || sourceCurrent == null || terminalSourceCommit == null) return BodyHandItemTransfer.Result.DENIED;
        MemoizedCommit commit = new MemoizedCommit(terminalSourceCommit);
        try { if (!sourceCurrent.getAsBoolean()) return BodyHandItemTransfer.Result.DENIED; }
        catch (RuntimeException stale) { return BodyHandItemTransfer.Result.DENIED; }
        boolean outputResult = true;
        try {
            if (composition.destructionSound() != null)
                com.juicyslew.moonstation14.ms14.explosion.grenade.network.ControlledSoundDispatch.emit(
                        level, composition.destructionSound(), origin.x, origin.y, origin.z,
                        composition.destructionSoundGain(), 1f);
        } catch (RuntimeException ignored) { }
        try {
            if (composition.spillCurrentReagents() && spillSurface != null) {
                ItemStack detached = ownedSourceSnapshot.copy();
                ItemFoodSolutions.initializeResolved(detached, level);
                var reagent = detached.get(ModDataComponents.REAGENT.get());
                if (reagent != null) {
                    ReagentAttachment spill = new ReagentAttachment(reagent);
                    if (!spill.isEmpty()) com.juicyslew.moonstation14.ms14.reagent.ReagentSystem
                            .handleSpillSolution(new ReagentAttachment(spill.getMap()), level, spillSurface);
                }
            }
        } catch (RuntimeException ignored) { }
        if (composition.destructionExplosion() != null) {
            try { com.juicyslew.moonstation14.ms14.explosion.server.QueuedExplosionSystem.enqueue(
                    level, BlockPos.containing(origin), composition.destructionExplosion()); }
            catch (RuntimeException ignored) { }
        }
        if (!composition.destructionOutputs().isEmpty()) {
            try {
                var ids = new ArrayList<net.minecraft.resources.ResourceLocation>();
                for (var output : composition.destructionOutputs())
                    for (int i = 0; i < output.count(); i++) ids.add(output.output());
                List<ItemStack> stacks = FreshItemOutputStacks.build(ids, level);
                if (stacks != null) {
                    var sourceItem = BuiltInRegistries.ITEM.getKey(ownedSourceSnapshot.getItem());
                    var result = FreshWorldOutputBatch.publishAtSourcePosition(level, origin, stacks,
                            sourceCurrent, commit, null);
                    outputResult = result == BodyHandItemTransfer.Result.SUCCESS;
                } else outputResult = false;
            } catch (RuntimeException ignored) { outputResult = false; }
        }
        BodyHandItemTransfer.Result terminal;
        terminal = commit.get();
        if (terminal != BodyHandItemTransfer.Result.SUCCESS) return terminal;
        return outputResult ? BodyHandItemTransfer.Result.SUCCESS : BodyHandItemTransfer.Result.DENIED;
    }

    static final class MemoizedCommit implements Supplier<BodyHandItemTransfer.Result> {
        private final Supplier<BodyHandItemTransfer.Result> callback;
        private boolean attempted;
        private BodyHandItemTransfer.Result result;
        MemoizedCommit(Supplier<BodyHandItemTransfer.Result> callback) { this.callback = callback; }
        @Override public BodyHandItemTransfer.Result get() {
            if (!attempted) {
                attempted = true;
                try { result = callback.get(); }
                catch (RuntimeException failure) { result = BodyHandItemTransfer.Result.RECOVERY_REQUIRED; }
                if (result == null) result = BodyHandItemTransfer.Result.RECOVERY_REQUIRED;
            }
            return result;
        }
    }
}
