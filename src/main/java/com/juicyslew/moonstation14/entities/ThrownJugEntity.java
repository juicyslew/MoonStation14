package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class ThrownJugEntity extends ThrowableItemProjectile {
    public ThrownJugEntity(EntityType<? extends ThrownJugEntity> type, Level level) {
        super(type, level);
    }

    public ThrownJugEntity(Level level, LivingEntity shooter, ItemStack stack) {
        super(ModEntities.THROWN_JUG.get(), shooter, level);
        this.setItem(stack);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.JUG.get(); // Fallback if item is missing
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            BlockPos hitPos = BlockPos.containing(result.getLocation());

            // Adjust position so the puddle doesn't spawn inside a wall
            if (result instanceof BlockHitResult blockHit) {
                hitPos = blockHit.getBlockPos().relative(blockHit.getDirection());
            }

            ItemStack stack = getItem();
            Item item = stack.getItem();
            ReagentSystem.handleSpill(((IReagentTrait) item).toHandle(stack), level(), hitPos.below(), Float.MAX_VALUE);

            // Visual/Sound "Smash"
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }
}