package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Resolved local domain projection of supported gas-canister physical defaults. */
public record GasCanisterProfile(double volumeCubicMeters, double temperatureKelvin,
                                 Map<GasType, Double> initialMoles, double releasePressureKpa,
                                 double minReleasePressureKpa, double maxReleasePressureKpa,
                                 String modelSkinPrefix, boolean defaultLockedVisual,
                                 ResourceLocation slotInsertSound, ResourceLocation slotEjectSound,
                                 ResourceLocation valveSound, float valveVolumeDb) {
    private static final Codec<Map<String, Double>> MOLES = GasCompositionCodec.CODEC;
    public static final Codec<GasCanisterProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.fieldOf("volumeCubicMeters").forGetter(GasCanisterProfile::volumeCubicMeters),
            Codec.DOUBLE.fieldOf("temperatureKelvin").forGetter(GasCanisterProfile::temperatureKelvin),
            MOLES.optionalFieldOf("initialMoles", Map.of()).forGetter(GasCanisterProfile::encodedMoles),
            Codec.DOUBLE.optionalFieldOf("releasePressureKpa", 101.325).forGetter(GasCanisterProfile::releasePressureKpa),
            Codec.DOUBLE.optionalFieldOf("minReleasePressureKpa",10.1325).forGetter(GasCanisterProfile::minReleasePressureKpa),
            Codec.DOUBLE.optionalFieldOf("maxReleasePressureKpa",1013.25).forGetter(GasCanisterProfile::maxReleasePressureKpa),
            Codec.STRING.optionalFieldOf("modelSkinPrefix", "grey").forGetter(GasCanisterProfile::modelSkinPrefix),
            Codec.BOOL.optionalFieldOf("defaultLockedVisual", false).forGetter(GasCanisterProfile::defaultLockedVisual),
            ResourceLocation.CODEC.optionalFieldOf("slotInsertSound", com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.insert()).forGetter(GasCanisterProfile::slotInsertSound),
            ResourceLocation.CODEC.optionalFieldOf("slotEjectSound", com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.eject()).forGetter(GasCanisterProfile::slotEjectSound),
            ResourceLocation.CODEC.optionalFieldOf("valveSound", ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_valve_toggle")).forGetter(GasCanisterProfile::valveSound),
            Codec.floatRange(-80, 24).optionalFieldOf("valveVolumeDb", -5f).forGetter(GasCanisterProfile::valveVolumeDb)
    ).apply(i, (v,t,m,p,lo,hi,skin,locked,insert,eject,valve,volume) -> new GasCanisterProfile(v,t,decodeMoles(m),p,lo,hi,skin,locked,insert,eject,valve,volume)));
    public GasCanisterProfile(double volumeCubicMeters,double temperatureKelvin,Map<GasType,Double> initialMoles) {
        this(volumeCubicMeters,temperatureKelvin,initialMoles,101.325,10.1325,1013.25,"grey",false, defaultInsert(), defaultEject(), defaultValve(), -5f);
    }
    public GasCanisterProfile(double volumeCubicMeters,double temperatureKelvin,Map<GasType,Double> initialMoles,
                              double releasePressureKpa,double minReleasePressureKpa,double maxReleasePressureKpa) {
        this(volumeCubicMeters,temperatureKelvin,initialMoles,releasePressureKpa,minReleasePressureKpa,maxReleasePressureKpa,"grey",false, defaultInsert(), defaultEject(), defaultValve(), -5f);
    }
    public GasCanisterProfile(double volumeCubicMeters,double temperatureKelvin,Map<GasType,Double> initialMoles,double releasePressureKpa,double minReleasePressureKpa,double maxReleasePressureKpa,String skin,boolean locked) {
        this(volumeCubicMeters,temperatureKelvin,initialMoles,releasePressureKpa,minReleasePressureKpa,maxReleasePressureKpa,skin,locked,defaultInsert(),defaultEject(),defaultValve(),-5f);
    }
    public GasCanisterProfile {
        if (!Double.isFinite(volumeCubicMeters) || volumeCubicMeters <= 0 || !Double.isFinite(temperatureKelvin)
                || temperatureKelvin < GasMixture.MIN_TEMPERATURE_KELVIN || temperatureKelvin > GasMixture.MAX_TEMPERATURE_KELVIN)
            throw new IllegalArgumentException("Invalid gas canister physical profile");
        initialMoles = Map.copyOf(initialMoles);
        if (slotInsertSound == null || slotEjectSound == null || valveSound == null || !Float.isFinite(valveVolumeDb)) throw new IllegalArgumentException("Invalid canister audio profile");
        if (modelSkinPrefix == null || !modelSkinPrefix.matches("[a-z0-9_/-]+")) throw new IllegalArgumentException("Invalid canister model skin prefix");
        new GasMixture(initialMoles, temperatureKelvin, volumeCubicMeters);
        if (!Double.isFinite(releasePressureKpa) || !Double.isFinite(minReleasePressureKpa) || !Double.isFinite(maxReleasePressureKpa)
                || minReleasePressureKpa < 0 || minReleasePressureKpa > releasePressureKpa || releasePressureKpa > maxReleasePressureKpa)
            throw new IllegalArgumentException("Invalid canister release pressure settings");
    }
    public GasMixture initialMixture() { return new GasMixture(initialMoles, temperatureKelvin, volumeCubicMeters); }
    private Map<String, Double> encodedMoles() { return GasCompositionCodec.encode(initialMoles); }
    private static Map<GasType, Double> decodeMoles(Map<String, Double> values) { return GasCompositionCodec.decode(values); }
    private static ResourceLocation defaultInsert() { return com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.insert(); }
    private static ResourceLocation defaultEject() { return com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.eject(); }
    private static ResourceLocation defaultValve() { return ResourceLocation.fromNamespaceAndPath("moonstation14", "gas_valve_toggle"); }
}

