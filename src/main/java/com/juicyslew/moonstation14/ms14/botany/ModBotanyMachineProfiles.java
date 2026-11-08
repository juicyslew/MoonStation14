package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

public final class ModBotanyMachineProfiles {
    public static final PrototypeType<MachineProfile> TYPE = new PrototypeType<>(id("botany_machine_profile"),
            "moonstation14/botany_machine_profile", MachineProfile.CODEC);
    private ModBotanyMachineProfiles() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, path); }
    public static PrototypeCatalog<MachineProfile> serverCatalog() { return PrototypeRuntime.serverBotanyMachineProfiles(); }
    public static PrototypeCatalog<MachineProfile> clientCatalog() { return PrototypeRuntime.clientBotanyMachineProfiles(); }
}
