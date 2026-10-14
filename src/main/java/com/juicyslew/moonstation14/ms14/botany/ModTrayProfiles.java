package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;

/** Tray profile type; catalog registration is owned by the prototype runtime bootstrap. */
public final class ModTrayProfiles {
    public static final PrototypeType<TrayProfile> TYPE = new PrototypeType<>(id("tray_profile"),
            "moonstation14/tray_profile", TrayProfile.CODEC);
    private ModTrayProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<TrayProfile> serverCatalog() { return PrototypeRuntime.serverTrayProfiles(); }
    public static PrototypeCatalog<TrayProfile> clientCatalog() { return PrototypeRuntime.clientTrayProfiles(); }
    public static ResourceLocation trayId(String name) { return id(name); }
    public static TrayProfile serverLookup(ResourceLocation id) { return id == null ? null : serverCatalog().get(id); }
    public static TrayProfile clientLookup(ResourceLocation id) { return id == null ? null : clientCatalog().get(id); }
    public static boolean serverReady() { return serverCatalog().contains(id("hydroponics_tray")) && serverCatalog().contains(id("empty_hydroponics_tray")); }
    public static boolean clientReady() { return clientCatalog().contains(id("hydroponics_tray")) && clientCatalog().contains(id("empty_hydroponics_tray")); }
}
