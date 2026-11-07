package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
public final class ModAmmoContainerProfiles {
 private ModAmmoContainerProfiles(){}
 public static final PrototypeType<AmmoContainerProfile> TYPE=new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"ammo_container_profile"), "moonstation14/ammo_container_profile", AmmoContainerProfile.CODEC, new AmmoContainerProfileMergeStrategy());
 public static PrototypeCatalog<AmmoContainerProfile> serverCatalog(){ return PrototypeRuntime.serverAmmoContainerProfiles(); }
 public static PrototypeCatalog<AmmoContainerProfile> clientCatalog(){ return PrototypeRuntime.clientAmmoContainerProfiles(); }
}
