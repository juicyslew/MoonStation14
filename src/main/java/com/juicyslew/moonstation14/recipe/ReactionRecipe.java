package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.*;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record ReactionRecipe(
        Map<ResourceKey<ReagentData>, Float> inputs,
        Map<ResourceKey<ReagentData>, Float> catalysts, //TODO: Make this a Set (Figure out the Stream Codecs :C )
        Map<ResourceKey<ReagentData>, Float> outputs
) implements Recipe<ReactionRecipeInput> {

    @Override
    public boolean matches(ReactionRecipeInput input, Level level) {
        Set<ResourceKey<ReagentData>> present_reagents = input.container().keySet();
        return present_reagents.containsAll(inputs.keySet()) && present_reagents.containsAll(catalysts.keySet());
    }

    @Override
    public ItemStack assemble(ReactionRecipeInput input, HolderLookup.Provider registries) {
        // TODO: I should be able to run this on the hashmap, rather than the itemstack directly, then manage the item stack after the fact. That'd prevent chain reactions from reserializing between every step.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    // TODO: Implement the rest!
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.REACTION_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.REACTION_RECIPE_TYPE.get();
    }

    private static Codec<Map<ResourceKey<ReagentData>, Float>> reagentCountMapCodec() {
        return Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                ResourceKey::location
        ), Codec.FLOAT);
    }

//    private static Codec<List<ReagentEnum>> reagentSetCodec() {
//        // represent as List<String> then map strings to enum
//        return ReagentEnum.CODEC.listOf();
//    }

    public static StreamCodec<RegistryFriendlyByteBuf, Map<ResourceKey<ReagentData>, Float>> mapSubStreamCodec(){
        return ByteBufCodecs.map(
                HashMap::new,
                ResourceKey.streamCodec(ModReagents.REAGENT_REGISTRY_KEY),
                ByteBufCodecs.FLOAT
        );
    }
    public static class Serializer implements RecipeSerializer<ReactionRecipe> {
        public static final MapCodec<ReactionRecipe> CODEC = RecordCodecBuilder.mapCodec((RecordCodecBuilder.Instance<ReactionRecipe> inst) -> inst.group(
                reagentCountMapCodec().fieldOf("inputs").forGetter(ReactionRecipe::inputs),
                reagentCountMapCodec().fieldOf("catalysts").forGetter(ReactionRecipe::catalysts),
                reagentCountMapCodec().fieldOf("outputs").forGetter(ReactionRecipe::outputs)
        ).apply(inst, ReactionRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ReactionRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        mapSubStreamCodec(), ReactionRecipe::inputs,
                        mapSubStreamCodec(), ReactionRecipe::catalysts,
                        mapSubStreamCodec(), ReactionRecipe::outputs,
                        ReactionRecipe::new
                );

        @Override
        public MapCodec<ReactionRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReactionRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
