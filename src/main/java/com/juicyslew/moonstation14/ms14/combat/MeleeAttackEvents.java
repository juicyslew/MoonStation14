package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

/** NeoForge melee event contracts. Attack pipeline order: cancellable pre-cooldown attempt, cooldown admission,
 * cancellable post-cooldown attempt, then hit resolution event (including valid misses). Events never apply damage. */
public final class MeleeAttackEvents {
    private MeleeAttackEvents() { }

    public enum DisarmSource { HELD_ITEM, TARGET }
    public record OwnedItem(String hand, String token, long revision, ItemStack stack) {
        public OwnedItem { Objects.requireNonNull(hand); Objects.requireNonNull(token); if (revision < 0) throw new IllegalArgumentException("invalid owned item revision"); stack = Objects.requireNonNull(stack).copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    /** Cancellable source-specific stage. Held item and target effects are separate ordered events. */
    public static final class DisarmAttemptEvent extends Event implements ICancellableEvent {
        private final LivingEntity source;
        private final Entity target;
        private final Optional<Entity> actualHeldItem;
        private final DisarmSource disarmSource;
        private final float pushDamage;
        private final Optional<OwnedItem> ownedItem;
        public DisarmAttemptEvent(LivingEntity source, Entity target, Optional<Entity> actualHeldItem, DisarmSource disarmSource, float pushDamage) {
            this(source, target, actualHeldItem, disarmSource, pushDamage, Optional.empty());
        }
        public DisarmAttemptEvent(LivingEntity source, Entity target, Optional<Entity> actualHeldItem, DisarmSource disarmSource, float pushDamage, Optional<OwnedItem> ownedItem) {
            this.source = Objects.requireNonNull(source); this.target = Objects.requireNonNull(target);
            this.actualHeldItem = Objects.requireNonNull(actualHeldItem); this.disarmSource = Objects.requireNonNull(disarmSource);
            this.ownedItem = Objects.requireNonNull(ownedItem);
            if (!Float.isFinite(pushDamage) || pushDamage < 0) throw new IllegalArgumentException("invalid disarm push damage");
            this.pushDamage = pushDamage;
        }
        public LivingEntity source() { return source; }
        public Entity target() { return target; }
        public Optional<Entity> actualHeldItem() { return actualHeldItem; }
        public DisarmSource disarmSource() { return disarmSource; }
        public float pushDamage() { return pushDamage; }
        public Optional<OwnedItem> ownedItem() { return ownedItem; }
    }

    public static final class DisarmedEvent extends Event {
        private final Entity target;
        private final float pushProbability;
        private boolean handled;
        private boolean stunned;
        private String popupPrefix;
        private boolean terminalAbort;
        public DisarmedEvent(Entity target, float pushProbability) {
            this.target = Objects.requireNonNull(target);
            if (!Float.isFinite(pushProbability) || pushProbability < 0 || pushProbability > 1) throw new IllegalArgumentException("invalid push probability");
            this.pushProbability = pushProbability;
        }
        public Entity target() { return target; }
        public float pushProbability() { return pushProbability; }
        public boolean handled() { return handled; }
        public void setHandled(boolean handled) { this.handled = handled; }
        public boolean isStunned() { return stunned; }
        public void setStunned(boolean stunned) { this.stunned = stunned; }
        public Optional<String> popupPrefix() { return Optional.ofNullable(popupPrefix); }
        public void setPopupPrefix(String popupPrefix) { this.popupPrefix = popupPrefix; }
        public boolean terminalAbort() { return terminalAbort; }
        public void setTerminalAbort(boolean terminalAbort) { this.terminalAbort = terminalAbort; }
    }

    /** Before cooldown is consumed; cancellation must prevent cooldown and all later attack stages. */
    public static final class SharedMeleeAttackAttemptEvent extends Event implements ICancellableEvent {
        private final LivingEntity attacker;
        private final Optional<Entity> target;
        private final MeleeWeaponResolver.Selection weaponSelection;
        private final boolean disarm;
        public SharedMeleeAttackAttemptEvent(LivingEntity attacker, Optional<Entity> target, MeleeWeaponResolver.Selection selection) { this(attacker, target, selection, false); }
        public SharedMeleeAttackAttemptEvent(LivingEntity attacker, Optional<Entity> target, MeleeWeaponResolver.Selection selection, boolean disarm) {
            this.attacker = Objects.requireNonNull(attacker); this.target = Objects.requireNonNull(target);
            this.weaponSelection = Objects.requireNonNull(selection); this.disarm = disarm;
        }
        public LivingEntity attacker() { return attacker; }
        public Optional<Entity> target() { return target; }
        public MeleeWeaponResolver.Selection weaponSelection() { return weaponSelection; }
        public boolean disarm() { return disarm; }
    }

    /** After cooldown is consumed and before hit resolution; cancellation suppresses the hit stage, not cooldown. */
    public static final class AttemptMeleeEvent extends Event implements ICancellableEvent {
        private final LivingEntity attacker;
        private final Optional<Entity> target;
        private final MeleeWeaponResolver.Selection weaponSelection;
        public AttemptMeleeEvent(LivingEntity attacker, Optional<Entity> target, MeleeWeaponResolver.Selection selection) {
            this.attacker = Objects.requireNonNull(attacker); this.target = Objects.requireNonNull(target);
            this.weaponSelection = Objects.requireNonNull(selection);
        }
        public LivingEntity attacker() { return attacker; }
        public Optional<Entity> target() { return target; }
        public MeleeWeaponResolver.Selection weaponSelection() { return weaponSelection; }
    }

    /** After cooldown, once valid nominated targets or a miss are resolved; targets is immutable. */
    public static final class MeleeHitEvent extends Event {
        private final LivingEntity attacker;
        private final List<Entity> targets;
        private final MeleeWeaponResolver.Selection weaponSelection;
        private boolean handled;
        private PositionalSoundCue hitSoundOverride;
        private final Map<String, Float> bonusDamage = new LinkedHashMap<>();
        public MeleeHitEvent(LivingEntity attacker, List<? extends Entity> targets, MeleeWeaponResolver.Selection selection) {
            this.attacker = Objects.requireNonNull(attacker); Objects.requireNonNull(targets);
            if (targets.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("melee targets must not contain null entities");
            this.targets = List.copyOf(targets); this.weaponSelection = Objects.requireNonNull(selection);
        }
        public LivingEntity attacker() { return attacker; }
        public List<Entity> targets() { return targets; }
        public boolean isHit() { return !targets.isEmpty(); }
        public MeleeWeaponResolver.Selection weaponSelection() { return weaponSelection; }
        public boolean handled() { return handled; }
        public void setHandled(boolean handled) { this.handled = handled; }
        public Optional<PositionalSoundCue> hitSoundOverride() { return Optional.ofNullable(hitSoundOverride); }
        public void setHitSoundOverride(PositionalSoundCue cue) { hitSoundOverride = cue; }
        public Map<String, Float> bonusDamage() { return Map.copyOf(bonusDamage); }
        public void addBonusDamage(String type, float amount) { addValidatedBonus(bonusDamage, type, amount); }
    }

    /** After hit listeners, before ordinary melee damage; listeners may add validated bonus typed damage. */
    public static final class MeleeTargetAttackedEvent extends Event {
        private final LivingEntity attacker;
        private final Entity target;
        private final MeleeWeaponResolver.Selection weaponSelection;
        private final Map<String, Float> bonusDamage = new LinkedHashMap<>();
        public MeleeTargetAttackedEvent(LivingEntity attacker, Entity target, MeleeWeaponResolver.Selection selection) {
            this.attacker = Objects.requireNonNull(attacker); this.target = Objects.requireNonNull(target);
            this.weaponSelection = Objects.requireNonNull(selection);
        }
        public LivingEntity attacker() { return attacker; }
        public Entity target() { return target; }
        public MeleeWeaponResolver.Selection weaponSelection() { return weaponSelection; }
        public Map<String, Float> bonusDamage() { return Map.copyOf(bonusDamage); }
        public void addBonusDamage(String type, float amount) {
            addValidatedBonus(bonusDamage, type, amount);
        }
    }

    private static void addValidatedBonus(Map<String, Float> bonusDamage, String type, float amount) {
        Map<String, Float> checked = DamageKeys.validateState(Map.of(type, amount));
        if (checked.containsKey(type)) bonusDamage.merge(type, checked.get(type), Float::sum);
    }
}
