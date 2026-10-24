package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** JFR tick-duration sampler; phase orchestration is owned by the isolated fixture driver. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID)
public final class AnimalPopulationServerSampler {
    private static AnimalPopulationDiagnostic.ServerTick current;
    private AnimalPopulationServerSampler() { }

    @SubscribeEvent public static void pre(ServerTickEvent.Pre event) {
        if (!AnimalPopulationDiagnostic.enabled()) return;
        AnimalPopulationDiagnostic.scenario();
        AnimalPopulationScenarioDriver.pre(event.getServer());
        current = new AnimalPopulationDiagnostic.ServerTick();
        current.phase = AnimalPopulationScenarioDriver.phase(event.getServer());
        current.animalCount = AnimalPopulationScenarioDriver.count(event.getServer());
        current.gameTick = event.getServer().overworld().getGameTime();
        if (AnimalPopulationDiagnostic.flyerPosesEnabled())
            AnimalPopulationScenarioDriver.sampleFlyerPoses(event.getServer(), current.gameTick);
        current.begin();
    }

    @SubscribeEvent public static void post(ServerTickEvent.Post event) {
        if (!AnimalPopulationDiagnostic.enabled() || current == null) return;
        current.end();
        current.commit();
        current = null;
        AnimalPopulationScenarioDriver.post(event.getServer());
    }
}
