package com.juicyslew.moonstation14.ms14.player_body_control.ghost;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Isolated registration for the vertical-slice ghost harness. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class GhostMobHarnessRegistration {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            MoonStation14.MOD_ID, "ghost_mob_harness");

    private static EntityType<GhostMobHarnessEntity> entityType;

    private GhostMobHarnessRegistration() { }

    @SubscribeEvent
    public static void registerEntity(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, ID, () -> {
            entityType = EntityType.Builder.<GhostMobHarnessEntity>of(GhostMobHarnessEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .noSave()
                    .build(ID.toString());
            return entityType;
        });
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(getEntityType(), GhostMobHarnessEntity.createAttributes().build());
    }

    public static EntityType<GhostMobHarnessEntity> getEntityType() {
        if (entityType == null) {
            throw new IllegalStateException("Ghost mob harness entity type has not been registered");
        }
        return entityType;
    }
}
