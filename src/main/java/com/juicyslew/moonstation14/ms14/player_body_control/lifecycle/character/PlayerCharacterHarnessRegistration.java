package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Registration only: deliberately does not create or bind player bodies. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class PlayerCharacterHarnessRegistration {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "player_character_harness");
    private static EntityType<PlayerCharacterHarnessEntity> entityType;

    private PlayerCharacterHarnessRegistration() { }

    @SubscribeEvent
    public static void registerEntity(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, ID, () -> {
            entityType = EntityType.Builder.<PlayerCharacterHarnessEntity>of(
                            PlayerCharacterHarnessEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .build(ID.toString());
            return entityType;
        });
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(getEntityType(), PlayerCharacterHarnessEntity.createAttributes().build());
    }

    public static EntityType<PlayerCharacterHarnessEntity> getEntityType() {
        if (entityType == null) throw new IllegalStateException("Player character harness type is not registered");
        return entityType;
    }
}
