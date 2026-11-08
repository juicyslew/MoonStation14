package com.juicyslew.moonstation14.ms14.tesla;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Resolved behavior composition of upstream BaseEnergyBall and its Tesla variants. Nullable
 * optionals intentionally distinguish the mini's absent energy/horizon/jump behaviors. */
public record TeslaBallProfile(Shooter shooter, ChasingWalk chasingWalk, Jump jump,
        Double colliderRadius, Double contactRadius,
        /** Minecraft adaptation: block reach may exceed hard collision without padding living/PA contact. */
        Double blockConsumerRadius, Double horizonRadius,
        Energy energy, Double timedDespawnSeconds, Contact contact, Contact lightning, Ambient ambient, Collision collision,
        Double visualRadius, Orbit orbit) {
    public enum Collision { NONE, WORLD_AND_FIELDS }
    public record Ambient(net.minecraft.resources.ResourceLocation event, double volumeDb, double range) {
        public static final Codec<Ambient> CODEC=RecordCodecBuilder.create(i->i.group(
                net.minecraft.resources.ResourceLocation.CODEC.fieldOf("event").forGetter(Ambient::event),
                Codec.DOUBLE.fieldOf("volumeDb").forGetter(Ambient::volumeDb), Codec.DOUBLE.fieldOf("range").forGetter(Ambient::range)
        ).apply(i,Ambient::new));
    }
    public record Shooter(double range, int depth, int maxBolts,
            double minimumIntervalSeconds, double maximumIntervalSeconds) {
        public static final Codec<Shooter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("range").forGetter(Shooter::range),
                Codec.INT.fieldOf("depth").forGetter(Shooter::depth), Codec.INT.fieldOf("maxBolts").forGetter(Shooter::maxBolts),
                Codec.DOUBLE.fieldOf("minimumIntervalSeconds").forGetter(Shooter::minimumIntervalSeconds),
                Codec.DOUBLE.fieldOf("maximumIntervalSeconds").forGetter(Shooter::maximumIntervalSeconds)
        ).apply(i, Shooter::new));
    }
    public record ChasingWalk(double minimumSpeed, double maximumSpeed, double updateSeconds,
            double minimumRetargetSeconds, double maximumRetargetSeconds, double range, double targetlessVerticalHalfLifeSeconds) {
        public static final Codec<ChasingWalk> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("minimumSpeed").forGetter(ChasingWalk::minimumSpeed), Codec.DOUBLE.fieldOf("maximumSpeed").forGetter(ChasingWalk::maximumSpeed),
                Codec.DOUBLE.fieldOf("updateSeconds").forGetter(ChasingWalk::updateSeconds), Codec.DOUBLE.fieldOf("minimumRetargetSeconds").forGetter(ChasingWalk::minimumRetargetSeconds),
                Codec.DOUBLE.fieldOf("maximumRetargetSeconds").forGetter(ChasingWalk::maximumRetargetSeconds), Codec.DOUBLE.fieldOf("range").forGetter(ChasingWalk::range),
                Codec.DOUBLE.optionalFieldOf("targetlessVerticalHalfLifeSeconds",0d).forGetter(ChasingWalk::targetlessVerticalHalfLifeSeconds)
        ).apply(i, (min,max,update,minRet,maxRet,range,halfLife)->new ChasingWalk(min,max,update,minRet,maxRet,range,halfLife)));
    }
    public record Jump(double minimumIntervalSeconds, double maximumIntervalSeconds, double minimumDistance, double maximumDistance) {
        public static final Codec<Jump> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("minimumIntervalSeconds").forGetter(Jump::minimumIntervalSeconds), Codec.DOUBLE.fieldOf("maximumIntervalSeconds").forGetter(Jump::maximumIntervalSeconds),
                Codec.DOUBLE.fieldOf("minimumDistance").forGetter(Jump::minimumDistance), Codec.DOUBLE.fieldOf("maximumDistance").forGetter(Jump::maximumDistance)
        ).apply(i, Jump::new));
    }
    public record Energy(double initial, double gainPerConsumedEntity, double growthThreshold, double collapseThreshold) {
        public static final Codec<Energy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("initial").forGetter(Energy::initial), Codec.DOUBLE.fieldOf("gainPerConsumedEntity").forGetter(Energy::gainPerConsumedEntity),
                Codec.DOUBLE.fieldOf("growthThreshold").forGetter(Energy::growthThreshold), Codec.DOUBLE.fieldOf("collapseThreshold").forGetter(Energy::collapseThreshold)
        ).apply(i, Energy::new));
    }
    public record Contact(double shockDamage, double siemens, double stunSeconds, double sourceLifetimeSeconds, double beamHalfWidth) {
        public static final Codec<Contact> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("shockDamage").forGetter(Contact::shockDamage), Codec.DOUBLE.fieldOf("siemens").forGetter(Contact::siemens),
                Codec.DOUBLE.fieldOf("stunSeconds").forGetter(Contact::stunSeconds), Codec.DOUBLE.optionalFieldOf("sourceLifetimeSeconds",3d).forGetter(Contact::sourceLifetimeSeconds),
                Codec.DOUBLE.optionalFieldOf("beamHalfWidth",0d).forGetter(Contact::beamHalfWidth)
        ).apply(i, Contact::new));
    }
    /** Optional Minecraft-only mini-ball orbit tuning; absence retains legacy chase movement. */
    public record Orbit(double minimumRadius, double maximumRadius, double radialPeriodSeconds,
            double angularPeriodSeconds, double acceleration,
            double responseRate, double groundProbeDistance) {
        public static final Codec<Orbit> CODEC=RecordCodecBuilder.create(i->i.group(
                Codec.DOUBLE.fieldOf("minimumRadius").forGetter(Orbit::minimumRadius), Codec.DOUBLE.fieldOf("maximumRadius").forGetter(Orbit::maximumRadius),
                Codec.DOUBLE.fieldOf("radialPeriodSeconds").forGetter(Orbit::radialPeriodSeconds), Codec.DOUBLE.fieldOf("angularPeriodSeconds").forGetter(Orbit::angularPeriodSeconds),
                Codec.DOUBLE.fieldOf("acceleration").forGetter(Orbit::acceleration),
                Codec.DOUBLE.fieldOf("responseRate").forGetter(Orbit::responseRate), Codec.DOUBLE.fieldOf("groundProbeDistance").forGetter(Orbit::groundProbeDistance)
         ).apply(i,Orbit::new));
    }
    public static final Codec<TeslaBallProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Shooter.CODEC.fieldOf("shooter").forGetter(TeslaBallProfile::shooter),
            ChasingWalk.CODEC.optionalFieldOf("chasingWalk").forGetter(p -> java.util.Optional.ofNullable(p.chasingWalk)),
            Jump.CODEC.optionalFieldOf("jump").forGetter(p -> java.util.Optional.ofNullable(p.jump)),
            Codec.DOUBLE.optionalFieldOf("colliderRadius").forGetter(p -> java.util.Optional.ofNullable(p.colliderRadius)),
             Codec.DOUBLE.optionalFieldOf("contactRadius").forGetter(p -> java.util.Optional.ofNullable(p.contactRadius)),
             Codec.DOUBLE.optionalFieldOf("blockConsumerRadius").forGetter(p -> java.util.Optional.ofNullable(p.blockConsumerRadius)),
            Codec.DOUBLE.optionalFieldOf("horizonRadius").forGetter(p -> java.util.Optional.ofNullable(p.horizonRadius)),
            Energy.CODEC.optionalFieldOf("energy").forGetter(p -> java.util.Optional.ofNullable(p.energy)),
            Codec.DOUBLE.optionalFieldOf("timedDespawnSeconds").forGetter(p -> java.util.Optional.ofNullable(p.timedDespawnSeconds)),
            Contact.CODEC.optionalFieldOf("contact").forGetter(p -> java.util.Optional.ofNullable(p.contact)),
             Contact.CODEC.optionalFieldOf("lightning").forGetter(p -> java.util.Optional.ofNullable(p.lightning)),
             Ambient.CODEC.optionalFieldOf("ambient").forGetter(p -> java.util.Optional.ofNullable(p.ambient)),
              Codec.STRING.optionalFieldOf("collision", "NONE").xmap(Collision::valueOf, Collision::name).forGetter(TeslaBallProfile::collision),
              Codec.DOUBLE.optionalFieldOf("visualRadius").forGetter(p->java.util.Optional.ofNullable(p.visualRadius)),
              Orbit.CODEC.optionalFieldOf("orbit").forGetter(p->java.util.Optional.ofNullable(p.orbit))
        ).apply(i, (s,w,j,c,t,block,h,e,d,k,l,a,collision,visual,orbit) -> new TeslaBallProfile(s,w.orElse(null),j.orElse(null),c.orElse(null),t.orElse(null),block.orElse(null),h.orElse(null),e.orElse(null),d.orElse(null),k.orElse(null),l.orElse(null),a.orElse(null),collision,visual.orElse(null),orbit.orElse(null))));
    public void validate() {
        if (shooter == null || !finitePositive(shooter.range) || shooter.depth < 0 || shooter.maxBolts < 1 || !finitePositive(shooter.minimumIntervalSeconds) || !finitePositive(shooter.maximumIntervalSeconds) || shooter.maximumIntervalSeconds < shooter.minimumIntervalSeconds)
            throw new IllegalArgumentException("invalid Tesla shooter profile");
        if (energy != null && (!finiteNonnegative(energy.initial) || !finitePositive(energy.gainPerConsumedEntity) || !finitePositive(energy.growthThreshold) || !Double.isFinite(energy.collapseThreshold) || energy.collapseThreshold >= 0)) throw new IllegalArgumentException("invalid Tesla energy profile");
        if (timedDespawnSeconds != null && !finitePositive(timedDespawnSeconds)) throw new IllegalArgumentException("invalid Tesla lifetime");
        if (blockConsumerRadius != null && !finitePositive(blockConsumerRadius)) throw new IllegalArgumentException("invalid Tesla block consumer radius");
        if (contact != null && (!finitePositive(contact.shockDamage) || !finitePositive(contact.siemens) || !finitePositive(contact.stunSeconds) || !finitePositive(contact.sourceLifetimeSeconds) || !finiteNonnegative(contact.beamHalfWidth))) throw new IllegalArgumentException("invalid Tesla contact profile");
        if (lightning != null && (!finitePositive(lightning.shockDamage) || !finitePositive(lightning.siemens) || !finitePositive(lightning.stunSeconds) || !finitePositive(lightning.sourceLifetimeSeconds) || !finiteNonnegative(lightning.beamHalfWidth))) throw new IllegalArgumentException("invalid Tesla lightning profile");
        if (chasingWalk != null && (!finitePositive(chasingWalk.minimumSpeed) || chasingWalk.maximumSpeed < chasingWalk.minimumSpeed || !finitePositive(chasingWalk.updateSeconds) || chasingWalk.minimumRetargetSeconds <= 0 || chasingWalk.maximumRetargetSeconds < chasingWalk.minimumRetargetSeconds || chasingWalk.range <= 0 || !finiteNonnegative(chasingWalk.targetlessVerticalHalfLifeSeconds))) throw new IllegalArgumentException("invalid Tesla chasing profile");
        if (jump != null && (!finitePositive(jump.minimumIntervalSeconds) || jump.maximumIntervalSeconds < jump.minimumIntervalSeconds || jump.minimumDistance < 0 || jump.maximumDistance < jump.minimumDistance)) throw new IllegalArgumentException("invalid Tesla jump profile");
        if(visualRadius!=null&&!finitePositive(visualRadius))throw new IllegalArgumentException("invalid Tesla visual radius");
         if(orbit!=null&&(!finiteNonnegative(orbit.minimumRadius)||!finitePositive(orbit.maximumRadius)||orbit.maximumRadius<orbit.minimumRadius||!finitePositive(orbit.radialPeriodSeconds)||!finitePositive(orbit.angularPeriodSeconds)||!finitePositive(orbit.acceleration)||!finitePositive(orbit.responseRate)||!finitePositive(orbit.groundProbeDistance)))throw new IllegalArgumentException("invalid Tesla orbit profile");
    }
    /** Block-only consumer reach; absent values preserve legacy profiles' contact reach. */
    public double resolvedBlockConsumerRadius() { return blockConsumerRadius != null ? blockConsumerRadius : contactRadius != null ? contactRadius : .65; }
    private static boolean finitePositive(double v) { return Double.isFinite(v) && v > 0; }
    private static boolean finiteNonnegative(double v) { return Double.isFinite(v) && v >= 0; }
}
