package com.juicyslew.moonstation14.ms14.disposal.prototype;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Resolved local disposal tube prototype family. */
public final class ModDisposals {
    public static final ResourceLocation BASE = id("disposal_tube_base");
    public static final ResourceLocation MACHINE_BASE = id("base_structure");
    public static final ResourceLocation MACHINE = id("base_machine");
    public static final ResourceLocation POWERED_MACHINE = id("base_machine_powered");
    public static final ResourceLocation UNIT_BASE = id("disposal_unit_base");
    public static final ResourceLocation UNIT = id("disposal_unit");
    public static final ResourceLocation HOLDER = id("disposal_holder");
    public static final PrototypeType<DisposalEntityData> DISPOSAL_TYPE = new PrototypeType<>(
            id("disposal_entity"), "moonstation14/disposal_entity", DisposalEntityData.CODEC,
            ModDisposals::validateInheritance, ModDisposals::validateCatalog);
    private ModDisposals() { }
    public static ResourceLocation id(String path) {
        return path.indexOf(':') >= 0 ? ResourceLocation.parse(path)
                : ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path);
    }
    private static final Set<ResourceLocation> REQUIRED = Set.of("disposal_pipe", "disposal_bend", "disposal_trunk", "disposal_junction", "disposal_junction_flipped", "disposal_y_junction", "disposal_x_junction", "disposal_tagger", "disposal_router", "disposal_router_flipped").stream().map(ModDisposals::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static void validateInheritance(PrototypeCatalog<JsonObject> raw, PrototypeCatalog<JsonObject> resolved) {
        JsonObject base = raw.get(BASE);
        if (base == null || !base.has("abstract") || !base.get("abstract").getAsBoolean() || base.has("parent") || !base.has("anchored")
                 || !base.has("segmentSeconds") || !base.has("turnDamage") || !base.has("tubeDiameter")) throw new IllegalArgumentException("invalid abstract disposal tube base");
        if (resolved.get(BASE) != null || resolved.get(MACHINE_BASE) != null || resolved.get(MACHINE) != null
                || resolved.get(POWERED_MACHINE) != null || resolved.get(UNIT_BASE) != null) throw new IllegalArgumentException("abstract disposal parent published");
        for (ResourceLocation childId : REQUIRED) {
            JsonObject child = raw.get(childId);
            if (child == null || !child.has("parent") || !child.get("parent").getAsString().equals(BASE.getPath()))
                throw new IllegalArgumentException("disposal child must inherit tube base: " + childId);
        }
        parent(raw, MACHINE, MACHINE_BASE); parent(raw, POWERED_MACHINE, MACHINE); parent(raw, UNIT_BASE, POWERED_MACHINE); parent(raw, UNIT, UNIT_BASE);
    }
    private static void validateCatalog(PrototypeCatalog<DisposalEntityData> catalog) {
        for (ResourceLocation id : REQUIRED) if (catalog.get(id) == null) throw new IllegalArgumentException("missing disposal prototype: " + id);
        var unit = catalog.get(UNIT);
        if (unit == null || unit.unit() == null || unit.unit().powerWatts() != 1000 || unit.unit().airLiters() != 2500)
            throw new IllegalArgumentException("missing or invalid disposal unit composition");
        var holder = catalog.get(HOLDER);
        if (holder == null || holder.holder() == null || holder.holder().fallbackAirLiters() != 70
                || holder.holder().segmentSeconds() != .1 || !holder.holder().containerId().equals("DisposalHolderComponent"))
            throw new IllegalArgumentException("missing or invalid disposal holder composition");
    }
    private static void parent(PrototypeCatalog<JsonObject> raw, ResourceLocation childId, ResourceLocation parentId) {
        JsonObject child = raw.get(childId);
        if (child == null || !child.has("parent") || !parentId.getPath().equals(child.get("parent").getAsString()))
            throw new IllegalArgumentException(childId + " must inherit " + parentId);
    }
}

