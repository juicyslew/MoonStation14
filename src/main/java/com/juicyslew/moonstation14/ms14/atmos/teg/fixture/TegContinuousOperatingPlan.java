package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;
import java.util.List;
import java.util.Set;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Pure, ordered initial settings plan for the continuous TEG fixture machines. */
public final class TegContinuousOperatingPlan {
    private TegContinuousOperatingPlan() { }

    /**
     * Explicit bounded fixture recipe selected to offset measured local plasma inventory loss while retaining
     * oxygen surplus. This is not stoichiometry, an SS14 mixer default, or a guaranteed stable operating point.
     */
    public static final double FUEL_OXYGEN_FRACTION = 13.0d / 15.0d;
    /** Fixture operator recipe: reduce cold circulation after heater cutoff; not a prototype or physics coefficient. */
    public static final double HEATER_OFF_COLD_COOLANT_FRACTION = 0.70d;
    public static final double STARTUP_MIXER_TARGET_KPA = 12d;
    public static final double IGNITION_BURNER_SUPPLY_TARGET_KPA = 5d;
    /** Deliberately bounded fixture pressures used at every operating phase. */
    public static final double RUNNING_MIXER_TARGET_KPA = STARTUP_MIXER_TARGET_KPA;
    public static final double RUNNING_BURNER_SUPPLY_TARGET_KPA = IGNITION_BURNER_SUPPLY_TARGET_KPA;

        public enum Role { CORE, HOT_CARRIER_PUMP, COLD_CARRIER_PUMP, COLD_RETURN_PRESSURE_PUMP, IGNITION_HEATER, MIXER, PASSIVE }
    public enum Stage { COLD_PREPARATION, STARTUP, IGNITION, RUNNING, HEATER_OFF }
    public enum VentilationRole { SOURCE_OXYGEN, SOURCE_PLASMA, BURNER_SUPPLY, BURNER_WASTE, EXTERIOR_DISCHARGE }

    /** Selects the measured ambient independently of the boundary's SPACE/planetary classification. */
    public static double exteriorAmbientPressure(
            com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind kind,
            double observedAmbientPressureKPa) {
        if (kind == null || (kind != com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.SPACE
                && kind != com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT))
            throw new IllegalArgumentException("unsupported exterior boundary kind");
        if (!Double.isFinite(observedAmbientPressureKPa) || observedAmbientPressureKPa < 0)
            throw new IllegalArgumentException("valid observed exterior ambient pressure required");
        return observedAmbientPressureKPa;
    }
    public record VentilationProposal(long expectedRevision, VentilationDeviceConfig.Settings settings) {
        public VentilationProposal {
            if (expectedRevision < 0 || settings == null || settings.revision() != expectedRevision + 1)
                throw new IllegalArgumentException("next-revision ventilation settings required");
        }
    }
    public record Request(AtmosMachineSettings.Operation operation, AtmosMachineSettings.Value value) {
        public Request {
            if (operation == null || value == null) throw new IllegalArgumentException("setting request required");
        }
    }
    public record Plan(long expectedInitialRevision, List<Request> requests, AtmosMachineSettings.Values finalValues) {
        public Plan {
            if (expectedInitialRevision < 0 || requests == null || finalValues == null) throw new IllegalArgumentException("valid settings plan required");
            requests = List.copyOf(requests);
        }
    }

    public static Plan create(Role role, AtmosMachineProfile profile, AtmosMachineSettings.Values current) {
        return create(role, profile, current, Stage.STARTUP);
    }

    public static Plan create(Role role, AtmosMachineProfile profile, AtmosMachineSettings.Values current, Stage stage) {
        if (role == null || profile == null || current == null || stage == null) throw new IllegalArgumentException("role, resolved profile, settings and stage required");
        AtmosMachineSettings.validate(profile, current);
        List<Request> requests;
        switch (role) {
            case CORE -> {
                require(profile.teg() != null, "TEG composition required");
                requests = List.of(enabled());
            }
            case HOT_CARRIER_PUMP -> {
                require(profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP && profile.pump() != null
                        && profile.pump().mode() == AtmosMachineProfile.PumpMode.PRESSURE, "hot pressure pump composition required");
                double maximum = profile.pump().maxPressureKpa().orElseThrow(
                        () -> new IllegalArgumentException("resolved maximum pressure required"));
                requests = List.of(enabled(stage!=Stage.COLD_PREPARATION), numeric(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA, maximum));
            }
            case COLD_CARRIER_PUMP -> {
                require(profile.kind() == AtmosMachineProfile.Kind.VOLUME_PUMP && profile.pump() != null
                        && profile.pump().mode() == AtmosMachineProfile.PumpMode.VOLUME, "volume pump composition required");
                var rate = AtmosMachineSettings.defaults(profile).transferRateM3PerSecond()
                        .orElseThrow(() -> new IllegalArgumentException("resolved pump transfer rate required"));
                if (role == Role.COLD_CARRIER_PUMP) rate /= TegContinuousCoolingPlan.RADIATOR_COUNT;
                if (role == Role.COLD_CARRIER_PUMP && stage == Stage.HEATER_OFF)
                    rate *= HEATER_OFF_COLD_COOLANT_FRACTION;
                boolean flowEnabled=coldCirculationOn(stage);
                requests = List.of(enabled(flowEnabled), numeric(AtmosMachineSettings.Operation.SET_TRANSFER_RATE_M3_PER_SECOND, rate));
            }
            case COLD_RETURN_PRESSURE_PUMP -> {
                require(profile.kind() == AtmosMachineProfile.Kind.PRESSURE_PUMP && profile.pump() != null
                        && profile.pump().mode() == AtmosMachineProfile.PumpMode.PRESSURE, "pressure pump composition required");
                double maximum = profile.pump().maxPressureKpa().orElseThrow(
                        () -> new IllegalArgumentException("resolved maximum pressure required"));
                requests = List.of(enabled(coldCirculationOn(stage)), numeric(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA, maximum));
            }
            case IGNITION_HEATER -> {
                require(profile.kind() == AtmosMachineProfile.Kind.HEATER && profile.thermostat() != null
                        && profile.thermostat().mode() == AtmosMachineProfile.ThermostatMode.HEATER, "heater thermostat composition required");
                requests = List.of(enabled(stage!=Stage.COLD_PREPARATION&&stage!=Stage.HEATER_OFF), numeric(AtmosMachineSettings.Operation.SET_THERMOSTAT_SETPOINT_K,
                        profile.thermostat().maximumTemperatureK()));
            }
            case MIXER -> {
                require(profile.kind() == AtmosMachineProfile.Kind.MIXER && profile.mixer() != null, "mixer composition required");
                // Explicit fixture startup choices, not SS14 defaults or a proven stable operating point.
                 requests = List.of(enabled(), numeric(AtmosMachineSettings.Operation.SET_TARGET_PRESSURE_KPA,
                                    stage == Stage.RUNNING || stage == Stage.HEATER_OFF ? RUNNING_MIXER_TARGET_KPA : STARTUP_MIXER_TARGET_KPA),
                          numeric(AtmosMachineSettings.Operation.SET_MIXER_FRACTION_A, FUEL_OXYGEN_FRACTION));
            }
            case PASSIVE -> {
                require(profile.kind() == AtmosMachineProfile.Kind.TEG_CIRCULATOR
                        || profile.kind() == AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER, "passive role requires a circulator or passive exchanger");
                return new Plan(current.revision(), List.of(), current);
            }
            default -> throw new IllegalArgumentException("unsupported machine role");
        }
        requests = requests.stream().filter(request -> !alreadyHasValue(current, request)).toList();
        var values = current;
        for (Request request : requests) values = AtmosMachineSettings.apply(profile, values, request.operation(), request.value());
        return new Plan(current.revision(), requests, values);
    }

    /** Canonical typed carrier-pump positions: the ordered PUMP entries are hot then cold. */
    public static List<net.minecraft.core.BlockPos> carrierPumpPositions(TegFueledFixturePlan.Layout layout) {
        if (layout == null) throw new IllegalArgumentException("resolved fixture layout required");
        var pumps=layout.machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.PUMP||m.kind()==TegFueledFixturePlan.MachineKind.PRESSURE_PUMP).toList();
        if(pumps.size()!=2 || pumps.get(0).position().equals(pumps.get(1).position()))
            throw new IllegalArgumentException("exactly two unique planned carrier pumps required");
        return List.of(pumps.get(0).position(),pumps.get(1).position());
    }

    /** Classifies a live pump by its typed position in the ordered resolved plan. */
    public static Role pumpRole(TegFueledFixturePlan.Layout layout, net.minecraft.core.BlockPos body) {
        if(layout==null||body==null)throw new IllegalArgumentException("resolved fixture layout and pump body required");
        var positions=carrierPumpPositions(layout);
        if(positions.get(0).equals(body))return Role.HOT_CARRIER_PUMP;
        if(positions.get(1).equals(body))return Role.COLD_CARRIER_PUMP;
        throw new IllegalArgumentException("body is not a planned carrier pump: "+body);
    }

    /** Validates the complete live pump roster against its typed layout responsibilities. */
    public static void validatePumpRoster(TegContinuousFuelPlan.Layout layout, Map<BlockPos, AtmosMachineProfile> actual) {
        if (layout == null || actual == null) throw new IllegalArgumentException("resolved continuous layout and actual pump roster required");
        var carriers = carrierPumpPositions(layout.fueled());
        var coldReturn = layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP);
        if (coldReturn.equals(carriers.get(0)) || coldReturn.equals(carriers.get(1)))
            throw new IllegalArgumentException("cold return pump must have a unique position");
        if (actual.size() != 3 || !actual.keySet().equals(Set.of(carriers.get(0), carriers.get(1), coldReturn)))
            throw new IllegalArgumentException("actual pump positions do not match typed pump roster");
        requirePumpKind(actual.get(carriers.get(0)), AtmosMachineProfile.Kind.PRESSURE_PUMP, "hot carrier");
        requirePumpKind(actual.get(carriers.get(1)), AtmosMachineProfile.Kind.VOLUME_PUMP, "cold carrier");
        requirePumpKind(actual.get(coldReturn), AtmosMachineProfile.Kind.PRESSURE_PUMP, "cold return");
    }

    private static void requirePumpKind(AtmosMachineProfile profile, AtmosMachineProfile.Kind expected, String role) {
        if (profile == null || profile.kind() != expected || profile.pump() == null
                || profile.pump().mode() != (expected == AtmosMachineProfile.Kind.VOLUME_PUMP
                        ? AtmosMachineProfile.PumpMode.VOLUME : AtmosMachineProfile.PumpMode.PRESSURE))
            throw new IllegalArgumentException(role + " has wrong resolved pump profile");
    }

    private static boolean alreadyHasValue(AtmosMachineSettings.Values current, Request request) {
        var value = request.value();
        return switch (request.operation()) {
            case SET_ENABLED -> current.enabled().equals(java.util.Optional.of(((AtmosMachineSettings.BooleanValue) value).value()));
            case SET_TARGET_PRESSURE_KPA -> current.targetPressureKpa().equals(java.util.Optional.of(((AtmosMachineSettings.NumericValue) value).value()));
            case SET_TRANSFER_RATE_M3_PER_SECOND -> current.transferRateM3PerSecond().equals(java.util.Optional.of(((AtmosMachineSettings.NumericValue) value).value()));
            case SET_MIXER_FRACTION_A -> current.mixerFractionA().equals(java.util.Optional.of(((AtmosMachineSettings.NumericValue) value).value()));
            case SET_THERMOSTAT_SETPOINT_K -> current.thermostatSetpointK().equals(java.util.Optional.of(((AtmosMachineSettings.NumericValue) value).value()));
            default -> false;
        };
    }

    /** Creates a pure, single-revision proposal from validated resolved ventilation defaults. */
    public static VentilationProposal proposeVentilation(VentilationRole role, VentilationDeviceData prototype,
                                                         VentilationDeviceConfig.Settings current,
                                                         double exteriorAmbientPressureKPa) {
        return proposeVentilation(role, prototype, current, exteriorAmbientPressureKPa, Stage.STARTUP);
    }

    public static VentilationProposal proposeVentilation(VentilationRole role, VentilationDeviceData prototype,
                                                         VentilationDeviceConfig.Settings current,
                                                         double exteriorAmbientPressureKPa, Stage stage) {
        if (stage == null) throw new IllegalArgumentException("operating stage required");
        if (role == null || prototype == null || current == null) throw new IllegalArgumentException("role, resolved prototype and current settings required");
        VentilationDeviceConfig.Kind kind = switch (role) {
            case SOURCE_OXYGEN, SOURCE_PLASMA, BURNER_WASTE -> VentilationDeviceConfig.Kind.SCRUBBER;
            case BURNER_SUPPLY, EXTERIOR_DISCHARGE -> VentilationDeviceConfig.Kind.VENT;
        };
        var config = new VentilationDeviceConfig(kind, prototype);
        if (config.receiverDemandWatts() == 0 || !config.settingsValid()) throw new IllegalArgumentException("valid resolved ventilation prototype required");
        if (current.revision() == Long.MAX_VALUE) throw new IllegalArgumentException("settings revision exhausted");
        if (role != VentilationRole.EXTERIOR_DISCHARGE && exteriorAmbientPressureKPa != 0)
            throw new IllegalArgumentException("ambient pressure only applies to exterior discharge");
        if (role == VentilationRole.EXTERIOR_DISCHARGE && (!Double.isFinite(exteriorAmbientPressureKPa) || exteriorAmbientPressureKPa < 0))
            throw new IllegalArgumentException("valid exterior ambient pressure required");
        var defaults = config.settings();
        VentilationDeviceConfig.Settings proposed;
        switch (role) {
            case SOURCE_OXYGEN, SOURCE_PLASMA -> {
                GasType gas = role == VentilationRole.SOURCE_OXYGEN ? GasType.OXYGEN : GasType.PLASMA;
                proposed = new VentilationDeviceConfig.Settings(current.revision() + 1, true, defaults.externalPressureBoundKPa(),
                        defaults.targetPressureChangeKPaPerSecond(), defaults.targetPressureKPa(), defaults.volumeRateM3PerSecond(),
                        VentilationDeviceConfig.ScrubberMode.FILTER, Set.of(gas), VentilationDeviceConfig.Layer.PRIMARY);
            }
            case BURNER_WASTE -> {
                var gases = java.util.EnumSet.allOf(GasType.class);
                gases.remove(GasType.OXYGEN); gases.remove(GasType.PLASMA);
                boolean enabled = stage != Stage.COLD_PREPARATION && stage != Stage.STARTUP;
                proposed = new VentilationDeviceConfig.Settings(current.revision() + 1, enabled, defaults.externalPressureBoundKPa(),
                        defaults.targetPressureChangeKPaPerSecond(), defaults.targetPressureKPa(), defaults.volumeRateM3PerSecond(),
                        VentilationDeviceConfig.ScrubberMode.SIPHON, gases, VentilationDeviceConfig.Layer.PRIMARY);
            }
            // Explicit bounded, paid fixture startup choice; not an upstream default or stability claim.
              case BURNER_SUPPLY -> proposed = new VentilationDeviceConfig.Settings(current.revision() + 1, stage != Stage.STARTUP && stage != Stage.COLD_PREPARATION,
                        defaults.externalPressureBoundKPa(), defaults.targetPressureChangeKPaPerSecond(), IGNITION_BURNER_SUPPLY_TARGET_KPA,
                    defaults.volumeRateM3PerSecond(), defaults.scrubberMode(), defaults.filteredGases(), VentilationDeviceConfig.Layer.PRIMARY);
            case EXTERIOR_DISCHARGE -> {
                 // Bounded exterior bleed: pressure is only ambient + 0.05 kPa, not pipe-pressure regulation.
                 double pressure = Math.max(defaults.externalPressureBoundKPa(), exteriorAmbientPressureKPa + 0.05d);
                 boolean enabled = stage != Stage.COLD_PREPARATION && stage != Stage.STARTUP;
                 proposed = new VentilationDeviceConfig.Settings(current.revision() + 1, enabled, pressure,
                         defaults.targetPressureChangeKPaPerSecond(), exteriorAmbientPressureKPa + 0.05d, defaults.volumeRateM3PerSecond(),
                        defaults.scrubberMode(), defaults.filteredGases(), VentilationDeviceConfig.Layer.PRIMARY);
            }
            default -> throw new IllegalArgumentException("unsupported ventilation role");
        }
        if (current.revision() < 0) throw new IllegalArgumentException("invalid current revision");
        return new VentilationProposal(current.revision(), proposed);
    }

    private static Request enabled() { return enabled(true); }
    private static boolean coldCirculationOn(Stage stage) {
        return stage == Stage.COLD_PREPARATION || stage == Stage.IGNITION || stage == Stage.RUNNING || stage == Stage.HEATER_OFF;
    }
    private static Request enabled(boolean value) { return new Request(AtmosMachineSettings.Operation.SET_ENABLED, new AtmosMachineSettings.BooleanValue(value)); }
    private static Request numeric(AtmosMachineSettings.Operation operation, double value) {
        return new Request(operation, new AtmosMachineSettings.NumericValue(value));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
