package com.juicyslew.moonstation14.ms14.examine;

import net.minecraft.network.chat.Component;

/** Pure presentation of resolved receiver demand and already-observed allocation. */
final class AtmosMachinePowerExamine {
    private AtmosMachinePowerExamine() { }

    static Component receiver(Double demand, boolean known, double delivered) {
        if (demand != null && Double.isFinite(demand) && demand == 0)
            return Component.translatable("examine.moonstation14.power.standby");
        if (demand == null || !Double.isFinite(demand) || demand < 0 || !known || !Double.isFinite(delivered))
            return Component.translatable("examine.moonstation14.power.unavailable");
        if (delivered >= demand) return Component.translatable("examine.moonstation14.power.on");
        return Component.translatable(delivered <= 0 ? "examine.moonstation14.power.off" : "examine.moonstation14.power.insufficient");
    }

    static Component tegDelivered(boolean known, double delivered) {
        return known && Double.isFinite(delivered) && delivered >= 0
                ? Component.translatable("examine.moonstation14.atmos.teg-delivered", powerWatts(delivered))
                : Component.translatable("examine.moonstation14.atmos.teg-delivery-unknown");
    }

    static Component tegCapacity(double capacity) {
        if (!Double.isFinite(capacity) || capacity < 0)
            return Component.translatable("examine.moonstation14.atmos.teg-delivery-unknown");
        return Component.translatable("examine.moonstation14.atmos.teg-capacity", powerWatts(capacity));
    }

    static Component tegRuntime(double generatedWatts,double offeredWatts,boolean suppliedKnown,
                                double suppliedWatts,long sampleAgeTicks) {
        if(!Double.isFinite(generatedWatts)||generatedWatts<0||!Double.isFinite(offeredWatts)||offeredWatts<0)
            return Component.literal("TEG generation/offer unavailable");
        String supplied=suppliedKnown&&Double.isFinite(suppliedWatts)&&suppliedWatts>=0
                ?powerWatts2(suppliedWatts):"unknown";
        String age=sampleAgeTicks>=0?sampleAgeTicks+"t":"stale";
        return Component.literal("Generation "+powerWatts2(generatedWatts)+" | offered "+powerWatts2(offeredWatts)
                +" | supplied "+supplied+" (sample age "+age+")");
    }

    private static String powerWatts(double watts) {
        if (watts >= 1_000_000_000) return oneDecimal(watts / 1_000_000_000) + " GW";
        if (watts >= 1_000_000) return oneDecimal(watts / 1_000_000) + " MW";
        if (watts >= 1_000) return oneDecimal(watts / 1_000) + " kW";
        return oneDecimal(watts) + " W";
    }

    private static String powerWatts2(double watts) {
        if (watts >= 1_000_000_000) return twoDecimals(watts / 1_000_000_000) + " GW";
        if (watts >= 1_000_000) return twoDecimals(watts / 1_000_000) + " MW";
        if (watts >= 1_000) return twoDecimals(watts / 1_000) + " kW";
        return twoDecimals(watts) + " W";
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
    private static String twoDecimals(double value) { return String.format(java.util.Locale.ROOT,"%.2f",value); }
}
