package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.*;

/** Transient reducer state; persisted inventory and respiration live in BODY. */
public record LungComponent(Map<String, Double> gasMoles, double temperatureKelvin,
                             double saturation, boolean initialized, Phase phase) {
    public enum Phase { INHALING, EXHALING }
    public LungComponent {
         Objects.requireNonNull(gasMoles); Objects.requireNonNull(phase); if (!Double.isFinite(temperatureKelvin) || temperatureKelvin < 0 || !Double.isFinite(saturation) || saturation < -2) throw new IllegalArgumentException("invalid lung state");
        TreeMap<String,Double> copy=new TreeMap<>(); gasMoles.forEach((id,moles)-> { GasType.fromId(Objects.requireNonNull(id)); if(moles==null || !Double.isFinite(moles) || moles<=0) throw new IllegalArgumentException("invalid lung gas quantity"); copy.put(id,moles); });
        if(copy.size()>GasType.values().length) throw new IllegalArgumentException("too many lung gases"); gasMoles=Collections.unmodifiableMap(copy);
    }
    public GasMixture mixture() { EnumMap<GasType,Double> map=new EnumMap<>(GasType.class); gasMoles.forEach((id,n)->map.put(GasType.fromId(id),n)); return new GasMixture(map,temperatureKelvin); }
    public static LungComponent from(GasMixture mixture,double saturation,boolean initialized) { return from(mixture, saturation, initialized, Phase.INHALING); }
    public static LungComponent from(GasMixture mixture,double saturation,boolean initialized,Phase phase) { Map<String,Double> m=new TreeMap<>(); mixture.gasMoles().forEach((g,n)->m.put(g.id(),n)); return new LungComponent(m,mixture.temperatureKelvin(),saturation,initialized,phase); }
}
