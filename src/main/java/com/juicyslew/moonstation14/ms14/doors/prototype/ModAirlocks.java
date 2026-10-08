package com.juicyslew.moonstation14.ms14.doors.prototype;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class ModAirlocks {
    public static final ResourceLocation STANDARD = id("standard_airlock");
    public static final PrototypeType<AirlockData> TYPE = new PrototypeType<>(id("airlock"), "moonstation14/airlock", AirlockData.CODEC,
            ModAirlocks::validate);
    private ModAirlocks() { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,path); }
    public static PrototypeCatalog<AirlockData> catalog(Level level) { return level.isClientSide()?PrototypeRuntime.clientAirlocks():PrototypeRuntime.serverAirlocks(); }
    private static void validate(PrototypeCatalog<AirlockData> catalog) {
        AirlockData data=catalog.get(STANDARD);
        if(data==null) throw new IllegalArgumentException("missing standard airlock prototype");
        data.validate();
    }
}
