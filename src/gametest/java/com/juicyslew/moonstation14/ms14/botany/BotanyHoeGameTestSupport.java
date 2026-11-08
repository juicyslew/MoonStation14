package com.juicyslew.moonstation14.ms14.botany;

/** Test-only trusted state arrangement for authenticated botany interaction fixtures. */
public final class BotanyHoeGameTestSupport {
    private BotanyHoeGameTestSupport() { }

    public static void setWeeds(HydroponicsTrayBlockEntity tray, float weeds) {
        var plant = tray.plantState();
        plant.soil(plant.water(), plant.nutrition(), weeds, plant.soilToxins());
    }
}
