package com.juicyslew.moonstation14.ms14.singularity.beacon;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModSingularityBeacons {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_beacon");
    public static final ResourceLocation BASE_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "singularity_beacon_base");
    public static final PrototypeType<SingularityBeaconProfile> TYPE = SingularityBeaconProfile.TYPE;
    private ModSingularityBeacons() { }
    public static PrototypeCatalog<SingularityBeaconProfile> catalog(Level level) {
        return (level.isClientSide() ? PrototypeRuntime.clientManager() : PrototypeRuntime.serverManager()).snapshot(TYPE);
    }
    /** Side-independent server composition lookup for server-only consumers such as portable food credit. */
    public static SingularityBeaconProfile serverDefaultProfile() {
        return PrototypeRuntime.serverSingularityBeacons().get(DEFAULT_ID);
    }
}
