package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;

/** Standard firelock prototype family; normal resolver inheritance is sufficient. */
public final class ModFirelocks {
    public static final ResourceLocation STANDARD=id("firelock");
    public static final PrototypeType<FirelockData> TYPE=new PrototypeType<>(id("firelock"),"moonstation14/firelock",FirelockData.CODEC,catalog->{
        if(!catalog.contains(STANDARD))throw new IllegalArgumentException("missing standard firelock prototype");
        catalog.values().forEach(FirelockData::validate);
    });
    private ModFirelocks(){}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,path);}
}
