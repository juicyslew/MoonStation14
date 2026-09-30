package com.juicyslew.moonstation14.ms14.eye;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.character.components.BlindablePrototypeComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server-authoritative persistence boundary for character-owned eye damage. */
public final class EyeDamageSystem {
    private EyeDamageSystem() {
    }

    public static EffectResult apply(Entity target, int amount, float scale) {
        if (!(target instanceof LivingEntity living) || !supports(living)) {
            return EffectResult.SKIPPED_UNSUPPORTED;
        }
        try {
            EyeDamageComponent current = existing(living);
            EyeDamageComponent next = EyeDamageReducer.apply(current, amount, scale);
            persistIfChanged(living, current, next);
            return EffectResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return EffectResult.FAILED;
        }
    }

    public static EyeDamageComponent existing(LivingEntity entity) {
        EyeDamageAttachment attachment = entity.getExistingDataOrNull(ModDataAttachments.EYE_DAMAGE.get());
        return attachment == null ? EyeDamageComponent.EMPTY : attachment.toComponent();
    }

    /** Raw persisted damage for diagnostics, including dormant state on an ineligible host. */
    public static int damage(Entity entity) {
        return entity instanceof LivingEntity living ? existing(living).damage() : 0;
    }

    public static boolean isBlind(Entity entity) {
        return entity instanceof LivingEntity living && supports(living) && existing(living).isBlind();
    }

    public static boolean supports(LivingEntity entity) {
        return entity != null && entity.level() instanceof ServerLevel
                && ActiveCharacterPolicy.resolveActor(entity)
                .flatMap(character -> character.component(BlindablePrototypeComponent.class)).isPresent();
    }

    private static void persistIfChanged(LivingEntity entity, EyeDamageComponent before,
                                         EyeDamageComponent next) {
        if (next.isEmpty()) {
            // Normalize an accidentally materialized zero without making an
            // absent no-op materialize first.
            if (entity.getExistingDataOrNull(ModDataAttachments.EYE_DAMAGE.get()) != null) {
                entity.removeData(ModDataAttachments.EYE_DAMAGE.get());
            }
            return;
        }
        if (before == next || before.equals(next)) {
            return;
        }
        entity.setData(ModDataAttachments.EYE_DAMAGE.get(), next.toAttachment());
    }
}
