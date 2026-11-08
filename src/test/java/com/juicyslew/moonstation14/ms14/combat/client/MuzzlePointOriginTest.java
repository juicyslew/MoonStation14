package com.juicyslew.moonstation14.ms14.combat.client;

import com.juicyslew.moonstation14.ms14.combat.GunMuzzleFlashPayload;
import com.juicyslew.moonstation14.ms14.combat.ProjectileProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MuzzlePointOriginTest {
    private static ProjectileProfile.ProjectileSpriteEffectData effect(float offset){return new ProjectileProfile.ProjectileSpriteEffectData(
            new ProjectileProfile.SpriteData(ResourceLocation.withDefaultNamespace("muzzle_laser"),"muzzle_laser",96,96,5,0,true,false),.4f,false,offset,0,0);}
    @Test void artCenterOffsetMovesPointVolumeAlongCapturedBoreForEitherHandScaleAndAudience(){
        for(String hand:new String[]{"left","right"}) {
            var item=ResourceLocation.withDefaultNamespace("weapon_laser_svalinn");
            var source=new com.juicyslew.moonstation14.ms14.combat.ShotVisualSource(hand,item,7);
            var frozen=new ActualMuzzleFrame(new Vec3(2,3,4),new Vec3(0,0,1),new Vec3(0,1,0),12);
            var entry=new GunMuzzleFlashClient.Entry(effect(.5f),UUID.randomUUID(),0,ResourceLocation.withDefaultNamespace("overworld"),Vec3.ZERO,Vec3.ZERO,0,null,GunMuzzleFlashPayload.Kind.MUZZLE,source,frozen);
            var local=new GunMuzzleFlashClient.Sample(entry,1,frozen);var observer=new GunMuzzleFlashClient.Sample(entry,1,frozen);
            assertEquals(frozen.muzzle(),local.origin());assertEquals(frozen.muzzle().add(0,0,.5),local.pointOrigin());
            assertEquals(local.pointOrigin(),observer.pointOrigin());
            assertEquals(frozen.muzzle(),new GunMuzzleFlashClient.Sample(new GunMuzzleFlashClient.Entry(effect(0),entry.body(),0,entry.dimension(),Vec3.ZERO,Vec3.ZERO,0,null,GunMuzzleFlashPayload.Kind.MUZZLE,source,frozen),1,frozen).pointOrigin());
        }
    }
    @Test void impactNeverUsesGunArtCenteringOffset(){
        var at=new Vec3(8,2,-1);var entry=new GunMuzzleFlashClient.Entry(effect(.5f),UUID.randomUUID(),0,ResourceLocation.withDefaultNamespace("overworld"),at,Vec3.ZERO,0,null,GunMuzzleFlashPayload.Kind.IMPACT,null,null);
        assertEquals(at,new GunMuzzleFlashClient.Sample(entry,1).pointOrigin());
    }
    @Test void beamTravelMustAdvanceFromActualMuzzleAlongBore(){
        var muzzle=new Vec3(1,2,3);var frame=new ActualMuzzleFrame(muzzle,new Vec3(0,0,1),new Vec3(0,1,0),9);
        var source=new com.juicyslew.moonstation14.ms14.combat.ShotVisualSource("right",ResourceLocation.withDefaultNamespace("bow"),11);
        assertTrue(beam(frame,source,new Vec3(1.2,2,8),new Vec3(1,2,3)).hasForwardTravel(1e-4)); // lateral spread, still forward
        assertFalse(beam(frame,source,muzzle,muzzle).hasForwardTravel(1e-4));
        assertFalse(beam(frame,source,new Vec3(1,2,2),muzzle).hasForwardTravel(1e-4)); // collision before rendered muzzle
        assertFalse(beam(frame,source,new Vec3(8,2,1),muzzle).hasForwardTravel(1e-4)); // side wall, no forward distance
    }
    private static GunMuzzleFlashClient.Sample beam(ActualMuzzleFrame frame,
            com.juicyslew.moonstation14.ms14.combat.ShotVisualSource source,Vec3 endpoint,Vec3 metadataEye){
        var entry=new GunMuzzleFlashClient.Entry(effect(0),UUID.randomUUID(),0,ResourceLocation.withDefaultNamespace("overworld"),metadataEye,new Vec3(0,0,1),0,endpoint,GunMuzzleFlashPayload.Kind.BEAM,source,frame);
        return new GunMuzzleFlashClient.Sample(entry,1,frame);
    }
}
