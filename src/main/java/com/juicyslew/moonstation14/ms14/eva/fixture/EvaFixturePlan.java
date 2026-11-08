package com.juicyslew.moonstation14.ms14.eva.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankData;
import com.juicyslew.moonstation14.ms14.atmos.tank.GasTankContents;
import com.juicyslew.moonstation14.ms14.atmos.tank.ModGasTanks;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Pure bounded room/scenario planning shared by the command runtime and JVM tests. */
public final class EvaFixturePlan {
    public static final int WIDTH=9, HEIGHT=6, DEPTH=9;
    private EvaFixturePlan() { }
    public static List<BlockPos> interior(BlockPos origin) {
        var cells=new ArrayList<BlockPos>();
        for(int x=1;x<WIDTH-1;x++)for(int y=1;y<HEIGHT-1;y++)for(int z=1;z<DEPTH-1;z++)cells.add(origin.offset(x,y,z));
        return List.copyOf(cells);
    }
    /** Sealed roof panels replaced with lights; never overlaps room interior. */
    public static List<BlockPos> lightingPositions(BlockPos origin) {
        return List.of(origin.offset(2, HEIGHT-1, 2), origin.offset(WIDTH-3, HEIGHT-1, 2),
                origin.offset(2, HEIGHT-1, DEPTH-3), origin.offset(WIDTH-3, HEIGHT-1, DEPTH-3));
    }
    public static boolean isLightingPosition(BlockPos origin, BlockPos pos) {
        return lightingPositions(origin).contains(pos);
    }
    /** One ordinary 0.5 L outlet draw in a real oxygen tank's resolved volume and temperature. */
    public static GasTankContents lowOxygenContents(GasTankData profile) {
        Objects.requireNonNull(profile, "profile");
        double moles=GasMixture.molesForPressureDelta(profile.outletPressureKpa(),.0005,profile.temperatureKelvin());
        return new GasTankContents(ModGasTanks.OXYGEN_ID,
                new GasMixture(Map.of(GasType.OXYGEN,moles),profile.temperatureKelvin(),profile.volumeCubicMeters()));
    }
    public static GasMixture mixture(String scenario,double volume) {
        if(!Double.isFinite(volume)||volume<=0)throw new IllegalArgumentException("volume must be finite and positive");
        if(!List.of("normal","vacuum","cold","hot","contaminated").contains(scenario))throw new IllegalArgumentException("unknown EVA gas scenario");
        double temperature=switch(scenario){case "cold"->100;case "hot"->600;default->293.15;};
        if(scenario.equals("vacuum"))return new GasMixture(Map.of(),temperature,volume);
        double moles=101325*volume/(8.3144626*temperature);
        Map<GasType,Double> gases=scenario.equals("contaminated")
                ?Map.of(GasType.OXYGEN,moles*.18,GasType.NITROGEN,moles*.72,GasType.PLASMA,moles*.10)
                :Map.of(GasType.OXYGEN,moles*.21,GasType.NITROGEN,moles*.79);
        return new GasMixture(gases,temperature,volume);
    }
}
