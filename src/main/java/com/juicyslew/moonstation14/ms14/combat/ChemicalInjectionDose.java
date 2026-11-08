package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutions;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import java.util.Map;
import java.util.Optional;

/** Prepares a detached injection dose and source remainder without publishing either. */
public final class ChemicalInjectionDose {
    private ChemicalInjectionDose() { }

    public record Prepared(ItemStack remainder, Map<ResourceKey<ReagentData>, Long> detachedDose) { }

    public static Optional<Prepared> prepare(ItemStack snapshot, ServerLevel level,
            MeleeWeaponProfile.MeleeChemicalInjectorData policy, int validCount) {
        if (snapshot == null || level == null || policy == null || validCount <= 0) return Optional.empty();
        ItemStack remainder = snapshot.copy();
        var food = ItemFoodSolutions.resolve(BuiltInRegistries.ITEM.getKey(remainder.getItem()), level);
        if (food == null || !food.solutionName().equals(policy.solutionName())) return Optional.empty();
        ItemFoodSolutions.initializeResolved(remainder, level);
        var current = remainder.get(ModDataComponents.REAGENT.get());
        ReagentAttachment source = current == null ? new ReagentAttachment() : new ReagentAttachment(current);
        long requested;
        try { requested = Math.multiplyExact(policy.transferCents(), (long) validCount); }
        catch (ArithmeticException overflow) { requested = ReagentUnits.MAX_CENTS; }
        var detachedDose = source.splitUnits(Math.min(requested, source.totalUnits()));
        if (detachedDose.isEmpty()) return Optional.empty();
        remainder.set(ModDataComponents.REAGENT.get(), source.toComponent());
        return Optional.of(new Prepared(remainder, Map.copyOf(detachedDose)));
    }
}
