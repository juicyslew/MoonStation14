package com.juicyslew.moonstation14.ms14.status_effect;


import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.recipe.ReactionRecipe;
import com.juicyslew.moonstation14.recipe.ReactionRecipeInput;
import com.juicyslew.moonstation14.util.interfaces.IClampedMapHolder;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.*;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;

public final class StatusEffectAttachment implements IMS14Attachment<StatusEffectAttachment, StatusEffectComponent> {
    private final Map<ResourceKey<StatusEffectData>, Float> statusEffectMap;

    public StatusEffectAttachment(StatusEffectComponent data){
        statusEffectMap = new HashMap<>(data.contents());
    }

    public StatusEffectAttachment(Map<ResourceKey<StatusEffectData>, Float> data){
        statusEffectMap = data;
    }

    public StatusEffectAttachment(){
        statusEffectMap = new HashMap<>();
    }

    public Map<ResourceKey<StatusEffectData>, Float> getMap() { return statusEffectMap; }

    public StatusEffectComponent toComponent() { return new StatusEffectComponent(Collections.unmodifiableMap(statusEffectMap)); }

    public static final Codec<StatusEffectAttachment> CODEC = StatusEffectComponent.CODEC.xmap(
            StatusEffectAttachment::new,
            StatusEffectAttachment::toComponent
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, StatusEffectAttachment> STREAM_CODEC = StatusEffectComponent.STREAM_CODEC.map(
        StatusEffectAttachment::new,
        StatusEffectAttachment::toComponent
    );

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatusEffectAttachment that = (StatusEffectAttachment) o;

        // 1. Check if the maps are identical (sizes and contents)
        // Most Java Map implementations (HashMap) handle deep equality automatically
        if (!Objects.equals(this.statusEffectMap, that.statusEffectMap)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        // Crucial: If you override equals, you MUST override hashCode
        return Objects.hash(statusEffectMap);
    }

    @Override
    public Codec<StatusEffectAttachment> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, StatusEffectAttachment> getStreamCodec() {
        return STREAM_CODEC;
    }
}