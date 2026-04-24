package com.juicyslew.moonstation14.ms14.reagent;


import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.recipe.ReactionRecipe;
import com.juicyslew.moonstation14.recipe.ReactionRecipeInput;
import com.juicyslew.moonstation14.util.interfaces.IClampedMapHolder;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.*;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static net.minecraft.util.Mth.ceil;

public final class ReagentAttachment implements IClampedMapHolder<ResourceKey<ReagentData>>, IMS14Attachment<ReagentAttachment, ReagentComponent> {
    private final Map<ResourceKey<ReagentData>, Float> reagentMap;

    public ReagentAttachment(ReagentComponent data){
        reagentMap = new HashMap<>(data.contents());
    }

    public ReagentAttachment(){
        reagentMap = new HashMap<>();
    }
    @Override public Map<ResourceKey<ReagentData>, Float> getMap() { return reagentMap; }

    public ReagentComponent toComponent() { return new ReagentComponent(Collections.unmodifiableMap(reagentMap)); }

    //-- LOGIC FUNCTIONS --//
    // TODO: MOVE THIS TO REAGENTSYSTEM
    public void recursiveReaction(Level level, float capacity){
        // Check For Reaction, also chain react
        while (true) {
            Optional<RecipeHolder<ReactionRecipe>> recipe = level.getRecipeManager().getRecipeFor(ModRecipes.REACTION_RECIPE_TYPE.get(), new ReactionRecipeInput(reagentMap), level);
            if (recipe.isPresent()) {
                resolveReaction(recipe.get().value(), capacity);
            }else{
                break;
            }
        }
    }

    public void resolveReaction(ReactionRecipe reactionRecipe, float capacity) {
        // Find the limiting chemical to determine the chemical multiplier (floats are allowed for multiplier, but round back to int])
        // Ignore Catalysts, the matching functionality already ensured they're present, and they don't change in value as a result of this recipe.
        float reaction_count = Float.MAX_VALUE;
        Map<ResourceKey<ReagentData>, Float> inputs = reactionRecipe.inputs();
        Map<ResourceKey<ReagentData>, Float> outputs = reactionRecipe.outputs();

        for(var input_reagent : reactionRecipe.inputs().keySet()) {
            // For Catalysts: The recipe won't match if there isn't any catalyst in the solution. So I can just take the difference between input and output to get the change per reaction_count.
            float new_max_count = reagentMap.getOrDefault(input_reagent, 0f) / inputs.get(input_reagent);
            if (new_max_count < reaction_count) {
                reaction_count = new_max_count;
            }
        }

        // Update Values
        for (ResourceKey<ReagentData> input_reagent : reactionRecipe.inputs().keySet()) {
            reagentMap.put(input_reagent, reagentMap.get(input_reagent) - reaction_count * inputs.get(input_reagent));
        }
        for (ResourceKey<ReagentData> output_reagent : reactionRecipe.outputs().keySet()) {
            reagentMap.put(output_reagent, reagentMap.getOrDefault(output_reagent, 0f) + reaction_count * outputs.get(output_reagent));
        }

        // TODO: Spill Extra
        // Just naive remove until we're at capacity
        float new_vol = getTotal(reagentMap);
        if (new_vol > capacity){
            // Spill extra
            naiveRemove(new_vol - capacity);
        }
        // Gotta do it, as recipe finding is reliant on keys not values (though I could change that I suppose)
        trimZeroes();
    }

    public static final Codec<ReagentAttachment> CODEC = ReagentComponent.CODEC.xmap(
            ReagentAttachment::new,
            ReagentAttachment::toComponent
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ReagentAttachment> STREAM_CODEC = ReagentComponent.STREAM_CODEC.map(
        ReagentAttachment::new,
        ReagentAttachment::toComponent
    );

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReagentAttachment that = (ReagentAttachment) o;

        // 1. Check if the maps are identical (sizes and contents)
        // Most Java Map implementations (HashMap) handle deep equality automatically
        if (!Objects.equals(this.reagentMap, that.reagentMap)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        // Crucial: If you override equals, you MUST override hashCode
        return Objects.hash(reagentMap);
    }

    @Override
    public Codec<ReagentAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ReagentAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }
}