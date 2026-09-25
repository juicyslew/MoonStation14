package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectComponent;
import com.juicyslew.moonstation14.ms14.alert.AlertComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerComponent;
import com.juicyslew.moonstation14.ms14.thirst.ThirstComponent;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityComponent;
import com.juicyslew.moonstation14.ms14.slip.SlidingComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENT_TYPES =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, MoonStation14.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReagentComponent>> REAGENT =
            register_component("reagent", builder -> builder.persistent(ReagentComponent.CODEC).networkSynchronized(ReagentComponent.STREAM_CODEC));

    /** Item-side half of the stomach bridge; ingestion currently writes character attachments only. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ReagentComponent>> STOMACH =
            register_component("stomach", builder -> builder.persistent(ReagentComponent.CODEC).networkSynchronized(ReagentComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StatusEffectComponent>> STATUS_EFFECT =
            register_component("status_effect", builder -> builder.persistent(StatusEffectComponent.CODEC).networkSynchronized(StatusEffectComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlertComponent>> ALERT =
            register_component("alert", builder -> builder.persistent(AlertComponent.CODEC).networkSynchronized(AlertComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HungerComponent>> HUNGER =
            register_component("hunger", builder -> builder.persistent(HungerComponent.CODEC).networkSynchronized(HungerComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ThirstComponent>> THIRST =
            register_component("thirst", builder -> builder.persistent(ThirstComponent.CODEC).networkSynchronized(ThirstComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CharacterIdentityComponent>> CHARACTER_IDENTITY =
            register_component("character_identity", builder -> builder.persistent(CharacterIdentityComponent.CODEC)
                    .networkSynchronized(CharacterIdentityComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlidingComponent>> SLIDING =
            register_component("sliding", builder -> builder.persistent(SlidingComponent.CODEC)
                    .networkSynchronized(SlidingComponent.STREAM_CODEC));


    private static <T>DeferredHolder<DataComponentType<?>, DataComponentType<T>> register_component(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator){
        return DATA_COMPONENT_TYPES.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }

    public static void register_component(IEventBus eventBus){
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
