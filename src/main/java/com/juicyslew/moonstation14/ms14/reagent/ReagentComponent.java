package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.juicyslew.moonstation14.util.interfaces.IMS14Codeced;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

public record ReagentComponent(Map<ResourceKey<ReagentData>, Float> contents) implements IMS14Codeced<ReagentComponent>, IMS14Component<ReagentComponent, ReagentAttachment> {

    public ReagentComponent() {
        this(Map.of());
    }

    public ReagentAttachment toAttachment(){
        return new ReagentAttachment(this);
    }

    public static int getBlendedColor(Map<ResourceKey<ReagentData>, Float> contents, Level level) {
        // This is called way more often than it probably needs to be. Maybe have a cached value or somethin.
        if (contents.isEmpty()) return -1; // Default/No tint

        Registry<ReagentData> reg = ModReagents.getRegistry(level);
        float totalWeight = 0;
        float r = 0, g = 0, b = 0;

        for (Map.Entry<ResourceKey<ReagentData>, Float> entry : contents.entrySet()) {
            float amount = entry.getValue();
            int color = reg.getOrThrow(entry.getKey()).color();

            r += ((color >> 16) & 0xFF) * amount;
            g += ((color >> 8) & 0xFF) * amount;
            b += (color & 0xFF) * amount;
            totalWeight += amount;
        }

        // These int casts might be better being Math.round
        return 0xFF000000 | ((int)(r / totalWeight) << 16) | ((int)(g / totalWeight) << 8) | (int)(b / totalWeight);
    }

    // This is the ONLY Codec you need to write for maps
    public static final Codec<ReagentComponent> CODEC =
            Codec.unboundedMap(LENIENT_ID_CODEC.xmap(
                    rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl),
                    ResourceKey::location
            ), Codec.FLOAT).xmap(ReagentComponent::new, ReagentComponent::contents);

    // NETWORK: Uses optimized Integer IDs for the ResourceKeys (Registry-aware)
    public static final StreamCodec<RegistryFriendlyByteBuf, ReagentComponent> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<ReagentData>, Float, Map<ResourceKey<ReagentData>, Float>>map(
                    HashMap::new,
                    ResourceKey.streamCodec(ModReagents.REAGENT_REGISTRY_KEY),
                    ByteBufCodecs.FLOAT
            ).map(ReagentComponent::new, ReagentComponent::contents);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReagentComponent other)) return false;
        // HashMap.equals() checks every key and every value for equality
        return Objects.equals(this.contents, other.contents);
    }

    @Override
    public Codec<ReagentComponent> getCodec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, ReagentComponent> getStreamCodec() {
        return STREAM_CODEC;
    }
}
