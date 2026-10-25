package com.juicyslew.moonstation14.ms14.botany;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Supported botany-machine settings only; upstream component presence remains explicit. */
public record MachineProfile(double powerWatts, double activePowerWatts, int chamberCapacity,
                             int startDelayTicks, boolean powered, boolean extractor,
                              int minimumPacketYield, int maximumPacketYield, SoundCue processingSound,
                               SoundCue beakerInsertSound, SoundCue beakerEjectSound, SoundCue chamberEjectSound,
                               SoundCue juiceSound) {
    public record SoundCue(net.minecraft.resources.ResourceLocation sound, float volume, float pitch) {
        public static final Codec<SoundCue> CODEC = RecordCodecBuilder.create(i -> i.group(
                net.minecraft.resources.ResourceLocation.CODEC.fieldOf("sound").forGetter(SoundCue::sound),
                Codec.floatRange(0, 4).optionalFieldOf("volume", 1f).forGetter(SoundCue::volume),
                Codec.floatRange(0.01f, 4).optionalFieldOf("pitch", 1f).forGetter(SoundCue::pitch)
        ).apply(i, SoundCue::new));
    }
    public MachineProfile(double powerWatts, double activePowerWatts, int chamberCapacity, int startDelayTicks,
                          boolean powered, boolean extractor, int minimumPacketYield, int maximumPacketYield) {
        this(powerWatts, activePowerWatts, chamberCapacity, startDelayTicks, powered, extractor, minimumPacketYield, maximumPacketYield, null, null, null, null, null);
    }
    public MachineProfile(double powerWatts, double activePowerWatts, int chamberCapacity, int startDelayTicks,
                          boolean powered, boolean extractor, int minimumPacketYield, int maximumPacketYield,
                          SoundCue processingSound, SoundCue beakerInsertSound, SoundCue beakerEjectSound, SoundCue chamberEjectSound) {
        this(powerWatts, activePowerWatts, chamberCapacity, startDelayTicks, powered, extractor, minimumPacketYield, maximumPacketYield,
                processingSound, beakerInsertSound, beakerEjectSound, chamberEjectSound, null);
    }
    public static final Codec<MachineProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("power_watts", 0d).forGetter(MachineProfile::powerWatts),
            Codec.DOUBLE.optionalFieldOf("active_power_watts", 0d).forGetter(MachineProfile::activePowerWatts),
            Codec.intRange(0, 64).optionalFieldOf("chamber_capacity", 0).forGetter(MachineProfile::chamberCapacity),
            Codec.intRange(0, 12000).optionalFieldOf("start_delay_ticks", 0).forGetter(MachineProfile::startDelayTicks),
            Codec.BOOL.optionalFieldOf("powered", false).forGetter(MachineProfile::powered),
            Codec.BOOL.optionalFieldOf("extractor", false).forGetter(MachineProfile::extractor),
            Codec.intRange(0, 64).optionalFieldOf("minimum_packet_yield", 0).forGetter(MachineProfile::minimumPacketYield),
            Codec.intRange(0, 64).optionalFieldOf("maximum_packet_yield", 0).forGetter(MachineProfile::maximumPacketYield),
            SoundCue.CODEC.optionalFieldOf("processing_sound").forGetter(p -> java.util.Optional.ofNullable(p.processingSound())),
             SoundCue.CODEC.optionalFieldOf("beaker_insert_sound").forGetter(p -> java.util.Optional.ofNullable(p.beakerInsertSound())),
             SoundCue.CODEC.optionalFieldOf("beaker_eject_sound").forGetter(p -> java.util.Optional.ofNullable(p.beakerEjectSound())),
              SoundCue.CODEC.optionalFieldOf("chamber_eject_sound").forGetter(p -> java.util.Optional.ofNullable(p.chamberEjectSound())),
              SoundCue.CODEC.optionalFieldOf("juice_sound").forGetter(p -> java.util.Optional.ofNullable(p.juiceSound()))
      ).apply(i, (power, active, capacity, delay, powered, extractor, min, max, processing, insert, beakerEject, chamberEject, juice) ->
              new MachineProfile(power, active, capacity, delay, powered, extractor, min, max, processing.orElse(null), insert.orElse(null), beakerEject.orElse(null), chamberEject.orElse(null), juice.orElse(null))));
    public MachineProfile {
        if (!Double.isFinite(powerWatts) || !Double.isFinite(activePowerWatts) || powerWatts < 0 || activePowerWatts < 0
                || chamberCapacity < 0 || startDelayTicks < 0 || minimumPacketYield < 0 || maximumPacketYield < minimumPacketYield
                || (extractor && (chamberCapacity != 0 || minimumPacketYield < 1 || maximumPacketYield < minimumPacketYield)))
            throw new IllegalArgumentException("invalid botany machine profile");
    }
}
