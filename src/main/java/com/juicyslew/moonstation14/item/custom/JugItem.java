package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ThrownJugEntity;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.util.MapOperations;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;

import static com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS;
import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;

public class JugItem extends BlockItem implements IReagentTrait {
    private final float capacity;

    public JugItem(Block block, Properties properties) {
        super(block, properties);
        this.capacity = 200f;
    }

    @Override
    public float getCapacity() { return this.capacity; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            Direction clickedFace = context.getClickedFace();
            if (clickedFace != Direction.UP) {
                return InteractionResult.PASS;
            }
            ItemStack stack = context.getItemInHand();

            return ReagentSystem.handleSpill(toHandle(stack), context.getLevel(), context.getClickedPos(), 5f);
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Only start if not empty
        if (MapOperations.getTotal(stack.getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent()).contents()) <= 0) {
            return InteractionResultHolder.fail(stack);
        }

        // We only throw if NOT shifting (since Shift is for pouring/placing)
        if (!player.isShiftKeyDown()) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

            if (!level.isClientSide) {
                // Create the entity with the current stack data (including reagents!)
                ThrownJugEntity thrownJug = new ThrownJugEntity(level, player, stack);
                thrownJug.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
                level.addFreshEntity(thrownJug);
            }

            // Consume the item from the hand
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int used = getUseDuration(stack, entity) - remaining;
        if (used > 0 && used % SIP_INTERVAL_TICKS == 0) {
            ReagentSystem.handleTransfer(
                    this.toHandle(stack),
                    ((IReagentTrait) entity).toHandleSelf(), // Player will implement this because of LivingEntityMixin
                    level,
                    UNITS_PER_SIP
            );

            level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
