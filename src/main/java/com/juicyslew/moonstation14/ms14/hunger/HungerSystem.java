package com.juicyslew.moonstation14.ms14.hunger;

import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.ms14.alert.AlertSystem;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Objects;

/** Owner boundary for the simplified custom hunger scalar. */
public final class HungerSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ALERT_CATEGORY = ResourceLocation.fromNamespaceAndPath("moonstation14", "hunger");
    public static final ResourceLocation STARVING_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "hunger/starving");
    private static final ResourceKey<AlertData> PECKISH_ALERT = ModAlerts.createKey("peckish");
    private static final ResourceKey<AlertData> STARVING_ALERT = ModAlerts.createKey("starving");
    /** Temporary explicit enrollment; future character prototypes should provide the same opt-in. */
    public static final TagKey<EntityType<?>> ELIGIBLE_ENTITY_TYPES = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("moonstation14", "thirst_eligible"));

    private HungerSystem() { }
    public static boolean isEligible(LivingEntity entity) {
        return !ActiveCharacterPolicy.isCarrier(entity) && entity.getType().is(ELIGIBLE_ENTITY_TYPES);
    }
    public static boolean needsTicking(HungerAttachment state) { return state.hunger() > 0f; }

    /** One-time server-join initialization. Existing values, including 150, are authoritative. */
    public static boolean initializeIfEligible(LivingEntity entity, ServerLevel level) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(level, "level");
        if (!isEligible(entity)) return false;
        if (entity.hasData(ModDataAttachments.HUNGER.get())) {
            reconcile(entity, level);
            return false;
        }
        int value = HungerReducer.initialValue(level.getRandom().nextInt(
                HungerReducer.INITIAL_MIN_INCLUSIVE, HungerReducer.INITIAL_MAX_EXCLUSIVE));
        HungerAttachment initial = new HungerAttachment(new HungerComponent(value));
        boolean changed = MS14Provider.updateIfChanged(entity, MS14Bridges.HUNGER,
                new HungerComponent(HungerComponent.DEFAULT_HUNGER), initial);
        reconcile(entity, level);
        return changed;
    }

    /** Bounded adaptation: one SS14 elapsed second per staggered 20-tick derived work item. */
    public static boolean decayOneSecond(LivingEntity entity) {
        if (!isEligible(entity) || !entity.hasData(ModDataAttachments.HUNGER.get())) return false;
        HungerAttachment state = MS14Provider.getDetached(entity, MS14Bridges.HUNGER);
        HungerComponent before = MS14Provider.snapshot(state);
        HungerReducer.Result result = HungerReducer.decayOneSecond(state.hunger());
        if (result.changed()) state.setHunger(result.after());
        boolean changed = MS14Provider.updateIfChanged(entity, MS14Bridges.HUNGER, before, state);
        if (entity.level() instanceof ServerLevel level) reconcile(entity, level);
        return changed;
    }

    /** Rebuild only hunger-owned alert and speed projections; never mutate authoritative hunger. */
    public static boolean reconcile(LivingEntity entity, ServerLevel level) {
        if (!isEligible(entity) || !entity.hasData(ModDataAttachments.HUNGER.get())) {
            try { removeProjection(entity); return true; }
            catch (RuntimeException failure) {
                LOGGER.warn("Unable to remove stale hunger projection for {}", entity.getUUID(), failure);
                return false;
            }
        }
        HungerReducer.Level status = HungerReducer.classify(read(entity));
        boolean starving = status == HungerReducer.Level.STARVING || status == HungerReducer.Level.DEAD;
        boolean peckish = status == HungerReducer.Level.PECKISH;
        try {
            AlertSystem.Outcome first = setAlert(entity, level, PECKISH_ALERT, peckish);
            AlertSystem.Outcome second = setAlert(entity, level, STARVING_ALERT, starving);
            AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attribute != null) reconcileModifier(attribute, starving);
            return first != AlertSystem.Outcome.FAILED && second != AlertSystem.Outcome.FAILED;
        } catch (RuntimeException failure) {
            LOGGER.warn("Hunger projection failed for {}; authoritative hunger remains unchanged", entity.getUUID(), failure);
            return false;
        }
    }

    private static AlertSystem.Outcome setAlert(LivingEntity entity, ServerLevel level,
            ResourceKey<AlertData> key, boolean present) {
        if (!present) return AlertSystem.remove(entity, key);
        if (PrototypeRuntime.serverAlerts().get(key.location()) == null) {
            LOGGER.warn("Missing hunger alert prototype {}; authoritative hunger remains unchanged", key.location());
            return AlertSystem.Outcome.FAILED;
        }
        return AlertSystem.apply(entity, level, key, false, 0, false);
    }

    private static void reconcileModifier(AttributeInstance attribute, boolean slowed) {
        AttributeModifier current = attribute.getModifier(STARVING_MODIFIER_ID);
        if (!slowed) { if (current != null) attribute.removeModifier(STARVING_MODIFIER_ID); return; }
        if (current != null && Double.compare(current.amount(), -0.25d) == 0
                && current.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        if (current != null) attribute.removeModifier(STARVING_MODIFIER_ID);
        attribute.addTransientModifier(new AttributeModifier(STARVING_MODIFIER_ID, -0.25d,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void removeProjection(LivingEntity entity) {
        AlertSystem.remove(entity, PECKISH_ALERT);
        AlertSystem.remove(entity, STARVING_ALERT);
        AttributeInstance attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(STARVING_MODIFIER_ID);
    }
    public static float read(LivingEntity entity) {
        HungerAttachment existing = entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get());
        return existing == null ? HungerComponent.DEFAULT_HUNGER : existing.hunger();
    }
    public static float defaultForAbsent(boolean present, float value) {
        return present ? value : HungerComponent.DEFAULT_HUNGER;
    }
    public static boolean satiate(LivingEntity entity, float factor, float scale) {
        if (ActiveCharacterPolicy.isCarrier(entity)) return false;
        HungerReducer.Result transition = HungerReducer.satiate(read(entity), factor, scale);
        if (!transition.valid()) return false;
        if (!transition.changed()) return true;
        HungerAttachment state = MS14Provider.getDetached(entity, MS14Bridges.HUNGER);
        HungerComponent before = state.toComponent();
        state.setHunger(transition.after());
        MS14Provider.updateIfChanged(entity, MS14Bridges.HUNGER, before, state);
        if (entity.level() instanceof ServerLevel level && isEligible(entity)) reconcile(entity, level);
        return true;
    }

    /** Applies an owner-authoritative hunger adjustment only when this character has initialized state. */
    public static boolean satiateIfInitialized(LivingEntity entity, float factor, float scale) {
        if (ActiveCharacterPolicy.isCarrier(entity)) return false;
        if (!entity.hasData(com.juicyslew.moonstation14.component.ModDataAttachments.HUNGER.get())) return true;
        return satiate(entity, factor, scale);
    }
}
