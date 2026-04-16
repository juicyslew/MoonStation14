package com.juicyslew.moonstation14.item;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;

public class ModFoodProperties {
    public static final FoodProperties WAFFLE = new FoodProperties.Builder().nutrition(2).saturationModifier(0.25f)
            .effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 20), .35f).build();

    public static final FoodProperties BOTTLE = new FoodProperties.Builder().nutrition(0).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.HEALTH_BOOST, 1000), 1f).build();
}
