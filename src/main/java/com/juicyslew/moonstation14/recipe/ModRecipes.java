package com.juicyslew.moonstation14.recipe;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MoonStation14.MOD_ID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, MoonStation14.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ReactionRecipe>> REACTION_RECIPE_SERIALIZER =
            SERIALIZERS.register("reaction_recipe", ReactionRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<ReactionRecipe>> REACTION_RECIPE_TYPE =
            RECIPE_TYPES.register("reaction_recipe", () -> new RecipeType<ReactionRecipe>() {
                @Override
                public String toString() {
                    return "reaction_recipe";
                }
            });

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
        RECIPE_TYPES.register(eventBus);
    }
}
