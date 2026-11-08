package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegThermodynamics;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TegFixtureEnergyBalanceTest {
    private static final TegFixtureEnergyBalance.ConversionModel MODEL = resolvedModel();
    private static TegFixtureEnergyBalance.ConversionModel resolvedModel(){var profile=TegContinuousFuelPlanTest.machineCatalog().get(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id("teg_center"));assertNotNull(profile);assertNotNull(profile.teg());return new TegFixtureEnergyBalance.ConversionModel(profile.teg().efficiency(),profile.teg().powerFactor());}

    @Test void coldRetentionIsStrictOnlyForSpaceBoundary() {
        assertFalse(TegFixtureEnergyBalance.retainsColdStock(true,1,0));
        assertTrue(TegFixtureEnergyBalance.retainsColdStock(true,.5,.5));
        assertTrue(TegFixtureEnergyBalance.retainsColdStock(false,152569.34,0));
        assertTrue(TegFixtureEnergyBalance.retainsColdStock(false,-10,0));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsColdStock(false,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsColdStock(true,0,-1));
    }
    private static TegFixtureEnergyBalance.HeatedStores stores(double carrier,double burner,double feedK) { return new TegFixtureEnergyBalance.HeatedStores(carrier,burner,feedK); }

    @Test void heatedStoresRequireIndependentRetentionWithinNumericalTolerances() {
        var before=stores(100,200,500);var tolerance=1e-6;
        assertTrue(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(101,201,501),0,0,0));
        assertFalse(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(101,199,500),0,0,0));
        assertFalse(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(100,200,499),0,0,.5));
        assertFalse(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(99,200,500),0,0,0));
        assertTrue(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(100-tolerance,200-tolerance,500-tolerance),tolerance,tolerance,tolerance));
        assertFalse(TegFixtureEnergyBalance.retainsHeatedStores(before,stores(100-2*tolerance,200,500),tolerance,tolerance,tolerance));
    }

    @Test void heatedStoresRejectNonfiniteOrNegativeValuesAndTolerances() {
        assertThrows(IllegalArgumentException.class,()->stores(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->stores(0,Double.POSITIVE_INFINITY,0));
        assertThrows(IllegalArgumentException.class,()->stores(0,0,-1));
        var valid=stores(0,0,0);
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsHeatedStores(valid,valid,Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsHeatedStores(valid,valid,0,Double.POSITIVE_INFINITY,0));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsHeatedStores(valid,valid,0,0,-1));
    }

    @Test void aggregateHotExcessAllowsRedistributionButRejectsColdMassMaskingDrawdown() {
        var hot=Map.of(GasType.OXYGEN,10d);
        var burner=Map.of(GasType.PLASMA,2d);
        double cp=10*GasType.OXYGEN.molarHeatCapacity()+2*GasType.PLASMA.molarHeatCapacity();
        var measured=TegFixtureEnergyBalance.hotThermalStock(cp*500,cp*500,hot,burner);
        assertEquals(cp*1000,measured.rawJoules(),1e-8);
        assertEquals(cp,measured.heatCapacityJoulesPerKelvin(),1e-8);
        assertEquals(cp*1000-cp*TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN,measured.excessJoules(),1e-8);
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.hotThermalStock(Double.NaN,0,hot,burner));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.hotThermalStock(-1,0,hot,burner));
        double before=TegFixtureEnergyBalance.hotExcessJoules(cp*500,cp*500,hot,burner);
        double redistributed=TegFixtureEnergyBalance.hotExcessJoules(cp*600,cp*400,hot,burner);
        assertEquals(before,redistributed,1e-9);
        var coldMass=Map.of(GasType.OXYGEN,1000d);
        double masked=TegFixtureEnergyBalance.hotExcessJoules(cp*500+20*293.15,cp*500, coldMass,burner);
        assertTrue((cp*500+20*293.15)+cp*500>cp*1000,"raw thermal stock rose");
        assertTrue(masked<before,"ambient-reference corrected stock declines despite cold added mass");
        assertTrue(TegFixtureEnergyBalance.retainsHotExcess(before,before,0));
        assertFalse(TegFixtureEnergyBalance.retainsHotExcess(before,before-1,0));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsHotExcess(before,before,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.retainsHotExcess(before,before,-1));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.hotExcessJoules(Double.NaN,0,hot,burner));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.hotExcessJoules(0,0,Map.of(GasType.OXYGEN,-1d),burner));
    }

    @Test void carrierWindowChecksSpeciesIsolationNotCumulativeEndpointRetention() {
        TegFixtureEnergyBalance.verifyCarrierSpeciesIsolation(Map.of(GasType.PLASMA,43.00018),Map.of(GasType.PLASMA,42.99966),GasType.PLASMA);
        for(var foreign:GasType.values())if(foreign!=GasType.PLASMA) {
            var contaminated=Map.of(GasType.PLASMA,43d,foreign,1e-9);
            assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.verifyCarrierSpeciesIsolation(Map.of(GasType.PLASMA,43d),contaminated,GasType.PLASMA));
        }
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.verifyCarrierSpeciesIsolation(Map.of(GasType.PLASMA,43d),Map.of(GasType.PLASMA,Double.NaN),GasType.PLASMA));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.verifyCarrierSpeciesIsolation(Map.of(GasType.PLASMA,43d),Map.of(GasType.PLASMA,0d),GasType.PLASMA));
    }
    private static TegFixtureEnergyBalance.Observation obs(double hr,double hp,double cr,double cp,double apc,double gen) {
        return new TegFixtureEnergyBalance.Observation(hr,hp,cr,cp,apc,gen,MODEL);
    }

    @Test void conversionExportAndColdInventoryCompleteBoundaryWithoutDoubleCountingOutputs() {
        var baseline=obs(631655.267578,778382.972656,2374515,0,1_000_000,0);
        var current=obs(631655.267578+715272.74,778382.972656,2396483.3,0,227840,73298.409);
        var result=TegFixtureEnergyBalance.compare(baseline,current);
        assertFalse(result.provesChemicalExcess());
        assertEquals(73298.409,result.generatedDelta(),1e-6);
        assertEquals(737241.04,result.thermalDelta(),1);
        assertEquals(3664.92045,result.modeledThermalSinkDelta(),1e-4);
        assertEquals(result.generatedDelta(),73298.409,1e-6); // actual electrical generation stays separately reported
    }

    @Test void heaterInputAloneAndNonpositiveChemicalExcessDoNotPassAndStrictMarginRemains() {
        var baseline=obs(0,0,0,0,1000,5);
        assertFalse(TegFixtureEnergyBalance.compare(baseline,obs(2000,0,0,0,0,5)).provesChemicalExcess());
        assertFalse(TegFixtureEnergyBalance.compare(baseline,obs(0,0,0,0,1000,1005)).provesChemicalExcess());
        var marginBase=obs(0,0,0,0,0,0);
        assertFalse(TegFixtureEnergyBalance.compare(marginBase,obs(1000,0,0,0,0,0)).provesChemicalExcess());
        assertTrue(TegFixtureEnergyBalance.compare(marginBase,obs(1000+Math.ulp(1000),0,0,0,0,0)).provesChemicalExcess());
    }

    @Test void thermalTransferBetweenHotAndColdInventoriesCancelsAndCounterResetFails() {
        var baseline=obs(100,200,300,400,1000,10);
        assertEquals(0,TegFixtureEnergyBalance.compare(baseline,obs(50,200,350,400,1000,10)).thermalDelta());
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.compare(baseline,obs(100,200,300,400,1000,9)));
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.compare(baseline,obs(100,200,300,400,1001,10)));
    }

    private static TegFixtureEnergyBalance.BoundaryObservation boundary(double thermal, double generated,
            double miner, double exported, double ambient, double space) {
        return new TegFixtureEnergyBalance.BoundaryObservation(thermal, generated, MODEL, miner, exported, ambient, space);
    }

    @Test void heaterOffBoundaryAccountsForMinerExportAndSignedAmbientAndSpace() {
        var zero = boundary(0, 0, 0, 0, 0, 0);
        var mined = TegFixtureEnergyBalance.compareBoundary(zero, boundary(100, 0, 100, 0, 0, 0));
        assertEquals(0, mined.inferredReactionHeatJoules());
        var warm = boundary(100, 0, 0, 0, 0, 0);
        var exported = TegFixtureEnergyBalance.compareBoundary(warm, boundary(0, 2000, 0, 0, 0, 0));
        assertEquals(0, exported.inferredReactionHeatJoules());
        assertEquals(0, TegFixtureEnergyBalance.compareBoundary(warm, boundary(80, 0, 0, 0, 20, 0)).inferredReactionHeatJoules());
        assertEquals(0, TegFixtureEnergyBalance.compareBoundary(warm, boundary(120, 0, 0, 0, -20, 0)).inferredReactionHeatJoules());
        assertEquals(0, TegFixtureEnergyBalance.compareBoundary(warm, boundary(70, 0, 0, 0, 0, 30)).inferredReactionHeatJoules());
        assertEquals(123, TegFixtureEnergyBalance.compareBoundary(zero, boundary(123, 0, 0, 0, 0, 0)).inferredReactionHeatJoules());
    }

    @Test void boundaryExportIsOnlyTheExteriorDischargeNotInternalScrubberTransfer() {
        var baseline=boundary(1000,0,0,0,0,0);
        // Internal collection changes neither the exterior receipt nor its boundary term.
        var collected=boundary(1000,0,0,0,0,0);
        assertEquals(0,TegFixtureEnergyBalance.compareBoundary(baseline,collected).inferredReactionHeatJoules());
        // Once the downstream vent discharges the collected gas, that exterior export is counted once.
        var discharged=boundary(900,0,0,100,0,0);
        assertEquals(0,TegFixtureEnergyBalance.compareBoundary(baseline,discharged).inferredReactionHeatJoules());
        var doubleCounted=boundary(900,0,0,200,0,0);
        assertEquals(100,TegFixtureEnergyBalance.compareBoundary(baseline,doubleCounted).inferredReactionHeatJoules());
        assertTrue(TegFixtureEnergyBalance.compareBoundary(baseline,doubleCounted).inferredReactionHeatJoules()
                > TegFixtureEnergyBalance.compareBoundary(baseline,discharged).inferredReactionHeatJoules());
    }

    @Test void continuousBoundaryRequiresStrictChemicalMaterialMargin() {
        var zero=boundary(0,0,0,0,0,0);
        var exactlyMargin=TegFixtureEnergyBalance.compareBoundary(zero,boundary(TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES,0,0,0,0,0));
        assertFalse(exactlyMargin.provesChemicalExcess());
        var aboveMargin=TegFixtureEnergyBalance.compareBoundary(zero,boundary(Math.nextUp(TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES),0,0,0,0,0));
        assertTrue(aboveMargin.provesChemicalExcess());
    }

    @Test void boundaryRejectsInvalidValuesAndCounterResets() {
        var base = boundary(10, 10, 10, 10, 0, 10);
        assertThrows(IllegalArgumentException.class, () -> boundary(Double.NaN, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> boundary(0, 0, 0, 0, 0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureEnergyBalance.compareBoundary(base, boundary(10, 9, 10, 10, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureEnergyBalance.compareBoundary(base, boundary(10, 10, 9, 10, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureEnergyBalance.compareBoundary(base, boundary(10, 10, 10, 9, 0, 10)));
        assertThrows(IllegalArgumentException.class, () -> TegFixtureEnergyBalance.compareBoundary(base, boundary(10, 10, 10, 10, 0, 9)));
    }

    @Test void modelUsesResolvedCoefficientsAndRejectsInvalidOrChangedBasis() {
        var model=new TegFixtureEnergyBalance.ConversionModel(.1,2);
        assertEquals(100,model.modeledThermalSinkForGeneration(2000));
        var alternate=new TegFixtureEnergyBalance.ConversionModel(.4,4);
        assertEquals(200,alternate.modeledThermalSinkForGeneration(2000));
        assertThrows(IllegalArgumentException.class,()->new TegFixtureEnergyBalance.ConversionModel(.1,0));
        assertThrows(IllegalArgumentException.class,()->new TegFixtureEnergyBalance.ConversionModel(Double.NaN,2));
        assertThrows(IllegalArgumentException.class,()->model.modeledThermalSinkForGeneration(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,()->model.modeledThermalSinkForGeneration(-1));
        var before=new TegFixtureEnergyBalance.Observation(0,0,0,0,10,0,model);
        var changed=new TegFixtureEnergyBalance.Observation(0,0,0,0,10,20,alternate);
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.compare(before,changed));
        assertEquals(.1,MODEL.efficiency(),0);assertEquals(2,MODEL.powerFactor(),0);
    }

    @Test void computedFiniteInputsRejectOverflowButAcceptLargeFiniteResults() {
        assertThrows(IllegalArgumentException.class,()->obs(Double.MAX_VALUE,Double.MAX_VALUE,0,0,0,0).thermalInventory());
        var zero=obs(0,0,0,0,0,0);
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.compare(zero,obs(Double.MAX_VALUE,0,0,0,0,Double.MAX_VALUE)));
        var large=TegFixtureEnergyBalance.compare(zero,obs(1e100,0,0,0,0,0));
        assertEquals(1e100,large.chemicalExcess());

        var base=boundary(0,0,0,0,0,0);
        var end=boundary(Double.MAX_VALUE,0,0,0,Double.MAX_VALUE,0);
        assertThrows(IllegalArgumentException.class,()->TegFixtureEnergyBalance.compareBoundary(base,end));
        var largeBoundary=TegFixtureEnergyBalance.compareBoundary(boundary(0,0,0,0,0,0),boundary(1e100,0,0,0,0,0));
        assertEquals(1e100,largeBoundary.inferredReactionHeatJoules());
    }

    @Test void conversionModelTracksActualKernelNetGasCoolingAcrossOrientationsAndProfiles() {
        for(var coefficients: java.util.List.of(new TegFixtureEnergyBalance.ConversionModel(.1,2),
                new TegFixtureEnergyBalance.ConversionModel(.4,4),MODEL)) {
            for(boolean reverse: new boolean[]{false,true}) for(double delta:new double[]{0.01,5,700}) {
                GasMixture hot=new GasMixture(Map.of(GasType.OXYGEN,10d),900);
                GasMixture cold=new GasMixture(Map.of(GasType.NITROGEN,10d),900-delta);
                var result=reverse?TegThermodynamics.generate(cold,hot,coefficients.efficiency(),coefficients.powerFactor()):TegThermodynamics.generate(hot,cold,coefficients.efficiency(),coefficients.powerFactor());
                double actualSink=result.hotThermalEnergyRemovedJoules()-result.coldThermalEnergyAddedJoules();
                double modeled=coefficients.modeledThermalSinkForGeneration(result.generatedEnergyJoules());
                assertEquals(modeled,actualSink,Math.ulp((float)hot.thermalEnergy())*4.0);
            }
        }
    }

    @Test void heaterOffCoolantCapacityOnlyEstimateExceedsObservedExcessDrawdown() {
        double ta=651.6744384765625d,tb=220.25274658203125d,dt=.6d;
        double cpA=1043.1015625d,cpB=129.071395874d;
        var hot=new GasMixture(Map.of(GasType.NITROGEN,cpA/GasType.NITROGEN.molarHeatCapacity()),ta);
        // Plasma is only a convenient carrier for the observed heat capacity, not a replay of the world packet.
        var cold=new GasMixture(Map.of(GasType.PLASMA,cpB/GasType.PLASMA.molarHeatCapacity()),tb);
        var oldResult=TegThermodynamics.generate(hot,cold,MODEL.efficiency(),MODEL.powerFactor());
        var reducedCold=cold.splitRatio(TegContinuousOperatingPlan.HEATER_OFF_COLD_COOLANT_FRACTION).removed();
        var newResult=TegThermodynamics.generate(hot,reducedCold,MODEL.efficiency(),MODEL.powerFactor());
        double savedWatts=(oldResult.hotThermalEnergyRemovedJoules()-newResult.hotThermalEnergyRemovedJoules())/dt;
        double newGrossWatts=newResult.generatedEnergyJoules()/dt;
        var pump=TegContinuousFuelPlanTest.machineCatalog().get(
                com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id("gas_volume_pump"));
        double nominal=AtmosMachineSettings.defaults(pump).transferRateM3PerSecond().orElseThrow()
                /TegContinuousCoolingPlan.RADIATOR_COUNT;
        var defaultValues=AtmosMachineSettings.defaults(pump);
        var running=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,pump,
                defaultValues,TegContinuousOperatingPlan.Stage.RUNNING).finalValues();
        var off=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,pump,
                running,TegContinuousOperatingPlan.Stage.HEATER_OFF).finalValues();
        assertEquals(.04d,nominal,1e-12);
        assertEquals(.04d,running.transferRateM3PerSecond().orElseThrow(),1e-12);
        assertEquals(.028d,off.transferRateM3PerSecond().orElseThrow(),1e-12);
        assertEquals(TegContinuousOperatingPlan.HEATER_OFF_COLD_COOLANT_FRACTION,
                off.transferRateM3PerSecond().orElseThrow()/nominal,1e-12);
        assertTrue(savedWatts>15428.5857169d/60d,"capacity-only saving must exceed measured 257.143 W drawdown");
        assertTrue(newGrossWatts>1725d+1000d,"modeled gross generation remains above loads plus margin; not APC net");
        System.out.printf(java.util.Locale.ROOT,
                "CAPACITY_ONLY_ESTIMATE_NOT_NATIVE oldHotWithdrawW=%.6f newHotWithdrawW=%.6f savingW=%.6f newModeledGrossW=%.6f nominalColdM3s=%.5f heaterOffColdM3s=%.5f%n",
                oldResult.hotThermalEnergyRemovedJoules()/dt,newResult.hotThermalEnergyRemovedJoules()/dt,
                savedWatts,newGrossWatts,nominal,off.transferRateM3PerSecond().orElseThrow());
    }

    @Test void capturedHotExcessPacketCapacitySweepIsBoundedCounterfactual() {
        double ta=689.9857788085938d,tb=217.18441772460938d,dt=.6d;
        double cpA=1180.9464111328125d,cpB=97.87383270263672d;
        var hot=new GasMixture(Map.of(GasType.NITROGEN,cpA/GasType.NITROGEN.molarHeatCapacity()),ta);
        var cold=new GasMixture(Map.of(GasType.PLASMA,cpB/GasType.PLASMA.molarHeatCapacity()),tb);
        var replay=TegThermodynamics.generate(hot,cold,MODEL.efficiency(),MODEL.powerFactor());
        assertEquals(42733.257687722515d,replay.maximumWorkJoules(),Math.ulp((float)replay.maximumWorkJoules())*4d);
        assertEquals(5030.052461281408d,replay.generatedEnergyJoules(),Math.ulp((float)replay.generatedEnergyJoules())*4d);
        assertEquals(replay.generatedEnergyJoules()/MODEL.powerFactor(),replay.hotThermalEnergyRemovedJoules(),Math.ulp((float)hot.thermalEnergy())*2d);
        assertEquals(MODEL.modeledThermalSinkForGeneration(replay.generatedEnergyJoules()),
                replay.hotThermalEnergyRemovedJoules()-replay.coldThermalEnergyAddedJoules(),
                Math.ulp((float)hot.thermalEnergy())*2d);
        double currentHotW=replay.hotThermalEnergyRemovedJoules()/dt;
        for(double flow:new double[]{.030d,.029d,.028d,.027d}) {
            double ratio=flow/.030d;
            var adjustedCold=new GasMixture(Map.of(GasType.PLASMA,(cpB*ratio)/GasType.PLASMA.molarHeatCapacity()),tb);
            var result=TegThermodynamics.generate(hot,adjustedCold,MODEL.efficiency(),MODEL.powerFactor());
            double hotW=result.hotThermalEnergyRemovedJoules()/dt;
            double savingJ=(currentHotW-hotW)*60d;
            double coldAddedW=result.coldThermalEnergyAddedJoules()/dt;
            double conditionalNet=4034.2172668087255d-.9d*(8383.420769d-result.generatedEnergyJoules()/dt);
            var pump=TegContinuousFuelPlanTest.machineCatalog().get(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id("gas_volume_pump"));
            var defaults=AtmosMachineSettings.defaults(pump);
            var configured=AtmosMachineSettings.apply(pump,defaults,AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(true));
            configured=AtmosMachineSettings.apply(pump,configured,AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND,new AtmosMachineSettings.NumericValue(flow));
            assertEquals(flow,configured.transferRateM3PerSecond().orElseThrow(),0);
            assertTrue(configured.enabled().orElseThrow()); assertEquals(200d,pump.power().watts(),0);
            assertTrue(flow>=0&&flow<=pump.pump().maxTransferRate().orElseThrow()/1000d);
            System.out.printf(java.util.Locale.ROOT,"BOUNDED_CAPACITY_COUNTERFACTUAL flow=%.3f ratio=%.6f hotWithdrawalW=%.6f generationW=%.6f deltaHotW=%.6f coldAddedW=%.6f saved60sJ=%.6f conditionalNetHeadroomW=%.6f assumptions=fixed_packet_density_cadence_temperatures_native_feedback_unverified%n",flow,ratio,hotW,result.generatedEnergyJoules()/dt,hotW-currentHotW,coldAddedW,savingJ,conditionalNet);
        }
        var pump=TegContinuousFuelPlanTest.machineCatalog().get(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id("gas_volume_pump"));
        assertEquals(.2d,AtmosMachineSettings.defaults(pump).transferRateM3PerSecond().orElseThrow(),1e-12);
        assertEquals(200d,pump.power().watts(),0);
        var baseline=TegFixtureEnergyBalance.hotThermalStock(1394683.8017578125d,0,Map.of(GasType.NITROGEN,1887.4625126872634d/GasType.NITROGEN.molarHeatCapacity()),Map.of());
        var end=TegFixtureEnergyBalance.hotThermalStock(1437537.6162109375d,0,Map.of(GasType.NITROGEN,2034.5644500368508d/GasType.NITROGEN.molarHeatCapacity()),Map.of());
        double observed=end.excessJoules()-baseline.excessJoules();
        double accountedRaw=106837.56121826172d+179329.7613436258d-465796.38979460765d/2d-(22486.88348388672d-12070.629455566406d);
        double accounted=accountedRaw-(2034.5644500368508d-1887.4625126872634d)*TegFixtureEnergyBalance.INLET_REFERENCE_KELVIN;
        assertTrue(Math.abs(observed-accounted)<2d);
        System.out.printf(java.util.Locale.ROOT,"WINDOW_HOT_STOCK observedExcessDeltaJ=%.12f accountedExcessJ=%.12f rawAccountedJ=%.12f residualJ=%.12f budgetJ=2.0 netHeadroomObservedW=4034.217267 heaterDemandAlreadyIncludedW=1725%n",observed,accounted,accountedRaw,observed-accounted);
        System.out.printf(java.util.Locale.ROOT,"CAPTURED_KERNEL_REPLAY wmax=%.12f efficiency=%.12f generatedJ=%.12f hotRemovedJ=%.12f coldAddedJ=%.12f hotOnlyResidualJ=%.9f netSinkResidualJ=%.9f%n",replay.maximumWorkJoules(),replay.effectiveEfficiency(),replay.generatedEnergyJoules(),replay.hotThermalEnergyRemovedJoules(),replay.coldThermalEnergyAddedJoules(),replay.hotThermalEnergyRemovedJoules()-replay.generatedEnergyJoules()/MODEL.powerFactor(),replay.hotThermalEnergyRemovedJoules()-replay.coldThermalEnergyAddedJoules()-MODEL.modeledThermalSinkForGeneration(replay.generatedEnergyJoules()));
    }
}
