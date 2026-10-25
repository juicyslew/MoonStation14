package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageKernel;
import com.juicyslew.moonstation14.ms14.damage.ItemDamageState;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Applies post-cooldown melee wear to the currently owned source item only. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class RegisteredItemMeleeHitEffectsSystem {
    private RegisteredItemMeleeHitEffectsSystem() { }

    @SubscribeEvent
    public static void onMeleeHit(MeleeAttackEvents.MeleeHitEvent event) {
        if (!eligibleForEvent(event)) return;
        var frozen = event.weaponSelection();
        if (!eligibleForAnyEffect(frozen)) return;
        LivingEntity attacker = event.attacker();
        var current = MeleeChemicalInjectorSystem.inject(attacker, frozen, event.targets());
        if (current == null) current = frozen;
        applySelfDamage(event, current);
    }

    private static void applySelfDamage(MeleeAttackEvents.MeleeHitEvent event, MeleeWeaponResolver.Selection selection) {
        if (!eligibleForSelfDamage(selection)) return;
        LivingEntity attacker = event.attacker();
        var owner = MeleeItemOwner.read(attacker, selection).orElse(null);
        if (owner == null) return;
        try {
            var composition = LooseObjectProfile.resolveForStack(owner.stack(), false).itemDamage();
            if (composition == null) return;
            var damage = selection.profile().damageOnHit();
            var modifiers = composition.damageModifiers();
            if (!ItemDamageKernel.hasApplicableDamage(damage, composition.allowedGroups(),
                    composition.allowedTypes(), modifiers)) return;
            ItemDamageState current = owner.stack().getOrDefault(ModDataComponents.ITEM_DAMAGE_STATE.get(), ItemDamageState.EMPTY);
            ItemDamageState next = ItemDamageKernel.apply(current, damage, composition.allowedGroups(),
                    composition.allowedTypes(), modifiers);
            var replacement = owner.stack();
            replacement.set(ModDataComponents.ITEM_DAMAGE_STATE.get(), next);
            var committed = MeleeItemOwner.commitComponents(owner, replacement).orElse(null);
            if (committed == null || com.juicyslew.moonstation14.ms14.damage.DamageReducer.total(next.damage())
                    < composition.destructionThreshold() || !(attacker.level() instanceof ServerLevel level)) return;
            var after = MeleeItemOwner.read(attacker, committed.selection()).orElse(null);
            if (after == null) return;
            var sourceStack = after.stack();
            var feet = new Vec3(attacker.getX(), attacker.getBoundingBox().minY, attacker.getZ());
            var spill = composition.spillCurrentReagents()
                    ? com.juicyslew.moonstation14.ms14.damage.CommonItemDestructionEffects.findSpillSurface(level, attacker.getBoundingBox()) : null;
            com.juicyslew.moonstation14.ms14.damage.CommonItemDestructionEffects.publish(level, feet, spill,
                    sourceStack, composition, () -> MeleeItemOwner.read(attacker, committed.selection()).isPresent(),
                    () -> MeleeItemOwner.removeWhole(after).isPresent()
                            ? com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS
                            : com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.DENIED);
        } catch (RuntimeException unavailableProfile) {
            // Missing/unavailable loose-object capability is a no-op for this hit.
        }
    }

    static boolean eligibleForSelfDamage(MeleeWeaponResolver.Selection selection) {
        return selection != null && selection.kind() != MeleeWeaponResolver.Kind.UNARMED
                && !selection.profile().damageOnHit().isEmpty();
    }

    static boolean eligibleForAnyEffect(MeleeWeaponResolver.Selection selection) {
        return selection != null && selection.kind() != MeleeWeaponResolver.Kind.UNARMED
                && (selection.profile().chemicalInjector().isPresent() || !selection.profile().damageOnHit().isEmpty());
    }

    static boolean eligibleForEvent(MeleeAttackEvents.MeleeHitEvent event) {
        return event != null && event.isHit() && eligibleForAnyEffect(event.weaponSelection());
    }
}
