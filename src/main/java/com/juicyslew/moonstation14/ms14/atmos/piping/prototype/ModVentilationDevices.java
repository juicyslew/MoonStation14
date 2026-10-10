package com.juicyslew.moonstation14.ms14.atmos.piping.prototype;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Set;

/** Definition catalog for SS14 unary ventilation identities, not runtime activation. */
public final class ModVentilationDevices {
    public static final ResourceLocation BASE = id("gas_unary_base");
    public static final ResourceLocation VENT = id("gas_vent");
    public static final ResourceLocation SCRUBBER = id("gas_scrubber");
    public static final PrototypeType<VentilationDeviceData> VENTILATION_DEVICE_TYPE = new PrototypeType<>(
            id("ventilation_device"), "moonstation14/ventilation_device", VentilationDeviceData.CODEC,
            new com.juicyslew.moonstation14.ms14.prototype.PrototypeMergeStrategy() {
                @Override public boolean handles(String field) { return field.equals("deviceComponents"); }
                @Override public JsonElement merge(String field, ResourceLocation id, JsonObject child, java.util.List<JsonObject> parents) {
                    JsonObject merged = new JsonObject();
                     for (JsonObject parent : parents) if (parent.has(field) && parent.get(field).isJsonObject()) overlay(merged, parent.getAsJsonObject(field));
                     if (child.has(field) && child.get(field).isJsonObject()) overlay(merged, child.getAsJsonObject(field));
                     return merged;
                 }

                 private void overlay(JsonObject target, JsonObject source) {
                     for (var entry : source.entrySet()) {
                         JsonElement old = target.get(entry.getKey());
                         JsonElement value = entry.getValue();
                         if (old != null && old.isJsonObject() && value.isJsonObject()) {
                             JsonObject nested = old.getAsJsonObject().deepCopy();
                             overlay(nested, value.getAsJsonObject());
                             target.add(entry.getKey(), nested);
                         } else target.add(entry.getKey(), value.deepCopy());
                     }
                 }
            },
            ModVentilationDevices::validateInheritance, ModVentilationDevices::validateCatalog);

    private ModVentilationDevices() { }

    public static PrototypeCatalog<VentilationDeviceData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientVentilationDevices() : PrototypeRuntime.serverVentilationDevices();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }

    private static void validateInheritance(PrototypeCatalog<JsonObject> raw, PrototypeCatalog<JsonObject> resolved) {
        JsonObject base = raw.get(BASE);
        if (base == null || !base.has("abstract") || !base.get("abstract").isJsonPrimitive()
                || !base.get("abstract").getAsBoolean() || base.has("parent") || base.has("kind")
                || base.has("gasVentPump") || base.has("gasVentScrubber")
                || !base.has("pipeLane") || !base.has("apcPowerReceiver")
                  || !base.has("extensionCableReceiver")
                  || !base.has("fixtureGeometry")
                  || !Set.of("abstract", "pipeLane", "apcPowerReceiver", "extensionCableReceiver",
                           "fixtureGeometry", "deviceComponents").containsAll(base.keySet()))
            throw new IllegalArgumentException("gas_unary_base must be an abstract shared parent without kind components");
        for (ResourceLocation id : Set.of(VENT, SCRUBBER)) {
            JsonObject child = raw.get(id);
            if (child == null || !child.has("parent") || !child.get("parent").isJsonPrimitive()
                    || !child.get("parent").getAsString().equals(BASE.getPath()) || child.has("abstract")
                    || child.has("pipeLane") || child.has("apcPowerReceiver")
                     || child.has("extensionCableReceiver") || child.has("fixtureGeometry"))
                throw new IllegalArgumentException(id + " must inherit gas_unary_base as a concrete child");
        }
        if (resolved.asMap().containsKey(BASE)) throw new IllegalArgumentException("abstract base was published");
        for (ResourceLocation id : raw.keys()) {
            if (id.equals(BASE)) continue;
            JsonObject child = raw.get(id);
            if (child.has("abstract") || child.has("pipeLane") || child.has("apcPowerReceiver")
                      || child.has("extensionCableReceiver") || child.has("fixtureGeometry"))
                throw new IllegalArgumentException(id + " must inherit shared fields without overriding them");
            JsonElement parent = child.get("parent");
            if (parent == null || !parent.isJsonPrimitive() || !parent.getAsString().equals(BASE.getPath()))
                throw new IllegalArgumentException(id + " must inherit gas_unary_base");
        }
    }

    private static void validateCatalog(PrototypeCatalog<VentilationDeviceData> catalog) {
        required(catalog, VENT, VentilationDeviceData.Kind.VENT);
        required(catalog, SCRUBBER, VentilationDeviceData.Kind.SCRUBBER);
        for (var entry : catalog.asMap().entrySet()) entry.getValue().validate();
    }

    private static void required(PrototypeCatalog<VentilationDeviceData> catalog, ResourceLocation id,
                                 VentilationDeviceData.Kind kind) {
        VentilationDeviceData data = catalog.get(id);
        if (data == null || data.kind() != kind
                || data.apcPowerReceiver().requestedWatts() != Ss14VentilationDefaults.APC_REQUESTED_POWER_WATTS)
            throw new IllegalArgumentException("missing or invalid required ventilation prototype: " + id);
    }
}
