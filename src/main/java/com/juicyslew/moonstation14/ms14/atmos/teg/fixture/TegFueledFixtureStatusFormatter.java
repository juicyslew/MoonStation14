package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import java.util.Locale;

/** Human-readable presentation for the values measured by the loaded-world fueled fixture status reader. */
final class TegFueledFixtureStatusFormatter {
    record Snapshot(String stage,String core,String heater,boolean heaterEnabled,double plasmaMol,double oxygenMol,double hotRoomK,
                    double coldNitrogenMol,double coldRoomK,double hotLoopMol,double hotLoopK,double hotLoopKpa,
                    double coldLoopMol,double coldLoopK,double coldLoopKpa,String apcLoads,double startupJ,
                    double generatedJ,double generatedW,double cumulativeGeneratedJ,String offeredW,String outputAllocation,
                    double outputStoredJ,boolean lampLit,String delivered) { }
    private TegFueledFixtureStatusFormatter() { }
    static String format(Snapshot s){return "Fueled TEG "+s.stage()+"\nCore "+s.core()+"; heater "+s.heater()+" "+(s.heaterEnabled()?"ON":"OFF")+". Switch OFF at plasma <= ~1.55 mol, confirm heater demand/allocation 0 W, then watch lamp.\nHot room: plasma "+n(s.plasmaMol())+" mol, O2 "+n(s.oxygenMol())+" mol, mean "+n(s.hotRoomK())+" K; cold room: N2 "+n(s.coldNitrogenMol())+" mol (270 mol initial), mean "+n(s.coldRoomK())+" K.\nHot loop N2 "+n(s.hotLoopMol())+" mol, "+n(s.hotLoopK())+" K, bulk-equivalent "+n(s.hotLoopKpa())+" kPa; cold loop N2 "+n(s.coldLoopMol())+" mol, "+n(s.coldLoopK())+" K, bulk-equivalent "+n(s.coldLoopKpa())+" kPa.\nAPC demand/allocation: "+s.apcLoads()+"\nStartup APC "+n(s.startupJ())+" / 1000000 J; TEG generated "+n(s.generatedJ())+" J / "+n(s.generatedW())+" W (cumulative "+n(s.cumulativeGeneratedJ())+" J); offered HV "+s.offeredW()+"; output APC allocation "+s.outputAllocation()+"; output stored "+n(s.outputStoredJ())+" J; lamp "+(s.lampLit()?"LIT":"OFF")+"; delivered "+s.delivered()+". Finite fuel, coolant and startup charge deplete; not net-positive or self-sustaining.";}
    private static String n(double x){return String.format(Locale.ROOT,"%.1f",x);}
}
