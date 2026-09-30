package com.juicyslew.moonstation14.ms14.chat.radio;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Radio channel definitions, not transmission or reception permissions. */
public final class ModRadioChannels {
    public static final ResourceLocation COMMON_ID = ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "common");

    public static final PrototypeType<RadioChannelData> RADIO_CHANNEL_TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, "radio_channel"),
            "moonstation14/radio_channel", RadioChannelData.CODEC,
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeJsonCatalogValidator) (owned, resolved) -> {
                // The path is the only ID; inheritance and abstract templates would silently alter the catalog.
                for (ResourceLocation id : owned.keys()) {
                    var json = owned.get(id);
                    for (String field : json.keySet()) {
                        if (!java.util.Set.of("label_translation_key", "keycode", "color").contains(field)) {
                            throw new IllegalArgumentException("radio channel '" + id + "' has unknown field '" + field + "'");
                        }
                    }
                }
            },
            (com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalogValidator<RadioChannelData>) catalog -> {
                Map<Character, ResourceLocation> keys = new HashMap<>();
                for (var entry : catalog.asMap().entrySet()) {
                    ResourceLocation previous = keys.putIfAbsent(entry.getValue().keycode(), entry.getKey());
                    if (previous != null) {
                        throw new IllegalArgumentException("duplicate radio keycode '" + entry.getValue().keycode()
                                + "' for '" + previous + "' and '" + entry.getKey() + "'");
                    }
                }
            });

    private ModRadioChannels() {
    }

    public static PrototypeCatalog<RadioChannelData> catalog(Level level) {
        Objects.requireNonNull(level, "level");
        return level.isClientSide() ? PrototypeRuntime.clientRadioChannels() : PrototypeRuntime.serverRadioChannels();
    }
}
