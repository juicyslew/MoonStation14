package com.juicyslew.moonstation14.ms14.atmos.tank;

import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModGasCanisters {
    public static final ResourceLocation BASE_ID=ModGasTanks.id("gas_canister_base"), STORAGE_ID=ModGasTanks.id("storage_canister"), PLASMA_ID=ModGasTanks.id("plasma_canister");
    public static final PrototypeType<GasCanisterProfile> TYPE=new PrototypeType<>(ModGasTanks.id("gas_canister"),"moonstation14/gas_canister",GasCanisterProfile.CODEC,new GasTankMergeStrategy());
    private ModGasCanisters() {}
    public static PrototypeCatalog<GasCanisterProfile> catalog(Level level) { return (level.isClientSide()?PrototypeRuntime.clientManager():PrototypeRuntime.serverManager()).snapshot(TYPE); }
}

