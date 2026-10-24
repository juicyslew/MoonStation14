package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Shared admission and episode owner for loose-item step contacts. */
public final class ItemStepContactSystem {
    private static final List<Predicate<TriggerContext>> GATES = new CopyOnWriteArrayList<>();
    private static final int MAX_HOOKS = 64;
    private ItemStepContactSystem() { }

    public static boolean addTriggerGate(Predicate<TriggerContext> gate) {
        if (GATES.size() >= MAX_HOOKS) return false;
        return GATES.add(java.util.Objects.requireNonNull(gate));
    }
    public static boolean removeTriggerGate(Predicate<TriggerContext> gate) { return GATES.remove(gate); }

    static void scan(ServerLevel level, LivingEntity target, AABB bounds, Vec3 velocity, double speed,
                     Consumer<ContactContext> consumer) {
        if (!Double.isFinite(speed)) return;
        AABB feet = ItemStepContactGeometry.feetBand(bounds);
        for (LooseItemEntity source : level.getEntitiesOfClass(LooseItemEntity.class, feet.inflate(1d))) {
            if (source.level() != level || source.isRemoved() || !source.isAddedToLevel()
                    || level.getEntity(source.getUUID()) != source || source.getItem().isEmpty()) continue;
            var profile = com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile
                    .resolveForStack(source.getItem(), false);
            var override = source.getItem().get(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_SLIPPERY_COMPOSITION.get());
            var trigger = profile.stepTrigger() != null ? profile.stepTrigger() : override == null ? null : override.stepTrigger();
            var response = override != null ? override.response() : profile.slippery();
            if (trigger == null) continue;
            if (profile.preventableStepTrigger()
                    && com.juicyslew.moonstation14.ms14.equipment.EquipmentStepTriggerProtectionPolicy.protects(target)) continue;
            if (source.getTarget() != null || !trigger.active() || (!trigger.ignoreWeightless() && !target.onGround())
                    || speed < trigger.requiredSpeed()
                    || ItemStepContactGeometry.intersectionRatio(feet, source.getBoundingBox()) < trigger.intersectionRatio()) continue;
            UUID actor = target.getUUID(), sourceId = source.getUUID();
            if (StepContactEpisodes.containsItem(level, actor, sourceId)) continue;
            ItemSlipperyComposition composition = response == null ? null : new ItemSlipperyComposition(trigger, response);
            TriggerContext gateContext = new TriggerContext(level, source, target, composition, trigger);
            boolean accepted = true;
            for (Predicate<TriggerContext> gate : GATES) {
                try { if (!gate.test(gateContext)) { accepted = false; break; } }
                catch (RuntimeException exception) { accepted = false; break; }
            }
            if (!accepted || !StepContactEpisodes.latchItem(level, actor, sourceId)) continue;
            consumer.accept(new ContactContext(level, source, target, trigger,
                    response, velocity, profile, composition == null ? null : composition));
        }
    }

    public record TriggerContext(ServerLevel level, LooseItemEntity source, LivingEntity target,
                                 ItemSlipperyComposition composition, ItemStepTriggerData trigger) { }
    public record ContactContext(ServerLevel level, LooseItemEntity source, LivingEntity target,
                                 ItemStepTriggerData trigger, SlipResponseData slipResponse,
                                  Vec3 velocity, com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile sourceProfile,
                                  ItemSlipperyComposition slipperyComposition) { }
}
