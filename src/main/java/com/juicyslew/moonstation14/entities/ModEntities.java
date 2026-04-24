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

import javax.swing.text.html.parser.Entity;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MoonStation14.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ThrownJugEntity>> THROWN_JUG =
            ENTITIES.register("thrown_jug", () -> EntityType.Builder.<ThrownJugEntity>of(ThrownJugEntity::new, MobCategory.MISC)
                    .sized(1f, 1f) // Small hitbox
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("thrown_jug"));

    public static void register(IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
