package com.juicyslew.moonstation14.ms14.hands;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Bounded, non-owning cached view of the source body; deliberately excludes physiology and mind. */
public record PortableBodyAppearance(ResourceLocation profile, String name, Coat coat, State state, String visualKey) {
    public enum Coat { WHITE, BROWN, GRAY, NONE }
    public enum State { ALIVE, CRITICAL, DEAD }
    private static final Codec<String> NAME_CODEC = Codec.STRING.comapFlatMap(value ->
            value != null && !value.isBlank() && value.length() <= 128
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "invalid portable body name"), value -> value);
    public static final Codec<PortableBodyAppearance> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("profile").forGetter(PortableBodyAppearance::profile),
            NAME_CODEC.fieldOf("name").forGetter(PortableBodyAppearance::name),
            Codec.STRING.xmap(value -> Coat.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
                    value -> value.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("coat").forGetter(PortableBodyAppearance::coat),
            Codec.STRING.xmap(value -> State.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
                    value -> value.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("state").forGetter(PortableBodyAppearance::state),
            Codec.STRING.optionalFieldOf("visual_key", "").forGetter(PortableBodyAppearance::visualKey)
    ).apply(i, PortableBodyAppearance::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PortableBodyAppearance> STREAM_CODEC = StreamCodec.of(
            (b, v) -> { b.writeResourceLocation(v.profile()); b.writeUtf(v.name(), 128); b.writeVarInt(v.coat().ordinal()); b.writeVarInt(v.state().ordinal()); b.writeUtf(v.visualKey(), 64); },
            b -> new PortableBodyAppearance(b.readResourceLocation(), b.readUtf(128),
                    Coat.values()[checked(b.readVarInt(), Coat.values().length)], State.values()[checked(b.readVarInt(), State.values().length)], b.readUtf(64)));
    private static int checked(int value, int length) { if (value < 0 || value >= length) throw new IllegalArgumentException("invalid portable appearance ordinal"); return value; }
    public PortableBodyAppearance {
        java.util.Objects.requireNonNull(profile); java.util.Objects.requireNonNull(coat); java.util.Objects.requireNonNull(state);
        name = name == null ? "" : name;
        if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("invalid portable body name");
        visualKey = visualKey == null ? "" : visualKey;
        if (visualKey.length() > 64 || (!visualKey.isEmpty() && !visualKey.matches("[a-z0-9_]+"))) throw new IllegalArgumentException("invalid portable visual key");
    }
    public PortableBodyAppearance(ResourceLocation profile, String name, Coat coat, State state) { this(profile, name, coat, state, ""); }
}
