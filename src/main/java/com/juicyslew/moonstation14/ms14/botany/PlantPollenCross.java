package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/** Pure, directional SS14-style genetics cross. The caller owns replacement of the seed reference. */
public final class PlantPollenCross {
    private PlantPollenCross() { }

    /** Random source yields a uniform [0,1) sample. Every source gene draw is retained, including unsupported genes. */
    public static GeneticValue cross(GeneticValue donor, GeneticValue recipient, DoubleSupplier random) {
        if (donor == null || recipient == null || random == null) throw new IllegalArgumentException("missing cross input");
        SeedProfile d = donor.profile(), r = recipient.profile();
        var rc = r.chemicals(); var dc = d.chemicals();
        var chemicals = new LinkedHashMap<ResourceLocation, SeedProfile.Chemical>(rc);
        var keys = new java.util.TreeSet<ResourceLocation>(Comparator.comparing(ResourceLocation::toString)); keys.addAll(dc.keySet());
        for (var id : keys) {
            var from = dc.get(id); var to = rc.get(id);
            if (from != null && to != null) chemicals.put(id, roll(random) ? from : to);
            else if (from != null) { if (roll(random)) chemicals.put(id, new SeedProfile.Chemical(from.min(), from.max(), from.potencyDivisor(), false)); }
        }
        var recipientOnly = new java.util.TreeSet<ResourceLocation>(Comparator.comparing(ResourceLocation::toString)); recipientOnly.addAll(rc.keySet()); recipientOnly.removeAll(dc.keySet());
        for (var id : recipientOnly) { boolean remove=roll(random); if (!remove || chemicals.size()<=1) chemicals.put(id,rc.get(id)); else chemicals.remove(id); }
        // The source first clones recipient, then overwrites crossed fields. Profile maps use stable local order.
        SeedProfile.Biology rb=r.biology(), db=d.biology();
        float nutrient=scalar(random,rb.nutrientConsumption(),db.nutrientConsumption());
        float water=scalar(random,rb.waterConsumption(),db.waterConsumption());
        float idealHeat=scalar(random,rb.idealHeat(),db.idealHeat());
        float heatTolerance=scalar(random,rb.heatTolerance(),db.heatTolerance());
        roll(random); // IdealLight is not represented locally.
        roll(random); // LightTolerance is not represented locally.
        float toxin=scalar(random,rb.toxinTolerance(),db.toxinTolerance());
        float low=scalar(random,rb.lowPressure(),db.lowPressure()); float high=scalar(random,rb.highPressure(),db.highPressure());
        float pest=scalar(random,rb.pestTolerance(),db.pestTolerance()); float weed=scalar(random,rb.weedTolerance(),db.weedTolerance());
        float endurance=scalar(random,rb.endurance(),db.endurance());
        int yield=scalar(random,r.lifecycle().yield(),d.lifecycle().yield());
        float lifespan=scalar(random,r.lifecycle().lifespan(),d.lifecycle().lifespan());
        float maturation=scalar(random,r.lifecycle().maturation(),d.lifecycle().maturation());
        float production=scalar(random,r.lifecycle().production(),d.lifecycle().production());
        float potency=scalar(random,r.lifecycle().potency(),d.lifecycle().potency());
        boolean seedless=scalar(random,r.lifecycle().seedless(),d.lifecycle().seedless());
        boolean ligneous=scalar(random,r.lifecycle().ligneous(),d.lifecycle().ligneous());
        roll(random); // TurnIntoKudzu is unsupported.
        boolean canScream=scalar(random,r.scream().enabled(),d.scream().enabled());
        Map<GasType,Float> exude=crossGases(random,d.exudeGasses(),r.exudeGasses());
        Map<GasType,Float> consume=crossGases(random,d.consumeGasses(),r.consumeGasses());
        // Mutation descriptors are captured snapshots: select each recipient (B) record, then
        // each donor (A) record, preserving original order and first-name-wins semantics.
        var mutations = new LinkedHashMap<String, PlantMutationRecord>();
        for (var record : recipient.mutations()) if (roll(random)) mutations.putIfAbsent(record.name(), record);
        for (var record : donor.mutations()) if (roll(random)) mutations.putIfAbsent(record.name(), record);
        if (!d.sourceName().equals(r.sourceName()) && fraction(random,(double) .7f)) seedless=true;
        var biology=new SeedProfile.Biology(endurance,nutrient,water,idealHeat,heatTolerance,low,high,weed,rb.weedHighLevel(),toxin,pest);
        var old=r.lifecycle();
        var life=new SeedProfile.Lifecycle(old.growthStages(),lifespan,maturation,production,yield,potency,old.harvest(),old.viable(),seedless,ligneous);
        // Upstream SeedPrototype.Clone omits ScreamSound: clone's default sound/-10 dB survives crossing.
        var scream=r.withDefaultScreamSound().scream().withEnabled(canScream);
        var profile=new SeedProfile(r.packet(),r.product(),r.visualKey(),biology,life,chemicals,consume,exude,
                r.mutationTargets(),scream,r.sourceName());
        return new GeneticValue(recipient.species(),profile, java.util.List.copyOf(mutations.values()));
    }
    private static boolean roll(DoubleSupplier rng) { double value=rng.getAsDouble(); if (!(value>=0 && value<1)) throw new IllegalArgumentException("random sample outside [0,1)"); return value < .5; }
    private static boolean fraction(DoubleSupplier rng,double probability) { double value=rng.getAsDouble(); if (!(value>=0 && value<1)) throw new IllegalArgumentException("random sample outside [0,1)"); return value < probability; }
    private static float scalar(DoubleSupplier rng,float recipient,float donor){ return roll(rng)?recipient:donor; }
    private static int scalar(DoubleSupplier rng,int recipient,int donor){ return roll(rng)?recipient:donor; }
    private static boolean scalar(DoubleSupplier rng,boolean recipient,boolean donor){ return roll(rng)?recipient:donor; }
    private static Map<GasType,Float> crossGases(DoubleSupplier rng,Map<GasType,Float> donor,Map<GasType,Float> recipient) {
        var result=new java.util.EnumMap<GasType,Float>(GasType.class);
        for (GasType gas:GasType.values()) {
            Float d=donor.get(gas), r=recipient.get(gas);
            if(d!=null&&r!=null) result.put(gas,roll(rng)?d:r);
            else if(d!=null) { if(roll(rng)) result.put(gas,d); }
            else if(r!=null) { if(!roll(rng)) result.put(gas,r); }
        }
        return result;
    }
}
