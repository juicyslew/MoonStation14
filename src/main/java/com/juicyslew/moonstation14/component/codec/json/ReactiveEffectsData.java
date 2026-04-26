package com.juicyslew.moonstation14.component.codec.json;

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

public record ReactiveEffectsData(List<String> methods, List<EffectData> effects) {
    public static final Codec<ReactiveEffectsData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.list(Codec.STRING).fieldOf("methods").forGetter(ReactiveEffectsData::methods),
            Codec.list(EffectData.CODEC).fieldOf("effects").forGetter(ReactiveEffectsData::effects) // If the reactive effect exists, these fields MUST be defined.
    ).apply(inst, ReactiveEffectsData::new));

    public void onTouched(Entity entity){
        // TODO: Implement
    }
}
