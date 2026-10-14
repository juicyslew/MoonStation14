package com.juicyslew.moonstation14.component;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectComponent;
import com.juicyslew.moonstation14.ms14.alert.AlertComponent;
import com.juicyslew.moonstation14.ms14.hunger.HungerComponent;
import com.juicyslew.moonstation14.ms14.thirst.ThirstComponent;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityComponent;
import com.juicyslew.moonstation14.ms14.blood.BloodComponent;
import com.juicyslew.moonstation14.ms14.slip.SlidingComponent;
import com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureComponent;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.chemistry.output.PillData;
import com.juicyslew.moonstation14.ms14.chemistry.output.ChemOutputLabel;
import com.juicyslew.moonstation14.ms14.botany.ProduceInstanceData;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelData;
import com.juicyslew.moonstation14.ms14.medical.HealthAnalyzerStackData;
import com.juicyslew.moonstation14.ms14.medical.SyringeStackData;
import com.juicyslew.moonstation14.ms14.devicenet.item.NetworkConfiguratorStackData;
import com.juicyslew.moonstation14.ms14.devicenet.item.RemoteSignallerStackData;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import net.minecraft.resources.ResourceLocation;
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

    /** Bridge representation for entity-owned body temperature; no item behavior is currently assigned. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BodyTemperatureComponent>> BODY_TEMPERATURE =
            register_component("body_temperature", builder -> builder.persistent(BodyTemperatureComponent.CODEC)
                    .networkSynchronized(BodyTemperatureComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CharacterIdentityComponent>> CHARACTER_IDENTITY =
            register_component("character_identity", builder -> builder.persistent(CharacterIdentityComponent.CODEC)
                    .networkSynchronized(CharacterIdentityComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HandComponent>> HANDS =
            register_component("hands", builder -> builder.persistent(HandComponent.CODEC)
                    .networkSynchronized(HandComponent.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BloodComponent>> BLOOD =
            register_component("blood", builder -> builder.persistent(BloodComponent.CODEC)
                    .networkSynchronized(BloodComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SlidingComponent>> SLIDING =
            register_component("sliding", builder -> builder.persistent(SlidingComponent.CODEC)
                    .networkSynchronized(SlidingComponent.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PouchContents>> POUCH_CONTENTS =
            register_component("pouch_contents", builder -> builder.persistent(PouchContents.CODEC)
                    .networkSynchronized(PouchContents.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GridContents>> GRID_CONTENTS =
            register_component("grid_contents", builder -> builder.persistent(GridContents.CODEC)
                    .networkSynchronized(GridContents.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PillData>> PILL_TYPE =
            register_component("pill_type", builder -> builder.persistent(PillData.CODEC).networkSynchronized(
                    net.minecraft.network.codec.ByteBufCodecs.fromCodec(PillData.CODEC)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChemOutputLabel>> CHEM_OUTPUT_LABEL =
            register_component("chem_output_label", builder -> builder.persistent(ChemOutputLabel.CODEC)
                    .networkSynchronized(ChemOutputLabel.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProduceInstanceData>> PRODUCE_INSTANCE =
            register_component("produce_instance", builder -> builder.persistent(ProduceInstanceData.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.fromCodec(ProduceInstanceData.CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GrenadeFuseData>> GRENADE_FUSE =
            register_component("grenade_fuse", builder -> builder.persistent(GrenadeFuseData.CODEC)
                    .networkSynchronized(GrenadeFuseData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WelderFuelData>> WELDER_FUEL =
            register_component("welder_fuel", builder -> builder.persistent(WelderFuelData.CODEC)
                    .networkSynchronized(WelderFuelData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GasTankContents>> GAS_TANK_CONTENTS =
            register_component("gas_tank_contents", builder -> builder.persistent(GasTankContents.CODEC)
                    .networkSynchronized(GasTankContents.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> LOOSE_OBJECT_PROFILE =
            register_component("loose_object_profile", builder -> builder.persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> NETWORK_DEVICE_ITEM_PROFILE =
            register_component("network_device_item_profile", builder -> builder.persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HealthAnalyzerStackData>> HEALTH_ANALYZER =
            register_component("health_analyzer", builder -> builder.persistent(HealthAnalyzerStackData.CODEC)
                    .networkSynchronized(HealthAnalyzerStackData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SyringeStackData>> SYRINGE =
            register_component("syringe", builder -> builder.persistent(SyringeStackData.CODEC)
                    .networkSynchronized(SyringeStackData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NetworkConfiguratorStackData>> NETWORK_CONFIGURATOR =
            register_component("network_configurator", builder -> builder.persistent(NetworkConfiguratorStackData.CODEC)
                    .networkSynchronized(NetworkConfiguratorStackData.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RemoteSignallerStackData>> REMOTE_SIGNALLER =
            register_component("remote_signaller", builder -> builder.persistent(RemoteSignallerStackData.CODEC)
                    .networkSynchronized(RemoteSignallerStackData.STREAM_CODEC));

    private static <T>DeferredHolder<DataComponentType<?>, DataComponentType<T>> register_component(
            String name,
            UnaryOperator<DataComponentType.Builder<T>> builderOperator){
        return DATA_COMPONENT_TYPES.register(name, () -> builderOperator.apply(DataComponentType.builder()).build());
    }

    public static void register_component(IEventBus eventBus){
        DATA_COMPONENT_TYPES.register(eventBus);
    }
}

