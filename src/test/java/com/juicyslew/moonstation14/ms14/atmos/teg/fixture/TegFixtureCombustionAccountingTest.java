package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.reaction.BundledGasReactionCatalog;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereChunkData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TegFixtureCombustionAccountingTest {
    private static List<GasReactionEvaluator.Event> fire(String name, Map<GasType,Double> moles, double temp) {
        var source=BundledGasReactionCatalog.load();
        var entries=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,GasReactionData>();
        source.asMap().forEach((id,data)->{if(id.getPath().equals(name))entries.put(id,data);});
        var catalog=new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(entries);
        return GasReactionEvaluator.evaluate(catalog,new GasMixture(moles,temp)).events();
    }
    private static GasReactionEvaluator.Event event(GasReactionData.EffectType type, Map<GasType,Double> d,double q) {
        return new GasReactionEvaluator.Event(ResourceLocation.withDefaultNamespace("test"),type,99,d,q);
    }
    @Test void actualBundledEventsAccountForEveryCombustionBranchAndPreexistingProducts() {
        var accounting=new TegFixtureCombustionAccounting();
        accounting.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,10d),1643.15));
        var co2=accounting.snapshot(); assertTrue(co2.carbonDioxideProduced()>0);assertEquals(0,co2.tritiumProduced());
        assertEquals(160000*co2.plasmaConsumed(),co2.chemicalHeatJoules(),1);

        var mixed=new TegFixtureCombustionAccounting();
        mixed.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d,GasType.TRITIUM,.01),1643.15));
        var mix=mixed.snapshot();assertTrue(mix.tritiumProduced()>0);assertTrue(mix.carbonDioxideProduced()>0);
        var both=new TegFixtureCombustionAccounting();both.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d,GasType.TRITIUM,.01),1643.15));
        both.observe(fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1d),1643.15));
        var branches=both.snapshot();assertTrue(branches.tritiumConsumed()>0);assertTrue(branches.waterProduced()>0);
        assertEquals(branches.tritiumProduced()-branches.tritiumConsumed(),branches.netSpeciesDelta().get(GasType.TRITIUM),1e-7);
        assertEquals(branches.tritiumConsumed(),branches.waterProduced(),1e-7);
        var low=fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1d),550);
        var high=fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1000d),1643.15);
        assertTrue(low.getFirst().energyDeltaJoules()/(-low.getFirst().speciesDelta().get(GasType.TRITIUM))
                != high.getFirst().energyDeltaJoules()/(-high.getFirst().speciesDelta().get(GasType.TRITIUM)));
        var preexisting=fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1d,GasType.WATER_VAPOR,.5),1643.15);
        assertTrue(preexisting.getFirst().speciesDelta().get(GasType.WATER_VAPOR)<.5);
    }

    @Test void rejectsMalformedOrUnsupportedCommittedChemistry() {
        var accounting=new TegFixtureCombustionAccounting();
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,1d),1))));
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,1d,GasType.NITROGEN,1d,GasType.OXYGEN,-1d),160000))));
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,-1d,GasType.OXYGEN,-1d),160000))));
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,1d,GasType.TRITIUM,-1d,GasType.OXYGEN,-1d),160000))));
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,1d,GasType.OXYGEN,-1d),Double.NaN))));
        assertThrows(IllegalArgumentException.class,()->accounting.observe(List.of(event(GasReactionData.EffectType.PLASMA_FIRE,Map.of(GasType.PLASMA,-1d,GasType.CARBON_DIOXIDE,1d,GasType.OXYGEN,-1d),Double.POSITIVE_INFINITY))));
    }
    @Test void localClosureRejectsMissingOrDoubleObservationAndPacketOmission() {
        var observed=new TegFixtureCombustionAccounting();
        observed.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d,GasType.TRITIUM,.01),1643.15));
        var snap=observed.snapshot();
        var before=Map.<GasType,Double>of();var after=snap.netSpeciesDelta();var zero=Map.<GasType,Double>of();
        assertDoesNotThrow(()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,zero,zero,snap));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,zero,zero,zero,snap));
        var doubled=new TegFixtureCombustionAccounting();doubled.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d,GasType.TRITIUM,.01),1643.15));doubled.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d,GasType.TRITIUM,.01),1643.15));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,zero,zero,doubled.snapshot()));
        var packet=Map.of(GasType.PLASMA,1d);
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,packet,zero,snap));
    }

    @Test void onlyMeasuredCutoffReceiptCompensatesLocalMaterialClosure() {
        var accounting=new TegFixtureCombustionAccounting();
        accounting.observe(fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1d),1643.15));
        var committed=accounting.snapshot();var cleanup=Map.of(GasType.WATER_VAPOR,8e-8);
        var before=Map.<GasType,Double>of();var after=new java.util.EnumMap<GasType,Double>(GasType.class);
        after.putAll(committed.netSpeciesDelta());after.merge(GasType.WATER_VAPOR,-8e-8,Double::sum);
        assertDoesNotThrow(()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,Map.of(),Map.of(),committed,Map.of(),cleanup));
        var larger=new java.util.EnumMap<GasType,Double>(GasType.class);larger.putAll(after);larger.merge(GasType.WATER_VAPOR,-1e-5,Double::sum);
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,larger,Map.of(),Map.of(),committed,Map.of(),cleanup));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,Map.of(),Map.of(),committed));
    }

    @Test void cumulativeActualSplitReceiptsMayExceedSingleEventCutoffAndCloseExactly() {
        var accounting=new TegFixtureCombustionAccounting();
        accounting.observe(fire("tritium_fire",Map.of(GasType.TRITIUM,.01,GasType.OXYGEN,1d),1643.15));
        var committed=accounting.snapshot();
        double actualCleanup=0;
        for(int i=0;i<6;i++) {
            var receipt=new GasMixture(Map.of(GasType.WATER_VAPOR,1e-7),300).splitRatio(.25).discardedMoles();
            assertTrue(receipt.get(GasType.WATER_VAPOR)<GasMixture.GAS_MIN_MOLES*2);
            actualCleanup+=receipt.get(GasType.WATER_VAPOR);
        }
        assertTrue(actualCleanup>2*GasMixture.GAS_MIN_MOLES);
        var cleanup=Map.of(GasType.WATER_VAPOR,actualCleanup);
        var before=Map.<GasType,Double>of();
        var after=new java.util.EnumMap<GasType,Double>(GasType.class);
        after.putAll(committed.netSpeciesDelta());after.merge(GasType.WATER_VAPOR,-actualCleanup,Double::sum);
        assertDoesNotThrow(()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,Map.of(),Map.of(),committed,Map.of(),cleanup));
        var untracked=new java.util.EnumMap<GasType,Double>(after);
        untracked.merge(GasType.WATER_VAPOR,-1e-6,Double::sum);
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,untracked,Map.of(),Map.of(),committed,Map.of(),cleanup));
    }

    @Test void internalScrubberTransferAndExteriorDischargeRemainDistinctFromBurnerClosure() {
        var accounting=new TegFixtureCombustionAccounting();
        accounting.observe(fire("plasma_fire",Map.of(GasType.PLASMA,1d,GasType.OXYGEN,50d),1643.15));
        var committed=accounting.snapshot();
        // The burner loses a committed reaction packet plus residual fuel collected by the scrubber.
        var injected=Map.of(GasType.PLASMA,2d,GasType.OXYGEN,20d);
        var collected=Map.of(GasType.PLASMA,1d,GasType.OXYGEN,3d);
        var before=Map.of(GasType.PLASMA,10d,GasType.OXYGEN,100d);
        var after=new java.util.EnumMap<GasType,Double>(GasType.class);
        after.putAll(before);
        committed.netSpeciesDelta().forEach((gas,delta)->after.merge(gas,delta,Double::sum));
        injected.forEach((gas,amount)->after.merge(gas,amount,Double::sum));
        collected.forEach((gas,amount)->after.merge(gas,-amount,Double::sum));
        assertDoesNotThrow(()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,injected,collected,committed));

        // Internal collection is already represented by the extracted packet. Treating downstream
        // exterior discharge as another burner extraction double-counts it; omitting collection or
        // substituting the unequal vent packet also breaks actual local closure.
        var exteriorExport=Map.of(GasType.OXYGEN,1d,GasType.PLASMA,.2d);
        var doubleCounted=new java.util.EnumMap<GasType,Double>(GasType.class);
        doubleCounted.putAll(collected);exteriorExport.forEach((gas,amount)->doubleCounted.merge(gas,amount,Double::sum));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,injected,doubleCounted,committed));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,injected,Map.of(),committed));
        assertThrows(IllegalArgumentException.class,()->TegFixtureCombustionAccounting.verifyLocalClosure(before,after,injected,exteriorExport,committed));
    }

    @Test void measuredWriteAllowanceAdmitsOnlyRepresentationalResidualAndRejectsInvalidBudgets() {
        var chunk = new AtmosphereChunkData();
        var cell = new AtmosphereChunkData.CellPosition(1, 2, 3);
        var observer = chunk.observeWrites(java.util.Set.of(cell));
        var ambient = GasMixture.vacuum();
        for (double water : new double[]{0.1, 0.2, 0.3, 0.4, 0.5})
            chunk.put(1, 2, 3, new GasMixture(Map.of(GasType.WATER_VAPOR, water), 300), ambient);
        Map<GasType, Double> allowance = observer.snapshot().speciesBounds();
        observer.close();

        var committedAccounting = new TegFixtureCombustionAccounting();
        committedAccounting.observe(fire("tritium_fire", Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, 1d), 1643.15));
        var committed = committedAccounting.snapshot();
        double measured = allowance.get(GasType.WATER_VAPOR);
        double residual = measured * .75;
        var before = Map.<GasType, Double>of();
        var after = Map.of(GasType.WATER_VAPOR, committed.waterProduced() + residual,
                GasType.TRITIUM, committed.netSpeciesDelta().get(GasType.TRITIUM),
                GasType.OXYGEN, committed.netSpeciesDelta().get(GasType.OXYGEN));
        var zero = Map.<GasType, Double>of();
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, committed));
        assertDoesNotThrow(() -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, committed, allowance));

        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero,
                new TegFixtureCombustionAccounting().snapshot(), allowance));
        var doubled = new TegFixtureCombustionAccounting();
        doubled.observe(fire("tritium_fire", Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, 1d), 1643.15));
        doubled.observe(fire("tritium_fire", Map.of(GasType.TRITIUM, .01, GasType.OXYGEN, 1d), 1643.15));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, doubled.snapshot(), allowance));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, Map.of(GasType.TRITIUM, 1d), zero, committed, allowance));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, Map.of(GasType.OXYGEN, 1d), committed, allowance));

        for (Map<GasType, Double> invalid : java.util.Arrays.asList(null,
                Map.of(GasType.WATER_VAPOR, -1d), Map.of(GasType.WATER_VAPOR, Double.NaN),
                Map.of(GasType.WATER_VAPOR, Double.POSITIVE_INFINITY), Map.of(GasType.WATER_VAPOR, Double.NEGATIVE_INFINITY))) {
            assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, committed, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, committed,
                Map.of(GasType.WATER_VAPOR, 1000d / 2840000d)));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureCombustionAccounting.verifyLocalClosure(before, after, zero, zero, committed,
                Map.of(GasType.WATER_VAPOR, Math.nextDown(1000d / 2840000d), GasType.PLASMA, 1e-9)));
    }
}
