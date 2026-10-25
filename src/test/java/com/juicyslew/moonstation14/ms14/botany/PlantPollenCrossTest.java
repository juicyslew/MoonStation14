package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PlantPollenCrossTest {
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("test",value); }
    private static GeneticValue value(String species, Map<ResourceLocation,SeedProfile.Chemical> chemicals,
                                      Map<GasType,Float> consume, Map<GasType,Float> exude, List<ResourceLocation> history,
                                      SeedProfile.Scream scream, int yield) {
        var bio=new SeedProfile.Biology(11,2,3,4,5,6,7,8,9,10,12);
         var life=new SeedProfile.Lifecycle(3,20,21,22,yield,24,SeedProfile.Harvest.SELF_HARVEST,false,true);
        var profile=new SeedProfile(id(species+"_packet"),id(species+"_product"),species,bio,life,chemicals,consume,exude,history,scream,"test-"+species+"-name");
        return new GeneticValue(id(species),profile);
    }
    private static GeneticValue value(String species) { return value(species,Map.of(),Map.of(),Map.of(),List.of(),new SeedProfile.Scream(true,id("custom"),-2),7); }
    private static GeneticValue seedless(String species,boolean seedless) {
        var base=value(species); var p=base.profile(); var l=p.lifecycle();
        var life=new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),l.yield(),l.potency(),l.harvest(),l.viable(),seedless,l.ligneous());
        return new GeneticValue(base.species(),new SeedProfile(p.packet(),p.product(),p.visualKey(),p.biology(),life,p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),p.sourceName()));
    }
    @Test void directionalFieldsAndIdentityStayRecipientOwnedAndInputsRemainUnchanged() {
        var donor=value("donor"); var recipient=value("recipient");
        var crossed=PlantPollenCross.cross(donor,recipient,()->0.9);
        assertEquals(recipient.species(),crossed.species());
        assertEquals(recipient.profile().packet(),crossed.profile().packet());
        assertEquals(recipient.profile().product(),crossed.profile().product());
        assertEquals(recipient.profile().visualKey(),crossed.profile().visualKey());
        assertEquals(recipient.profile().sourceName(),crossed.profile().sourceName());
        assertEquals(7,crossed.profile().lifecycle().yield());
        assertFalse(crossed.profile().lifecycle().viable());
        assertTrue(crossed.profile().scream().enabled());
        assertEquals(SeedProfile.Scream.DEFAULT_SOUND,crossed.profile().scream().sound());
        assertEquals(-10f,crossed.profile().scream().volumeDb());
        assertNotSame(recipient,crossed);
        assertEquals(id("custom"),recipient.profile().scream().sound());
    }
    @Test void equivalentFixtureNamesDoNotHybridizeDifferentSpeciesButDifferentNamesDo() {
        var donor=value("fake-capfruit"); var recipient=value("real-capfruit");
        donor=new GeneticValue(donor.species(),withSourceName(donor.profile(),"seeds-capfruit-name"));
        recipient=new GeneticValue(recipient.species(),withSourceName(recipient.profile(),"seeds-capfruit-name"));
        donor=new GeneticValue(donor.species(),withSeedless(donor.profile(),false));
        recipient=new GeneticValue(recipient.species(),withSeedless(recipient.profile(),false));
        var sameNameCalls=new AtomicInteger();
        var sameName=PlantPollenCross.cross(donor,recipient,()->{int i=sameNameCalls.getAndIncrement();return i==21?.7:.9;});
        assertFalse(sameName.profile().lifecycle().seedless());
        assertEquals(21,sameNameCalls.get());
        donor=new GeneticValue(donor.species(),withSourceName(donor.profile(),"fake-capfruit-name"));
        var differentNameCalls=new AtomicInteger();
        var differentName=PlantPollenCross.cross(donor,recipient,()->{int i=differentNameCalls.getAndIncrement();return i==21?.7:.9;});
        assertFalse(differentName.profile().lifecycle().seedless());
        assertEquals(22,differentNameCalls.get());
    }
    private static SeedProfile withSourceName(SeedProfile p,String name) {
        return new SeedProfile(p.packet(),p.product(),p.visualKey(),p.biology(),p.lifecycle(),p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),name);
    }
    private static SeedProfile withSeedless(SeedProfile p,boolean seedless) {
        var l=p.lifecycle(); var life=new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),l.yield(),l.potency(),l.harvest(),l.viable(),seedless,l.ligneous());
        return new SeedProfile(p.packet(),p.product(),p.visualKey(),p.biology(),life,p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),p.sourceName());
    }
    @Test void unsupportedGenesStillConsumeDrawsAndDifferentSpeciesSeedlessDrawIsLast() {
        var calls=new AtomicInteger();
        var crossed=PlantPollenCross.cross(value("donor"),value("recipient"),()->{int n=calls.getAndIncrement();return n==22?0.1:0.9;});
        assertEquals(22,calls.get());
        assertTrue(crossed.profile().lifecycle().seedless());
    }
    @Test void hybridSeedlessUsesStrictSeventyPercentThresholdAndSameSpeciesSkipsDraw() {
        for (double sample : new double[]{.5,.699999}) {
            var calls=new AtomicInteger();
            var crossed=PlantPollenCross.cross(seedless("donor",false),seedless("recipient",false),()->calls.getAndIncrement()==21?sample:.9);
            assertTrue(crossed.profile().lifecycle().seedless());
            assertEquals(22,calls.get());
        }
        for (double sample : new double[]{.7,.9}) {
            var crossed=PlantPollenCross.cross(seedless("donor",false),seedless("recipient",false),()->sample);
            assertFalse(crossed.profile().lifecycle().seedless());
        }
        var calls=new AtomicInteger();
        PlantPollenCross.cross(value("same"),value("same"),()->{calls.incrementAndGet();return .9;});
        assertEquals(21,calls.get());
    }
    @Test void ligneousUsesItsExistingCrossDrawRatherThanAddingRandomConsumption() {
        var calls=new AtomicInteger();
        var donor=value("donor"); var recipient=value("recipient");
        var ligneousLife=new SeedProfile.Lifecycle(3,20,21,22,7,24,SeedProfile.Harvest.SELF_HARVEST,false,true,true);
        donor=new GeneticValue(donor.species(),new SeedProfile(donor.profile().packet(),donor.profile().product(),donor.profile().visualKey(),
                donor.profile().biology(),ligneousLife,Map.of(),Map.of(),Map.of(),List.of(),donor.profile().scream(),donor.profile().sourceName()));
        var crossed=PlantPollenCross.cross(donor,recipient,()->{int draw=calls.getAndIncrement();return draw==18?.9:.1;});
        assertTrue(crossed.profile().lifecycle().ligneous());
        assertEquals(22,calls.get(),"Ligneous is source draw index 18; unsupported phantom draw is absent");
        var inverseCalls=new AtomicInteger();
        var inverse=PlantPollenCross.cross(recipient,donor,()->{int draw=inverseCalls.getAndIncrement();return draw==18?.9:.1;});
        assertFalse(inverse.profile().lifecycle().ligneous(),"the distinctive recipient-side draw at index 19 must be used");
        assertEquals(22,inverseCalls.get());
    }
    @Test void sameSpeciesSkipsFinalSeedlessDrawAndRetainsRecipientMutationTargets() {
        var base=value("same");
        var donorProfile=base.profile(); var b=donorProfile.biology();
        var donorBio=new SeedProfile.Biology(b.endurance(),99,b.waterConsumption(),b.idealHeat(),b.heatTolerance(),b.lowPressure(),b.highPressure(),b.weedTolerance(),b.weedHighLevel(),b.toxinTolerance(),b.pestTolerance());
        var donorLife=new SeedProfile.Lifecycle(3,20,21,22,7,24,SeedProfile.Harvest.SELF_HARVEST,false,false);
        var donor=new GeneticValue(base.species(),new SeedProfile(donorProfile.packet(),donorProfile.product(),donorProfile.visualKey(),donorBio,donorLife,Map.of(),Map.of(),Map.of(),List.of(),donorProfile.scream(),donorProfile.sourceName()));
        var targetA=id("target_a"); var targetB=id("target_b");
        var recipient=new GeneticValue(base.species(),new SeedProfile(base.profile().packet(),base.profile().product(),base.profile().visualKey(),
                base.profile().biology(),new SeedProfile.Lifecycle(3,20,21,22,7,24,SeedProfile.Harvest.SELF_HARVEST,false,false),
                 Map.of(),Map.of(),Map.of(),List.of(targetB,targetA),base.profile().scream(),base.profile().sourceName()));
        var calls=new AtomicInteger();
        var crossed=PlantPollenCross.cross(donor,recipient,()->{int n=calls.getAndIncrement();return n==18?.9:.1;});
        assertEquals(21,calls.get());
        assertFalse(crossed.profile().lifecycle().seedless());
        assertEquals(List.of(targetB,targetA),crossed.profile().mutationTargets());
    }
    @Test void soleRecipientChemicalRemovalDrawIsConsumedButCannotRemoveSoleEntry() {
        var chemicalId=id("sole");
        var base=value("same"); var originalDonor=base.profile(); var oldBio=originalDonor.biology();
        var donorBio=new SeedProfile.Biology(oldBio.endurance(),99,oldBio.waterConsumption(),oldBio.idealHeat(),oldBio.heatTolerance(),oldBio.lowPressure(),oldBio.highPressure(),oldBio.weedTolerance(),oldBio.weedHighLevel(),oldBio.toxinTolerance(),oldBio.pestTolerance());
        var donor=new GeneticValue(base.species(),new SeedProfile(originalDonor.packet(),originalDonor.product(),originalDonor.visualKey(),donorBio,originalDonor.lifecycle(),Map.of(),Map.of(),Map.of(),List.of(),originalDonor.scream(),originalDonor.sourceName()));
        var bio=base.profile().biology();
        var recipientProfile=new SeedProfile(base.profile().packet(),base.profile().product(),base.profile().visualKey(),bio,
                base.profile().lifecycle(),Map.of(chemicalId,new SeedProfile.Chemical(1,2,3)),Map.of(),Map.of(),List.of(),base.profile().scream(),base.profile().sourceName());
        var recipient=new GeneticValue(base.species(),recipientProfile);
        var calls=new AtomicInteger();
        var crossed=PlantPollenCross.cross(donor,recipient,()->{int n=calls.getAndIncrement();return n==0?.1:.9;});
        assertTrue(crossed.profile().chemicals().containsKey(chemicalId));
        assertEquals(99,crossed.profile().biology().nutrientConsumption());
        assertEquals(22,calls.get());
    }
    @Test void chemicalsAreWholeEntriesAndGasSelectionsAreOpposite() {
        var shared=id("shared"); var onlyDonor=id("donor_only"); var onlyRecipient=id("recipient_only");
        var dc=Map.of(shared,new SeedProfile.Chemical(2,9,4,true),onlyDonor,new SeedProfile.Chemical(3,8,5,true));
        var rc=Map.of(shared,new SeedProfile.Chemical(1,6,2,true),onlyRecipient,new SeedProfile.Chemical(4,7,3,true));
        var donor=value("donor",dc,Map.of(GasType.OXYGEN,4f,GasType.PLASMA,8f),Map.of(),List.of(),SeedProfile.Scream.DISABLED,2);
        var recipient=value("recipient",rc,Map.of(GasType.OXYGEN,2f,GasType.NITROGEN,6f),Map.of(),List.of(),SeedProfile.Scream.DISABLED,3);
        var samples=new ArrayDeque<Double>();
        // chem shared donor; donor-only present (inherent reset); recipient-only removal, but retain sole? there are two recipient chemicals.
        samples.addAll(List.of(.1,.1,.1));
        // 21 scalar/unsupported draws, hybrid roll, then shared/donor-only/recipient-only gas draws.
        for(int i=0;i<21;i++) samples.add(.9);
        samples.addAll(List.of(.9,.1,.1,.1));
        var calls=new AtomicInteger();
        var crossed=PlantPollenCross.cross(donor,recipient,()->{calls.incrementAndGet(); return .1;});
        assertEquals(dc.get(shared),crossed.profile().chemicals().get(shared));
        assertFalse(crossed.profile().chemicals().get(onlyDonor).inherent());
        assertFalse(crossed.profile().chemicals().containsKey(onlyRecipient));
        assertEquals(4f,crossed.profile().consumeGasses().get(GasType.OXYGEN));
        assertEquals(8f,crossed.profile().consumeGasses().get(GasType.PLASMA));
        assertFalse(crossed.profile().consumeGasses().containsKey(GasType.NITROGEN));
        assertEquals(28,calls.get());
    }
    @Test void mutationRecordsDrawRecipientThenDonorAndKeepFirstCapturedDescriptor() {
        var slippery=com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT;
        var bFirst=new PlantMutationRecord("shared",true,false,false,slippery);
        var aDuplicate=new PlantMutationRecord("shared",false,true,true,slippery);
        var aOnly=new PlantMutationRecord("a",true,true,false,slippery);
        var bOnly=new PlantMutationRecord("b",false,false,true,slippery);
        var donor=new GeneticValue(value("donor").species(),value("donor").profile(),List.of(aDuplicate,aOnly));
        var recipient=new GeneticValue(value("recipient").species(),value("recipient").profile(),List.of(bFirst,bOnly));
        var calls=new AtomicInteger();
        // Baseline ends after chemistry/gases at draw 21; then B records (21,22), A records (23,24), final name roll (25).
        var crossed=PlantPollenCross.cross(donor,recipient,()->{int n=calls.getAndIncrement(); return n==21||n==23||n==24?.1:.9;});
        assertEquals(List.of(bFirst,aOnly),crossed.mutations());
        assertSame(bFirst,crossed.mutations().get(0));
        assertEquals(26,calls.get());
        assertEquals(List.of(aDuplicate,aOnly),donor.mutations());
        assertEquals(List.of(bFirst,bOnly),recipient.mutations());
        assertThrows(UnsupportedOperationException.class,()->crossed.mutations().add(bOnly));
    }
    @Test void selectedDonorDescriptorSurvivesWhenRecipientDuplicateIsNotSelected() {
        var slippery=com.juicyslew.moonstation14.ms14.slip.ItemSlipperyComposition.DEFAULT;
        var b=new PlantMutationRecord("same",true,false,false,slippery);
        var a=new PlantMutationRecord("same",false,true,true,slippery);
        var dv=value("donor"); var rv=value("recipient");
        var donor=new GeneticValue(dv.species(),dv.profile(),List.of(a));
        var recipient=new GeneticValue(rv.species(),rv.profile(),List.of(b));
        var calls=new AtomicInteger();
        var crossed=PlantPollenCross.cross(donor,recipient,()->calls.getAndIncrement()==21?.9:.1);
        assertEquals(List.of(a),crossed.mutations());
        assertEquals(24,calls.get());
    }
}
