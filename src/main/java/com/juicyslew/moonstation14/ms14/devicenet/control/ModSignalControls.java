package com.juicyslew.moonstation14.ms14.devicenet.control;

import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;

/** Upstream BaseWallmount -> BaseWallmountMetallic ancestry for signal controls. */
public final class ModSignalControls {
    public static final ResourceLocation BASE_WALLMOUNT = id("base_wallmount");
    public static final ResourceLocation BASE_WALLMOUNT_METALLIC = id("base_wallmount_metallic");
    public static final ResourceLocation SIGNAL_BUTTON = id("signal_button");
    public static final ResourceLocation SIGNAL_SWITCH = id("signal_switch");
    public static final PrototypeType<SignalControlData> TYPE = new PrototypeType<>(id("signal_control"),
            "moonstation14/signal_control", SignalControlData.CODEC, ModSignalControls::validateInheritance,
            ModSignalControls::validateCatalog);
    private ModSignalControls() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<SignalControlData> catalog(Level level) {
        return level.isClientSide ? PrototypeRuntime.clientSignalControls() : PrototypeRuntime.serverSignalControls();
    }
    private static void validateInheritance(PrototypeCatalog<JsonObject> raw, PrototypeCatalog<JsonObject> resolved) {
        Set<ResourceLocation> expected = Set.of(BASE_WALLMOUNT, BASE_WALLMOUNT_METALLIC, SIGNAL_BUTTON, SIGNAL_SWITCH);
        if (!raw.keys().equals(expected) || !resolved.keys().equals(Set.of(SIGNAL_BUTTON, SIGNAL_SWITCH)))
            throw new IllegalArgumentException("signal controls require base wallmount, metallic, button and switch prototypes");
        Map<ResourceLocation, String> parents = Map.of(BASE_WALLMOUNT_METALLIC, "base_wallmount",
                SIGNAL_BUTTON, "base_wallmount_metallic", SIGNAL_SWITCH, "base_wallmount_metallic");
        for (ResourceLocation prototype : expected) {
            JsonObject json = raw.get(prototype);
            if (parents.containsKey(prototype) && (!json.has("parent") || !parents.get(prototype).equals(json.get("parent").getAsString())))
                throw new IllegalArgumentException("wrong signal-control parent for " + prototype);
            boolean abstractPrototype = json.has("abstract") && json.get("abstract").getAsBoolean();
            if (prototype.equals(SIGNAL_BUTTON) || prototype.equals(SIGNAL_SWITCH)) {
                if (abstractPrototype) throw new IllegalArgumentException("concrete signal control marked abstract");
            } else if (!abstractPrototype) throw new IllegalArgumentException("base wallmount family must be abstract");
        }
        for (ResourceLocation id : Set.of(SIGNAL_BUTTON, SIGNAL_SWITCH)) {
            JsonObject json = resolved.get(id);
            if (!json.has("wallMount") || !json.has("deviceComponents") || !json.has("onOutputs") || !json.has("offOutputs"))
                throw new IllegalArgumentException("resolved signal control is missing composition/policy fields: " + id);
        }
    }
    private static void validateCatalog(PrototypeCatalog<SignalControlData> catalog) {
        if (!catalog.keys().equals(Set.of(SIGNAL_BUTTON, SIGNAL_SWITCH)))
            throw new IllegalArgumentException("missing signal button or switch");
    }
}

