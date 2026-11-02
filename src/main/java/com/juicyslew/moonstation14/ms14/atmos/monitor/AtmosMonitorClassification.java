package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;

/** Reusable pressure, temperature and gas mole-fraction classification against resolved source data. */
public final class AtmosMonitorClassification {
    private AtmosMonitorClassification() { }

    public static AtmosAlarmSeverity classify(PrototypeCatalog<AtmosAlarmThresholdData> thresholds,
                                               AtmosMonitorData monitor, GasMixture mixture) {
        if (thresholds == null || monitor == null || mixture == null) throw new IllegalArgumentException("unavailable atmosphere observation");
        AtmosAlarmSeverity result = AtmosAlarmSeverity.NORMAL;
        result = AtmosAlarmSeverity.max(result, required(thresholds, monitor.pressureThreshold()).classify(mixture.pressureKpa()));
        result = AtmosAlarmSeverity.max(result, required(thresholds, monitor.temperatureThreshold()).classify(mixture.temperatureKelvin()));
        double total = mixture.totalMoles();
        for (var entry : monitor.gasThresholds().entrySet()) {
            double fraction = mixture.moles(entry.getKey()) / total;
            result = AtmosAlarmSeverity.max(result, required(thresholds, entry.getValue()).classify(fraction));
        }
        return result;
    }
    private static AtmosAlarmThresholdData required(PrototypeCatalog<AtmosAlarmThresholdData> catalog, ResourceLocation id) {
        AtmosAlarmThresholdData data = catalog.get(id);
        if (data == null) throw new IllegalArgumentException("missing resolved alarm threshold " + id);
        return data;
    }
}
