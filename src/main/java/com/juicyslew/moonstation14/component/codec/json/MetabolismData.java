package com.juicyslew.moonstation14.component.codec.json;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record MetabolismData(List<EffectData> effects, Map<ResourceKey<ReagentData>, Float> metabolites, float rate) {

    public static final float DEFAULT_RATE = 0.5f;
    public static final Map<ResourceKey<ReagentData>, Float> DEFAULT_METABOLITES = Map.of();
    public static final List<EffectData> DEFAULT_EFFECTS = List.of();
    public static final MetabolismData DEFAULT = new MetabolismData(DEFAULT_EFFECTS, DEFAULT_METABOLITES, DEFAULT_RATE);

    public static final Codec<MetabolismData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(EffectData.CODEC).optionalFieldOf("effects", DEFAULT_EFFECTS).forGetter(MetabolismData::effects),
            Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                    rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                    ResourceKey::location
            ), Codec.FLOAT).optionalFieldOf("metabolites", DEFAULT_METABOLITES).forGetter(MetabolismData::metabolites),
            Codec.FLOAT.optionalFieldOf("metabolismRate", DEFAULT_RATE).forGetter(MetabolismData::rate)
    ).apply(inst, MetabolismData::new));

    public void Digest(Entity entity){
        ReagentAttachment reagentContainer = MS14Provider.get(entity, ReagentSystem.bridge);
        reagentContainer.mergeAdd(metabolites, 1000f);
    }
}