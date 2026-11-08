package com.juicyslew.moonstation14.entities.client;

import com.juicyslew.moonstation14.entities.SpentCasingParticleOptions;
import com.juicyslew.moonstation14.ms14.combat.SpentCasingMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;

/** Client-only casing with collider movement; no server entity or collision/audio messages. */
public final class SpentCasingParticle extends TextureSheetParticle {
    private final ResourceLocation landing;
    private final float damping;
    private final float volumeDb, pitchVariation;
    private final ResourceLocation sprite;
    private boolean landed;
    private boolean clippedX,clippedY,clippedZ;
    private SpentCasingParticle(ClientLevel level, double x,double y,double z,double xd,double yd,double zd,SpentCasingParticleOptions options){
        super(level,x,y,z,xd,yd,zd); this.xd=xd;this.yd=yd;this.zd=zd;this.landing=options.landingEvent();this.damping=options.damping();this.lifetime=options.lifetimeTicks();this.gravity=options.gravity();this.hasPhysics=true;this.volumeDb=options.volumeDb();this.pitchVariation=options.pitchVariation();
        var generic=com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.require(
                com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.catalog(level),
                com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles.GENERIC_ID);
        this.quadSize=SpentCasingParticleScale.halfExtent(generic); this.sprite=options.sprite();
        setSprite(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(sprite));
    }
    @Override public void tick(){
        xo=x;yo=y;zo=z; age++; if(age>=lifetime){remove();return;}
        double preX=xd,preY=yd,preZ=zd;
        move(preX,preY-gravity,preZ);
        var step=SpentCasingMotion.step(preX,preY,preZ,gravity,damping,.32f,clippedX,clippedY,clippedZ,onGround,landed);
        xd=step.vx();yd=step.vy();zd=step.vz();
        if(step.landedNow()){landed=true;playLanding();}
        oRoll=roll;
        roll=SpentCasingMotion.advanceRoll(oRoll,roll,xd,yd,zd,landed);
    }
    /** Native clipped voxel collision without Particle's one-way stoppedByCollision latch. */
    @Override public void move(double dx,double dy,double dz){
        if(!hasPhysics){setBoundingBox(getBoundingBox().move(dx,dy,dz));setLocationFromBoundingbox();return;}
        var requested=new net.minecraft.world.phys.Vec3(dx,dy,dz);
        var box=getBoundingBox();
        var clipped=net.minecraft.world.entity.Entity.collideBoundingBox(null,requested,box,level,java.util.List.of());
        clippedX=clipped.x!=dx;clippedY=clipped.y!=dy;clippedZ=clipped.z!=dz;
        onGround=clippedY&&dy<0;
        setBoundingBox(box.move(clipped));setLocationFromBoundingbox();
    }
    private void playLanding(){var mc=Minecraft.getInstance();if(mc.level==null)return;var event=BuiltInRegistries.SOUND_EVENT.getOptional(landing).orElse(null);if(event!=null){float pitch=(float)(1+((random.nextFloat()*2-1)*pitchVariation));mc.level.playLocalSound(x,y,z,event,SoundSource.PLAYERS,(float)Math.pow(10,volumeDb/20),pitch,false);}}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.TERRAIN_SHEET;}
    public static final class Provider implements ParticleProvider<SpentCasingParticleOptions>{
        @Override public Particle createParticle(SpentCasingParticleOptions options,ClientLevel level,double x,double y,double z,double xd,double yd,double zd){return new SpentCasingParticle(level,x,y,z,xd,yd,zd,options);}
    }
}
