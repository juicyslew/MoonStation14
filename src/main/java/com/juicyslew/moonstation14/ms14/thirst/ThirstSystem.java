package com.juicyslew.moonstation14.ms14.thirst;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.ms14.alert.AlertSystem;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Objects;

/** Server owner of explicit opt-in character thirst state and its passive decay. */
public final class ThirstSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ALERT_CATEGORY = ResourceLocation.fromNamespaceAndPath("moonstation14", "thirst");
    public static final ResourceLocation PARCHED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "thirst/parched");
    private static final ResourceKey<AlertData> THIRSTY_ALERT = ModAlerts.createKey("thirsty");
    private static final ResourceKey<AlertData> PARCHED_ALERT = ModAlerts.createKey("parched");
    /** Temporary enrollment until character prototypes can own an equivalent opt-in. */
    public static final TagKey<EntityType<?>> ELIGIBLE_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("moonstation14", "thirst_eligible"));

    private ThirstSystem() { }

    public static boolean isEligible(LivingEntity entity) {
        return !ActiveCharacterPolicy.isCarrier(entity) && entity.getType().is(ELIGIBLE_ENTITY_TYPES);
    }

    public static boolean needsTicking(ThirstAttachment thirst) {
        return thirst.thirst() > ThirstComponent.MIN_THIRST;
    }

    /** Only join-time opt-in initializes; existing state is never replaced or rerolled. */
    public static boolean initializeIfEligible(LivingEntity entity, ServerLevel level) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(level, "level");
        if (!isEligible(entity)) return false;
        if (entity.hasData(ModDataAttachments.THIRST.get())) {
            reconcile(entity, level);
            return false;
        }
        int randomValue = ThirstReducer.initialValue(level.getRandom().nextInt(
                ThirstReducer.INITIAL_MIN_INCLUSIVE, ThirstReducer.INITIAL_MAX_EXCLUSIVE));
        ThirstAttachment initial = new ThirstAttachment(new ThirstComponent(randomValue));
        boolean changed = MS14Provider.updateIfChanged(entity, MS14Bridges.THIRST,
                new ThirstComponent(ThirstComponent.MIN_THIRST), initial);
        reconcile(entity, level);
        return changed;
    }

    /** Applies exactly one elapsed second; scheduler cadence, not elapsed-time catch-up, drives this. */
    public static boolean decayOneSecond(LivingEntity entity) {
        if (!isEligible(entity) || !entity.hasData(ModDataAttachments.THIRST.get())) return false;
        ThirstAttachment thirst = MS14Provider.getDetached(entity, MS14Bridges.THIRST);
        ThirstComponent before = MS14Provider.snapshot(thirst);
        ThirstReducer.Result result = ThirstReducer.decayOneSecond(thirst.thirst());
        if (result.changed()) thirst.setThirst(result.after());
        boolean changed = MS14Provider.updateIfChanged(entity, MS14Bridges.THIRST, before, thirst);
        // The caller is the scheduled server tick path; entity.level is server-owned here.
        if (entity.level() instanceof ServerLevel level) reconcile(entity, level);
        return changed;
    }

    public enum Outcome { APPLIED, SKIPPED_UNSUPPORTED, FAILED }

    /** Applies factor * scale only to an already initialized, explicitly enrolled target. */
    public static Outcome satiate(LivingEntity entity, ServerLevel level, float factor, float scale) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(level, "level");
        if (!Float.isFinite(factor) || !Float.isFinite(scale)) return Outcome.FAILED;
        if (!isEligible(entity) || !entity.hasData(ModDataAttachments.THIRST.get())) return Outcome.SKIPPED_UNSUPPORTED;
        ThirstAttachment thirst = MS14Provider.getDetached(entity, MS14Bridges.THIRST);
        ThirstComponent before = MS14Provider.snapshot(thirst);
        ThirstReducer.Result result = ThirstReducer.satiate(thirst.thirst(), factor, scale);
        if (!result.valid()) return Outcome.FAILED;
        if (result.changed()) thirst.setThirst(result.after());
        MS14Provider.updateIfChanged(entity, MS14Bridges.THIRST, before, thirst);
        return reconcile(entity, level) ? Outcome.APPLIED : Outcome.FAILED;
    }

    /** Rebuilds only thirst-owned alerts and speed modifiers from the authoritative scalar. */
    public static boolean reconcile(LivingEntity entity, ServerLevel level) {
        if (!isEligible(entity) || !entity.hasData(ModDataAttachments.THIRST.get())) {
            try {
                removeProjection(entity);
                return true;
            } catch (RuntimeException projectionFailure) {
                LOGGER.warn("Unable to remove stale thirst projection for {}", entity.getUUID(), projectionFailure);
                return false;
            }
        }
        float value = MS14Provider.get(entity, MS14Bridges.THIRST).thirst();
        ThirstReducer.Level status = ThirstReducer.classify(value);
        boolean parched = status == ThirstReducer.Level.PARCHED || status == ThirstReducer.Level.DEAD;
        boolean thirsty = status == ThirstReducer.Level.THIRSTY;
        try {
            AlertSystem.Outcome first = setAlert(entity, level, THIRSTY_ALERT, thirsty);
            AlertSystem.Outcome second = setAlert(entity, level, PARCHED_ALERT, parched);
            if (first == AlertSystem.Outcome.FAILED || second == AlertSystem.Outcome.FAILED) {
                LOGGER.warn("Unable to reconcile thirst alerts for {}; authoritative thirst remains unchanged", entity.getUUID());
            }
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute == null) return first != AlertSystem.Outcome.FAILED && second != AlertSystem.Outcome.FAILED;
            reconcileModifier(attribute, PARCHED_MODIFIER_ID, parched);
            return first != AlertSystem.Outcome.FAILED && second != AlertSystem.Outcome.FAILED;
        } catch (RuntimeException projectionFailure) {
            LOGGER.warn("Thirst projection failed for {}; authoritative thirst remains unchanged", entity.getUUID(), projectionFailure);
            return false;
        }
    }

    private static AlertSystem.Outcome setAlert(LivingEntity entity, ServerLevel level,
            ResourceKey<AlertData> key, boolean present) {
        if (!present) return AlertSystem.remove(entity, key);
        if (PrototypeRuntime.serverAlerts().get(key.location()) == null) {
            LOGGER.warn("Missing thirst alert prototype {}; authoritative thirst remains unchanged", key.location());
            return AlertSystem.Outcome.FAILED;
        }
        return AlertSystem.apply(entity, level, key, false, 0, false);
    }

    private static void reconcileModifier(AttributeInstance attribute, ResourceLocation id, boolean slowed) {
        AttributeModifier current = attribute.getModifier(id);
        if (!slowed) {
            if (current != null) attribute.removeModifier(id);
            return;
        }
        double amount = -0.25d;
        if (current != null && Double.compare(current.amount(), amount) == 0
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        if (current != null) attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id, amount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeProjection(LivingEntity entity) {
        AlertSystem.remove(entity, THIRSTY_ALERT);
        AlertSystem.remove(entity, PARCHED_ALERT);
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.removeModifier(PARCHED_MODIFIER_ID);
        }
    }
}
