package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import java.util.*;

/** Authoritative persisted lung inventory. Gas keys are stable atmosphere ids, not enum ordinals. */
public record LungComponent(Map<String, Double> gasMoles, double temperatureKelvin,
                             double saturation, boolean initialized, Phase phase)
        implements IMS14Component<LungComponent, LungAttachment> {
    public enum Phase { INHALING, EXHALING }
    /** Legacy saves had no phase: begin their next valid due update with an inhale. */
    public LungComponent(Map<String, Double> gasMoles, double temperatureKelvin,
                         double saturation, boolean initialized) {
        this(gasMoles, temperatureKelvin, saturation, initialized, Phase.INHALING);
    }
    private static final Codec<Double> FINITE_NONNEGATIVE = Codec.DOUBLE.comapFlatMap(v ->
             Double.isFinite(v) && v >= 0 ? DataResult.success(v) : DataResult.error(() -> "must be finite and nonnegative"), v -> v);
    private static final Codec<Double> FINITE_SATURATION = Codec.DOUBLE.comapFlatMap(v ->
            Double.isFinite(v) && v >= -2 ? DataResult.success(v) : DataResult.error(() -> "saturation below -2 or non-finite"), v -> v);
    private static final Codec<LungComponent> RECORD = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, FINITE_NONNEGATIVE).fieldOf("gas_moles").forGetter(LungComponent::gasMoles),
            FINITE_NONNEGATIVE.fieldOf("temperature_kelvin").forGetter(LungComponent::temperatureKelvin),
             FINITE_SATURATION.fieldOf("saturation").forGetter(LungComponent::saturation),
            Codec.BOOL.fieldOf("initialized").forGetter(LungComponent::initialized),
            Codec.STRING.comapFlatMap(value -> { try { return DataResult.success(Phase.valueOf(value)); }
                catch (IllegalArgumentException invalid) { return DataResult.error(() -> "unknown lung phase: " + value); } }, Phase::name)
                    .optionalFieldOf("phase", Phase.INHALING).forGetter(LungComponent::phase)
    ).apply(i, LungComponent::new));
    public static final Codec<LungComponent> CODEC = Codec.of(RECORD, new com.mojang.serialization.Decoder<>() {
        @Override public <T> DataResult<com.mojang.datafixers.util.Pair<LungComponent,T>> decode(com.mojang.serialization.DynamicOps<T> ops,T input) {
            return RECORD.decode(ops,input).flatMap(p -> { try { p.getFirst().mixture(); return DataResult.success(p); }
                catch (RuntimeException ex) { return DataResult.error(() -> ex.getMessage()); } });
        }
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, LungComponent> STREAM_CODEC = StreamCodec.of(
             (b,s) -> { b.writeVarInt(s.gasMoles.size()); s.gasMoles.forEach((id,n) -> { b.writeUtf(id); b.writeDouble(n); }); b.writeDouble(s.temperatureKelvin); b.writeDouble(s.saturation); b.writeBoolean(s.initialized); b.writeEnum(s.phase); },
             b -> { int size=b.readVarInt(); if(size<0 || size>GasType.values().length) throw new IllegalArgumentException("invalid lung gas count"); Map<String,Double> gases=new LinkedHashMap<>(); for(int n=0;n<size;n++) if(gases.put(b.readUtf(),b.readDouble())!=null) throw new IllegalArgumentException("duplicate lung gas"); return new LungComponent(gases,b.readDouble(),b.readDouble(),b.readBoolean(),b.readEnum(Phase.class)); });
    public LungComponent {
         Objects.requireNonNull(gasMoles); Objects.requireNonNull(phase); if (!Double.isFinite(temperatureKelvin) || temperatureKelvin < 0 || !Double.isFinite(saturation) || saturation < -2) throw new IllegalArgumentException("invalid lung state");
        TreeMap<String,Double> copy=new TreeMap<>(); gasMoles.forEach((id,moles)-> { GasType.fromId(Objects.requireNonNull(id)); if(moles==null || !Double.isFinite(moles) || moles<=0) throw new IllegalArgumentException("invalid lung gas quantity"); copy.put(id,moles); });
        if(copy.size()>GasType.values().length) throw new IllegalArgumentException("too many lung gases"); gasMoles=Collections.unmodifiableMap(copy);
    }
    public GasMixture mixture() { EnumMap<GasType,Double> map=new EnumMap<>(GasType.class); gasMoles.forEach((id,n)->map.put(GasType.fromId(id),n)); return new GasMixture(map,temperatureKelvin); }
    public static LungComponent from(GasMixture mixture,double saturation,boolean initialized) { Map<String,Double> m=new TreeMap<>(); mixture.gasMoles().forEach((g,n)->m.put(g.id(),n)); return new LungComponent(m,mixture.temperatureKelvin(),saturation,initialized); }
    public static LungComponent from(GasMixture mixture,double saturation,boolean initialized,Phase phase) { Map<String,Double> m=new TreeMap<>(); mixture.gasMoles().forEach((g,n)->m.put(g.id(),n)); return new LungComponent(m,mixture.temperatureKelvin(),saturation,initialized,phase); }
    @Override public LungAttachment toAttachment(){ return new LungAttachment(this); }
    @Override public Codec<LungComponent> getCodec(){ return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf,LungComponent> getStreamCodec(){ return STREAM_CODEC; }
}
