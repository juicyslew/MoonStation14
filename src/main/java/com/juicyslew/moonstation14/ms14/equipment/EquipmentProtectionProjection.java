package com.juicyslew.moonstation14.ms14.equipment;

import com.juicyslew.moonstation14.ms14.character.components.BarotraumaComponent;

import java.util.List;
import java.util.Optional;

/** Projects SS14 pressure protection from the shared resolved worn-equipment snapshot. */
public final class EquipmentProtectionProjection {
    private EquipmentProtectionProjection() { }

    public static Optional<EquipmentItemData.PressureProtection> pressure(
            List<ResolvedWornEquipment.Entry> worn, BarotraumaComponent policy) {
        if (worn == null || policy == null || policy.protectionSlots().isEmpty()) return Optional.empty();
        EquipmentItemData.PressureProtection weakest = null;
        for (String required : policy.protectionSlots()) {
            EquipmentItemData.PressureProtection found = null;
            for (var entry : worn) {
                if (slotMatches(required, entry.slotId()) && entry.profile().pressureProtection().isPresent()) {
                    found = entry.profile().pressureProtection().orElseThrow();
                    break;
                }
            }
            if (found == null) return Optional.empty();
            if (weakest == null) weakest = found;
            else weakest = new EquipmentItemData.PressureProtection(
                    Math.max(weakest.highPressureMultiplier(), found.highPressureMultiplier()),
                    Math.min(weakest.lowPressureMultiplier(), found.lowPressureMultiplier()),
                    Math.max(weakest.highPressureModifier(), found.highPressureModifier()),
                    Math.min(weakest.lowPressureModifier(), found.lowPressureModifier()));
        }
        return Optional.ofNullable(weakest);
    }

    /** SS14 relays temperature modification to every worn non-pocket item, multiplying per-item factors. */
    public static double temperatureCoefficient(List<ResolvedWornEquipment.Entry> worn, double heatDeltaJoules) {
        var factors = temperatureCoefficients(worn);
        return heatDeltaJoules < 0 ? factors.cooling() : factors.heating();
    }

    public static TemperatureCoefficients temperatureCoefficients(List<ResolvedWornEquipment.Entry> worn) {
        if (worn == null) return new TemperatureCoefficients(1, 1);
        double heating = 1.0, cooling = 1.0;
        for (var entry : worn) {
            if (entry.slotFlags().contains(InventoryTemplateData.SlotFlag.POCKET)) continue;
            var protection = entry.profile().temperatureProtection();
            if (protection.isEmpty()) continue;
            heating *= protection.get().heatingCoefficient();
            cooling *= protection.get().coolingCoefficient();
            if (!Double.isFinite(heating) || !Double.isFinite(cooling)) return new TemperatureCoefficients(0, 0);
        }
        return new TemperatureCoefficients(heating, cooling);
    }

    public record TemperatureCoefficients(double heating, double cooling) { }

    private static boolean slotMatches(String required, String slotId) {
        return switch (required) {
            case "head" -> "head".equals(slotId);
            case "outerClothing" -> "outerClothing".equals(slotId);
            default -> required.equals(slotId);
        };
    }
}
