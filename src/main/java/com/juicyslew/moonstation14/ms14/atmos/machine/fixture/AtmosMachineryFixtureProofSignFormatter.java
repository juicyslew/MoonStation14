package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import net.minecraft.network.chat.Component;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import java.util.Locale;

/** Compact four-line physical receipt for machinery whose committed work is verified. */
public final class AtmosMachineryFixtureProofSignFormatter {
    private AtmosMachineryFixtureProofSignFormatter() {}

    public record Lines(Component title, String status, String first, String second) {}
    public record RoomComponents(Component title,Component status,Component first,Component second) {}

    public static Lines pump(Component title, boolean observed, double inlet, double outlet) {
        return new Lines(title, observed ? "PAID TRANSFER" : "WAIT / UNVER.",
                "In " + mol(inlet), "Out " + mol(outlet));
    }

    public static Lines thermal(Component title, boolean observed, boolean heating, double beforeK,
                                double nowK, double paidJ) {
        return new Lines(title, observed ? (heating ? "PAID HEATING" : "PAID COOLING") : "WAIT / UNVER.",
                temperature(beforeK) + "→" + temperature(nowK) + " K", "Paid " + si(paidJ) + " J");
    }

    public static Lines mixer(Component title, boolean observed, double plasmaPercent, double waterVaporPercent) {
        return mixer(title, observed, GasType.PLASMA, plasmaPercent, GasType.WATER_VAPOR, waterVaporPercent);
    }

    public static Lines mixer(Component title, boolean observed, GasType firstGas, double firstPercent,
                              GasType secondGas, double secondPercent) {
        String first = mixerGasLabel(firstGas) + " " + si(firstPercent) + "%";
        String second = mixerGasLabel(secondGas) + " " + si(secondPercent) + "%";
        if (si(firstPercent).contains("e")) first = compactGasLabel(firstGas) + " " + compactReadout(firstPercent) + "%";
        if (si(secondPercent).contains("e")) second = compactGasLabel(secondGas) + " " + compactReadout(secondPercent) + "%";
        return new Lines(title, observed ? "MIXED" : "PENDING / UNVER.", first, second);
    }

    private static String mixerGasLabel(GasType gas) {
        return gas == GasType.PLASMA ? "PLASMA" : gasLabel(gas);
    }

    public static Lines filter(Component title, boolean observed, String gas, double selectedMoles, double passMoles) {
        return new Lines(title, observed ? "SEPARATED" : "PENDING / UNVER.",
                gasLabel(gas) + " " + mol(selectedMoles), "Pass " + si(passMoles));
    }

    public static Lines filter(Component title, boolean observed, GasType gas, double selectedMoles, double passMoles) {
        String label=gasLabel(gas);
        Lines lines=filter(title, observed, label, selectedMoles, passMoles);
        if(!si(selectedMoles).contains("e"))return lines;
        return new Lines(lines.title(),lines.status(),compactGasLabel(gas)+":"+compactReadout(selectedMoles)+"mol",lines.second());
    }

    public static Lines filter(Component title,boolean observed,GasType selectedGas,double selectedMoles,GasType passGas,double passMoles) {
        String first=gasLabel(selectedGas)+" "+mol(selectedMoles);
        String second=gasLabel(passGas)+" pass "+mol(passMoles);
        if(si(selectedMoles).contains("e"))first=compactGasLabel(selectedGas)+":"+compactReadout(selectedMoles)+"mol";
        if(si(passMoles).contains("e"))second=compactGasLabel(passGas)+":"+compactReadout(passMoles)+"mol pass";
        return new Lines(title,observed?"SEPARATED":"PENDING / UNVER.",first,second);
    }

    public static Lines unconfiguredFilter(Component title) {
        return new Lines(title, "NO FILTER GAS", "All -> outlet", "Filter bypass");
    }

    public static Lines valve(Component title, boolean open, boolean observed, double inletDelta, double outletDelta) {
        return new Lines(title, observed ? "OPEN / FLOW" : (open ? "UNVER." : "CLOSED / UNVER."),
                "Src Δ " + compactRoomMoles(inletDelta), "Dst Δ " + compactRoomMoles(outletDelta));
    }

    private static String si(double value) {
        if (!Double.isFinite(value)) return "?";
        double abs = Math.abs(value);
        if (abs > 9_999_999 || (abs > 0 && abs < 0.01))
            return String.format(Locale.ROOT,"%.2e",value).replace("e+", "e");
        String unit = "";
        double scaled = value;
        if (abs >= 1_000_000) { scaled = value / 1_000_000; unit = "M"; }
        else if (abs >= 1_000) { scaled = value / 1_000; unit = "k"; }
        return String.format(Locale.ROOT, "%.2f%s", scaled, unit);
    }

    private static String temperature(double kelvin) {
        if(!Double.isFinite(kelvin)||kelvin<0)return "?";
        if(kelvin>=10_000)return String.format(Locale.ROOT,"%.0fk",kelvin/1_000);
        return String.format(Locale.ROOT,"%.2f",kelvin);
    }

    private static String mol(double value) {
        String formatted=si(value);
        return formatted.contains("e")?formatted+"mol":formatted+" mol";
    }

    public static Lines exchanger(Component title, boolean observed, double pipeDeltaJ, double roomDeltaJ) {
        return exchanger(title,observed,pipeDeltaJ<0,pipeDeltaJ,roomDeltaJ);
    }
    public static Lines exchanger(Component title, boolean observed, boolean roomToPipe, double pipeDeltaJ, double roomDeltaJ) {
        String room=si(roomDeltaJ/1000);
        return new Lines(title, observed ? (roomToPipe?"ROOM -> PIPE":"PIPE -> ROOM") : "PENDING / UNK.",
                (roomToPipe?"Room ":"Pipe ") + si((roomToPipe?roomDeltaJ:pipeDeltaJ) / 1000) + " kJ",
                (roomToPipe?"Pipe ":"Room ") + (((roomToPipe?pipeDeltaJ:roomDeltaJ)>=0)?"+":"") + si((roomToPipe?pipeDeltaJ:roomDeltaJ)/1000) + " kJ");
    }
    public static Lines manifold(Component title, boolean observed, double oxygenPercent, double nitrogenPercent) {
        return manifold(title, observed, GasType.OXYGEN, oxygenPercent, GasType.NITROGEN, nitrogenPercent);
    }
    public static Lines manifold(Component title, boolean observed, GasType firstGas, double firstPercent,
                                 GasType secondGas, double secondPercent) {
        return new Lines(title, observed ? "ONE MIX/3 LANES" : "PENDING / UNVER.",
                mixerGasLabel(firstGas) + " " + si(firstPercent) + "%", mixerGasLabel(secondGas) + " " + si(secondPercent) + "%");
    }
    public static Lines miner(Component title, boolean observed, double producedMoles, double capKpa) {
        return new Lines(title, observed ? "PRODUCED" : "NO PRODUCTION",
                "Made+" + mol(producedMoles), "Cap " + si(capKpa) + " kPa");
    }
    public static Lines scrubber(Component title, String status, String species, double movedMoles, double roomDeltaMoles) {
        return new Lines(title, status,
                gasLabel(species) + " " + mol(movedMoles), "Room " + (roomDeltaMoles >= 0 ? "+" : "") + compactRoomMoles(roomDeltaMoles));
    }
    public static Lines vent(Component title, String status, double receiverDeltaMoles, double receiverNow) {
        return vent(title, status, receiverDeltaMoles, receiverDeltaMoles, receiverNow);
    }
    public static Lines vent(Component title, String status, double movedMoles, double receiverDeltaMoles, double receiverNow) {
        boolean scientific=si(receiverDeltaMoles).contains("e")||si(receiverNow).contains("e");
        return new Lines(title, status,
                "Moved " + (movedMoles >= 0 ? "+" : "") + compactRoomMoles(movedMoles),
                (scientific?"R":"Room ") + (receiverDeltaMoles >= 0 ? "+" : "") + compactReadout(receiverDeltaMoles)
                        + " n" + compactReadout(receiverNow));
    }

    public static Lines room(Component title, String role, String gas, double amount, double amountDelta,
                             double temperatureK, double temperatureDeltaK) {
        String species=compactGasLabel(gas);
        String actual=species+compactReadout(amount)+"mol";
        boolean thermal=role.equals("THERMAL_ROOM");
        return new Lines(title,actual,
                thermal ? "dT "+(temperatureDeltaK>=0?"+":"")+compactReadout(temperatureDeltaK)+"K"
                        : "d"+species+" "+(amountDelta>=0?"+":"")+compactReadout(amountDelta)+" mol",
                temperature(temperatureK)+" K");
    }

    public static RoomComponents localizedRoom(Component title,String role,GasType gas,double amount,double amountDelta,
                                                double temperatureK,double temperatureDeltaK) {
        Component label=Component.translatable("gui.moonstation14.gas."+gas.id());
        Component actual=Component.empty().append(label).append(Component.literal(" "+compactReadout(amount)+" mol"));
        boolean thermal=role.equals("THERMAL_ROOM");
        Component delta=thermal?Component.literal("ΔT "+(temperatureDeltaK>=0?"+":"")+compactReadout(temperatureDeltaK)+" K")
                :Component.empty().append(Component.literal("Δ ")).append(label)
                .append(Component.literal(" "+(amountDelta>=0?"+":"")+compactReadout(amountDelta)+" mol"));
        return new RoomComponents(title,actual,delta,Component.literal(temperature(temperatureK)+" K"));
    }

    public static Lines thermalGauge(Component title,String status,double baselineK,double currentK,double deltaK) {
        String before=temperature(baselineK),now=temperature(currentK),change=temperature(Math.abs(deltaK));
        return new Lines(title,status,"T0 "+before+" K","T "+now+" K  ΔT "+(deltaK>=0?"+":"-")+change+" K");
    }

    public static Lines exchangerGauge(Component title,String status,double roomDeltaJ,double pipeDeltaJ) {
        return new Lines(title,status,"Room ΔE "+(roomDeltaJ>=0?"+":"")+si(roomDeltaJ/1000)+" kJ",
                "Pipe ΔE "+(pipeDeltaJ>=0?"+":"")+si(pipeDeltaJ/1000)+" kJ");
    }

    private static String gasLabel(String gas) {
        return switch(gas.toUpperCase(Locale.ROOT)) {
            case "CARBON DIOXIDE" -> "CO2";
            case "OXYGEN" -> "OXYGEN";
            case "NITROGEN" -> "NITROGEN";
            case "PLASMA" -> "Plasma";
            default -> gas;
        };
    }

    private static String gasLabel(GasType gas) {
        if (gas == null) return "?";
        return switch (gas) {
            case OXYGEN -> "O2";
            case NITROGEN -> "N2";
            case CARBON_DIOXIDE -> "CO2";
            case PLASMA -> "Plasma";
            case TRITIUM -> "T";
            case WATER_VAPOR -> "H2O";
            case AMMONIA -> "NH3";
            case NITROUS_OXIDE -> "N2O";
            case FREZON -> "Frezon";
        };
    }

    /** Human-facing label for the currently selected species in detailed operator receipts. */
    public static String operatorGasLabel(GasType gas) {
        return gasLabel(gas);
    }

    private static String compactGasLabel(String gas) {
        return switch(gas.toUpperCase(Locale.ROOT)) { case "PLASMA" -> "PL"; case "NITROGEN" -> "N2"; case "OXYGEN" -> "O2"; case "CARBON DIOXIDE" -> "CO2"; default -> gas; };
    }
    private static String compactGasLabel(GasType gas) {
        return switch(gas) {
            case OXYGEN -> "O2";
            case NITROGEN -> "N2";
            case CARBON_DIOXIDE -> "CO2";
            case PLASMA -> "Pl";
            case TRITIUM -> "T";
            case WATER_VAPOR -> "H2O";
            case AMMONIA -> "NH3";
            case NITROUS_OXIDE -> "N2O";
            case FREZON -> "Fz";
        };
    }

    private static String compactRoomMoles(double value) {
        String formatted=si(value);
        return formatted+(formatted.contains("e")?"":" mol");
    }

    private static String compactReadout(double value) {
        String formatted=si(value);
        int e=formatted.indexOf('e');
        if(e<0)return formatted;
        String mantissa=formatted.substring(0,e), exponent=formatted.substring(e+1);
        int dot=mantissa.indexOf('.');
        if(dot>=0) {
            int places=mantissa.length()-dot-1;
            mantissa=mantissa.substring(0,dot)+mantissa.substring(dot+1);
            exponent=Integer.toString(Integer.parseInt(exponent)-places);
        }
        return mantissa+"e"+exponent;
    }
}
