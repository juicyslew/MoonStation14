package com.juicyslew.moonstation14.ms14.atmos.piping.alarm.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.Set;

/** Upstream-shaped abstract chain, restricted to locally representable fields. */
public final class ModAirAlarms {
    public static final ResourceLocation WALLMOUNT = id("base_wallmount");
    public static final ResourceLocation METALLIC = id("base_wallmount_metallic");
    public static final ResourceLocation MACHINE = id("base_wallmount_machine");
    public static final ResourceLocation AIR_ALARM = id("air_alarm");
    public static final PrototypeType<AirAlarmData> AIR_ALARM_TYPE = new PrototypeType<>(
            id("air_alarm"), "moonstation14/air_alarm", AirAlarmData.CODEC,
            ModAirAlarms::validateInheritance, ModAirAlarms::validateCatalog);

    private ModAirAlarms() { }

    public static PrototypeCatalog<AirAlarmData> catalog(Level level) {
        return level.isClientSide() ? PrototypeRuntime.clientAirAlarms() : PrototypeRuntime.serverAirAlarms();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }

    private static void validateInheritance(PrototypeCatalog<JsonObject> raw, PrototypeCatalog<JsonObject> resolved) {
        Map<ResourceLocation, ResourceLocation> parents = Map.of(METALLIC, WALLMOUNT, MACHINE, METALLIC, AIR_ALARM, MACHINE);
        if (!raw.keys().equals(Set.of(WALLMOUNT, METALLIC, MACHINE, AIR_ALARM)))
            throw new IllegalArgumentException("air alarm requires exactly the wallmount inheritance chain");
        for (ResourceLocation id : raw.keys()) {
            JsonObject object = raw.get(id);
            Set<String> allowed = id.equals(WALLMOUNT) ? Set.of("abstract", "wallMount")
                    : id.equals(METALLIC) ? Set.of("abstract", "parent")
                    : id.equals(MACHINE) ? Set.of("abstract", "parent", "apcPowerReceiver", "extensionCableReceiver")
                    : Set.of("parent", "deviceList");
            if (!object.keySet().equals(allowed))
                throw new IllegalArgumentException(id + " has missing or unsupported local fields");
            if (id.equals(AIR_ALARM)) {
                if (object.has("abstract")) throw new IllegalArgumentException("air_alarm must be concrete");
            } else if (!object.get("abstract").isJsonPrimitive()
                    || !object.get("abstract").getAsJsonPrimitive().isBoolean()
                    || !object.get("abstract").getAsBoolean())
                throw new IllegalArgumentException(id + " must be abstract");
            if (parents.containsKey(id) && (!object.get("parent").isJsonPrimitive()
                    || !object.get("parent").getAsString().equals(parents.get(id).getPath())))
                throw new IllegalArgumentException(id + " has wrong parent");
        }
        if (!resolved.keys().equals(Set.of(AIR_ALARM)))
            throw new IllegalArgumentException("only concrete air_alarm may be published");
    }

    private static void validateCatalog(PrototypeCatalog<AirAlarmData> catalog) {
        if (!catalog.keys().equals(Set.of(AIR_ALARM)))
            throw new IllegalArgumentException("missing or extra air alarm entries");
        catalog.get(AIR_ALARM).validate();
    }
}
