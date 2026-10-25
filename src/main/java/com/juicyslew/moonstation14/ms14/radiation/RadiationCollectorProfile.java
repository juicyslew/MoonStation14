package com.juicyslew.moonstation14.ms14.radiation;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** Resolved collector reaction and inherited operational defaults (SS14 collector.yml). */
public record RadiationCollectorProfile(ResourceLocation id, double chargeModifier, GasType reactant,
                                        double efficiency, double breakdownRate, boolean enabled,
                                          int powerHoldoverTicks, String startingTank, int useDelayTicks,
                                          ResourceLocation slotInsertSound, ResourceLocation slotEjectSound) {
    public static final Codec<RadiationCollectorProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(RadiationCollectorProfile::id),
            Codec.doubleRange(0, 1_000_000).fieldOf("charge_modifier").forGetter(RadiationCollectorProfile::chargeModifier),
            Codec.STRING.xmap(GasType::fromId, GasType::id).fieldOf("reactant").forGetter(RadiationCollectorProfile::reactant),
            Codec.doubleRange(0, 1000).fieldOf("efficiency").forGetter(RadiationCollectorProfile::efficiency),
            Codec.doubleRange(0, 1000).fieldOf("breakdown_rate").forGetter(RadiationCollectorProfile::breakdownRate),
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(RadiationCollectorProfile::enabled),
            Codec.intRange(0, 1200).fieldOf("power_holdover_ticks").forGetter(RadiationCollectorProfile::powerHoldoverTicks),
            Codec.STRING.optionalFieldOf("starting_tank", "").forGetter(RadiationCollectorProfile::startingTank),
            Codec.intRange(0, 1200).optionalFieldOf("use_delay_ticks", 20).forGetter(RadiationCollectorProfile::useDelayTicks),
            ResourceLocation.CODEC.optionalFieldOf("slotInsertSound", com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.insert()).forGetter(RadiationCollectorProfile::slotInsertSound),
            ResourceLocation.CODEC.optionalFieldOf("slotEjectSound", com.juicyslew.moonstation14.sounds.ItemSlotSoundDefaults.eject()).forGetter(RadiationCollectorProfile::slotEjectSound)
    ).apply(i, RadiationCollectorProfile::new));
    public static final PrototypeType<RadiationCollectorProfile> TYPE = new PrototypeType<>(
            ResourceLocation.fromNamespaceAndPath("moonstation14", "radiation_collector"),
            "moonstation14/radiation_collector", CODEC);
    public RadiationCollectorProfile {
        if (id == null || reactant == null || startingTank == null || slotInsertSound == null || slotEjectSound == null) throw new IllegalArgumentException("Invalid collector profile");
    }
}

