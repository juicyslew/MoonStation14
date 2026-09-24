package com.juicyslew.moonstation14.ms14.fire;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative persistence and mutation boundary for character fire stacks.
 * This milestone deliberately does not set vanilla fire ticks, deal burn damage,
 * render flames, spread fire, or ignite equipment.
 */
public final class FireStackSystem {
    private FireStackSystem() {
    }

    public static EffectResult flammable(Entity target, float multiplier,
                                         Float multiplierOnExisting, float scale) {
        if (!(target instanceof LivingEntity living) || living.fireImmune()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        try {
            FireStackComponent current = existing(living);
            FireStackComponent next = FireStackReducer.flammable(
                    current, multiplier, multiplierOnExisting, scale);
            persistIfChanged(living, current, next);
            return EffectResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return EffectResult.FAILED;
        }
    }

    public static EffectResult applyFlammable(Entity target, float multiplier,
                                              Float multiplierOnExisting, float scale) {
        return flammable(target, multiplier, multiplierOnExisting, scale);
    }

    public static EffectResult ignite(Entity target, float ignoredScale) {
        if (!(target instanceof LivingEntity living) || living.fireImmune()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        FireStackComponent current = existing(living);
        if (current.ignited() || current.stacks() <= 0f) {
            return EffectResult.APPLIED;
        }
        persistIfChanged(living, current, FireStackReducer.ignite(current));
        return EffectResult.APPLIED;
    }

    public static EffectResult applyIgnite(Entity target, float ignoredScale) {
        return ignite(target, ignoredScale);
    }

    public static EffectResult extinguish(Entity target, float adjustment, float scale) {
        if (!(target instanceof LivingEntity living) || living.fireImmune()) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        try {
            FireStackComponent current = existing(living);
            FireStackComponent next = FireStackReducer.extinguish(current, adjustment, scale);
            persistIfChanged(living, current, next);
            return EffectResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return EffectResult.FAILED;
        }
    }

    public static EffectResult applyExtinguish(Entity target, float adjustment, float scale) {
        return extinguish(target, adjustment, scale);
    }

    /** Applies one due 20-tick drying step without materializing absent state. */
    public static void dry(LivingEntity entity) {
        if (entity.fireImmune()) {
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        FireStackComponent current = existing(entity);
        if (current.stacks() >= 0f) {
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        persistIfChanged(entity, current, FireStackReducer.dry(current));
    }

    public static void dryOneInterval(LivingEntity entity) {
        dry(entity);
    }

    public static boolean needsDrying(FireStackComponent state) {
        return state != null && state.stacks() < 0f;
    }

    public static boolean supports(LivingEntity entity) {
        return entity != null && !entity.fireImmune();
    }

    public static FireStackComponent existing(LivingEntity entity) {
        FireStackAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        return attachment == null ? FireStackComponent.EMPTY : attachment.toComponent();
    }

    private static void persistIfChanged(LivingEntity entity, FireStackComponent before,
                                         FireStackComponent next) {
        if (before.equals(next)) {
            return;
        }
        if (next.isEmpty()) {
            entity.removeData(ModDataAttachments.FIRE_STACK.get());
            EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, false);
            return;
        }
        entity.setData(ModDataAttachments.FIRE_STACK.get(), next.toAttachment());
        EntityActivitySystem.update(entity, EntityActivity.FIRE_DRYING, needsDrying(next));
    }
}
