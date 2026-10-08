package com.juicyslew.moonstation14.ms14.construction.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** Browser recipe intent; starting costs are derived from the graph edge. */
public record ConstructionRecipeData(String name, String description, String category, String family,
                                     String icon, String graph, String startingNode, String targetNode,
                                     OutputKind outputKind) {
    public enum OutputKind { STRUCTURE, ITEM }
    public static final Codec<ConstructionRecipeData> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.fieldOf("name").forGetter(ConstructionRecipeData::name),
            Codec.STRING.fieldOf("description").forGetter(ConstructionRecipeData::description),
            Codec.STRING.fieldOf("category").forGetter(ConstructionRecipeData::category),
            Codec.STRING.fieldOf("family").forGetter(ConstructionRecipeData::family),
            Codec.STRING.fieldOf("icon").forGetter(ConstructionRecipeData::icon),
            Codec.STRING.fieldOf("graph").forGetter(ConstructionRecipeData::graph),
            Codec.STRING.fieldOf("startingNode").forGetter(ConstructionRecipeData::startingNode),
            Codec.STRING.fieldOf("targetNode").forGetter(ConstructionRecipeData::targetNode),
            Codec.STRING.fieldOf("outputKind").xmap(OutputKind::valueOf,Enum::name).forGetter(ConstructionRecipeData::outputKind)
    ).apply(i,ConstructionRecipeData::new));
    public ConstructionRecipeData { Objects.requireNonNull(name);Objects.requireNonNull(description);Objects.requireNonNull(category);Objects.requireNonNull(family);Objects.requireNonNull(icon);Objects.requireNonNull(graph);Objects.requireNonNull(startingNode);Objects.requireNonNull(targetNode);Objects.requireNonNull(outputKind); }
}
