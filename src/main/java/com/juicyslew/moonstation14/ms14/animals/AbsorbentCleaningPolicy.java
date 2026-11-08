package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.ms14.character.components.AbsorbentCleaningComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import net.minecraft.resources.ResourceKey;
import java.util.LinkedHashMap;

/** Pure cent-native absorbent exchange and periodic chemistry policies. */
public final class AbsorbentCleaningPolicy {
    public record Transfer(ReagentAttachment puddleAfter, ReagentAttachment cleanerAfter, long transferredCents) { }
    private AbsorbentCleaningPolicy() { }

    /** SS14 UseAbsorberSolution: transfer non-water contaminants into the cleaner and replace them with its water. */
    public static Transfer prepare(ReagentAttachment puddle, ReagentAttachment cleaner,
                                   AbsorbentCleaningComponent profile, ResourceKey<ReagentData> water,
                                   long puddleCapacityCents) {
        if (puddle == null || cleaner == null || profile == null || water == null) throw new IllegalArgumentException("missing absorbent transfer input");
        var contaminants = new LinkedHashMap<>(puddle.snapshotUnits());
        contaminants.remove(water);
        long nonWater = ReagentUnits.total(contaminants.values());
        long waterAvailable = cleaner.snapshotUnits().getOrDefault(water, 0L);
        long freeAfterWaterUse = profile.capacityCents() - cleaner.totalUnits() + waterAvailable;
        long amount = Math.min(Math.min(profile.pickupCents(), freeAfterWaterUse), nonWater);
        if (amount <= 0 || waterAvailable < amount) return null;
        var moved = ReagentUnits.split(contaminants, amount);
        var puddleAfter = new ReagentAttachment(puddle.getMap());
        moved.forEach(puddleAfter::removeUnits);
        puddleAfter.admitUnits(water, amount, puddleCapacityCents);
        var cleanerAfter = new ReagentAttachment(cleaner.getMap());
        cleanerAfter.removeUnits(water, amount);
        var admitted = cleanerAfter.addUnitsCapacitySafe(moved, profile.capacityCents());
        if (admitted.excess().values().stream().anyMatch(value -> value != 0)) return null;
        return new Transfer(puddleAfter, cleanerAfter, amount);
    }

    /** One upstream fixed-duration regen/purge cycle; purge excludes water and caps at requested quantity. */
    public static ReagentAttachment cycle(ReagentAttachment source, AbsorbentCleaningComponent profile,
                                           ResourceKey<ReagentData> water) {
        if (source == null || profile == null || water == null) throw new IllegalArgumentException("missing absorbent cycle input");
        ReagentAttachment result = new ReagentAttachment(source.getMap());
        long regen = Math.min(profile.regenerationCents(), Math.max(0, profile.capacityCents() - result.totalUnits()));
        if (regen > 0) result.admitUnits(water, regen, profile.capacityCents());
        long contaminants = result.totalUnits() - result.snapshotUnits().getOrDefault(water, 0L);
        if (contaminants > 0 && profile.purgeCents() > 0) {
            var mix = new LinkedHashMap<>(result.snapshotUnits()); mix.remove(water);
            var removed = ReagentUnits.split(mix, Math.min(contaminants, profile.purgeCents()));
            removed.forEach(result::removeUnits);
        }
        return result;
    }
}
