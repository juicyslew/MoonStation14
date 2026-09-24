package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.util.MapOperations;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

import static com.juicyslew.moonstation14.util.Constants.SIP_INTERVAL_TICKS;
import static com.juicyslew.moonstation14.util.Constants.UNITS_PER_SIP;

public class BottleItem extends Item implements IReagentTrait {
    private final float capacity;

    public BottleItem(Properties properties) {
        super(properties);
        this.capacity = 30f;
    }

    @Override public float getCapacity() { return capacity; }

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
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        if (component != null && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                component.contents(), level, "bottle item use")) return InteractionResultHolder.fail(stack);
        // Only start if not empty
        if (MapOperations.getTotal(stack.getOrDefault(ModDataComponents.REAGENT.get(), new ReagentComponent()).contents()) <= 0) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        if (component != null && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                component.contents(), level, "bottle use tick")) {
            entity.stopUsingItem();
            return;
        }
        int used = getUseDuration(stack, entity) - remaining;
        if (used > 0 && used % SIP_INTERVAL_TICKS == 0) {
            float sourceBefore = getSourceTotal(stack);
            if (!(sourceBefore > 0f)) {
                entity.stopUsingItem();
                return;
            }

            if (entity instanceof Player player && level instanceof ServerLevel serverLevel) {
                StomachSystem.ingest(this.toHandle(stack), player, serverLevel, UNITS_PER_SIP);
            }

            float sourceAfter = getSourceTotal(stack);
            if (!level.isClientSide && sourceBefore - sourceAfter > 0f) {
                level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            if (!(sourceAfter > 0f)) {
                entity.stopUsingItem();
            }
        }
    }

    private static float getSourceTotal(ItemStack stack) {
        ReagentComponent data = stack.get(ModDataComponents.REAGENT.get());
        return data == null ? 0f : MapOperations.getTotal(data.contents());
    }
}
