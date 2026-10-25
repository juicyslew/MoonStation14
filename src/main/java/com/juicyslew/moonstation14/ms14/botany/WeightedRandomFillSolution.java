package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Ordered weighted groups; selection intentionally weights groups before uniformly selecting an ID. */
public record WeightedRandomFillSolution(List<Group> fills) {
    public record Group(float quantity, float weight, List<ResourceLocation> reagents) {
        public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("quantity").forGetter(Group::quantity),
                Codec.FLOAT.fieldOf("weight").forGetter(Group::weight),
                ResourceLocation.CODEC.listOf().fieldOf("reagents").forGetter(Group::reagents)
        ).apply(i, Group::new));
        public Group {
            reagents = List.copyOf(reagents);
            if (!Float.isFinite(quantity) || quantity < 0 || !Float.isFinite(weight) || weight < 0 || reagents.isEmpty())
                throw new IllegalArgumentException("invalid weighted fill group");
        }
    }
    public record Selection(ResourceLocation reagent, float quantity) { }
    @FunctionalInterface public interface RandomSource { float nextFloat(); }
    public static final Codec<WeightedRandomFillSolution> CODEC = RecordCodecBuilder.create(i -> i.group(
            Group.CODEC.listOf().fieldOf("fills").forGetter(WeightedRandomFillSolution::fills)
    ).apply(i, WeightedRandomFillSolution::new));
    public WeightedRandomFillSolution {
        fills = List.copyOf(fills);
        if (fills.isEmpty() || fills.stream().noneMatch(g -> g.weight() > 0 && !g.reagents().isEmpty()))
            throw new IllegalArgumentException("weighted fill requires a selectable group");
    }
    public Selection select(RandomSource random) {
        float total = 0;
        for (Group group : fills) total += group.weight();
        float roll = random.nextFloat() * total;
        float accumulated = 0;
        Group selected = null;
        for (Group group : fills) {
            accumulated += group.weight();
            if (accumulated >= roll) { selected = group; break; }
        }
        if (selected == null) throw new IllegalStateException("weighted selection exceeded total weight");
        float reagentRoll = random.nextFloat() * selected.reagents().size();
        int index = 0;
        while (index < selected.reagents().size() - 1 && index + 1 < reagentRoll) index++;
        return new Selection(selected.reagents().get(index), selected.quantity());
    }
}
