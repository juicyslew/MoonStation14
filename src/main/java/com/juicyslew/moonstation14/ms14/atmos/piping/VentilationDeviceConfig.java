package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;

/** Pure, revisioned configuration for a vent pump or scrubber; this does not activate simulation. */
public final class VentilationDeviceConfig {
    public enum Kind { VENT, SCRUBBER }
    public enum ScrubberMode { FILTER, SIPHON }
    public enum Layer { PRIMARY, SECONDARY, TERTIARY }

    public record Settings(long revision, boolean configuredEnabled, double externalPressureBoundKPa,
                            double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                            double volumeRateM3PerSecond, ScrubberMode scrubberMode,
                            Set<GasType> filteredGases, Layer selectedLayer) {
        public Settings(long revision, boolean configuredEnabled, double externalPressureBoundKPa,
                        double targetPressureChangeKPaPerSecond, double targetPressureKPa,
                        double volumeRateM3PerSecond, ScrubberMode scrubberMode, Set<GasType> filteredGases) {
            this(revision, configuredEnabled, externalPressureBoundKPa, targetPressureChangeKPaPerSecond,
                    targetPressureKPa, volumeRateM3PerSecond, scrubberMode, filteredGases, Layer.PRIMARY);
        }
        public Settings {
            if (revision < 0) throw new IllegalArgumentException("revision must be nonnegative");
            nonnegative(externalPressureBoundKPa, "external pressure bound");
            nonnegative(targetPressureChangeKPaPerSecond, "target pressure change");
            nonnegative(targetPressureKPa, "target pressure");
            if (targetPressureKPa > Ss14VentilationDefaults.MAXIMUM_TARGET_PRESSURE_KPA)
                throw new IllegalArgumentException("target pressure exceeds source maximum");
            if (externalPressureBoundKPa > 1_000_000 || targetPressureChangeKPaPerSecond > 1_000_000
                    || volumeRateM3PerSecond > 1_000_000)
                throw new IllegalArgumentException("ventilation setting exceeds safe bound");
            nonnegative(volumeRateM3PerSecond, "volume rate");
            if (scrubberMode == null || filteredGases == null || selectedLayer == null)
                throw new IllegalArgumentException("scrubber settings must not be null");
            filteredGases = Set.copyOf(filteredGases);
        }

        private static void nonnegative(double value, String name) {
            if (!Double.isFinite(value) || value < 0.0)
                throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }

        /** Siphon transfers all gas; only FILTER mode uses this selected set. */
        public Set<GasType> gasesToScrub() {
            return scrubberMode == ScrubberMode.SIPHON ? Set.of(GasType.values()) : filteredGases;
        }
    }

    private final Kind kind;
    private final AtomicReference<Settings> settings;
    private volatile boolean settingsValid = true;
    private boolean legacyDefaults = true;
    private VentilationDeviceData prototype;
    private boolean explicitDefaults;

    public VentilationDeviceConfig(Kind kind) {
        this(kind, null);
        // Explicit defaults are retained for isolated configuration tests, never for live BEs.
        explicitDefaults = true;
        settings.set(defaultSettings());
    }

    public VentilationDeviceConfig(Kind kind, VentilationDeviceData prototype) {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        this.kind = kind;
        settings = new AtomicReference<>();
        bindPrototype(prototype);
    }

    /** Rebase only never-configured legacy instances; persisted revisions remain authoritative. */
    public void bindPrototype(VentilationDeviceData resolved) {
        if (resolved != null && (resolved.kind() != VentilationDeviceData.Kind.valueOf(kind.name())
                || !validPrototype(resolved))) resolved = null;
        if (prototype == resolved && settings.get() != null) return;
        prototype = resolved;
        if (legacyDefaults && settingsValid) settings.set(defaultSettings());
    }

    private static boolean validPrototype(VentilationDeviceData data) {
        try {
            data.validate();
            return data.apcPowerReceiver().requestedWatts() == Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS;
        } catch (RuntimeException invalid) { return false; }
    }

    public Kind kind() { return kind; }
    public Settings settings() { return settings.get(); }
    public boolean settingsValid() { return settingsValid; }
    /** Fixed APC receiver draw, independent of configured enablement and not a power-loop request. */
    public double receiverDemandWatts() { return prototype == null
            ? (explicitDefaults ? Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS : 0)
            : prototype.apcPowerReceiver().requestedWatts(); }

    /** Restore strictly validated persisted settings. An absent key is a legacy instance. */
    public boolean restore(CompoundTag tag) {
        if (!tag.contains("Version")) {
            legacyDefaults = true;
            settings.set(defaultSettings());
            settingsValid = true;
            return true;
        }
        try {
            legacyDefaults = false;
            int version = tag.getInt("Version");
            if (!tag.contains("Version", CompoundTag.TAG_INT) || (version != 1 && version != 2)
                    || !tag.contains("Revision", CompoundTag.TAG_LONG) || !tag.contains("Enabled", CompoundTag.TAG_BYTE)
                    || !tag.contains("ExternalBound", CompoundTag.TAG_DOUBLE) || !tag.contains("PressureChange", CompoundTag.TAG_DOUBLE)
                    || !tag.contains("TargetPressure", CompoundTag.TAG_DOUBLE) || !tag.contains("VolumeRate", CompoundTag.TAG_DOUBLE)
                    || !tag.contains("Mode", CompoundTag.TAG_STRING) || !tag.contains("Filters", CompoundTag.TAG_STRING))
                throw new IllegalArgumentException("missing or unsupported settings fields");
            java.util.Set<GasType> gases = new java.util.HashSet<>();
            String raw = tag.getString("Filters");
            if (!raw.isEmpty()) for (String id : raw.split(",", -1)) {
                GasType gas = java.util.Arrays.stream(GasType.values()).filter(g -> g.id().equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown gas filter"));
                if (!gases.add(gas)) throw new IllegalArgumentException("duplicate gas filter");
            }
            Layer layer = version == 1 ? Layer.PRIMARY : Layer.valueOf(tag.getString("Layer"));
            if (version == 2 && !tag.contains("Layer", CompoundTag.TAG_STRING)) throw new IllegalArgumentException("missing layer");
            Settings restored = new Settings(tag.getLong("Revision"), tag.getBoolean("Enabled"),
                    tag.getDouble("ExternalBound"), tag.getDouble("PressureChange"), tag.getDouble("TargetPressure"),
                    tag.getDouble("VolumeRate"), ScrubberMode.valueOf(tag.getString("Mode")), gases, layer);
            settings.set(restored);
            settingsValid = true;
            return true;
        } catch (RuntimeException invalid) {
            Settings defaults = defaultSettings();
            settings.set(new Settings(0, false, defaults.externalPressureBoundKPa(), defaults.targetPressureChangeKPaPerSecond(),
                    defaults.targetPressureKPa(), defaults.volumeRateM3PerSecond(), defaults.scrubberMode(), defaults.filteredGases(), defaults.selectedLayer()));
            settingsValid = false;
            return false;
        }
    }

    public CompoundTag save() {
        Settings s = settings();
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 2); tag.putLong("Revision", s.revision()); tag.putBoolean("Enabled", s.configuredEnabled());
        tag.putDouble("ExternalBound", s.externalPressureBoundKPa()); tag.putDouble("PressureChange", s.targetPressureChangeKPaPerSecond());
        tag.putDouble("TargetPressure", s.targetPressureKPa()); tag.putDouble("VolumeRate", s.volumeRateM3PerSecond());
        tag.putString("Mode", s.scrubberMode().name());
        tag.putString("Layer", s.selectedLayer().name());
        tag.putString("Filters", s.filteredGases().stream().map(GasType::id).sorted().collect(java.util.stream.Collectors.joining(",")));
        return tag;
    }

    private Settings defaultSettings() {
        if (prototype != null) {
            Layer lane = Layer.valueOf(prototype.pipeLane().name());
            return kind == Kind.VENT
                    ? new Settings(0, true, prototype.gasVentPump().externalPressureBoundKPa(),
                        prototype.gasVentPump().targetPressureChangeKPaPerSecond(),
                        prototype.gasVentPump().externalPressureBoundKPa(), 0, ScrubberMode.FILTER, Set.of(), lane)
                    : new Settings(0, true, 0, 0, 0, prototype.gasVentScrubber().volumeRateM3PerSecond(),
                        ScrubberMode.FILTER, Set.copyOf(prototype.gasVentScrubber().filteredGases()), lane);
        }
        // A missing live prototype cannot produce enabled nominal gas flow.
        return fallbackSettings(explicitDefaults);
    }

    private Settings fallbackSettings(boolean enabled) {
        return kind == Kind.VENT
                ? new Settings(0, enabled, Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA,
                    Ss14VentilationDefaults.VENT_TARGET_PRESSURE_CHANGE_KPA_PER_SECOND,
                    Ss14VentilationDefaults.VENT_EXTERNAL_PRESSURE_BOUND_KPA, 0, ScrubberMode.FILTER, Set.of(), Layer.PRIMARY)
                : new Settings(0, enabled, 0, 0, 0, Ss14VentilationDefaults.SCRUBBER_VOLUME_RATE_M3_PER_SECOND,
                    ScrubberMode.FILTER, Ss14VentilationDefaults.DEFAULT_SCRUBBED_GASES, Layer.PRIMARY);
    }

    /** Replace settings iff expectedRevision is current; successful updates advance revision once. */
    public boolean compareAndSet(long expectedRevision, Settings proposed) {
        if (proposed == null) throw new IllegalArgumentException("settings must not be null");
        Settings current = settings.get();
        if (!settingsValid || current.revision() != expectedRevision || current.revision() == Long.MAX_VALUE) return false;
        Settings next = new Settings(expectedRevision + 1, proposed.configuredEnabled(),
                proposed.externalPressureBoundKPa(), proposed.targetPressureChangeKPaPerSecond(),
                proposed.targetPressureKPa(), proposed.volumeRateM3PerSecond(),
                proposed.scrubberMode(), proposed.filteredGases(), proposed.selectedLayer());
        boolean changed = settings.compareAndSet(current, next);
        if (changed) legacyDefaults = false;
        return changed;
    }
}
