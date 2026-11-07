package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionEvaluator;
import com.juicyslew.moonstation14.ms14.atmos.reaction.GasReactionData;
import com.juicyslew.moonstation14.ms14.atmos.reaction.BundledGasReactionCatalog;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationPrototypeTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TegContinuousOperatingPlanTest {
    @Test void pumpRosterValidationUsesTypedCarrierAndColdReturnPositions() {
        var catalog=TegContinuousFuelPlanTest.machineCatalog();
        var pressure=catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        var volume=catalog.get(ModAtmosMachines.id("gas_volume_pump"));
        var layout=TegContinuousFuelPlan.layout(net.minecraft.core.BlockPos.ZERO);
        var carriers=TegContinuousOperatingPlan.carrierPumpPositions(layout.fueled());
        var returned=layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP);
        var correct=java.util.Map.of(carriers.get(0),pressure,carriers.get(1),volume,returned,pressure);
        assertDoesNotThrow(()->TegContinuousOperatingPlan.validatePumpRoster(layout,correct));
        assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.validatePumpRoster(layout,
                java.util.Map.of(carriers.get(0),pressure,carriers.get(1),volume)));
        assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.validatePumpRoster(layout,
                java.util.Map.of(carriers.get(0),pressure,carriers.get(1),volume,carriers.get(0).relative(net.minecraft.core.Direction.UP),pressure)));
        assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.validatePumpRoster(layout,
                java.util.Map.of(carriers.get(0),volume,carriers.get(1),volume,returned,pressure)));
        assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.validatePumpRoster(layout,
                java.util.Map.of(carriers.get(0),pressure,carriers.get(1),pressure,returned,volume)));
    }

    @Test void nativeFixtureAssertionChecksRealPlansAndRejectsStaleCarrierAndReturnSettingsReadOnly() {
        var catalog=TegContinuousFuelPlanTest.machineCatalog();
        var volume=catalog.get(ModAtmosMachines.id("gas_volume_pump"));
        var pressure=catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        for(var role:java.util.List.of(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP))
            for(var stage:java.util.List.of(TegContinuousOperatingPlan.Stage.COLD_PREPARATION,TegContinuousOperatingPlan.Stage.STARTUP,
                    TegContinuousOperatingPlan.Stage.IGNITION,TegContinuousOperatingPlan.Stage.RUNNING,TegContinuousOperatingPlan.Stage.HEATER_OFF)) {
                var input=AtmosMachineSettings.defaults(volume);
                var correct=TegContinuousOperatingPlan.create(role,volume,input,stage).finalValues();
                TegContinuousInitializationGameTests.assertPlannedSettings(volume,input,correct,role,stage,"JVM carrier fixture");
                var badEnabled=new AtmosMachineSettings.Values(correct.revision(),java.util.Optional.of(!correct.enabled().orElseThrow()),
                        correct.targetPressureKpa(),correct.transferRateM3PerSecond(),correct.mixerFractionA(),correct.filterGas(),correct.thermostatSetpointK(),correct.valveOpen());
                assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->TegContinuousInitializationGameTests.assertPlannedSettings(volume,input,badEnabled,role,stage,"negative enable"));
                var badRate=new AtmosMachineSettings.Values(correct.revision(),correct.enabled(),correct.targetPressureKpa(),java.util.Optional.of(.19d),
                        correct.mixerFractionA(),correct.filterGas(),correct.thermostatSetpointK(),correct.valveOpen());
                assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->TegContinuousInitializationGameTests.assertPlannedSettings(volume,input,badRate,role,stage,"negative rate"));
                assertEquals(0,input.revision());
            }
        var input=AtmosMachineSettings.defaults(pressure);
        var hot=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,pressure,input,TegContinuousOperatingPlan.Stage.STARTUP).finalValues();
        assertEquals(pressure.pump().maxPressureKpa(),hot.targetPressureKpa());
        assertEquals(java.util.Optional.of(true),hot.enabled());
        var startup=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,pressure,input,TegContinuousOperatingPlan.Stage.STARTUP).finalValues();
        TegContinuousInitializationGameTests.assertPlannedSettings(pressure,input,startup,TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,TegContinuousOperatingPlan.Stage.STARTUP,"typed cold return");
        var badReturn=new AtmosMachineSettings.Values(startup.revision(),java.util.Optional.of(true),startup.targetPressureKpa(),startup.transferRateM3PerSecond(),startup.mixerFractionA(),startup.filterGas(),startup.thermostatSetpointK(),startup.valveOpen());
        assertThrows(net.minecraft.gametest.framework.GameTestAssertException.class,()->TegContinuousInitializationGameTests.assertPlannedSettings(pressure,input,badReturn,TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,TegContinuousOperatingPlan.Stage.STARTUP,"negative cold return"));
        assertThrows(IllegalArgumentException.class,()->TegContinuousInitializationGameTests.assertPlannedSettings(pressure,input,startup,TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,TegContinuousOperatingPlan.Stage.STARTUP,"wrong role/profile"));
        assertEquals(0,input.revision());
    }
    @Test void measuredBalanceRecipeKeepsPlasmaAboveThePreliminaryLimiterBalance() {
        double oxygenFraction = TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION;
        assertEquals(13d / 15d, oxygenFraction, 0d);
        assertEquals(6.5d / 7.5d, oxygenFraction, 0d);
        assertTrue(oxygenFraction >= 0d && oxygenFraction <= 1d);
        double plasmaFraction = 1d - oxygenFraction;
        assertTrue(plasmaFraction > oxygenFraction / 10d,
                "the ordinary recipe increases plasma availability; this does not claim limiter-optimal operation");
        // Retain the original preliminary-limiter arithmetic as evaluator characterization, not current recipe selection.
        assertEquals(1d / 11d, (10d / 11d) / 10d, 1e-16);
        assertEquals(1d / 11d, 1d - 10d / 11d, 1e-16);
    }

    @Test void retainedOxygenRichRecipeProducesMoreActualPlasmaFireHeatThanPlasmaRichFeed() {
        var catalog = BundledGasReactionCatalog.load();
        assertNotNull(catalog.get(net.minecraft.resources.ResourceLocation.parse("moonstation14:plasma_fire")));
        var retained = new GasMixture(java.util.Map.of(GasType.OXYGEN, 90d, GasType.PLASMA, 10d), 550, 1);
        var plasmaRich = new GasMixture(java.util.Map.of(GasType.OXYGEN, 100d / 3d, GasType.PLASMA, 200d / 3d), 550, 1);
        assertEquals(100d, retained.totalMoles(), 1e-4);
        assertEquals(retained.totalMoles(), plasmaRich.totalMoles(), 1e-4);
        assertEquals(retained.volumeCubicMeters(), plasmaRich.volumeCubicMeters());
        assertEquals(550d, retained.temperatureKelvin());

        var retainedResult = GasReactionEvaluator.evaluate(catalog, retained,
                effect -> effect == GasReactionData.EffectType.PLASMA_FIRE);
        var plasmaRichResult = GasReactionEvaluator.evaluate(catalog, plasmaRich,
                effect -> effect == GasReactionData.EffectType.PLASMA_FIRE);
        double retainedPlasma = -retainedResult.speciesDelta().getOrDefault(GasType.PLASMA, 0d);
        double plasmaRichPlasma = -plasmaRichResult.speciesDelta().getOrDefault(GasType.PLASMA, 0d);
        assertTrue(retainedPlasma > 0 && plasmaRichPlasma > 0);
        double retainedHeat = 160_000d * retainedPlasma;
        double plasmaRichHeat = 160_000d * plasmaRichPlasma;
        assertEquals(retainedHeat, retainedResult.energyDeltaJoules(), Math.max(1e-3, retainedHeat * 2e-6));
        assertEquals(plasmaRichHeat, plasmaRichResult.energyDeltaJoules(), Math.max(1e-3, plasmaRichHeat * 2e-6));
        assertEquals(2.7d, retainedHeat / plasmaRichHeat, 2e-5);
        assertEquals(3_800d, retained.heatCapacity(), .1d);
        assertEquals(14_000d, plasmaRich.heatCapacity(), .2d);
        assertEquals(retainedResult.mixture().thermalEnergy() - retained.thermalEnergy(), retainedResult.energyDeltaJoules(), 1d);
    }

    @Test void activeRecipeAtBurnerTemperaturesUsesRealEvaluatorStoichiometryAndHasOxygenSurplus() {
        var catalog = BundledGasReactionCatalog.load();
        var recipe = new GasMixture(java.util.Map.of(GasType.OXYGEN, 6.5d, GasType.PLASMA, 1d), 760, 1);
        var result = GasReactionEvaluator.evaluate(catalog, recipe,
                effect -> effect == GasReactionData.EffectType.PLASMA_FIRE);
        double plasmaBurned = -result.speciesDelta().getOrDefault(GasType.PLASMA, 0d);
        double oxygenBurned = -result.speciesDelta().getOrDefault(GasType.OXYGEN, 0d);
        assertTrue(plasmaBurned > 0);
        assertTrue(oxygenBurned > 0);
        assertTrue(oxygenBurned < 6.5d, "reaction leaves oxygen surplus at the selected ordinary fixture recipe");
        assertTrue(plasmaBurned <= 1d);
        assertEquals(160_000d * plasmaBurned, result.energyDeltaJoules(), Math.max(1e-3, plasmaBurned * .32));
    }

    @Test void plansUseResolvedComponentsDefaultsAndOrderedReducerRevisions() {
        var catalog = TegContinuousFuelPlanTest.machineCatalog();
        var core = catalog.get(ModAtmosMachines.id("teg_center"));
        var coreInitial = AtmosMachineSettings.defaults(core);
        var corePlan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.CORE, core, coreInitial);
        assertEquals(0, corePlan.expectedInitialRevision());
        assertEquals(0, corePlan.finalValues().revision());
        assertTrue(corePlan.requests().isEmpty());
        assertEquals(java.util.Optional.of(true), corePlan.finalValues().enabled());
        assertEquals(0, coreInitial.revision());

        var pump = catalog.get(ModAtmosMachines.id("gas_volume_pump"));
        var pumpInitial = AtmosMachineSettings.defaults(pump);
          var hotPump = catalog.get(ModAtmosMachines.id("gas_pressure_pump"));
        var hotInitial = AtmosMachineSettings.defaults(hotPump);
         var layout=TegContinuousFuelPlan.layout(net.minecraft.core.BlockPos.ZERO);
          var fueled=layout.fueled();
          var carrierPositions=TegContinuousOperatingPlan.carrierPumpPositions(fueled);
          var hotBody=carrierPositions.get(0);
          var coldBody=carrierPositions.get(1);
          assertEquals(2,carrierPositions.size());
         assertEquals(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,TegContinuousOperatingPlan.pumpRole(fueled,hotBody));
         assertEquals(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,TegContinuousOperatingPlan.pumpRole(fueled,coldBody));
         assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.pumpRole(fueled,fueled.core()));
         assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.pumpRole(fueled,coldBody.relative(net.minecraft.core.Direction.EAST)));
        var pumpPlan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP, hotPump, hotInitial);
           assertEquals(hotPump.pump().maxPressureKpa(), pumpPlan.finalValues().targetPressureKpa());
          assertEquals(2, pumpPlan.finalValues().revision());
        assertEquals(java.util.Optional.of(true),pumpPlan.finalValues().enabled());
           assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED,AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA), pumpPlan.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
          var coldPumpPlan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP, pump, pumpInitial);
         assertEquals(java.util.Optional.of(.04d), coldPumpPlan.finalValues().transferRateM3PerSecond(),
                 "the resolved volume-pump profile is shared; cold role owns its 0.04 m3/s radiator rate");
        var runningPump = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP, hotPump, hotInitial,
                TegContinuousOperatingPlan.Stage.RUNNING);
        var ignitionPump = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP, pump, pumpInitial,
                TegContinuousOperatingPlan.Stage.IGNITION);
        var startupCold=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,pump,pumpInitial,TegContinuousOperatingPlan.Stage.STARTUP);
        assertEquals(java.util.Optional.of(false),startupCold.finalValues().enabled(),"cold pump flow is off only during STARTUP");
           assertEquals(java.util.Optional.of(true), ignitionPump.finalValues().enabled(), "cold circulation is restored for ignition");
        assertEquals(java.util.Optional.of(true), runningPump.finalValues().enabled());
             assertEquals(hotPump.pump().maxPressureKpa(), runningPump.finalValues().targetPressureKpa());
             assertEquals(2, runningPump.finalValues().revision());
             assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED,AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA),
                 runningPump.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
        assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP, hotPump, runningPump.finalValues(),
                TegContinuousOperatingPlan.Stage.RUNNING).requests().isEmpty());
        assertEquals(java.util.Optional.of(true),pumpPlan.finalValues().enabled());

        var heater = catalog.get(ModAtmosMachines.id("gas_thermo_heater"));
        var heaterPlan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER, heater, AtmosMachineSettings.defaults(heater));
        assertEquals(java.util.Optional.of(heater.thermostat().maximumTemperatureK()), heaterPlan.finalValues().thermostatSetpointK());
        assertEquals(2, heaterPlan.finalValues().revision());
        assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED, AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K),
                heaterPlan.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());

        var mixer = catalog.get(ModAtmosMachines.id("gas_mixer"));
        var mixerPlan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer, AtmosMachineSettings.defaults(mixer));
        assertEquals(java.util.Optional.of(12d), mixerPlan.finalValues().targetPressureKpa());
        assertEquals(java.util.Optional.of(TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION), mixerPlan.finalValues().mixerFractionA());
        assertEquals(1d-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION, mixerPlan.finalValues().mixerFractionB(), 1e-12);
        assertEquals(3, mixerPlan.finalValues().revision());
        assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED, AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                        AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A),
                mixerPlan.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
    }

    @Test void everyRoleIsIdempotentAndPartiallyChangedValuesOnlyRequestDifferences() {
        var catalog = TegContinuousFuelPlanTest.machineCatalog();
        var cases = java.util.List.of(
                new Object[]{TegContinuousOperatingPlan.Role.CORE, "teg_center"},
                new Object[]{TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP, "gas_pressure_pump"},
                new Object[]{TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP, "gas_volume_pump"},
                new Object[]{TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP, "gas_pressure_pump"},
                new Object[]{TegContinuousOperatingPlan.Role.IGNITION_HEATER, "gas_thermo_heater"},
                new Object[]{TegContinuousOperatingPlan.Role.MIXER, "gas_mixer"},
                new Object[]{TegContinuousOperatingPlan.Role.PASSIVE, "passive_heat_exchanger"});
        for (var item : cases) {
            var role = (TegContinuousOperatingPlan.Role) item[0];
            var profile = catalog.get(ModAtmosMachines.id((String) item[1]));
            var first = TegContinuousOperatingPlan.create(role, profile, AtmosMachineSettings.defaults(profile));
            var second = TegContinuousOperatingPlan.create(role, profile, first.finalValues());
            assertTrue(second.requests().isEmpty(), role.toString());
            assertEquals(first.finalValues(), second.finalValues(), role.toString());
            assertEquals(first.finalValues().revision(), second.finalValues().revision(), role.toString());
            if (role == TegContinuousOperatingPlan.Role.IGNITION_HEATER)
                assertFalse(second.requests().stream().anyMatch(r -> r.operation() == AtmosMachineSettings.Operation.SET_ENABLED));
        }
        var heater = catalog.get(ModAtmosMachines.id("gas_thermo_heater"));
        var defaults = AtmosMachineSettings.defaults(heater);
        var changed = new AtmosMachineSettings.Values(7, defaults.enabled(), defaults.targetPressureKpa(), defaults.transferRateM3PerSecond(),
                defaults.mixerFractionA(), defaults.filterGas(), java.util.Optional.of(heater.thermostat().maximumTemperatureK()), defaults.valveOpen());
        var partial = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER, heater, changed);
        assertEquals(1, partial.requests().size());
        assertEquals(AtmosMachineSettings.Operation.SET_ENABLED, partial.requests().getFirst().operation());
        assertEquals(8, partial.finalValues().revision());
    }

    @Test void coldPumpFlowFollowsColdPreparationThenPausesForStartupAndWarmupUsesExistingBudget() {
        var machine=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_volume_pump"));
        var defaults=AtmosMachineSettings.defaults(machine);
        var runningCold=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,defaults,TegContinuousOperatingPlan.Stage.RUNNING);
        var heaterOffCold=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,defaults,TegContinuousOperatingPlan.Stage.HEATER_OFF);
         var pressure=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_pressure_pump"));
         var pressureDefaults=AtmosMachineSettings.defaults(pressure);
           var heaterOffHot=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,pressure,pressureDefaults,TegContinuousOperatingPlan.Stage.HEATER_OFF);
        assertTrue(TegContinuousFixtureService.pumpSettingsMatch(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,heaterOffCold.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF));
         assertFalse(TegContinuousFixtureService.pumpSettingsMatch(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,runningCold.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF));
        assertFalse(TegContinuousFixtureService.pumpSettingsMatch(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,heaterOffHot.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF));
          assertFalse(TegContinuousFixtureService.pumpSettingsMatch(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,machine,heaterOffCold.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF));
         for(var role:java.util.List.of(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP))
            for(var stage:TegContinuousOperatingPlan.Stage.values()) {
                 var plan=TegContinuousOperatingPlan.create(role,machine,defaults,stage);
                   boolean flow=stage==TegContinuousOperatingPlan.Stage.COLD_PREPARATION || stage==TegContinuousOperatingPlan.Stage.IGNITION || stage==TegContinuousOperatingPlan.Stage.RUNNING || stage==TegContinuousOperatingPlan.Stage.HEATER_OFF;
                assertEquals(java.util.Optional.of(flow),plan.finalValues().enabled(),role+" "+stage);
                 var nominalRate=.2d/TegContinuousCoolingPlan.RADIATOR_COUNT;
                 var expectedRate=java.util.Optional.of(stage==TegContinuousOperatingPlan.Stage.HEATER_OFF
                         ? nominalRate*TegContinuousOperatingPlan.HEATER_OFF_COLD_COOLANT_FRACTION : nominalRate);
                assertEquals(expectedRate,plan.finalValues().transferRateM3PerSecond());
                assertEquals(200,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState.apcDemand(machine,false),"disabled flow retains ordinary paid receiver demand");
                assertTrue(TegContinuousOperatingPlan.create(role,machine,plan.finalValues(),stage).requests().isEmpty(),role+" "+stage+" idempotence");
            }
        var coldOff=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,defaults,TegContinuousOperatingPlan.Stage.HEATER_OFF);
         var hotOff=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,pressure,pressureDefaults,TegContinuousOperatingPlan.Stage.HEATER_OFF);
          assertEquals(.028d,coldOff.finalValues().transferRateM3PerSecond().orElseThrow(),1e-12);
           assertEquals(pressure.pump().maxPressureKpa(),hotOff.finalValues().targetPressureKpa());
        assertEquals(200,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState.apcDemand(machine,false));
        assertEquals(java.util.Optional.of(.04d),TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,defaults,TegContinuousOperatingPlan.Stage.RUNNING).finalValues().transferRateM3PerSecond());
        assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,coldOff.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF).requests().isEmpty());
        var appliedColdOff=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,runningCold.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF);
        assertEquals(java.util.Optional.of(true),runningCold.finalValues().enabled());
        assertEquals(java.util.Optional.of(true),appliedColdOff.finalValues().enabled());
          assertEquals(.028d,appliedColdOff.finalValues().transferRateM3PerSecond().orElseThrow(),1e-12);
        assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP,machine,appliedColdOff.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF).requests().isEmpty());
        var heater=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_thermo_heater"));
        var runningHeater=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER,heater,AtmosMachineSettings.defaults(heater),TegContinuousOperatingPlan.Stage.RUNNING);
        var heaterOff=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER,heater,runningHeater.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF);
        assertEquals(java.util.Optional.of(false),heaterOff.finalValues().enabled());
        assertEquals(java.util.Optional.of(heater.thermostat().maximumTemperatureK()),heaterOff.finalValues().thermostatSetpointK());
        assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED),heaterOff.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
        assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.IGNITION_HEATER,heater,heaterOff.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF).requests().isEmpty());
        assertEquals(TegContinuousFixtureService.WarmupDecision.WAIT,TegContinuousFixtureService.warmupAdmission(40,1039,true));
        assertEquals(TegContinuousFixtureService.WarmupDecision.ADMIT,TegContinuousFixtureService.warmupAdmission(40,1040,true));
        assertEquals(TegContinuousFixtureService.WarmupDecision.EXPIRED,TegContinuousFixtureService.warmupAdmission(40,1040,false));
        assertEquals(TegContinuousFixtureService.WarmupDecision.EXPIRED,TegContinuousFixtureService.warmupAdmission(40,1041,true));
    }

    @Test void coldPreparationAndIgnitionKeepColdCarrierCirculating() {
        var machine=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_volume_pump"));
        var role=TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP;
        var defaults=AtmosMachineSettings.defaults(machine);
        var coldPrep=TegContinuousOperatingPlan.create(role,machine,defaults,TegContinuousOperatingPlan.Stage.COLD_PREPARATION);
        assertEquals(java.util.Optional.of(true),coldPrep.finalValues().enabled());
        assertEquals(java.util.Optional.of(.04d),coldPrep.finalValues().transferRateM3PerSecond());
        assertEquals(200d,machine.power().watts());
        var startup=TegContinuousOperatingPlan.create(role,machine,coldPrep.finalValues(),TegContinuousOperatingPlan.Stage.STARTUP);
        assertEquals(java.util.Optional.of(false),startup.finalValues().enabled());
        assertEquals(java.util.Optional.of(.04d),startup.finalValues().transferRateM3PerSecond());
        var ignition=TegContinuousOperatingPlan.create(role,machine,startup.finalValues(),TegContinuousOperatingPlan.Stage.IGNITION);
        assertEquals(java.util.Optional.of(true),ignition.finalValues().enabled());
        assertEquals(java.util.Optional.of(.04d),ignition.finalValues().transferRateM3PerSecond());
    }

    @Test void coldReturnPressurePumpUsesResolvedMaximumAndStageFlowGate() {
        var pump=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_pressure_pump"));
        var defaults=AtmosMachineSettings.defaults(pump);
        assertEquals(200,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState.apcDemand(pump,false));
        for(var stage:TegContinuousOperatingPlan.Stage.values()) {
            var plan=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,pump,defaults,stage);
            assertEquals(pump.pump().maxPressureKpa(),plan.finalValues().targetPressureKpa());
            assertEquals(java.util.Optional.of(stage==TegContinuousOperatingPlan.Stage.COLD_PREPARATION || stage==TegContinuousOperatingPlan.Stage.IGNITION || stage==TegContinuousOperatingPlan.Stage.RUNNING || stage==TegContinuousOperatingPlan.Stage.HEATER_OFF),plan.finalValues().enabled());
            assertEquals(200,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePowerState.apcDemand(pump,false));
            assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP,pump,plan.finalValues(),stage).requests().isEmpty());
        }
    }

    @Test void hotPressureCarrierUsesResolvedPressurePumpCompositionAndMaximumTarget() {
        var profile=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_pressure_pump"));
        var plan=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,profile,
                AtmosMachineSettings.defaults(profile),TegContinuousOperatingPlan.Stage.HEATER_OFF);
        assertEquals(4500d,profile.pump().maxPressureKpa().orElseThrow());
        assertEquals(profile.pump().maxPressureKpa(),plan.finalValues().targetPressureKpa());
        assertEquals(java.util.Optional.of(true),plan.finalValues().enabled());
        assertEquals(200d,profile.power().watts());
    }

    private static void assertConserved(GasMixture before, java.util.List<GasMixture> after) {
        for(GasType gas:GasType.values()) {
            double sum=after.stream().mapToDouble(m->m.moles(gas)).sum();
            double bound=after.stream().mapToDouble(m->8*Math.ulp((float)m.moles(gas))).sum()+8*Math.ulp((float)before.moles(gas));
            assertTrue(Math.abs(before.moles(gas)-sum)<=bound,"species "+gas+" discrepancy="+(before.moles(gas)-sum)+" bound="+bound);
        }
        double energy=after.stream().mapToDouble(GasMixture::thermalEnergy).sum();
        double energyBound=after.stream().mapToDouble(m->8*Math.ulp((float)m.thermalEnergy())).sum()+8*Math.ulp((float)before.thermalEnergy());
        assertTrue(Math.abs(before.thermalEnergy()-energy)<=energyBound,"thermal energy");
    }

    @Test void hotPressureCarrierUsesResolvedMaximumAndStageGate() {
        var pressure=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_pressure_pump"));
        var defaults=AtmosMachineSettings.defaults(pressure);
        for(var stage:TegContinuousOperatingPlan.Stage.values()) {
            var plan=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,pressure,defaults,stage);
            assertEquals(java.util.Optional.of(stage!=TegContinuousOperatingPlan.Stage.COLD_PREPARATION),plan.finalValues().enabled());
            assertEquals(pressure.pump().maxPressureKpa(),plan.finalValues().targetPressureKpa());
            assertEquals(200d,pressure.power().watts());
            var repeated=TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,pressure,plan.finalValues(),stage);
            assertTrue(repeated.requests().isEmpty());
            assertEquals(plan.finalValues().revision(),repeated.finalValues().revision());
        }
        var wrong=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_volume_pump"));
        assertThrows(IllegalArgumentException.class,()->TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP,wrong,AtmosMachineSettings.defaults(wrong)));
    }

    @Test void coldSnapshotIsEnabledAtIgnitionAndRemainsEnabledThroughRun() {
        var pump=TegContinuousFuelPlanTest.machineCatalog().get(ModAtmosMachines.id("gas_volume_pump"));
        var coldRole=TegContinuousOperatingPlan.Role.COLD_CARRIER_PUMP;
        var snapshot=AtmosMachineSettings.defaults(pump);
        var startup=TegContinuousOperatingPlan.create(coldRole,pump,snapshot,TegContinuousOperatingPlan.Stage.STARTUP);
        assertEquals(java.util.Optional.of(false),startup.finalValues().enabled(),"STARTUP negative control keeps cold flow off");
        var ignition=TegContinuousOperatingPlan.create(coldRole,pump,startup.finalValues(),TegContinuousOperatingPlan.Stage.IGNITION);
        assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_ENABLED),ignition.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
        assertEquals(java.util.Optional.of(true),ignition.finalValues().enabled());
        assertEquals(2,ignition.finalValues().revision());
        assertEquals(java.util.Optional.of(.04d),ignition.finalValues().transferRateM3PerSecond());
        var running=TegContinuousOperatingPlan.create(coldRole,pump,ignition.finalValues(),TegContinuousOperatingPlan.Stage.RUNNING);
        assertTrue(running.requests().isEmpty());
        assertEquals(java.util.Optional.of(true),running.finalValues().enabled());
          assertEquals(2,running.finalValues().revision());
        assertEquals(java.util.Optional.of(.04d),running.finalValues().transferRateM3PerSecond());
        assertTrue(TegContinuousOperatingPlan.create(coldRole,pump,running.finalValues(),TegContinuousOperatingPlan.Stage.RUNNING).requests().isEmpty());
         var heaterOff=TegContinuousOperatingPlan.create(coldRole,pump,running.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF);
         assertEquals(java.util.List.of(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND),
                 heaterOff.requests().stream().map(TegContinuousOperatingPlan.Request::operation).toList());
         assertEquals(3,heaterOff.finalValues().revision());
          assertEquals(.028d,heaterOff.finalValues().transferRateM3PerSecond().orElseThrow(),1e-12);
         assertTrue(TegContinuousOperatingPlan.create(coldRole,pump,heaterOff.finalValues(),TegContinuousOperatingPlan.Stage.HEATER_OFF).requests().isEmpty());
    }

    @Test void allStagesRetainBoundedFuelPressure() {
        var catalog = TegContinuousFuelPlanTest.machineCatalog();
        var mixer = catalog.get(ModAtmosMachines.id("gas_mixer"));
        var startup = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer,
                AtmosMachineSettings.defaults(mixer));
        var running = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer,
                startup.finalValues(), TegContinuousOperatingPlan.Stage.RUNNING);
        var ignition = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer,
                startup.finalValues(), TegContinuousOperatingPlan.Stage.IGNITION);
        assertEquals(java.util.Optional.of(TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION), ignition.finalValues().mixerFractionA());
        assertEquals(java.util.Optional.of(12d), startup.finalValues().targetPressureKpa());
        assertEquals(java.util.Optional.of(TegContinuousOperatingPlan.RUNNING_MIXER_TARGET_KPA), running.finalValues().targetPressureKpa());
        assertEquals(startup.finalValues().targetPressureKpa(),running.finalValues().targetPressureKpa());
        assertEquals(java.util.Optional.of(TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION), running.finalValues().mixerFractionA());
        for (var stage : TegContinuousOperatingPlan.Stage.values()) {
            var staged = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer,
                    AtmosMachineSettings.defaults(mixer), stage);
            assertEquals(13d / 15d, staged.finalValues().mixerFractionA().orElseThrow(), 0d, stage.toString());
            assertEquals(2d / 15d, staged.finalValues().mixerFractionB(), 1e-12, stage.toString());
            assertEquals(java.util.Optional.of(12d), staged.finalValues().targetPressureKpa(), stage.toString());
        }
        assertEquals(1d-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION, running.finalValues().mixerFractionB(), 1e-12);
        assertTrue(running.requests().isEmpty(),"running stage retains already-correct 12 kPa fixture pressure without a redundant CAS request");
        assertTrue(TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER, mixer,
                running.finalValues(), TegContinuousOperatingPlan.Stage.RUNNING).requests().isEmpty());
        assertEquals(java.util.Optional.of(TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION), startup.finalValues().mixerFractionA());

        var ventilation = VentilationPrototypeTest.resolvedCatalog();
        var vent = ventilation.get(ModVentilationDevices.VENT);
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings();
          assertEquals(5, TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY,
                vent, defaults, 0).settings().targetPressureKPa());
        assertEquals(TegContinuousOperatingPlan.RUNNING_BURNER_SUPPLY_TARGET_KPA, TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY,
                vent, defaults, 0, TegContinuousOperatingPlan.Stage.RUNNING).settings().targetPressureKPa());
        var ignitionSupply=TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY,
                vent, defaults, 0, TegContinuousOperatingPlan.Stage.IGNITION);
        assertTrue(ignitionSupply.settings().configuredEnabled());
           assertEquals(5, ignitionSupply.settings().targetPressureKPa());
         var startupSupply=TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY,
                 vent, defaults, 0, TegContinuousOperatingPlan.Stage.STARTUP);
         assertFalse(startupSupply.settings().configuredEnabled());
           assertEquals(5, startupSupply.settings().targetPressureKPa());
          for(var stage:java.util.List.of(TegContinuousOperatingPlan.Stage.RUNNING,TegContinuousOperatingPlan.Stage.HEATER_OFF))
                assertEquals(5,TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY,vent,defaults,0,stage).settings().targetPressureKPa());
    }

    @Test void passiveNoopAndMismatchesAreRejectedWithoutMutatingInput() {
        var catalog = TegContinuousFuelPlanTest.machineCatalog();
        var passive = catalog.get(ModAtmosMachines.id("passive_heat_exchanger"));
        var initial = AtmosMachineSettings.defaults(passive);
        var plan = TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.PASSIVE, passive, initial);
        assertTrue(plan.requests().isEmpty());
        assertSame(initial, plan.finalValues());
        assertEquals(0, initial.revision());
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.CORE, passive, initial));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER,
                catalog.get(ModAtmosMachines.id("gas_pressure_pump")), AtmosMachineSettings.defaults(catalog.get(ModAtmosMachines.id("gas_pressure_pump")))));
        var invalid = new AtmosMachineSettings.Values(0, java.util.Optional.of(true), initial.targetPressureKpa(), initial.transferRateM3PerSecond(),
                initial.mixerFractionA(), initial.filterGas(), initial.thermostatSetpointK(), initial.valveOpen());
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.PASSIVE, passive, invalid));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.create(null, passive, initial));
    }

    @Test void ventilationSourcesAndBurnerRolesUseResolvedDefaultsAndTypedSpecies() {
        var catalog = VentilationPrototypeTest.resolvedCatalog();
        var scrubber = catalog.get(ModVentilationDevices.SCRUBBER);
        var vent = catalog.get(ModVentilationDevices.VENT);
        var scrubberDefaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER, scrubber).settings();
        var ventDefaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings();
        var current = scrubberDefaults;
        for (var role : java.util.List.of(TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN,
                TegContinuousOperatingPlan.VentilationRole.SOURCE_PLASMA)) {
            var proposal = TegContinuousOperatingPlan.proposeVentilation(role, scrubber, current, 0);
            GasType selected = role == TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN ? GasType.OXYGEN : GasType.PLASMA;
            assertEquals(current.revision(), proposal.expectedRevision());
            assertEquals(current.revision() + 1, proposal.settings().revision());
            assertTrue(proposal.settings().configuredEnabled());
            assertEquals(VentilationDeviceConfig.ScrubberMode.FILTER, proposal.settings().scrubberMode());
            assertEquals(java.util.Set.of(selected), proposal.settings().filteredGases());
            assertEquals(VentilationDeviceConfig.Layer.PRIMARY, proposal.settings().selectedLayer());
            assertEquals(scrubberDefaults.volumeRateM3PerSecond(), proposal.settings().volumeRateM3PerSecond());
            assertEquals(scrubberDefaults, current);
        }
        for(var stage:TegContinuousOperatingPlan.Stage.values()) {
            var waste = TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_WASTE, scrubber, current, 0,stage);
            assertEquals(current.revision(), waste.expectedRevision());
            assertEquals(current.revision() + 1, waste.settings().revision());
            assertEquals(VentilationDeviceConfig.Layer.PRIMARY, waste.settings().selectedLayer());
            assertEquals(java.util.Optional.of(stage != TegContinuousOperatingPlan.Stage.COLD_PREPARATION
                    && stage != TegContinuousOperatingPlan.Stage.STARTUP), java.util.Optional.of(waste.settings().configuredEnabled()));
            assertEquals(VentilationDeviceConfig.ScrubberMode.SIPHON, waste.settings().scrubberMode());
            assertEquals(java.util.Set.of(GasType.values()),waste.settings().gasesToScrub());
            assertEquals(java.util.EnumSet.complementOf(java.util.EnumSet.of(GasType.OXYGEN, GasType.PLASMA)), waste.settings().filteredGases());
         assertEquals(scrubberDefaults.volumeRateM3PerSecond(), waste.settings().volumeRateM3PerSecond());
            assertEquals(scrubberDefaults.externalPressureBoundKPa(), waste.settings().externalPressureBoundKPa());
            assertEquals(scrubberDefaults.targetPressureKPa(), waste.settings().targetPressureKPa());
            assertEquals(scrubberDefaults.targetPressureChangeKPaPerSecond(), waste.settings().targetPressureChangeKPaPerSecond());
        }
        assertEquals(0.2d,scrubberDefaults.volumeRateM3PerSecond(),1e-12,"resolved scrubber prototype owns ordinary 0.2 m3/s siphon rate");
        var supply = TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, vent, ventDefaults, 0);
          assertEquals(5, supply.settings().targetPressureKPa());
        assertEquals(ventDefaults.externalPressureBoundKPa(), supply.settings().externalPressureBoundKPa());
        assertEquals(ventDefaults.targetPressureChangeKPaPerSecond(), supply.settings().targetPressureChangeKPaPerSecond());
        assertEquals(VentilationDeviceConfig.Layer.PRIMARY, supply.settings().selectedLayer());
    }

    @Test void exteriorVentilationUsesAmbientAdjustedBoundAndRejectsInvalidRequests() {
        var catalog = VentilationPrototypeTest.resolvedCatalog();
        var vent = catalog.get(ModVentilationDevices.VENT);
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings();
        var ambient = TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE, vent, defaults, 101.325, TegContinuousOperatingPlan.Stage.IGNITION);
        assertEquals(101.375, ambient.settings().externalPressureBoundKPa(), 1e-9);
        assertEquals(101.375, ambient.settings().targetPressureKPa(), 1e-9);
        assertEquals(VentilationDeviceConfig.Layer.PRIMARY, ambient.settings().selectedLayer());
        assertTrue(ambient.settings().configuredEnabled());
        var vacuum = TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE, vent, defaults, 0, TegContinuousOperatingPlan.Stage.RUNNING);
        assertEquals(defaults.externalPressureBoundKPa(), vacuum.settings().externalPressureBoundKPa());
        assertEquals(0.05d,vacuum.settings().targetPressureKPa(),1e-12);
        for (var stage : TegContinuousOperatingPlan.Stage.values()) {
            var settings = TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE,
                    vent, defaults, 101.325, stage).settings();
            var vacuumStage=TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE,
                    vent,defaults,0,stage).settings();
            assertEquals(defaults.revision() + 1, settings.revision());
            assertEquals(java.util.Optional.of(stage != TegContinuousOperatingPlan.Stage.COLD_PREPARATION
                    && stage != TegContinuousOperatingPlan.Stage.STARTUP), java.util.Optional.of(settings.configuredEnabled()));
            assertEquals(VentilationDeviceConfig.Layer.PRIMARY, settings.selectedLayer());
            assertEquals(101.375, settings.externalPressureBoundKPa(), 1e-9);
            assertEquals(101.375, settings.targetPressureKPa(), 1e-9);
            assertEquals(defaults.externalPressureBoundKPa(),vacuumStage.externalPressureBoundKPa(),1e-9);
            assertEquals(.05d,vacuumStage.targetPressureKPa(),1e-12);
            assertEquals(defaults.targetPressureChangeKPaPerSecond(), settings.targetPressureChangeKPaPerSecond());
            assertEquals(defaults.volumeRateM3PerSecond(), settings.volumeRateM3PerSecond());
            assertEquals(defaults, new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings());
        }
        for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(
                    TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE, vent, defaults, invalid));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(
                TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, vent, defaults, 1));
    }

    @Test void exteriorControlUsesObservedPressureRegardlessOfBoundaryClassification() {
        var space = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE;
        var planetary = com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT;
        var vent = VentilationPrototypeTest.resolvedCatalog().get(ModVentilationDevices.VENT);
        var defaults = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings();
        for (var sample : java.util.List.of(new Object[]{space, 101.325d, 101.375d,101.375d},
                new Object[]{space, 0d, 0.05d,defaults.externalPressureBoundKPa()},
                new Object[]{planetary, 101.325d, 101.375d,101.375d})) {
            double ambient = TegContinuousOperatingPlan.exteriorAmbientPressure(
                    (com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind) sample[0],
                    (double) sample[1]);
            var proposal = TegContinuousOperatingPlan.proposeVentilation(
                    TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE, vent, defaults, ambient);
            assertEquals((double) sample[2], proposal.settings().targetPressureKPa(), 1e-9);
            assertEquals((double) sample[3], proposal.settings().externalPressureBoundKPa(), 1e-9);
        }
        assertTrue(0.05d > Math.ulp(0f)&&0.05d>Math.ulp(101.325f),"ambient offset exceeds float pressure storage resolution at vacuum and nonzero ambient");
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.exteriorAmbientPressure(null, 0));
    }

    @Test void ventilationRejectsWrongKindsInvalidPrototypeNullAndExhaustedRevision() {
        var catalog = VentilationPrototypeTest.resolvedCatalog();
        var vent = catalog.get(ModVentilationDevices.VENT);
        var scrubber = catalog.get(ModVentilationDevices.SCRUBBER);
        var ventCurrent = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, vent).settings();
        var scrubCurrent = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER, scrubber).settings();
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.SOURCE_OXYGEN, vent, scrubCurrent, 0));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, scrubber, ventCurrent, 0));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(null, vent, ventCurrent, 0));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, null, ventCurrent, 0));
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, vent, null, 0));
        var exhausted = new VentilationDeviceConfig.Settings(Long.MAX_VALUE, true, ventCurrent.externalPressureBoundKPa(),
                ventCurrent.targetPressureChangeKPaPerSecond(), ventCurrent.targetPressureKPa(), ventCurrent.volumeRateM3PerSecond(),
                ventCurrent.scrubberMode(), ventCurrent.filteredGases(), ventCurrent.selectedLayer());
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, vent, exhausted, 0));
        assertEquals(0, new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, (VentilationDeviceData) null).receiverDemandWatts());
        assertThrows(IllegalArgumentException.class, () -> TegContinuousOperatingPlan.proposeVentilation(
                TegContinuousOperatingPlan.VentilationRole.BURNER_SUPPLY, null, ventCurrent, 0));
    }
}
