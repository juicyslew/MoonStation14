package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static com.juicyslew.moonstation14.util.CodecHelpers.LENIENT_ID_CODEC;

/** Immutable item component; integer hundredths are authoritative. */
public final class ReagentComponent implements IMS14Component<ReagentComponent, ReagentAttachment> {
    private static final int WARNING_CACHE_LIMIT = 2048;
    private static final Set<String> MISSING_COLOR_WARNINGS = new LinkedHashSet<>();
    private static volatile Consumer<String> colorWarningSink = message -> MoonStation14.LOGGER.warn("{}", message);
    private final Map<ResourceKey<ReagentData>, Long> cents;

    /** Compatibility/input boundary for legacy callers. The returned float view is approximate at large quantities. */
    public ReagentComponent(Map<ResourceKey<ReagentData>, Float> contents) {
        Objects.requireNonNull(contents, "contents");
        Map<ResourceKey<ReagentData>, Long> converted = new LinkedHashMap<>();
        contents.forEach((key, value) -> {
            long amount = ReagentUnits.fromFloat(Objects.requireNonNull(value, "reagent amount"));
            if (amount != 0) converted.put(Objects.requireNonNull(key, "reagent key"), amount);
        });
        ReagentUnits.total(converted.values());
        this.cents = Collections.unmodifiableMap(converted);
    }

    private ReagentComponent(Map<ResourceKey<ReagentData>, Long> values, boolean exact) {
        Objects.requireNonNull(values, "cents");
        Map<ResourceKey<ReagentData>, Long> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            Objects.requireNonNull(key, "reagent key");
            long amount = Objects.requireNonNull(value, "reagent cents");
            if (amount < 0 || amount > ReagentUnits.MAX_CENTS) throw new IllegalArgumentException("invalid reagent cents");
            if (amount != 0) copied.put(key, amount);
        });
        ReagentUnits.total(copied.values());
        this.cents = Collections.unmodifiableMap(copied);
    }

    public static ReagentComponent fromCents(Map<ResourceKey<ReagentData>, Long> cents) {
        return new ReagentComponent(cents, true);
    }

    /** Immutable compatibility snapshot in units; float precision is approximate at large quantities. */
    public Map<ResourceKey<ReagentData>, Float> contents() {
        Map<ResourceKey<ReagentData>, Float> result = new LinkedHashMap<>();
        cents.forEach((key, value) -> result.put(key, ReagentUnits.toFloat(value)));
        return Collections.unmodifiableMap(result);
    }

    /** Immutable exact authoritative snapshot in integer hundredths. */
    public Map<ResourceKey<ReagentData>, Long> centContents() { return cents; }

    public ReagentComponent() { this(Map.of()); }

    @Override public ReagentAttachment toAttachment() { return new ReagentAttachment(this); }

    public static int getBlendedColor(Map<ResourceKey<ReagentData>, Float> contents, Level level) {
        if (contents.isEmpty() || level == null) return -1;
        PrototypeCatalog<ReagentData> catalog = ModReagents.catalog(level);
        return getBlendedColor(contents, catalog, (level.isClientSide() ? "client" : "server") + " resolved prototype catalog");
    }

    public static int getBlendedColorFromCatalog(Map<ResourceKey<ReagentData>, Float> contents, PrototypeCatalog<ReagentData> catalog) {
        return getBlendedColor(contents, catalog, "resolved prototype catalog");
    }

    private static int getBlendedColor(Map<ResourceKey<ReagentData>, Float> contents, PrototypeCatalog<ReagentData> catalog, String description) {
        if (contents.isEmpty()) return -1;
        double totalWeight = 0, r = 0, g = 0, b = 0;
        for (var entry : contents.entrySet()) {
            float amount = entry.getValue();
            if (!(amount > 0f) || !Float.isFinite(amount)) continue;
            ReagentData reagent = catalog == null ? null : catalog.get(entry.getKey().location());
            if (reagent == null) {
                warnMissingColor(entry.getKey().location(), description);
                continue;
            }
            int color = reagent.color();
            r += ((color >> 16) & 0xFF) * amount;
            g += ((color >> 8) & 0xFF) * amount;
            b += (color & 0xFF) * amount;
            totalWeight += amount;
        }
        if (!(totalWeight > 0d)) return -1;
        return 0xFF000000 | ((int) (r / totalWeight) << 16) | ((int) (g / totalWeight) << 8) | (int) (b / totalWeight);
    }

    private static void warnMissingColor(net.minecraft.resources.ResourceLocation id, String context) {
        String warningKey = context + "|" + id;
        synchronized (MISSING_COLOR_WARNINGS) {
            if (MISSING_COLOR_WARNINGS.contains(warningKey)) return;
            if (MISSING_COLOR_WARNINGS.size() >= WARNING_CACHE_LIMIT)
                MISSING_COLOR_WARNINGS.remove(MISSING_COLOR_WARNINGS.iterator().next());
            MISSING_COLOR_WARNINGS.add(warningKey);
        }
        colorWarningSink.accept("Missing positive reagent '" + id + "' in " + context + "; omitting it from tint blend");
    }

    static void setColorWarningSinkForTests(Consumer<String> sink) { colorWarningSink = Objects.requireNonNull(sink); }
    static void resetColorWarningsForTests() {
        synchronized (MISSING_COLOR_WARNINGS) { MISSING_COLOR_WARNINGS.clear(); }
        colorWarningSink = message -> MoonStation14.LOGGER.warn("{}", message);
    }

    private static final Codec<Double> AMOUNT_CODEC = Codec.DOUBLE.validate(value -> {
        try { ReagentUnits.fromDouble(value); return DataResult.success(value); }
        catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    });
    private static final Codec<Map<ResourceKey<ReagentData>, Double>> NUMERIC_MAP_CODEC = Codec.unboundedMap(
            LENIENT_ID_CODEC.xmap(rl -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, rl), ResourceKey::location), AMOUNT_CODEC);

    /** Numeric JSON/NBT units remain backward-compatible with old float-shaped saves. */
    public static final Codec<ReagentComponent> CODEC = NUMERIC_MAP_CODEC.comapFlatMap(ReagentComponent::fromNumericMap, ReagentComponent::numericMap);

    // Wire format v2: resource key + unsigned varlong cents. Old float payloads are incompatible.
    public static final StreamCodec<RegistryFriendlyByteBuf, ReagentComponent> STREAM_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<ReagentData>, Long, Map<ResourceKey<ReagentData>, Long>>map(
                    HashMap::new, ResourceKey.streamCodec(ModReagents.REAGENT_REGISTRY_KEY), ByteBufCodecs.VAR_LONG)
                    .map(ReagentComponent::fromCents, ReagentComponent::centContents);

    private static DataResult<ReagentComponent> fromNumericMap(Map<ResourceKey<ReagentData>, Double> values) {
        try {
            Map<ResourceKey<ReagentData>, Long> converted = new LinkedHashMap<>();
            values.forEach((key, value) -> { long cents = ReagentUnits.fromDouble(value); if (cents != 0) converted.put(key, cents); });
            return DataResult.success(fromCents(converted));
        } catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
    }

    private Map<ResourceKey<ReagentData>, Double> numericMap() {
        Map<ResourceKey<ReagentData>, Double> result = new LinkedHashMap<>();
        cents.forEach((key, value) -> result.put(key, value / 100d));
        return result;
    }

    @Override public boolean equals(Object other) { return this == other || other instanceof ReagentComponent that && cents.equals(that.cents); }
    @Override public int hashCode() { return cents.hashCode(); }
    @Override public Codec<ReagentComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, ReagentComponent> getStreamCodec() { return STREAM_CODEC; }
}
