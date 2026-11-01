package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Source-backed alarm thresholds, loaded through the shared prototype manager. */
public final class ModAtmosAlarmThresholds {
    public static final PrototypeType<AtmosAlarmThresholdData> TYPE = new PrototypeType<>(
            id("alarm_threshold"), "moonstation14/alarm_threshold", AtmosAlarmThresholdData.CODEC);
    private ModAtmosAlarmThresholds() { }
    public static PrototypeCatalog<AtmosAlarmThresholdData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientAtmosAlarmThresholds() : PrototypeRuntime.serverAtmosAlarmThresholds();
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
}
