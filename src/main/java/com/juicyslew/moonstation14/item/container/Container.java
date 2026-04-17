package com.juicyslew.moonstation14.item.container;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.juicyslew.moonstation14.enums.ReagentEnum;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.recipe.ReactionRecipe;
import com.juicyslew.moonstation14.recipe.ReactionRecipeInput;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.minecraft.util.Mth.ceil;

public abstract class Container extends Item {
    // TODO: Split container functionality from Item
    public final int capacity; // In MilliUnits
    private static final int SIP_INTERVAL_TICKS = 10; // consume every 10 ticks
    private static final int UNITS_PER_SIP = 500;

    public Container(Item.Properties properties, int capacity) {
        super(properties);
        this.capacity = capacity;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        if (level.isClientSide()) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        Map<ReagentEnum, Integer> immutableReagentMap = stack.getOrDefault(
                ModDataComponents.REAGENT_CONTAINER,
                new ReagentContainerData()
        ).getMap();
        // Stack NBT data can't be mutated.
        Map<ReagentEnum, Integer> mutableReagentMap = new HashMap<>(immutableReagentMap);
        ClampedAdd(level, mutableReagentMap, ReagentEnum.BICARIDINE, 100, capacity);

        stack.set(ModDataComponents.REAGENT_CONTAINER.get(), new ReagentContainerData(mutableReagentMap));

        return InteractionResult.SUCCESS;
    }

    public static void ClampedAdd(Level level, Map<ReagentEnum, Integer> data, ReagentEnum Reagent, int amount, int capacity) {
        int totalVolume = getTotalVolume(data);
        int actual_add = Integer.min(totalVolume + amount, capacity) - totalVolume;
        data.put(Reagent, data.getOrDefault(Reagent, 0) + actual_add);
        recursiveReaction(level, data, capacity);
    }

    public static void mergeAdd(Level level, Map<ReagentEnum, Integer> data, Map<ReagentEnum, Integer> to_add, int capacity){
        // Can Output the difference if necessary somewhere.

        int totalVolume = getTotalVolume(data);
        int to_add_volume = getTotalVolume(to_add);
        int actual_add_volume = Integer.min(totalVolume + to_add_volume, capacity) - totalVolume;
        System.out.println(to_add);
        System.out.println(to_add_volume);
        float to_mult = actual_add_volume / to_add_volume;
        for (ReagentEnum r : to_add.keySet()){
            data.put(r, data.getOrDefault(r, 0) + ceil(to_add.get(r) * to_mult));
        }
        recursiveReaction(level, data, capacity);
    }

    public static void SpecificRemove(Map<ReagentEnum, Integer> data, ReagentEnum reagent, int amount) {
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        int reagent_present = data.getOrDefault(reagent, 0);
        int new_amount = Integer.max(reagent_present - amount, 0);
        if (new_amount == 0){
            data.remove(reagent);
        }else{
            data.put(reagent, new_amount); // Add your clamping logic here
        }

    }

    public static Map<ReagentEnum, Integer> NaiveRemove(Map<ReagentEnum, Integer> data, int amount) {
        // MODIFIES PROVIDED DICTIONARY, THEN RETURNS THE DIFFERENCE.

        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        // TODO: Save removed data, so that we may easily transfer it to another reagent container.
        int vol = getTotalVolume(data);
        int new_amount = Integer.min(vol, amount);
        Map<ReagentEnum, Integer> difference = new HashMap<>();
        for (Map.Entry<ReagentEnum, Integer> e : data.entrySet()) {
            int diff = ceil((float) e.getValue() * new_amount / vol); // TODO: This will likely cause problems at low transfer amounts. Need to ensure that "amount" milliliters of reagent in total are actually removed.
            difference.put(e.getKey(), diff);
            data.put(e.getKey(), e.getValue() - diff);
        }
        data.entrySet().removeIf(entry -> entry.getValue() == 0);
        return difference;
    }

    public static void recursiveReaction(Level level, Map<ReagentEnum, Integer> data, int capacity){
        // Check For Reaction, also chain react
        while (true) {
            Optional<RecipeHolder<ReactionRecipe>> recipe = level.getRecipeManager().getRecipeFor(ModRecipes.REACTION_RECIPE_TYPE.get(), new ReactionRecipeInput(data), level);
            if (recipe.isPresent()) {
                resolveReaction(data, recipe.get().value(), capacity);
            }else{
                break;
            }
        }
    }

    public static void resolveReaction(Map<ReagentEnum, Integer> presentReagents, ReactionRecipe reactionRecipe, int capacity) {
        // Find the limiting chemical to determine the chemical multiplier (floats are allowed for multiplier, but round back to int])
        // Ignore Catalysts, the matching functionality already ensured they're present, and they don't change in value as a result of this recipe.
        int reaction_count = Integer.MAX_VALUE;
        Map<ReagentEnum, Integer> inputs = reactionRecipe.inputs();
        Map<ReagentEnum, Integer> outputs = reactionRecipe.outputs();

        for(ReagentEnum input_reagent : reactionRecipe.inputs().keySet()) {
            // For Catalysts: The recipe won't match if there isn't any catalyst in the solution. So I can just take the difference between input and output to get the change per reaction_count.
            int new_max_count = presentReagents.getOrDefault(input_reagent, 0) / inputs.get(input_reagent);
            if (new_max_count < reaction_count) {
                reaction_count = new_max_count;
            }
        }

        // Update Values
        for (ReagentEnum input_reagent : reactionRecipe.inputs().keySet()) {
            presentReagents.put(input_reagent, presentReagents.get(input_reagent) - reaction_count * inputs.get(input_reagent));
        }
        for (ReagentEnum output_reagent : reactionRecipe.outputs().keySet()) {
            presentReagents.put(output_reagent, presentReagents.getOrDefault(output_reagent, 0) + reaction_count * outputs.get(output_reagent));
        }

        // TODO: Spill Extra
        // Just naive remove until we're at capacity
        int new_vol = getTotalVolume(presentReagents);
        if (new_vol > capacity){
            // Spill extra
            NaiveRemove(presentReagents, new_vol - capacity);
        }

        presentReagents.entrySet().removeIf(entry -> entry.getValue() == 0);
    }

    public static int getTotalVolume(ItemStack stack){
        Map<ReagentEnum, Integer> containerData = stack.getOrDefault(ModDataComponents.REAGENT_CONTAINER, new ReagentContainerData()).getMap();
        return getTotalVolume(containerData);
    }
    public static int getTotalVolume(Map<ReagentEnum, Integer> reagentMap) {
        int sum = 0;
        for (int i : reagentMap.values()) {
            sum += i;
        }
        return sum;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Only start using if there is content
        ReagentContainerData data = stack.getOrDefault(ModDataComponents.REAGENT_CONTAINER, new ReagentContainerData());
        if (data.getMap().isEmpty()) return InteractionResultHolder.pass(stack);

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; } // An hour - funny, so if you sit long enough, you can consume the container itself lol.


    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        // TODO: Add visual for how far along you're drink action is. (much like the progress bar for actions in SS14)
        if (!(livingEntity instanceof Player player)) return;
        if (livingEntity.level().isClientSide()) return; // handle server-side authoritative changes only

        int usedTicks = getUseDuration(stack, livingEntity) - remainingUseDuration;
        if (usedTicks % SIP_INTERVAL_TICKS != 0 || usedTicks == 0) return;

        Map<ReagentEnum, Integer> containerReagentMap = new HashMap<>(stack.getOrDefault(ModDataComponents.REAGENT_CONTAINER, new ReagentContainerData()).getMap());
        if (getTotalVolume(containerReagentMap) == 0) {
            // stop using when empty
            player.stopUsingItem();
            return;
        }

        // consume units
        int consumed = Math.min(UNITS_PER_SIP, getTotalVolume(containerReagentMap));
        Map<ReagentEnum, Integer> consumed_reagents = NaiveRemove(containerReagentMap, consumed);
        stack.set(ModDataComponents.REAGENT_CONTAINER.get(), new ReagentContainerData(containerReagentMap)); // persist change
        // TODO: Add capacity to entities. If at capacity, stop using item.

        // Put the info on the Player!
        Map<ReagentEnum, Integer> entityReagents = new HashMap<>(livingEntity.getData(ModDataAttachments.REAGENT_CONTAINER.get()).getMap());
        mergeAdd(level, entityReagents, consumed_reagents, 10000); // Entity Capacity.
        livingEntity.setData(ModDataAttachments.REAGENT_CONTAINER.get(), new ReagentContainerData(entityReagents));


        // TODO??: sync to client if needed
        // ComponentSync.syncItem(stack, ModDataComponents.REAGENT_CONTAINER);

        // TODO: apply effects to the user
        // ChemicalSystem.applySipEffects((LivingEntity) player, consumed, containerData);

        // play sound / particle if desired
        livingEntity.level().playSound(null, livingEntity.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        // Called when player stops using (right-click released or cancelled).
        // No extra logic required unless you want finalization.
    }
}