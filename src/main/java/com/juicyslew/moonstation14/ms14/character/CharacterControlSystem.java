package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

import java.util.Objects;
import java.util.Map;
import java.util.OptionalInt;

/** Shared authoritative stun policy for every bound character profile. */
public final class CharacterControlSystem {
    private static final ResourceKey<StatusEffectData> STUNNED =
            ModStatusEffects.createKey("statuseffectstunned");
    private static final ResourceKey<StatusEffectData> KNOCKDOWN =
            ModStatusEffects.createKey("knockdown");

    private CharacterControlSystem() { }

    /** Applies a finite stun, only to a resolved character which permits it. */
    public static boolean applyStun(LivingEntity entity, int durationTicks) {
        Objects.requireNonNull(entity, "entity");
        if (durationTicks <= 0 || !(entity.level() instanceof ServerLevel level)
                || CharacterIdentitySystem.resolve(entity).filter(data -> data.slipData().canReceiveStun()).isEmpty()
                || !(entity instanceof IStatusEffectTrait statusTrait)) {
            return false;
        }

        StatusEffectSystem.apply(statusTrait.toHandleSelf(), level, STUNNED,
                StatusEffectOperation.SET, OptionalInt.of(durationTicks), 0);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        return true;
    }

    /** Converts seconds with the status system's finite-duration rounding rules. */
    public static boolean applyStun(LivingEntity entity, float seconds) {
        return applyStun(entity, StatusEffectSystem.secondsToTicks(seconds));
    }

    /** True only for active action-blocking statuses on an eligible bound profile. */
    public static boolean isStunned(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!(entity.level() instanceof ServerLevel level)
                || CharacterIdentitySystem.resolve(entity)
                .filter(data -> data.slipData().canReceiveStun()).isEmpty()
                || !(entity instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            return false;
        }
        var statuses = MS14Provider.get(entity, MS14Bridges.STATUS_EFFECT).snapshot();
        var catalog = ModStatusEffects.catalog(level);
        return statuses.entrySet().stream().anyMatch(entry -> {
            if (!entry.getValue().isActive()) return false;
            StatusEffectData definition = catalog.get(entry.getKey().location());
            return definition != null
                    && definition.behaviors().contains(StatusEffectBehavior.STUN_ACTION_BLOCK);
        });
    }

    /** Read-only active knockdown marker query for movement projection; does not materialize attachments. */
    public static boolean isKnockedDown(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.level().isClientSide) {
            if (!(entity instanceof IAttachmentHolder holder)
                    || !holder.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())
                    || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) return false;
            CharacterIdentityAttachment identity =
                    entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
            if (identity == null || !identity.isBound()) return false;
            CharacterData character = ModCharacters.catalog(entity.level()).get(identity.characterId());
            if (character == null || !character.slipData().canReceiveStun()) return false;
            var statuses = entity.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
            return statuses != null && isActive(statuses.snapshot(), ModStatusEffects.catalog(entity.level()));
        }
        if (!(entity.level() instanceof ServerLevel level)
                || CharacterIdentitySystem.resolve(entity)
                .filter(data -> data.slipData().canReceiveStun()).isEmpty()
                || !(entity instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) return false;
        return isActive(MS14Provider.get(entity, MS14Bridges.STATUS_EFFECT).snapshot(),
                ModStatusEffects.catalog(level));
    }

    private static boolean isActive(Map<ResourceKey<StatusEffectData>,
                                    com.juicyslew.moonstation14.ms14.status_effect.StatusEffectInstance> statuses,
                                    com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<StatusEffectData> catalog) {
        return statuses.entrySet().stream().anyMatch(entry -> entry.getValue().isActive()
                && catalog.get(entry.getKey().location()) != null
                && entry.getKey().location().equals(KNOCKDOWN.location()));
    }

    /**
     * Read-only client prediction query based exclusively on synchronized snapshots.
     * Missing client data or unresolved prototypes leave actions unblocked; server
     * authorization continues to use {@link #isStunned(LivingEntity)}.
     */
    public static boolean isClientActionBlocked(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (!entity.level().isClientSide || !(entity instanceof IAttachmentHolder holder)
                || !holder.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())
                || !holder.hasData(ModDataAttachments.STATUS_EFFECT.get())) {
            return false;
        }

        CharacterIdentityAttachment identity =
                entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound()) return false;

        PrototypeCatalog<CharacterData> characters = ModCharacters.catalog(entity.level());
        CharacterData character = characters.get(identity.characterId());
        if (character == null || !character.slipData().canReceiveStun()) return false;

        var statuses = entity.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        if (statuses == null) return false;
        var catalog = ModStatusEffects.catalog(entity.level());
        return statuses.snapshot().entrySet().stream().anyMatch(entry -> {
            if (!entry.getValue().isActive()) return false;
            StatusEffectData definition = catalog.get(entry.getKey().location());
            return definition != null
                    && definition.behaviors().contains(StatusEffectBehavior.STUN_ACTION_BLOCK);
        });
    }

    public static boolean canAct(LivingEntity entity) {
        return !isStunned(entity);
    }
}
