package com.juicyslew.moonstation14.ms14.combat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
/** Typed ranged-shot composition; the historic type name is retained for moving shots. */
  public record ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite, ProjectileSpriteEffectData muzzleFlash, ProjectileSpriteEffectData impactEffect, PositionalSoundCue hitSound, FlyByData flyBySound, HitscanData hitscan, SpreadData spread, float staminaDamage, boolean ignoreShooter, ProjectileSpriteEffectData travelEffect) {
  public record HitscanData(float range) {
   public static final Codec<HitscanData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.FLOAT.optionalFieldOf("range",20f).forGetter(HitscanData::range)).apply(i,HitscanData::new));
   public HitscanData { if(!Float.isFinite(range)||range<=0)throw new IllegalArgumentException("hitscan range must be positive"); }
  }
  public record SpreadData(int count,float degrees,java.util.Optional<ResourceLocation> childProjectile) {
   public static final Codec<SpreadData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.INT.fieldOf("count").forGetter(SpreadData::count),Codec.FLOAT.fieldOf("degrees").forGetter(SpreadData::degrees),ResourceLocation.CODEC.optionalFieldOf("child_projectile").forGetter(SpreadData::childProjectile)).apply(i,SpreadData::new));
   public SpreadData(int count,float degrees){this(count,degrees,java.util.Optional.empty());}
   public SpreadData { childProjectile=childProjectile==null?java.util.Optional.empty():childProjectile; if(count<1||count>128||!Float.isFinite(degrees)||degrees<0||degrees>180)throw new IllegalArgumentException("invalid shot spread"); }
  }
  public record FlyByData(float probability, float range, PositionalSoundCue cue) {
   public static final Codec<FlyByData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.FLOAT.fieldOf("probability").forGetter(FlyByData::probability),Codec.FLOAT.fieldOf("range").forGetter(FlyByData::range),PositionalSoundCue.codec(0f,0f).fieldOf("sound").forGetter(FlyByData::cue)).apply(i,FlyByData::new));
   public FlyByData { if(!Float.isFinite(probability)||probability<0||probability>1||!Float.isFinite(range)||range<0||cue==null)throw new IllegalArgumentException("invalid fly-by sound"); }
  }
   public record SpriteData(ResourceLocation texture, String state, int frameWidth, int frameHeight, int frameCount, int frameColumns, float frameSeconds, boolean unshaded, boolean noRot, int color, float rotationOffset) {
     public static final Codec<SpriteData> CODEC=RecordCodecBuilder.create(i->i.group(ResourceLocation.CODEC.fieldOf("texture").forGetter(SpriteData::texture),Codec.STRING.fieldOf("state").forGetter(SpriteData::state),Codec.INT.optionalFieldOf("frame_width",32).forGetter(SpriteData::frameWidth),Codec.INT.optionalFieldOf("frame_height",32).forGetter(SpriteData::frameHeight),Codec.INT.optionalFieldOf("frame_count",1).forGetter(SpriteData::frameCount),Codec.INT.optionalFieldOf("frame_columns",0).forGetter(SpriteData::frameColumns),Codec.FLOAT.optionalFieldOf("frame_seconds",0f).forGetter(SpriteData::frameSeconds),Codec.BOOL.optionalFieldOf("unshaded",false).forGetter(SpriteData::unshaded),Codec.BOOL.optionalFieldOf("no_rot",false).forGetter(SpriteData::noRot),Codec.INT.optionalFieldOf("color_argb",0xffffffff).forGetter(SpriteData::color),Codec.FLOAT.optionalFieldOf("rotation_offset_degrees",0f).forGetter(SpriteData::rotationOffset)).apply(i,SpriteData::new));
     public SpriteData(ResourceLocation texture,String state,int frameWidth,int frameHeight,int frameCount,int frameColumns,float frameSeconds,boolean unshaded,boolean noRot,int color){this(texture,state,frameWidth,frameHeight,frameCount,frameColumns,frameSeconds,unshaded,noRot,color,0f);}
     public SpriteData(ResourceLocation texture,String state,int frameWidth,int frameHeight,int frameCount,float frameSeconds,boolean unshaded,boolean noRot,int color){this(texture,state,frameWidth,frameHeight,frameCount,frameCount,frameSeconds,unshaded,noRot,color,0f);}
     public SpriteData(ResourceLocation texture,String state,int frameWidth,int frameHeight,int frameCount,float frameSeconds,boolean unshaded,boolean noRot){this(texture,state,frameWidth,frameHeight,frameCount,frameCount,frameSeconds,unshaded,noRot,0xffffffff,0f);}
    public SpriteData { if(frameColumns==0)frameColumns=frameCount; if(frameWidth<=0||frameHeight<=0||frameCount<=0||frameColumns<=0||frameColumns>frameCount||!Float.isFinite(frameSeconds)||frameSeconds<0||!Float.isFinite(rotationOffset)) throw new IllegalArgumentException("invalid projectile sprite data"); }
  }
  /** Supported presentation subset of AmmoComponent.muzzleFlash and its effect prototype. */
  public record ProjectileSpriteEffectData(SpriteData sprite, float lifetimeSeconds, boolean worldAligned, float forwardOffset, float startInset, float endInset) {
    public static final Codec<ProjectileSpriteEffectData> CODEC=RecordCodecBuilder.create(i->i.group(SpriteData.CODEC.fieldOf("sprite").forGetter(ProjectileSpriteEffectData::sprite),Codec.FLOAT.optionalFieldOf("lifetime_seconds",.4f).forGetter(ProjectileSpriteEffectData::lifetimeSeconds),Codec.BOOL.optionalFieldOf("world_aligned",false).forGetter(ProjectileSpriteEffectData::worldAligned),Codec.FLOAT.optionalFieldOf("forward_offset",0f).forGetter(ProjectileSpriteEffectData::forwardOffset),Codec.FLOAT.optionalFieldOf("start_inset",0f).forGetter(ProjectileSpriteEffectData::startInset),Codec.FLOAT.optionalFieldOf("end_inset",0f).forGetter(ProjectileSpriteEffectData::endInset)).apply(i,ProjectileSpriteEffectData::new));
    public ProjectileSpriteEffectData(SpriteData sprite,float lifetimeSeconds){this(sprite,lifetimeSeconds,false,0,0,0);}
    public ProjectileSpriteEffectData { if(sprite==null||!Float.isFinite(lifetimeSeconds)||lifetimeSeconds<=0||!Float.isFinite(forwardOffset)||!Float.isFinite(startInset)||!Float.isFinite(endInset)||startInset<0||endInset<0)throw new IllegalArgumentException("invalid projectile sprite effect data"); }
  }
 public static final Codec<ProjectileProfile> CODEC=RecordCodecBuilder.create(i->i.group(
  Codec.unboundedMap(Codec.STRING,Codec.FLOAT).optionalFieldOf("damage",Map.of()).forGetter(ProjectileProfile::damage),
  Codec.BOOL.optionalFieldOf("ignore_resistances",false).forGetter(ProjectileProfile::ignoreResistances),
  Codec.BOOL.optionalFieldOf("delete_on_collide",true).forGetter(ProjectileProfile::deleteOnCollide),
   Codec.FLOAT.optionalFieldOf("lifetime_seconds",10f).forGetter(ProjectileProfile::lifetimeSeconds),
    SpriteData.CODEC.optionalFieldOf("sprite").forGetter(p->java.util.Optional.ofNullable(p.sprite())),
    ProjectileSpriteEffectData.CODEC.optionalFieldOf("muzzle_flash").forGetter(p->java.util.Optional.ofNullable(p.muzzleFlash())),
    ProjectileSpriteEffectData.CODEC.optionalFieldOf("impact_effect").forGetter(p->java.util.Optional.ofNullable(p.impactEffect())),
     PositionalSoundCue.codec(0f,0f).optionalFieldOf("hit_sound").forGetter(p->java.util.Optional.ofNullable(p.hitSound())),
      FlyByData.CODEC.optionalFieldOf("fly_by_sound").forGetter(p->java.util.Optional.ofNullable(p.flyBySound())),
      HitscanData.CODEC.optionalFieldOf("hitscan").forGetter(p->java.util.Optional.ofNullable(p.hitscan())),
      SpreadData.CODEC.optionalFieldOf("spread").forGetter(p->java.util.Optional.ofNullable(p.spread())),
      Codec.FLOAT.optionalFieldOf("stamina_damage",0f).forGetter(ProjectileProfile::staminaDamage),
       Codec.BOOL.optionalFieldOf("ignore_shooter",true).forGetter(ProjectileProfile::ignoreShooter),
       ProjectileSpriteEffectData.CODEC.optionalFieldOf("travel_effect").forGetter(p->java.util.Optional.ofNullable(p.travelEffect()))
      ).apply(i,(d,r,c,l,s,m,impact,hit,fly,hitscan,spread,stamina,ignore,travel)->new ProjectileProfile(d,r,c,l,s.orElse(null),m.orElse(null),impact.orElse(null),hit.orElse(null),fly.orElse(null),hitscan.orElse(null),spread.orElse(null),stamina,ignore,travel.orElse(null))));
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,null,null,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,null,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite, ProjectileSpriteEffectData muzzleFlash) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage,boolean ignoreResistances,boolean deleteOnCollide,float lifetimeSeconds,SpriteData sprite,ProjectileSpriteEffectData muzzleFlash,ProjectileSpriteEffectData impactEffect,PositionalSoundCue hitSound){this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,impactEffect,hitSound,null);}
  public ProjectileProfile(Map<String,Float> damage,boolean ignoreResistances,boolean deleteOnCollide,float lifetimeSeconds,SpriteData sprite,ProjectileSpriteEffectData muzzleFlash,ProjectileSpriteEffectData impactEffect,PositionalSoundCue hitSound,FlyByData flyBySound){this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,impactEffect,hitSound,flyBySound,null,null,0f,true);}
   public ProjectileProfile(Map<String,Float> damage,boolean ignoreResistances,boolean deleteOnCollide,float lifetimeSeconds,SpriteData sprite,ProjectileSpriteEffectData muzzleFlash,ProjectileSpriteEffectData impactEffect,PositionalSoundCue hitSound,FlyByData flyBySound,HitscanData hitscan,SpreadData spread,float staminaDamage,boolean ignoreShooter){this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,impactEffect,hitSound,flyBySound,hitscan,spread,staminaDamage,ignoreShooter,null);}
   public ProjectileProfile { damage=com.juicyslew.moonstation14.ms14.damage.DamageKeys.validateState(damage); if(!Float.isFinite(lifetimeSeconds)||lifetimeSeconds<=0) throw new IllegalArgumentException("lifetime must be positive"); if(!Float.isFinite(staminaDamage)||staminaDamage<0)throw new IllegalArgumentException("stamina damage must be nonnegative"); }
}
