package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.item.ModFoodProperties;
import com.juicyslew.moonstation14.item.custom.BottleItem;
import com.juicyslew.moonstation14.item.custom.CrowbarItem;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.item.custom.SteelItem;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;


public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MoonStation14.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownJugEntity>> THROWN_JUG =
            ENTITIES.register("thrown_jug", () -> EntityType.Builder.<ThrownJugEntity>of(ThrownJugEntity::new, MobCategory.MISC)
                    .sized(1f, 1f) // Small hitbox
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_jug"));

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownGrenadeEntity>> THROWN_GRENADE =
            ENTITIES.register("thrown_grenade", () -> EntityType.Builder.<ThrownGrenadeEntity>of(ThrownGrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_grenade"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder>> DISPOSAL_TRANSIT_HOLDER =
            ENTITIES.register("disposal_transit_holder", () -> EntityType.Builder.<com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder>of(com.juicyslew.moonstation14.ms14.disposal.transport.DisposalTransitHolder::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(1).build("disposal_transit_holder"));
    public static final DeferredHolder<EntityType<?>, EntityType<LooseItemEntity>> LOOSE_ITEM =
            ENTITIES.register("loose_item", () -> EntityType.Builder.<LooseItemEntity>of(LooseItemEntity::new, MobCategory.MISC)
                    .sized(.25f, .25f).clientTrackingRange(8).updateInterval(1).build("loose_item"));
    public static final DeferredHolder<EntityType<?>, EntityType<GasCanisterEntity>> GAS_CANISTER =
            ENTITIES.register("gas_canister", () -> EntityType.Builder.<GasCanisterEntity>of(GasCanisterEntity::new, MobCategory.MISC)
                    .sized(.5f, 1f).clientTrackingRange(8).updateInterval(1).build("gas_canister"));
    public static final DeferredHolder<EntityType<?>, EntityType<EmitterBoltEntity>> EMITTER_BOLT =
            ENTITIES.register("emitter_bolt", () -> EntityType.Builder.<EmitterBoltEntity>of(EmitterBoltEntity::new, MobCategory.MISC)
                    .sized(.4f, .4f).clientTrackingRange(8).updateInterval(1).build("emitter_bolt"));
    public static final DeferredHolder<EntityType<?>, EntityType<SingularityEntity>> SINGULARITY =
            ENTITIES.register("singularity", () -> EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC)
                    .sized(1.5f, 1.5f).clientTrackingRange(16).updateInterval(1).build("singularity"));
    public static final DeferredHolder<EntityType<?>, EntityType<TeslaEnergyBallEntity>> TESLA_ENERGY_BALL =
            ENTITIES.register("tesla_energy_ball", () -> EntityType.Builder.<TeslaEnergyBallEntity>of(TeslaEnergyBallEntity::new, MobCategory.MISC)
                    .sized(1.5f, 1.5f).clientTrackingRange(16).updateInterval(1).build("tesla_energy_ball"));
    public static final DeferredHolder<EntityType<?>, EntityType<ParticleAcceleratorParticleEntity>> PA_PARTICLE =
            ENTITIES.register("particle_accelerator_particle", () -> EntityType.Builder.<ParticleAcceleratorParticleEntity>of(ParticleAcceleratorParticleEntity::new, MobCategory.MISC)
                    .sized(.96f, .96f).clientTrackingRange(8).updateInterval(1).build("particle_accelerator_particle"));
    public static final DeferredHolder<EntityType<?>, EntityType<MouseMob>> MOUSE =
            ENTITIES.register("mouse", () -> EntityType.Builder.<MouseMob>of(MouseMob::new, MobCategory.CREATURE)
                    // SS14 collision radius is 0.2; Minecraft's sized() takes width, so diameter is 0.4.
                    // Height 0.3 is a compact Minecraft geometry adaptation, not upstream fixture data.
                    .sized(0.4f, 0.3f).clientTrackingRange(8).updateInterval(1).build("mouse"));
    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
        eventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                event.put(MOUSE.get(), MouseMob.createAttributes().build()));
    }
}
