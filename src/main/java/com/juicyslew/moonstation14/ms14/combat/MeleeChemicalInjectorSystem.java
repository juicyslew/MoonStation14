package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.blood.BloodstreamSolutionAccess;
import com.juicyslew.moonstation14.ms14.equipment.MeleeInjectionProtectionPolicy;
import com.juicyslew.moonstation14.ms14.equipment.ResolvedWornEquipment;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import java.util.List;
import java.util.ArrayList;

/** Commits a melee injector's source dose before conditionally adding it to the target. */
public final class MeleeChemicalInjectorSystem {
    private MeleeChemicalInjectorSystem() { }

    public static MeleeWeaponResolver.Selection inject(LivingEntity attacker,
            MeleeWeaponResolver.Selection selected, List<Entity> hits) {
        if (selected == null) return null;
        var injector = selected.profile().chemicalInjector().orElse(null);
        if (injector == null || attacker == null || hits == null || hits.isEmpty()
                || !(attacker.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()) return selected;
        List<BloodstreamSolutionAccess.Snapshot> eligible = new ArrayList<>();
        for (Entity hit : hits) {
            if (!(hit instanceof LivingEntity target) || target.level() != level || target.isRemoved()
                    || level.getEntity(target.getUUID()) != target
                    || MeleeInjectionProtectionPolicy.protects(ResolvedWornEquipment.entries(target), injector.pierceArmor())) continue;
            BloodstreamSolutionAccess.inspect(target).ifPresent(eligible::add);
        }
        if (eligible.isEmpty()) return selected;

        var owner = MeleeItemOwner.read(attacker, selected).orElse(null);
        if (owner == null) return selected;
        ItemStack copy = owner.stack();
        var food = ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(copy.getItem()), level);
        if (food == null || !food.solutionName().equals(injector.solutionName())) return selected;
        ItemFoodSolutions.initializeResolved(copy, level);
        var current = copy.get(ModDataComponents.REAGENT.get());
        ReagentAttachment source = current == null ? new ReagentAttachment() : new ReagentAttachment(current);
        long requested;
        try { requested = Math.multiplyExact(injector.transferCents(), (long) eligible.size()); }
        catch (ArithmeticException overflow) { requested = ReagentUnits.MAX_CENTS; }
        var detachedDose = source.splitUnits(Math.min(requested, source.totalUnits()));
        if (detachedDose.isEmpty()) return selected;
        copy.set(ModDataComponents.REAGENT.get(), source.toComponent());
        var committed = MeleeItemOwner.commitComponents(owner, copy).orElse(null);
        if (committed == null) return selected;
        var shares = distributeDetachedDose(detachedDose, eligible.size());
        for (int i = 0; i < eligible.size(); i++) {
            var dose = shares.get(i);
            if (!dose.isEmpty()) BloodstreamSolutionAccess.tryAddWholeReacting(eligible.get(i), dose);
        }
        return committed.selection();
    }

    static List<java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>> distributeDetachedDose(
            java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long> detachedDose, int validCount) {
        if (validCount <= 0) return List.of();
        long each = detachedDose.values().stream().mapToLong(Long::longValue).sum() / validCount;
        ReagentAttachment remaining = new ReagentAttachment(ReagentComponent.fromCents(detachedDose));
        List<java.util.Map<net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData>, Long>> result = new ArrayList<>();
        for (int i = 0; i < validCount; i++) result.add(remaining.splitUnits(each));
        return List.copyOf(result);
    }
}
