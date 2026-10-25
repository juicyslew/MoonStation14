package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileSpriteFramesTest {
    private ProjectileProfile.SpriteData sprite(int count,float seconds) {
        return new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/weapons/objects/weapons/guns/projectiles/projectiles2.rsi"),"bullet",32,32,count,seconds,true,false);
    }
    @Test void selectsAndWrapsTwoFramesAtTwentyHzAndProducesAtlasUvs() {
        var s=sprite(2,.05f);
        assertEquals(new ProjectileSpriteFrames.Frame(0,0,.5f),ProjectileSpriteFrames.at(s,0,0));
        assertEquals(0,ProjectileSpriteFrames.at(s,0,.99f).index());
        assertEquals(1,ProjectileSpriteFrames.at(s,1,0).index());
        assertEquals(1,ProjectileSpriteFrames.at(s,1,.99f).index());
        assertEquals(0,ProjectileSpriteFrames.at(s,2,0).index());
        assertEquals(.5f,ProjectileSpriteFrames.at(s,0,0).u1());
        assertEquals("moonstation14:ss14/weapons/objects/weapons/guns/projectiles/projectiles2.rsi",s.texture().toString());
    }
    @Test void staticCapRemainsOnSingleFrameAndInvalidSpriteDataIsRejected() {
        var cap=new ProjectileProfile.SpriteData(ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/weapons/objects/fun/capgun.rsi"),"capbullet",32,32,1,0,false,false);
        assertEquals(0,ProjectileSpriteFrames.at(cap,100,.99f).index());
        assertThrows(IllegalArgumentException.class,()->new ProjectileProfile.SpriteData(cap.texture(),"x",32,32,0,.05f,false,false));
        assertThrows(IllegalArgumentException.class,()->new ProjectileProfile.SpriteData(cap.texture(),"x",32,32,1,Float.NaN,false,false));
    }
}
