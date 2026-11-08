package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Per-stack firing cadence and spread memory. Missing component means the initial state. */
public record GunRuntimeState(long nextFireTick, long lastFireTick, float currentSpreadDegrees, long nextUseTick,
                              String selectedFireMode, int batteryModeIndex) {
    public GunRuntimeState(long nextFireTick, long lastFireTick, float currentSpreadDegrees, long nextUseTick) {
        this(nextFireTick, lastFireTick, currentSpreadDegrees, nextUseTick, null, 0);
    }
    public GunRuntimeState(long nextFireTick, long lastFireTick, float currentSpreadDegrees) {
        this(nextFireTick, lastFireTick, currentSpreadDegrees, 0, null, 0);
    }
    public static final GunRuntimeState INITIAL = new GunRuntimeState(0, 0, 0f, 0);
    public static final Codec<GunRuntimeState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("next_fire_tick").forGetter(GunRuntimeState::nextFireTick),
            Codec.LONG.fieldOf("last_fire_tick").forGetter(GunRuntimeState::lastFireTick),
            Codec.FLOAT.fieldOf("current_spread_degrees").forGetter(GunRuntimeState::currentSpreadDegrees),
            Codec.LONG.fieldOf("next_use_tick").forGetter(GunRuntimeState::nextUseTick),
            Codec.STRING.optionalFieldOf("selected_fire_mode", "").forGetter(s -> s.selectedFireMode == null ? "" : s.selectedFireMode),
            Codec.INT.optionalFieldOf("battery_mode_index", 0).forGetter(GunRuntimeState::batteryModeIndex)
    ).apply(i, (fire,last,spread,use,mode,battery) -> new GunRuntimeState(fire,last,spread,use,mode.isEmpty()?null:mode,battery)));
    public static final StreamCodec<RegistryFriendlyByteBuf, GunRuntimeState> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> { ByteBufCodecs.VAR_LONG.encode(buf, state.nextFireTick); ByteBufCodecs.VAR_LONG.encode(buf, state.lastFireTick); ByteBufCodecs.FLOAT.encode(buf, state.currentSpreadDegrees); ByteBufCodecs.VAR_LONG.encode(buf, state.nextUseTick); ByteBufCodecs.STRING_UTF8.encode(buf, state.selectedFireMode == null ? "" : state.selectedFireMode); ByteBufCodecs.VAR_INT.encode(buf,state.batteryModeIndex); },
            buf -> new GunRuntimeState(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), emptyNull(ByteBufCodecs.STRING_UTF8.decode(buf)), ByteBufCodecs.VAR_INT.decode(buf)));
    public GunRuntimeState withFire(long nextFire, long lastFire, float spread) {
        return new GunRuntimeState(nextFire, lastFire, spread, nextUseTick, selectedFireMode, batteryModeIndex);
    }
    public GunRuntimeState withUseDeadline(long deadline) {
        return new GunRuntimeState(nextFireTick, lastFireTick, currentSpreadDegrees, deadline, selectedFireMode, batteryModeIndex);
    }
    public GunRuntimeState withSelectedFireMode(String mode) { return new GunRuntimeState(nextFireTick,lastFireTick,currentSpreadDegrees,nextUseTick,mode,batteryModeIndex); }
    public GunRuntimeState withBatteryModeIndex(int index) { return new GunRuntimeState(nextFireTick,lastFireTick,currentSpreadDegrees,nextUseTick,selectedFireMode,index); }
    private static String emptyNull(String value) { return value.isEmpty() ? null : value; }
    public GunRuntimeState {
        if (!Float.isFinite(currentSpreadDegrees) || currentSpreadDegrees < 0f)
            throw new IllegalArgumentException("spread must be finite and nonnegative");
        if (batteryModeIndex < 0) throw new IllegalArgumentException("battery mode index must be nonnegative");
    }
}
