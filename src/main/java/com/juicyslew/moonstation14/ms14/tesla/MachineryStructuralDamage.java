package com.juicyslew.moonstation14.ms14.tesla;

import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.random.RandomGenerator;

/** Server-authoritative structural damage, destruction and authored salvage for profiled machinery. */
public final class MachineryStructuralDamage {
    private MachineryStructuralDamage() { }

    public static int apply(BlockEntity entity, TeslaMachineProfile profile, double amount) {
        if (entity == null || profile == null || entity.isRemoved() || entity.getLevel() == null
                || entity.getLevel().isClientSide || !Double.isFinite(amount) || amount <= 0) return entity == null ? 0 : MachineryIntegrity.damage(entity);
        int applied = (int)Math.ceil(amount);
        int damage = MachineryIntegrity.add(entity, applied);
        if (profile.destructionThreshold() > 0 && damage >= profile.destructionThreshold()
                && entity.getLevel() instanceof ServerLevel level) destroy(level, entity, profile);
        return damage;
    }

    private static void destroy(ServerLevel level, BlockEntity entity, TeslaMachineProfile profile) {
        var pos = entity.getBlockPos().immutable();
        if (level.getBlockEntity(pos) != entity) return;
        // Normal state removal runs each machine's onRemove/setRemoved/power callbacks.
        if (!level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3)) return;
        CableStorage.removeHost(level, pos);
        // Keep the just-completed value on the detached BE instance for diagnostics; it is
        // identity-local and cannot be inherited by a newly placed BE at this position.
        int min = profile.salvageMin(), max = profile.salvageMax();
        int count = min == max ? min : min + level.getRandom().nextInt(max - min + 1);
        if (count > 0) {
            var drop = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    new net.minecraft.world.item.ItemStack(ModItems.STEEL.get(), count));
            if (!level.addFreshEntity(drop)) drop.discard();
        }
    }
}
