package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger;
import java.util.List;

/** Read-only bulk-equivalent view of three physically separate loop components. */
final class TegFueledLoopMeasurement {
    record Measurement(GasMixture mixture,int componentCount) { }
    private TegFueledLoopMeasurement() { }
    static Measurement measure(List<GasMixture> components){
        if(components==null||components.size()!=3)throw new IllegalArgumentException("exactly three loop components required");
        double volume=components.stream().mapToDouble(GasMixture::volumeCubicMeters).sum();
        GasMixture aggregate=PipeGasLedger.merge(components);
        return new Measurement(new GasMixture(aggregate.gasMoles(),aggregate.temperatureKelvin(),volume),components.size());
    }
}
