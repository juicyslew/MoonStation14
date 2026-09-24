package com.juicyslew.moonstation14.ms14.activity;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.fire.FireStackAttachment;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.util.SystemLink;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.EnumSet;
import java.util.Objects;

/** Maintains the non-authoritative activity index for living entity work. */
public final class EntityActivitySystem {
    private EntityActivitySystem() {
    }

    public static <A extends IMS14Attachment<A, C>, C extends IMS14Component<C, A>> void update(
            Entity entity, SystemLink<A, C> link, A data) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(data, "data");
        link.activityBinding().ifPresent(binding ->
                update(entity, binding.activity(), binding.needsTicking().test(data)));
    }

    public static void update(Entity entity, EntityActivity activity, boolean enabled) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(activity, "activity");
        // Activity dispatch is server-only and defined only for living entities;
        // never create or mutate derived scheduling state elsewhere.
        if (!(entity instanceof LivingEntity)
                || entity.level() == null
                || entity.level().isClientSide()) {
            return;
        }

        EntityActivityAttachment current = existing(entity);
        if (enabled) {
            if (current == null) {
                current = new EntityActivityAttachment();
                current.enable(activity);
                entity.setData(ModDataAttachments.ACTIVE_SYSTEMS.get(), current);
            } else {
                current.enable(activity);
            }
        } else if (current != null && current.disable(activity) && current.isEmpty()) {
            entity.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());
        }
    }

    /** Rebuilds derived flags from the authoritative attachments without creating absent data. */
    public static void reconcile(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.level() == null || entity.level().isClientSide()) {
            return;
        }
        EnumSet<EntityActivity> desired = EnumSet.noneOf(EntityActivity.class);

        var status = entity.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        if (status != null) {
            MS14Bridges.STATUS_EFFECT.activityBinding().ifPresent(binding -> {
                if (binding.needsTicking().test(status)) {
                    desired.add(binding.activity());
                }
            });
        }
        var reagent = entity.getExistingDataOrNull(ModDataAttachments.REAGENT.get());
        if (reagent != null) {
            MS14Bridges.REAGENT.activityBinding().ifPresent(binding -> {
                if (binding.needsTicking().test(reagent)) {
                    desired.add(binding.activity());
                }
            });
        }
        var stomach = entity.getExistingDataOrNull(ModDataAttachments.STOMACH.get());
        if (stomach != null && MS14Bridges.STOMACH.activityBinding().orElseThrow()
                .needsTicking().test(stomach)) {
            desired.add(EntityActivity.REAGENT_METABOLISM);
        }
        var alerts = entity.getExistingDataOrNull(ModDataAttachments.ALERT.get());
        if (alerts != null) {
            MS14Bridges.ALERT.activityBinding().ifPresent(binding -> {
                if (binding.needsTicking().test(alerts)) desired.add(binding.activity());
            });
        }
        FireStackAttachment fire = entity.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        if (fire != null && FireStackSystem.supports(entity)
                && FireStackSystem.needsDrying(fire.toComponent())) {
            desired.add(EntityActivity.FIRE_DRYING);
        }
        var thirst = entity.getExistingDataOrNull(ModDataAttachments.THIRST.get());
        if (thirst != null && com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.isEligible(entity)
                && com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.needsTicking(thirst)) {
            desired.add(EntityActivity.THIRST);
        }
        var hunger = entity.getExistingDataOrNull(ModDataAttachments.HUNGER.get());
        if (hunger != null && com.juicyslew.moonstation14.ms14.hunger.HungerSystem.isEligible(entity)
                && com.juicyslew.moonstation14.ms14.hunger.HungerSystem.needsTicking(hunger)) {
            desired.add(EntityActivity.HUNGER);
        }

        EntityActivityAttachment current = existing(entity);
        if (desired.isEmpty()) {
            if (current != null) {
                entity.removeData(ModDataAttachments.ACTIVE_SYSTEMS.get());
            }
            return;
        }
        if (current == null) {
            current = new EntityActivityAttachment();
            entity.setData(ModDataAttachments.ACTIVE_SYSTEMS.get(), current);
        }
        for (EntityActivity activity : EntityActivity.values()) {
            current.setActive(activity, desired.contains(activity));
        }
    }

    private static EntityActivityAttachment existing(Entity entity) {
        return entity.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
    }
}
