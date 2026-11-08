package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileVisualSyncCodecTest {
    @Test void spriteEffectPoseAndInsetsRoundTripThroughEffectPayloadCodec(){
        var sprite=new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","beam"),"beam",32,32,9,3,.1f,true,false,0xffffffff,90);
        var effect=new ProjectileProfile.ProjectileSpriteEffectData(sprite,.48f,true,.5f,1,.5f);
        var encoded=ProjectileProfile.ProjectileSpriteEffectData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,effect).getOrThrow();
        assertEquals(effect,ProjectileProfile.ProjectileSpriteEffectData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow());
    }
    @Test void multrowAtlasColumnsRoundTripThroughExistingVisualSyncPayload() {
        var s=new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/weapons/objects/weapons/guns/projectiles/projectiles_tg.rsi"),"impact_bullet",32,32,5,2,.05f,true,false,0xffffffff,90f);
        var decoded=ProjectileVisualSyncCodec.decode(ProjectileVisualSyncCodec.encode(s));
        assertEquals(s,decoded);
        assertEquals(2,decoded.frameColumns());
        var first=ProjectileSpriteFrames.at(decoded,0,0); var last=ProjectileSpriteFrames.at(decoded,4,0);
        assertEquals(0f,first.v0()); assertEquals(1f,last.v1());
        assertEquals(0f,last.u0()); assertEquals(.5f,last.u1());
    }
    @Test void unchangedStaticVisualAndLegacyPayloadRemainReadable() {
        var s=new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("test","static"),"x",32,32,1,0,true,false);
        assertEquals(s,ProjectileVisualSyncCodec.decode(ProjectileVisualSyncCodec.encode(s)));
        assertNotNull(ProjectileVisualSyncCodec.decode("test:legacy|x|32|32|2|0.05|true|false"));
    }
}
