package com.juicyslew.moonstation14.ms14.construction.tool;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-stack welder state; timestamps use the server overworld game-time clock. */
public record WelderFuelData(double fuel, boolean lit, long lastBurnTick) {
    public WelderFuelData {
        if (!Double.isFinite(fuel) || fuel < 0 || lastBurnTick < -1)
            throw new IllegalArgumentException("invalid welder fuel state");
    }

    private record Encoded(double fuel, boolean lit, long tick) { }
    private static final Codec<Encoded> ENCODED = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("fuel").forGetter(Encoded::fuel),
            Codec.BOOL.fieldOf("lit").forGetter(Encoded::lit),
            Codec.LONG.fieldOf("lastBurnTick").forGetter(Encoded::tick)
    ).apply(i, Encoded::new));
    public static final Codec<WelderFuelData> CODEC = ENCODED.comapFlatMap(e -> {
        try { return com.mojang.serialization.DataResult.success(new WelderFuelData(e.fuel, e.lit, e.tick)); }
        catch (IllegalArgumentException ex) { return com.mojang.serialization.DataResult.error(ex::getMessage); }
    }, s -> new Encoded(s.fuel, s.lit, s.lastBurnTick));
    public static final StreamCodec<RegistryFriendlyByteBuf, WelderFuelData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, WelderFuelData::fuel, ByteBufCodecs.BOOL, WelderFuelData::lit,
            ByteBufCodecs.VAR_LONG, WelderFuelData::lastBurnTick, WelderFuelData::new);

    public static WelderFuelData initial(ToolPrototypeData.Welder prototype) {
        return new WelderFuelData(prototype.initialFuel(), false, -1);
    }

    /** Toggle on charges ignition exactly once; a rejected ignition returns empty. */
    public java.util.Optional<WelderFuelData> toggle(ToolPrototypeData.Welder prototype, long now) {
        if (lit) return java.util.Optional.of(new WelderFuelData(fuel, false, -1));
        if (now < 0 || fuel < prototype.ignitionCost()) return java.util.Optional.empty();
        return java.util.Optional.of(new WelderFuelData(Math.max(0, fuel - prototype.ignitionCost()), true, now));
    }

    /** Burn only elapsed time since the persisted checkpoint, so owner changes cannot double-charge. */
    public WelderFuelData tick(ToolPrototypeData.Welder prototype, long now) {
        if (!lit) return this;
        if (now < 0 || lastBurnTick < 0 || now <= lastBurnTick) return this;
        double elapsedSeconds = (now - lastBurnTick) / 20.0;
        double nextFuel = Math.max(0, fuel - elapsedSeconds * prototype.fuelPerSecond());
        return new WelderFuelData(nextFuel, nextFuel > 0, nextFuel > 0 ? now : -1);
    }
}

