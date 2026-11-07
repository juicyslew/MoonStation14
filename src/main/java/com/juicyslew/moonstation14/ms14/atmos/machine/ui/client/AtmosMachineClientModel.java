package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot;
import java.util.UUID;

/** Pure reconciliation and presentation rules used by the client screen. */
public final class AtmosMachineClientModel {
    public enum Numeric { PRESSURE_KPA, RATE_LPS, MIXER_PERCENT, THERMOSTAT_K }
    private final int container; private final UUID session; private final String profile;
    private AtmosMachineControlSnapshot snapshot; private long pending=-1, highestSent=-1, lastAcceptedAck=-1;
    public AtmosMachineClientModel(int container,UUID session,String profile){this.container=container;this.session=session;this.profile=profile;}
    public void pending(long request){if(request<0){pending=-1;return;}if(request<=highestSent)throw new IllegalArgumentException("request ids must increase");highestSent=request;pending=request;}
    public long pending(){return pending;}
    public AtmosMachineControlSnapshot snapshot(){return snapshot;}
    public boolean accept(AtmosMachineControlSnapshot candidate){
        if(candidate.containerId()!=container||!candidate.session().equals(session)||!candidate.controls().profileId().equals(profile))return false;
        long ack=candidate.requestId();
        if(ack>=0&&(ack>highestSent||ack<lastAcceptedAck||(ack!=lastAcceptedAck&&ack!=pending)))return false;
        if(ack==-1&&lastAcceptedAck>=0)return false;
        if(snapshot!=null&&candidate.settings().revision()<snapshot.settings().revision())return false;
        if(ack>=0){if(ack==pending)pending=-1;lastAcceptedAck=Math.max(lastAcceptedAck,ack);}
        snapshot=candidate; return true;
    }
    public static boolean hasGasSelector(AtmosMachineControlSnapshot.Controls c){return c.filterGas();}
    public static java.util.List<Numeric> numericFields(AtmosMachineControlSnapshot.Controls c){var fields=new java.util.ArrayList<Numeric>(2);if(c.pressure())fields.add(Numeric.PRESSURE_KPA);if(c.rate())fields.add(Numeric.RATE_LPS);if(c.mixerRatio())fields.add(Numeric.MIXER_PERCENT);if(c.thermostat())fields.add(Numeric.THERMOSTAT_K);return java.util.List.copyOf(fields);}
    public static double displayValue(Numeric n,double canonical){return switch(n){case RATE_LPS->canonical*1000d;case MIXER_PERCENT->canonical*100d;default->canonical;};}
    public static double canonicalValue(Numeric n,double displayed){return switch(n){case RATE_LPS->displayed/1000d;case MIXER_PERCENT->displayed/100d;default->displayed;};}
    public static double complementaryPercent(double fractionA){return (1d-fractionA)*100d;}
    public static String format(Numeric n,double value){return String.format(java.util.Locale.ROOT,n==Numeric.THERMOSTAT_K?"%.2f":"%.2f",displayValue(n,value));}
    public static String gasLabel(java.util.Optional<com.juicyslew.moonstation14.ms14.atmos.core.GasType> gas){return gas.map(com.juicyslew.moonstation14.ms14.atmos.core.GasType::id).orElse("NONE");}
    public static boolean validNumeric(Numeric n,double displayed,AtmosMachineControlSnapshot.Controls c){if(!Double.isFinite(displayed))return false;double canonical=canonicalValue(n,displayed);return switch(n){case PRESSURE_KPA->canonical>=0&&canonical<=c.pressureMaxKpa();case RATE_LPS->canonical>=0&&canonical<=c.rateMaxM3PerSecond();case MIXER_PERCENT->displayed>=0&&displayed<=100;case THERMOSTAT_K->canonical>=c.thermostatMinK()&&canonical<=c.thermostatMaxK()&&Math.abs(canonical*10-Math.rint(canonical*10))<1e-7;};}
}
