package com.juicyslew.moonstation14.ms14.explosion.grenade.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;

/** Strict persistent snapshot of a grenade fuse. Deadlines use the persisted world game-time clock.
 * Integration must clear armed state to SAFE if that clock is reset or an item migrates to another clock domain;
 * a deadline alone cannot safely identify either event.
 */
public record GrenadeFuseData(GrenadeFuse.Phase phase, long deadline, boolean beepedAtPrime, boolean beepedAtForty) {
    public static final GrenadeFuseData SAFE = new GrenadeFuseData(GrenadeFuse.Phase.SAFE, -1, false, false);

    public GrenadeFuseData {
        Objects.requireNonNull(phase, "phase");
        // Delegate invariants (including nonnegative, overflow-safe deadline bounds) to the runtime state.
        GrenadeFuse.restore(phase, deadline, beepedAtPrime, beepedAtForty);
    }

    private static final Codec<GrenadeFuse.Phase> PHASE_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(GrenadeFuse.Phase.valueOf(value)); }
        catch (IllegalArgumentException ex) { return DataResult.error(() -> "unknown grenade fuse phase: " + value); }
    }, Enum::name);

    private record Encoded(GrenadeFuse.Phase phase, long deadline, boolean prime, boolean forty) { }
    private static final Codec<Encoded> ENCODED_CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                    PHASE_CODEC.fieldOf("phase").forGetter(Encoded::phase),
                    Codec.LONG.fieldOf("deadline").forGetter(Encoded::deadline),
                    Codec.BOOL.fieldOf("beeped_at_prime").forGetter(Encoded::prime),
                    Codec.BOOL.fieldOf("beeped_at_forty").forGetter(Encoded::forty)
            ).apply(instance, Encoded::new));

    /** Invalid states are codec errors; an absent data component is handled by ItemStack as SAFE. */
    public static final Codec<GrenadeFuseData> CODEC = ENCODED_CODEC.comapFlatMap(encoded -> {
        try { return DataResult.success(new GrenadeFuseData(encoded.phase, encoded.deadline, encoded.prime, encoded.forty)); }
        catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    }, data -> new Encoded(data.phase, data.deadline, data.beepedAtPrime, data.beepedAtForty));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrenadeFuseData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, data -> data.phase.ordinal(),
            ByteBufCodecs.VAR_LONG, GrenadeFuseData::deadline,
            ByteBufCodecs.BOOL, GrenadeFuseData::beepedAtPrime,
            ByteBufCodecs.BOOL, GrenadeFuseData::beepedAtForty,
            (phase, deadline, prime, forty) -> {
                if (phase < 0 || phase >= GrenadeFuse.Phase.values().length)
                    throw new IllegalArgumentException("invalid grenade fuse phase");
                return new GrenadeFuseData(GrenadeFuse.Phase.values()[phase], deadline, prime, forty);
            });

    public static GrenadeFuseData fromFuse(GrenadeFuse fuse) {
        Objects.requireNonNull(fuse, "fuse");
        return new GrenadeFuseData(fuse.phase(), fuse.phase() == GrenadeFuse.Phase.SAFE ? -1 : fuse.deadline(),
                fuse.beepedAtPrime(), fuse.beepedAtForty());
    }

    public GrenadeFuse toFuse() { return GrenadeFuse.restore(phase, deadline, beepedAtPrime, beepedAtForty); }
}

