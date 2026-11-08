package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
public final class ModBatteryProfiles {
 private ModBatteryProfiles(){}
 public static final ResourceLocation POWER_CELL_TAG=ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"power_cell");
 public static final PrototypeType<BatteryProfile> TYPE=new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"battery_profile"), "moonstation14/battery_profile", BatteryProfile.CODEC, new BatteryProfileMergeStrategy());
 public static PrototypeCatalog<BatteryProfile> serverCatalog(){ return PrototypeRuntime.serverBatteryProfiles(); }
 public static PrototypeCatalog<BatteryProfile> clientCatalog(){ return PrototypeRuntime.clientBatteryProfiles(); }
}
