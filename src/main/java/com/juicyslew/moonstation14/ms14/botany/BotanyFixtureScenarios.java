package com.juicyslew.moonstation14.ms14.botany;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;
import java.util.random.RandomGenerator;

/** Explicit, bounded debug-only plant arrangements for the permission-gated fixture command. */
public final class BotanyFixtureScenarios {
    record WheatTraitPresets(GeneticValue yield, GeneticValue potency, GeneticValue water, GeneticValue nutrient, GeneticValue fastStages, GeneticValue endurance) { }
    record WheatEnvironmentPresets(GeneticValue idealHeat, GeneticValue lowPressure) { }
    record TomatoProductionPreset(GeneticValue earlyFruit) { }
    private BotanyFixtureScenarios() { }

    public static String arrange(ServerLevel level, BlockPos origin) {
        if (!ModSeedProfiles.serverReady() || !ModTrayProfiles.serverReady()) return "Scenario refused: resolved seed/tray profiles are not ready.";
        var tomato = ModSeedProfiles.serverLookup(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"));
        var wheat = ModSeedProfiles.serverLookup(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"));
        if (tomato == null || wheat == null) return "Scenario refused: resolved tomato/wheat profiles are unavailable.";
        HydroponicsTrayBlockEntity tomatoTray = tray(level, SmallBotanyFixtureLayout.normalTray(origin, false));
        HydroponicsTrayBlockEntity wheatTray = tray(level, SmallBotanyFixtureLayout.normalTray(origin, true));
        HydroponicsTrayBlockEntity weedsTray = tray(level, SmallBotanyFixtureLayout.dryTray(origin, false));
        HydroponicsTrayBlockEntity lowTray = tray(level, SmallBotanyFixtureLayout.dryTray(origin, true));
        HydroponicsTrayBlockEntity yieldTray = tray(level, SmallBotanyFixtureLayout.yieldTray(origin));
        HydroponicsTrayBlockEntity potencyTray = tray(level, SmallBotanyFixtureLayout.potencyTray(origin));
        HydroponicsTrayBlockEntity waterTray = tray(level, SmallBotanyFixtureLayout.waterTray(origin));
        HydroponicsTrayBlockEntity nutrientTray = tray(level, SmallBotanyFixtureLayout.nutrientTray(origin));
        HydroponicsTrayBlockEntity fastStageTray = tray(level, SmallBotanyFixtureLayout.fastStageTray(origin));
        HydroponicsTrayBlockEntity enduranceTray = tray(level,SmallBotanyFixtureLayout.enduranceTray(origin));
        HydroponicsTrayBlockEntity idealHeatTray=tray(level,SmallBotanyFixtureLayout.idealHeatTray(origin));
        BlockPos wing=SmallBotanyFixtureLayout.careOrigin(origin);
        HydroponicsTrayBlockEntity earlyFruitTray = tray(level,SmallBotanyFixtureLayout.earlyFruitTray(wing));
        HydroponicsTrayBlockEntity lowPressureTray=tray(level,SmallBotanyFixtureLayout.lowPressureTray(wing));
        HydroponicsTrayBlockEntity ligneousTray=tray(level,SmallBotanyFixtureLayout.ligneousTray(wing));
        if (tomatoTray == null || wheatTray == null || weedsTray == null || lowTray == null
                || yieldTray == null || potencyTray == null || waterTray == null || nutrientTray == null || fastStageTray == null || enduranceTray == null
                || idealHeatTray == null || lowPressureTray == null || earlyFruitTray == null
                || tomatoTray.plantState() == null || wheatTray.plantState() == null
                || weedsTray.plantState() == null || lowTray.plantState() == null
                || yieldTray.plantState() == null || potencyTray.plantState() == null
                || waterTray.plantState() == null || nutrientTray.plantState() == null || fastStageTray.plantState() == null || enduranceTray.plantState() == null
                || idealHeatTray.plantState() == null || lowPressureTray.plantState() == null || earlyFruitTray.plantState() == null || ligneousTray == null || ligneousTray.plantState() == null)
            return "Scenario refused: fixture trays are not initialized; initialize atmosphere, then wait for normal tray initialization and retry.";
        setReady(tomatoTray, ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), tomato, level.getGameTime());
        setReady(wheatTray, ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), wheat, level.getGameTime());
        clearEnvironmentConfounders(wheatTray.plantState()); wheatTray.commitMutation();
        var wheatId=ResourceLocation.fromNamespaceAndPath("moonstation14","wheat");
        var base=new GeneticValue(wheatId,wheat);
        var presets=wheatTraitPresets(base);
        var yield=presets.yield();
        var potency=presets.potency();
        var water=presets.water(); var nutrient=presets.nutrient();
        var fastStages=presets.fastStages();
        var endurance=presets.endurance();
        var environmentPresets=wheatEnvironmentPresets(base);
        setReady(yieldTray,wheatId,yield.profile(),level.getGameTime()); yieldTray.plantState().genetics(yield); yieldTray.commitMutation();
        setReady(potencyTray,wheatId,potency.profile(),level.getGameTime()); potencyTray.plantState().genetics(potency); potencyTray.commitMutation();
        prepareResourceComparison(waterTray,new GeneticValue(wheatId,wheat),water,level.getGameTime());
        prepareResourceComparison(nutrientTray,new GeneticValue(wheatId,wheat),nutrient,level.getGameTime());
        var wheatBaseline=new GeneticValue(wheatId,wheat);
        fastStageTray.commitPlant(wheatBaseline,level.getGameTime());
        prepareStageHolder(fastStageTray.plantState(),fastStages); fastStageTray.commitMutation();
        enduranceTray.commitPlant(wheatBaseline,level.getGameTime());
        prepareStageHolder(enduranceTray.plantState(),endurance); enduranceTray.commitMutation();
        prepareEnvironmentComparison(idealHeatTray.plantState(),base,environmentPresets.idealHeat(),
                idealHeatTray.resolvedTrayProfile(),level.getGameTime()); idealHeatTray.commitMutation();
        prepareEnvironmentComparison(lowPressureTray.plantState(),base,environmentPresets.lowPressure(),
                lowPressureTray.resolvedTrayProfile(),level.getGameTime()); lowPressureTray.commitMutation();
        var tomatoId=ResourceLocation.fromNamespaceAndPath("moonstation14","tomato");
        var tomatoBaseline=new GeneticValue(tomatoId,tomato);
        var earlyFruit=tomatoEarlyFruit(tomatoBaseline).earlyFruit();
        prepareRepeatFruit(earlyFruitTray,tomatoBaseline,earlyFruit,level.getGameTime());
        setReady(ligneousTray, wheatId, wheat, level.getGameTime());
        ligneousTray.plantState().genetics(ligneousPreset(ligneousTray.plantState().genetics()));
        ligneousTray.commitMutation();
        var comparisonPositions = java.util.List.of(SmallBotanyFixtureLayout.noRepeatTray(origin),
                SmallBotanyFixtureLayout.repeatTray(origin), SmallBotanyFixtureLayout.selfHarvestTray(origin));
        var comparisonModes = java.util.List.of(SeedProfile.Harvest.NO_REPEAT, SeedProfile.Harvest.REPEAT,
                SeedProfile.Harvest.SELF_HARVEST);
        for (int index = 0; index < comparisonPositions.size(); index++) {
            BlockPos pos = comparisonPositions.get(index);
            HydroponicsTrayBlockEntity compare = tray(level, pos);
            if (compare == null || compare.plantState() == null) return "Scenario refused: harvest comparison trays are not initialized.";
            var original = wheat.lifecycle();
            var lifecycle = new SeedProfile.Lifecycle(original.growthStages(), original.lifespan(), original.maturation(),
                    original.production(), original.yield(), original.potency(), comparisonModes.get(index), original.viable(), original.seedless(), original.ligneous());
            var arranged = new SeedProfile(wheat.packet(), wheat.product(), wheat.visualKey(), wheat.biology(), lifecycle,
                    wheat.chemicals(), wheat.consumeGasses(), wheat.exudeGasses(), wheat.mutationTargets(),
                    wheat.scream(), wheat.sourceName());
            setReady(compare, ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), arranged, level.getGameTime());
        }
        HydroponicsTrayBlockEntity mutationTray = tray(level, SmallBotanyFixtureLayout.untouchedTray(origin));
        if (mutationTray == null || mutationTray.plantState() == null)
            return "Scenario refused: chemical mutation demonstration tray is not initialized.";
        var vestine = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("vestine");
        if (!com.juicyslew.moonstation14.ms14.reagent.ModReagents.catalog(level).contains(vestine.location()))
            return "Scenario refused: Vestine reagent prototype is unavailable.";
        mutationTray.commitPlant(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), wheat, level.getGameTime());
        var vestineSolution = new com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment();
        vestineSolution.admitUnits(vestine, 100, mutationTray.resolvedTrayProfile().soilCapacityCents());
        if (!mutationTray.tryReplaceSoil(mutationTray.placementIdentity(), mutationTray.plantState(),
                mutationTray.plantState().revision(), mutationTray.soilSolution(), vestineSolution))
            return "Scenario refused: could not install exactly 1 unit of Vestine in the normal wheat tray.";
        HydroponicsTrayBlockEntity statsTray = tray(level, SmallBotanyFixtureLayout.statsMutationTray(origin));
        if (statsTray == null || statsTray.plantState() == null)
            return "Scenario refused: ordinary mutation stats tray is not initialized.";
        statsTray.commitPlant(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), wheat, level.getGameTime());
        statsTray.plantState().mutationModifier(3f); // Operator-arranged holder preset; not a produced Left4Zed mutation.
        var mutagen = com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey("unstablemutagen");
        if (!com.juicyslew.moonstation14.ms14.reagent.ModReagents.catalog(level).contains(mutagen.location()))
            return "Scenario refused: Unstable Mutagen reagent prototype is unavailable.";
        // Leave soil empty: I demonstrates operator dosing via the real kit beaker.
        HydroponicsTrayBlockEntity gasTray = tray(level, SmallBotanyFixtureLayout.gasExchangeTray(origin));
        if (gasTray == null || gasTray.plantState() == null)
            return "Scenario refused: gas exchange tray is not initialized.";
        var nitrogen = com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN;
        var oxygen = com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN;
        var gasProfile = new SeedProfile(wheat.packet(), wheat.product(), wheat.visualKey(), wheat.biology(), wheat.lifecycle(),
                wheat.chemicals(), java.util.Map.of(nitrogen, .02f), java.util.Map.of(oxygen, .01f),
                wheat.mutationTargets(), wheat.scream(), wheat.sourceName());
        gasTray.commitPlant(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), gasProfile, level.getGameTime());
        gasTray.commitMutation();
        HydroponicsTrayBlockEntity samplingTray = tray(level, SmallBotanyFixtureLayout.samplingTray(origin));
        if (samplingTray == null || samplingTray.plantState() == null)
            return "Scenario refused: sampling tray is not initialized.";
        var samplingGenetics = wheatCanScreamPreset(new GeneticValue(wheatId,wheat));
        samplingTray.commitPlant(samplingGenetics, level.getGameTime());
        var samplingPlant = samplingTray.plantState(); prepareStageHolder(samplingPlant,samplingGenetics);
        samplingTray.commitMutation();
        weedsTray.commitPlant(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat"), wheat, level.getGameTime());
        PlantInstance weeds = weedsTray.plantState();
        weeds.soil(100, 100, 6, 0); weeds.touch(); weedsTray.commitMutation();
        var low = lowTray.plantState();
        low.plant(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato"), tomato, level.getGameTime());
        low.soil(15, 8, 0, 40);
        low.state(1, tomato.biology().endurance()/2, 0, 0, false, false);
        lowTray.commitMutation();
        String care=arrangeCare(level,origin,wheat);
        if(!care.startsWith("Arranged")) return care;
        String showcase=BotanyMutationShowcase.arrange(level,origin);
        if(!showcase.startsWith("Arranged")) return showcase;
        return "Arranged fixture stations: W/X yield/potency, Y/Z water/nutrient-use, AA fast-stage, AD ideal-heat, AE low-pressure, AB early-fruit, AF-AN tolerance, AL ligneous wheat, and twelve mutation showcase specimens. Presets are not acquired mutations.";
    }

    private static String arrangeCare(ServerLevel level,BlockPos origin,SeedProfile wheat) {
        var wing=SmallBotanyFixtureLayout.careOrigin(origin);
        var stations=com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careLabels(wing);
        var catalog=com.juicyslew.moonstation14.ms14.reagent.ModReagents.catalog(level);
        var trays=new java.util.ArrayList<HydroponicsTrayBlockEntity>();
        for(int i=0;i<stations.size();i++) {
            var t=tray(level,stations.get(i).trayPos());
            if(t==null||t.plantState()==null) return "Scenario refused: care tray "+stations.get(i).title()+" is not initialized.";
            var key=com.juicyslew.moonstation14.ms14.reagent.ModReagents.createKey(
                    com.juicyslew.moonstation14.ms14.botany.fixture.BotanyFixtureSigns.careDoses().get(i).reagent());
            if(!catalog.contains(key.location())) return "Scenario refused: required care reagent "+key.location()+" is not resolved.";
            trays.add(t);
        }
        long now=level.getGameTime();
        for(int i=0;i<trays.size();i++) {
            HydroponicsTrayBlockEntity t=trays.get(i);
            PlantInstance p=t.plantState();
            t.commitPlant(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"),wheat,now);
            p=t.plantState(); p.soil(100,100,0,0);
            float end=wheat.biology().endurance();
            switch(i) {
                case 0 -> { p.state(1,end/2,0,0,false,false); p.soil(100,80,0,0); }
                case 1 -> { p.state(1,end,0,0,false,false); p.soil(100,100,0,39); p.pestLevel(0); }
                case 2 -> { p.state(1,end,0,0,false,false); p.pestLevel(6); }
                case 3 -> { p.state(1,end,0,0,false,false); p.soil(100,100,6,0); }
                case 4 -> {
                    var g=p.genetics(); p.genetics(g.adjustPotency(12-g.profile().lifecycle().potency()));
                    p.state(1,end,0,0,false,false);
                }
                case 5 -> {
                    var g=p.genetics(); var l=g.profile().lifecycle();
                    var high=withLifecycle(g.profile(),new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),2,29,
                            l.harvest(),l.viable(),false,l.ligneous()));
                    p.genetics(new GeneticValue(g.species(),high)); p.state(1,end,0,0,false,false);
                }
                case 6 -> { p.state(wheat.lifecycle().maturation()+10,end/2,0,0,false,false); p.soil(100,100,0,9); }
                case 7 -> p.state(1,end,0,0,false,false);
                case 8 -> p.state(1,end,0,0,false,false);
                case 9 -> {
                    var g=p.genetics(); var l=g.profile().lifecycle();
                    p.genetics(new GeneticValue(g.species(),withLifecycle(g.profile(),new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),
                            l.maturation(),l.production(),l.yield(),l.potency(),l.harvest(),false,l.seedless(),l.ligneous()))));
                    p.state(1,end,0,0,false,false);
                }
                case 10 -> {
                    var g=p.genetics(); var l=g.profile().lifecycle();
                    var bio=g.profile().biology();
                    var roomy=new SeedProfile.Biology(Math.max(100,bio.endurance()),bio.nutrientConsumption(),bio.waterConsumption(),bio.idealHeat(),
                            bio.heatTolerance(),bio.lowPressure(),bio.highPressure(),bio.weedTolerance(),bio.weedHighLevel(),bio.toxinTolerance(),bio.pestTolerance());
                    var profile=withBiologyLifecycle(g.profile(),roomy,new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),
                            l.yield(),l.potency(),SeedProfile.Harvest.REPEAT,l.viable(),true,l.ligneous()));
                    p.genetics(new GeneticValue(g.species(),profile));
                    p.soil(100,100,0,0); p.state(l.maturation()+1,p.genetics().profile().biology().endurance(),0,0,true,false);
                }
                default -> throw new IllegalStateException("unmapped care station "+i);
            }
            p.touch(); t.commitMutation();
        }
        var toleranceValues=tolerancePresets(new GeneticValue(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"),wheat));
        var names=java.util.List.of("AF","AG","AH","AI","AJ","AK","AM","AN");
        var toleranceTrays=new java.util.ArrayList<HydroponicsTrayBlockEntity>();
        for(int i=0;i<names.size();i++) {
            var t=tray(level,SmallBotanyFixtureLayout.toleranceTray(wing,i));
            if(t==null||t.plantState()==null) return "Scenario refused: tolerance preset tray "+names.get(i)+" is not initialized.";
            toleranceTrays.add(t);
        }
        var base=new GeneticValue(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat"),wheat);
        for(int i=0;i<toleranceTrays.size();i++) {
            var t=toleranceTrays.get(i); var p=t.plantState();
            t.commitPlant(base,now); p=t.plantState(); prepareToleranceHolder(p,toleranceValues.get(i),i);
            t.commitMutation();
        }
        return "Arranged eleven resolved-wheat care baselines and eight matched healthy tolerance presets.";
    }

    static java.util.List<GeneticValue> tolerancePresets(GeneticValue base) {
        if(base==null||!base.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat")))
            throw new IllegalArgumentException("tolerance presets require resolved wheat genetics");
        var b=base.profile().biology();
        var result=new java.util.ArrayList<GeneticValue>();
        for(int i=0;i<8;i++) {
            float toxin=i==1?10:b.toxinTolerance(), pest=i==3?15:b.pestTolerance(), weed=i==5?15:b.weedTolerance();
            float idealHeat=i>=6?275:b.idealHeat(), heatTolerance=i==6?10:i==7?25:b.heatTolerance();
            var changed=new SeedProfile.Biology(b.endurance(),b.nutrientConsumption(),b.waterConsumption(),idealHeat,heatTolerance,
                    b.lowPressure(),b.highPressure(),weed,b.weedHighLevel(),toxin,pest);
            result.add(new GeneticValue(base.species(),withBiology(base.profile(),changed)));
        }
        return java.util.List.copyOf(result);
    }
    static void prepareToleranceHolder(PlantInstance plant,GeneticValue genetics,int station) {
        if(station<0||station>=8) throw new IllegalArgumentException("tolerance station index");
        plant.soil(100,100,station>=4&&station<6?6:0,station<2?5:0);
        plant.pestLevel(station>=2&&station<4?6:0);
        plant.state(2,genetics.profile().biology().endurance(),0,0,false,false); plant.genetics(genetics); plant.touch();
    }

    private static SeedProfile withLifecycle(SeedProfile p,SeedProfile.Lifecycle life) {
        return new SeedProfile(p.packet(),p.product(),p.visualKey(),p.biology(),life,p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),p.sourceName());
    }
    private static GeneticValue withYield(GeneticValue value,int yield) {
        var p=value.profile(); var l=p.lifecycle();
        return new GeneticValue(value.species(),withLifecycle(p,new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),l.maturation(),l.production(),yield,l.potency(),l.harvest(),l.viable(),l.seedless(),l.ligneous())));
    }
    static WheatTraitPresets wheatTraitPresets(GeneticValue resolvedBase) {
        if (resolvedBase == null || !resolvedBase.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat")))
            throw new IllegalArgumentException("wheat fixture presets require resolved wheat genetics");
        var life=resolvedBase.profile().lifecycle();
        var biology=resolvedBase.profile().biology();
        var waterBio=new SeedProfile.Biology(biology.endurance(),biology.nutrientConsumption(),.9f,biology.idealHeat(),biology.heatTolerance(),biology.lowPressure(),biology.highPressure(),biology.weedTolerance(),biology.weedHighLevel(),biology.toxinTolerance(),biology.pestTolerance());
        var nutrientBio=new SeedProfile.Biology(biology.endurance(),1.2f,biology.waterConsumption(),biology.idealHeat(),biology.heatTolerance(),biology.lowPressure(),biology.highPressure(),biology.weedTolerance(),biology.weedHighLevel(),biology.toxinTolerance(),biology.pestTolerance());
        return new WheatTraitPresets(withYield(resolvedBase,7),resolvedBase.adjustPotency(50-life.potency()),
                new GeneticValue(resolvedBase.species(),withBiology(resolvedBase.profile(),waterBio)),
                new GeneticValue(resolvedBase.species(),withBiology(resolvedBase.profile(),nutrientBio)),
                withMaturation(resolvedBase,3),
                new GeneticValue(resolvedBase.species(),withBiology(resolvedBase.profile(),new SeedProfile.Biology(50,biology.nutrientConsumption(),biology.waterConsumption(),biology.idealHeat(),biology.heatTolerance(),biology.lowPressure(),biology.highPressure(),biology.weedTolerance(),biology.weedHighLevel(),biology.toxinTolerance(),biology.pestTolerance()))));
    }
    static WheatEnvironmentPresets wheatEnvironmentPresets(GeneticValue resolvedBase) {
        if(resolvedBase==null||!resolvedBase.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat")))
            throw new IllegalArgumentException("wheat environment presets require resolved wheat genetics");
        var b=resolvedBase.profile().biology();
        var heat=new SeedProfile.Biology(b.endurance(),b.nutrientConsumption(),b.waterConsumption(),263,b.heatTolerance(),
                b.lowPressure(),b.highPressure(),b.weedTolerance(),b.weedHighLevel(),b.toxinTolerance(),b.pestTolerance());
        var pressure=new SeedProfile.Biology(b.endurance(),b.nutrientConsumption(),b.waterConsumption(),b.idealHeat(),b.heatTolerance(),
                100,b.highPressure(),b.weedTolerance(),b.weedHighLevel(),b.toxinTolerance(),b.pestTolerance());
        return new WheatEnvironmentPresets(new GeneticValue(resolvedBase.species(),withBiology(resolvedBase.profile(),heat)),
                new GeneticValue(resolvedBase.species(),withBiology(resolvedBase.profile(),pressure)));
    }
    static GeneticValue ligneousPreset(GeneticValue resolvedWheat) {
        if(resolvedWheat==null||!resolvedWheat.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14","wheat")))
            throw new IllegalArgumentException("ligneous preset requires resolved wheat genetics");
        var life=resolvedWheat.profile().lifecycle();
        return new GeneticValue(resolvedWheat.species(),withLifecycle(resolvedWheat.profile(),new SeedProfile.Lifecycle(
                life.growthStages(),life.lifespan(),life.maturation(),life.production(),life.yield(),life.potency(),life.harvest(),
                life.viable(),life.seedless(),true)));
    }
    static TomatoProductionPreset tomatoEarlyFruit(GeneticValue resolvedBase) {
        if(resolvedBase==null||!resolvedBase.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14","tomato")))
            throw new IllegalArgumentException("tomato timing preset requires resolved tomato genetics");
        var life=resolvedBase.profile().lifecycle();
        return new TomatoProductionPreset(withLifecycle(resolvedBase,new SeedProfile.Lifecycle(life.growthStages(),life.lifespan(),life.maturation(),1,
                life.yield(),life.potency(),life.harvest(),life.viable(),life.seedless(),life.ligneous())));
    }
    private static GeneticValue withMaturation(GeneticValue value,float maturation) {
        var l=value.profile().lifecycle();
        return withLifecycle(value,new SeedProfile.Lifecycle(l.growthStages(),l.lifespan(),maturation,l.production(),l.yield(),l.potency(),l.harvest(),l.viable(),l.seedless(),l.ligneous()));
    }
    private static GeneticValue withLifecycle(GeneticValue value,SeedProfile.Lifecycle life) {
        return new GeneticValue(value.species(),withLifecycle(value.profile(),life));
    }
    static void prepareStageHolder(PlantInstance plant,GeneticValue genetics) {
        plant.soil(80,80,0,0); plant.state(2,genetics.profile().biology().endurance(),0,0,false,false); plant.genetics(genetics); plant.touch();
    }
    static GeneticValue wheatCanScreamPreset(GeneticValue resolvedWheat) {
        if (resolvedWheat == null || !resolvedWheat.species().equals(ResourceLocation.fromNamespaceAndPath("moonstation14", "wheat")))
            throw new IllegalArgumentException("scream fixture preset requires resolved wheat genetics");
        return new GeneticValue(resolvedWheat.species(), resolvedWheat.profile().withScream(
                resolvedWheat.profile().scream().withEnabled(true)), resolvedWheat.mutations());
    }
    private static void prepareRepeatFruit(HydroponicsTrayBlockEntity tray,GeneticValue baseline,GeneticValue genetics,long now) {
        setReady(tray,baseline.species(),baseline.profile(),now); tray.plantState().genetics(genetics); tray.commitMutation();
    }
    private static SeedProfile withBiology(SeedProfile p,SeedProfile.Biology biology) {
        return new SeedProfile(p.packet(),p.product(),p.visualKey(),biology,p.lifecycle(),p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),p.sourceName());
    }
    private static void prepareResourceComparison(HydroponicsTrayBlockEntity tray,GeneticValue baseline,GeneticValue genetics,long now) {
        setReady(tray,baseline.species(),baseline.profile(),now);
        tray.plantState().genetics(genetics); tray.commitMutation();
    }
    private static SeedProfile withBiologyLifecycle(SeedProfile p,SeedProfile.Biology biology,SeedProfile.Lifecycle life) {
        return new SeedProfile(p.packet(),p.product(),p.visualKey(),biology,life,p.chemicals(),p.consumeGasses(),p.exudeGasses(),p.mutationTargets(),p.scream(),p.sourceName());
    }

    private static HydroponicsTrayBlockEntity tray(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HydroponicsTrayBlockEntity tray && tray.isLoadedValid() ? tray : null;
    }
    static void setReady(HydroponicsTrayBlockEntity tray, ResourceLocation id, SeedProfile seed, long now) {
        PlantInstance plant = tray.plantState();
        prepareFixtureReadyPlant(plant,id,seed,tray.resolvedTrayProfile(),now);
        tray.commitMutation();
    }
    /** Arranges a catalog gallery as living seedlings without advancing their growth clock. */
    public static String arrangeCatalog(ServerLevel level,
            com.juicyslew.moonstation14.ms14.botany.fixture.BotanyCatalogGalleryPlan plan) {
        if (level == null || plan == null || !ModSeedProfiles.serverReady())
            return "Scenario refused: resolved seed profiles are not ready.";
        var targets = new java.util.ArrayList<HydroponicsTrayBlockEntity>();
        for (var entry : plan.entries()) {
            var resolved = ModSeedProfiles.serverLookup(entry.species());
            var target = tray(level, entry.trayPosition());
            if (resolved == null || !resolved.equals(entry.profile()))
                return "Scenario refused: resolved seed profile is unavailable or changed for " + entry.species() + ".";
            if (target == null || target.plantState() == null)
                return "Scenario refused: catalog tray for " + entry.species() + " is not initialized.";
            targets.add(target);
        }
        long now = level.getGameTime();
        for (int i = 0; i < plan.entries().size(); i++) {
            var entry = plan.entries().get(i);
            var plant = targets.get(i).plantState();
            prepareFixtureSeedling(plant, entry.species(), entry.profile(), now);
            targets.get(i).commitMutation();
        }
        return "Arranged " + targets.size() + " catalog seedlings.";
    }
    static void prepareFixtureSeedling(PlantInstance plant, ResourceLocation id, SeedProfile resolvedSeed, long now) {
        plant.plant(id, resolvedSeed, now);
        plant.soil(100, 100, 0, 0);
    }
    static void prepareEnvironmentComparison(PlantInstance plant,GeneticValue baseline,GeneticValue preset,
            TrayProfile tray,long now) {
        prepareFixtureReadyPlant(plant,baseline.species(),baseline.profile(),tray,now);
        clearEnvironmentConfounders(plant);
        plant.genetics(preset);
    }
    static void clearEnvironmentConfounders(PlantInstance plant) {
        plant.soil(plant.water(),plant.nutrition(),0,0);
        plant.touch();
    }
    private static void prepareFixtureReadyPlant(PlantInstance plant,ResourceLocation id,SeedProfile seed,
            TrayProfile profile,long now) {
        plant.plant(id, seed, now);
        plant.soil(100, 100, 0, 0);
        var random = new FixtureRandom();
        for (int i=0; i<256 && !plant.ready() && !plant.dead(); i++)
            PlantMetabolismEvaluator.cycle(plant, profile, seed, now + (i+1L)*profile.cycleDelayTicks(), random,
                    Optional.of(new PlantMetabolismEvaluator.Environment(100, 293.15f)));
        // Pregrowth sets the simulated cycle time ahead; reset only the fixture clock so SELF_HARVEST
        // enters the ordinary world tick promptly while preserving the arranged plant state.
        plant.cycle(now);
    }
    private static final class FixtureRandom implements RandomGenerator {
        @Override public long nextLong() { return 0; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public int nextInt(int origin, int bound) { return origin; }
        @Override public float nextFloat() { return 0; }
    }
}
