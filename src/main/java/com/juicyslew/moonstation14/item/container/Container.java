package com.juicyslew.moonstation14.item.container;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.juicyslew.moonstation14.enums.ReagentEnum;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.recipe.ReactionRecipe;
import com.juicyslew.moonstation14.recipe.ReactionRecipeInput;
import net.minecraft.world.InteractionResult;
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
        // TODO: Add Give vs Take Mode.
        // If possible, feed to entity.
        // If possible, give to another object.
        // Else, drink it?
        Level level = context.getLevel();

        if (level.isClientSide()) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        Map<ReagentEnum, Integer> immutableReagentMap = stack.getOrDefault(
                ModDataComponents.REAGENT_CONTAINER,
                new ReagentContainerData()
        ).getMap();
        // Stack NBT data can't be mutated.
        Map<ReagentEnum, Integer> mutableReagentMap = new HashMap<>(immutableReagentMap);
        ClampedAdd(level, mutableReagentMap, ReagentEnum.BICARIDINE, 100);

        stack.set(ModDataComponents.REAGENT_CONTAINER.get(), new ReagentContainerData(mutableReagentMap));

        return InteractionResult.SUCCESS;
    }

    public void ClampedAdd(Level level, Map<ReagentEnum, Integer> data, ReagentEnum Reagent, int amount) {
        int totalVolume = getTotalVolume(data);
        int actual_add = Integer.min(totalVolume + amount, capacity) - totalVolume;
        data.put(Reagent, data.getOrDefault(Reagent, 0) + actual_add);

        // Check For Reaction, also chain react
        while (true) {
            Optional<RecipeHolder<ReactionRecipe>> recipe = level.getRecipeManager().getRecipeFor(ModRecipes.REACTION_RECIPE_TYPE.get(), new ReactionRecipeInput(data), level);
            if (recipe.isPresent()) {
                ResolveReaction(data, recipe.get().value());
            }else{
                break;
            }
        }
    }

    public void SpecificRemove(Map<ReagentEnum, Integer> data, ReagentEnum reagent, int amount) {
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        int reagent_present = data.getOrDefault(reagent, 0);
        int new_amount = Integer.max(reagent_present - amount, 0);
        if (new_amount == 0){
            data.remove(reagent);
        }else{
            data.put(reagent, new_amount); // Add your clamping logic here
        }

    }

    public void NaiveRemove(Map<ReagentEnum, Integer> data, int amount) {
        // Reactions can only happen when something's added to a container, NOT when removed is added to a container.
        // TODO: Save removed data, so that we may easily transfer it to another reagent container.
        int vol = getTotalVolume(data);
        int new_amount = Integer.min(vol, amount);
        data.replaceAll((k, v) -> v - ceil((float) (new_amount / vol))); // TODO: This will likely cause problems at low transfer amounts. Need to ensure that "amount" milliliters of reagent in total are actually removed.
        data.entrySet().removeIf(entry -> entry.getValue() == 0);
    }

    public Map<ReagentEnum, Integer> ResolveReaction (Map<ReagentEnum, Integer> presentReagents, ReactionRecipe reactionRecipe) {
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
        return presentReagents;
    }

    public int getTotalVolume(ItemStack stack){
        Map<ReagentEnum, Integer> containerData = stack.getOrDefault(ModDataComponents.REAGENT_CONTAINER, new ReagentContainerData()).getMap();
        return getTotalVolume(containerData);
    }
    public int getTotalVolume(Map<ReagentEnum, Integer> reagentMap) {
        int sum = 0;
        for (int i : reagentMap.values()) {
            sum += i;
        }
        return sum;
    }



//    @Override
//    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
//        // TODO: Add Give vs Take Mode.
//
//        // If possible, feed to entity.
//        // If possible, give to another object.
//        // Else, eat it.
//        // Give to something if possible
//
//        //Else start drinkin it.
//        if (level.isClientSide()) return InteractionResultHolder.pass(player.getItemInHand(hand));
//        ItemStack stack = player.getItemInHand(hand).set(ModDataComponents.REAGENT_CONTAINER, );
//
//        return InteractionResultHolder.success(stack);
//    }
}