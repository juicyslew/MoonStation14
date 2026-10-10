package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Formats a resolved solution snapshot for examine; storage and authoritative ownership remain elsewhere. */
public final class SolutionExamineFormatter {
    private SolutionExamineFormatter() { }

    public record Templates(String volume, String exactVolume, String volumeCategoryPrefix,
                            boolean exactIncludesCapacity, String physicalSingular, String physicalPlural, String recognized,
                            String list, String genericPhysical) {
        public static final Templates GENERIC = new Templates("examine.solution.volume", "examine.solution.volume.exact",
                "examine.solution.volume.", true,
                "examine.solution.physical.singular", "examine.solution.physical.plural",
                "examine.solution.recognized", "examine.solution.list",
                "examine.solution.generic-physical");
    }
    public record Output(Component volume, List<Component> details) {
        public Output { details = List.copyOf(details); }
    }
    private record Entry(ResourceLocation id, long amount, ReagentData data) { }

    public static Output format(ReagentAttachment solution, Level level, float capacity, boolean exact,
                                Templates templates,
                                Function<ResourceLocation, ReagentData> lookup) {
        var amounts = solution.snapshotUnits();
        long total = amounts.values().stream().mapToLong(Long::longValue).sum();
        Component volume;
        // SS14 OnExamineSolution returns the generic empty fill level before
        // exact-volume formatting when no primary reagent exists.
        if (total <= 0) {
            volume = Component.translatable(templates.volume(), Component.translatable(
                    templates.volumeCategoryPrefix() + "empty"));
            return new Output(volume, List.of());
        } else if (exact) {
            Object[] values = templates.exactIncludesCapacity()
                    ? new Object[]{ReagentUnits.formatCents(total), ReagentUnits.formatUnits(capacity)}
                    : new Object[]{ReagentUnits.formatCents(total)};
            volume = Component.translatable(templates.exactVolume(), values);
        } else {
            volume = Component.translatable(templates.volume(), Component.translatable(
                    templates.volumeCategoryPrefix() + volumeCategory(total, capacity)));
        }
        var entries = amounts.entrySet().stream().filter(e -> e.getValue() > 0)
                .map(e -> new Entry(e.getKey().location(), e.getValue(), lookup.apply(e.getKey().location())))
                // Stable prototype-id tie break avoids comparing unresolved translation keys on a dedicated server.
                .sorted(Comparator.comparingLong(Entry::amount).reversed().thenComparing(e -> e.id().toString()))
                .toList();
        int mixture = ReagentComponent.getBlendedColor(solution.getMap(), level);
        var details = new java.util.ArrayList<Component>();
        Entry primary = entries.getFirst();
        String physicalKey = primary.data() == null || primary.data().physicalDesc().isBlank()
                ? templates.genericPhysical() : primary.data().physicalDesc();
        Component description = Component.translatable(physicalKey);
        if (mixture != -1) description = description.copy().withStyle(s -> s.withColor(mixture));
        Component physical = Component.translatable(entries.size() == 1 ? templates.physicalSingular()
                : templates.physicalPlural(), Component.translatable(physicalKey + ".article"),
                description, entries.size());
        details.add(physical);
        List<Component> names = entries.stream().filter(e -> e.data() != null && e.data().recognizable().orElse(false))
                .map(e -> (Component) Component.translatable(e.data().name())
                        .withStyle(s -> s.withColor(e.data().color()))).toList();
        if (!names.isEmpty()) {
            Component list = names.getFirst();
            for (int i = 1; i < names.size(); i++) list = Component.translatable(templates.list(), list, names.get(i));
            details.add(Component.translatable(templates.recognized(), list));
        }
        return new Output(volume, details);
    }

    /** Source-aligned thresholds; cent quantities are compared against capacity in hundredths. */
    public static String volumeCategory(long cents, float capacity) {
        if (cents <= 0 || !Float.isFinite(capacity) || capacity <= 0) return "empty";
        int percent = (int) (cents / (double) capacity);
        if (percent == 0) return "empty";
        if (percent <= 33) return "mostly-empty";
        if (percent <= 66) return "half-full"; // Stable local approximation for examiner-dependent SS14 middle volume.
        if (percent == 100) return "full";
        if (percent > 66) return "mostly-full";
        return "empty";
    }
}
