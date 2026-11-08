package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.prototype.*;
import net.minecraft.resources.ResourceLocation;
public final class ModProjectileProfiles {
 public static final PrototypeType<ProjectileProfile> TYPE=new PrototypeType<>(ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID,"projectile_profile"),"moonstation14/projectile_profile",ProjectileProfile.CODEC,new ProjectileProfileMergeStrategy());
 private ModProjectileProfiles() {}
 public static PrototypeCatalog<ProjectileProfile> serverCatalog(){return PrototypeRuntime.serverProjectileProfiles();}
 public static PrototypeCatalog<ProjectileProfile> clientCatalog(){return PrototypeRuntime.clientProjectileProfiles();}
}
