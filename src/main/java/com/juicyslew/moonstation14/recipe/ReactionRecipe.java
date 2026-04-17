package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.component.codec.ReagentContainerData;
import com.juicyslew.moonstation14.enums.ReagentEnum;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;

public record ReactionRecipe(
        Map<ReagentEnum, Integer> inputs,
        Map<ReagentEnum, Integer> catalysts, //TODO: Make this a Set (Figure out the Stream Codecs :C )
        Map<ReagentEnum, Integer> outputs
) implements Recipe<ReactionRecipeInput> {

    @Override
    public boolean matches(ReactionRecipeInput input, Level level) {
        Set<ReagentEnum> present_reagents = input.container().keySet();
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
    private static Codec<Map<ReagentEnum, Integer>> reagentCountMapCodec() {
        // represent as Map<String,Integer> then map keys to enum
        Codec<Map<String, Integer>> stringIntMap = Codec.unboundedMap(Codec.STRING, Codec.INT);
        return stringIntMap.xmap(
                m -> {
                    return m.entrySet().stream()
                            //.filter(e -> e.getKey() != null)  // Shouldn't this only be possible if I wrote null in the recipe json?
                            .map((e) -> Map.entry(ReagentEnum.getEnum(e.getKey()), e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                },
                m -> {
                    return m.entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey().getId(), Map.Entry::getValue));
                }
        );
    }

//    private static Codec<List<ReagentEnum>> reagentSetCodec() {
//        // represent as List<String> then map strings to enum
//        return ReagentEnum.CODEC.listOf();
//    }

    public static StreamCodec<RegistryFriendlyByteBuf, Map<ReagentEnum, Integer>> mapSubStreamCodec(){
        return ByteBufCodecs.map(
                HashMap::new,
                ByteBufCodecs.fromCodec(ReagentEnum.CODEC),
                ByteBufCodecs.INT
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
