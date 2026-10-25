package com.juicyslew.moonstation14.entities;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.animals.AnimalCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

/** Registration and renderer-facing immutable mapping for custom animal hosts. */
public final class AnimalMobRegistration {
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MoonStation14.MOD_ID);
    private static final Map<String, DeferredHolder<EntityType<?>, EntityType<AnimalMob>>> HOSTS = new LinkedHashMap<>();
    static {
        for (AnimalCatalog.Entry entry : AnimalCatalog.entries()) HOSTS.put(entry.modelId(), TYPES.register(entry.id(), () -> EntityType.Builder
                .<AnimalMob>of(AnimalMob::new, MobCategory.CREATURE).sized(entry.width(), entry.height())
                .clientTrackingRange(8).updateInterval(1).build(entry.id())));
    }
    private AnimalMobRegistration() { }
    public static void register(net.neoforged.bus.api.IEventBus bus) { TYPES.register(bus); }
    public static Map<String, DeferredHolder<EntityType<?>, EntityType<AnimalMob>>> types() { return Map.copyOf(HOSTS); }
    public static void registerAttributes(net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) {
        HOSTS.values().forEach(type -> event.put(type.get(), AnimalMob.createAttributes().build()));
    }
}
