package com.juicyslew.moonstation14.item.custom;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.ThrownJugEntity;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentCatalogValidation;
import com.juicyslew.moonstation14.util.MapOperations;
import com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
import com.juicyslew.moonstation14.ms14.stomach.StomachSystem;
import net.minecraft.server.level.ServerLevel;
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

public class JugItem extends Item implements IReagentTrait {
    private final com.juicyslew.moonstation14.ms14.jug.JugData boundData;

    public JugItem(Properties properties) {
        this(properties, null);
    }

    /** Instance-bound composition is useful for isolated fixtures; normal items resolve the active server catalog. */
    public JugItem(Properties properties, com.juicyslew.moonstation14.ms14.jug.JugData boundData) {
        super(properties);
        this.boundData = boundData;
    }

    @Override
    public float getCapacity() {
        var data = boundData != null ? boundData : com.juicyslew.moonstation14.ms14.jug.ModJugs.serverResolved();
        return capacityFromResolved(data);
    }

    public float getCapacity(Level level) {
        var data = boundData != null ? boundData : com.juicyslew.moonstation14.ms14.jug.ModJugs.resolved(level);
        return capacityFromResolved(data);
    }

    /** Capacity projection shared by runtime getters and resolved-profile tests. */
    public static float capacityFromResolved(com.juicyslew.moonstation14.ms14.jug.JugData data) {
        return data == null ? com.juicyslew.moonstation14.ms14.jug.JugData.DEFAULT_CAPACITY
                : data.solutionContainer().maxVolume();
    }

    public com.juicyslew.moonstation14.ms14.jug.JugData resolvedJugData(Level level) {
        return boundData != null ? boundData : com.juicyslew.moonstation14.ms14.jug.ModJugs.resolved(level);
    }

    public com.juicyslew.moonstation14.ms14.jug.JugData getResolvedComposition(Level level) {
        return resolvedJugData(level);
    }

    @Override public com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy drainableSolution(ItemStack stack, Level level) {
        var data = resolvedJugData(level);
        return drainableSolutionFromResolved(data);
    }
    @Override public String solutionName(ItemStack stack, Level level) {
        var data = resolvedJugData(level);
        return primarySolutionNameFromResolved(data);
    }
    @Override public com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolution(ItemStack stack, Level level) { return refillableSolutionFromResolved(resolvedJugData(level)); }
    @Override public com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransfer(ItemStack stack, Level level) { return solutionTransferFromResolved(resolvedJugData(level)); }
    @Override public com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy drinkSolution(ItemStack stack, Level level) { return drinkSolutionFromResolved(resolvedJugData(level)); }
    public static com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy refillableSolutionFromResolved(com.juicyslew.moonstation14.ms14.jug.JugData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.RefillableSolutionPolicy.DISABLED : data.refillableSolution(); }
    public static com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy solutionTransferFromResolved(com.juicyslew.moonstation14.ms14.jug.JugData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.SolutionTransferPolicy.DISABLED : data.solutionTransfer(); }
    public static com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy drinkSolutionFromResolved(com.juicyslew.moonstation14.ms14.jug.JugData data) { return data == null ? com.juicyslew.moonstation14.ms14.reagent.DrinkSolutionPolicy.DISABLED : data.drinkSolution(); }

    /** Drain policy projection shared by bound runtime getters and resolved-profile validation. */
    public static com.juicyslew.moonstation14.ms14.reagent.DrainableSolutionPolicy drainableSolutionFromResolved(
            com.juicyslew.moonstation14.ms14.jug.JugData data) {
        return data == null ? null : data.drainableSolution();
    }

    /** Primary solution projection shared by runtime getters and resolved-profile validation. */
    public static String primarySolutionNameFromResolved(com.juicyslew.moonstation14.ms14.jug.JugData data) {
        return data == null ? "default" : data.solutionContainer().solutionName();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        if (component != null && !context.getLevel().isClientSide
                && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(component.contents(), context.getLevel(),
                "jug placement/useOn")) return InteractionResult.FAIL;
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            Direction clickedFace = context.getClickedFace();
            if (clickedFace != Direction.UP) {
                return InteractionResult.PASS;
            }
            return ReagentSystem.handleSpill(toHandle(stack), context.getLevel(), context.getClickedPos(), 5f);
        }
        // A jug is a reagent container, never a placeable block. Ordinary block
        // use deliberately passes through so the targeted world liquid can be used.
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        if (component != null && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                component.contents(), level, "jug item use")) return InteractionResultHolder.fail(stack);
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
        ReagentComponent component = stack.get(ModDataComponents.REAGENT.get());
        if (component != null && !ReagentCatalogValidation.hasOnlyKnownPositiveReagents(
                component.contents(), level, "jug use tick")) {
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
            if (level instanceof ServerLevel && sourceBefore - sourceAfter > 0f) {
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

