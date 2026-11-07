package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import java.util.Collection;
import java.util.Objects;

/** Read-only aggregate of actual burner-cell composition and already-published fire/overlay metadata. */
public final class TegBurnerDiagnostic {
    public static final double PLASMA_VISIBILITY_THRESHOLD_MOLES_PER_CELL = 0.25d;
    private TegBurnerDiagnostic() { }

    public record Cell(double temperatureKelvin,double plasmaMoles,double oxygenMoles,double carbonDioxideMoles,
                       int fireIntensity,int plasmaAlpha) {
        public Cell {
            if(!Double.isFinite(temperatureKelvin)||temperatureKelvin<0||!Double.isFinite(plasmaMoles)||plasmaMoles<0
                    ||!Double.isFinite(oxygenMoles)||oxygenMoles<0||!Double.isFinite(carbonDioxideMoles)||carbonDioxideMoles<0
                    ||fireIntensity<0||fireIntensity>255||plasmaAlpha<0||plasmaAlpha>255)
                throw new IllegalArgumentException("invalid burner cell diagnostic");
        }
    }
    public record Snapshot(int cells,double plasmaMoles,double oxygenMoles,double carbonDioxideMoles,
                           int reactionEligibleCells,int activeFireCells,int maximumFireIntensity,
                           int plasmaVisibleCells) {
        public Snapshot {
            if(cells<0||!Double.isFinite(plasmaMoles)||plasmaMoles<0||!Double.isFinite(oxygenMoles)||oxygenMoles<0
                    ||!Double.isFinite(carbonDioxideMoles)||carbonDioxideMoles<0||reactionEligibleCells<0
                    ||activeFireCells<0||maximumFireIntensity<0||maximumFireIntensity>255||plasmaVisibleCells<0)
                throw new IllegalArgumentException("invalid burner aggregate");
        }
    }
    public static Snapshot aggregate(Collection<Cell> cells) {
        Objects.requireNonNull(cells);
        double plasma=0,oxygen=0,carbonDioxide=0;int eligible=0,active=0,maxFire=0,visible=0;
        for(Cell cell:cells) {
            Objects.requireNonNull(cell);
            plasma+=cell.plasmaMoles();oxygen+=cell.oxygenMoles();carbonDioxide+=cell.carbonDioxideMoles();
            if(cell.temperatureKelvin()>373.15&&cell.plasmaMoles()>0&&cell.oxygenMoles()>0)eligible++;
            if(cell.fireIntensity()>0)active++;
            maxFire=Math.max(maxFire,cell.fireIntensity());
            if(cell.plasmaAlpha()>0)visible++;
        }
        return new Snapshot(cells.size(),plasma,oxygen,carbonDioxide,eligible,active,maxFire,visible);
    }
}
