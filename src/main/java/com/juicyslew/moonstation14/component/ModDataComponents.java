package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
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
            register("reagent", builder -> builder.persistent(ReagentComponent.CODEC).networkSynchronized(ReagentComponent.STREAM_CODEC));

    private static <T>DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator){
        return DATA_COMPONENT_TYPES.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }

    public static void register(IEventBus eventBus){
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}
