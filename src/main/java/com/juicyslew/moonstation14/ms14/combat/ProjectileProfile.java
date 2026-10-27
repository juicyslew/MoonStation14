package com.juicyslew.moonstation14.ms14.combat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
/** Supported subset of Projectile damage and timed despawn composition. */
 public record ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite, ProjectileSpriteEffectData muzzleFlash, ProjectileSpriteEffectData impactEffect, PositionalSoundCue hitSound, FlyByData flyBySound) {
  public record FlyByData(float probability, float range, PositionalSoundCue cue) {
   public static final Codec<FlyByData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.FLOAT.fieldOf("probability").forGetter(FlyByData::probability),Codec.FLOAT.fieldOf("range").forGetter(FlyByData::range),PositionalSoundCue.codec(0f,0f).fieldOf("sound").forGetter(FlyByData::cue)).apply(i,FlyByData::new));
   public FlyByData { if(!Float.isFinite(probability)||probability<0||probability>1||!Float.isFinite(range)||range<0||cue==null)throw new IllegalArgumentException("invalid fly-by sound"); }
  }
 public record SpriteData(ResourceLocation texture, String state, int frameWidth, int frameHeight, int frameCount, float frameSeconds, boolean unshaded, boolean noRot) {
  public static final Codec<SpriteData> CODEC=RecordCodecBuilder.create(i->i.group(ResourceLocation.CODEC.fieldOf("texture").forGetter(SpriteData::texture),Codec.STRING.fieldOf("state").forGetter(SpriteData::state),Codec.INT.optionalFieldOf("frame_width",32).forGetter(SpriteData::frameWidth),Codec.INT.optionalFieldOf("frame_height",32).forGetter(SpriteData::frameHeight),Codec.INT.optionalFieldOf("frame_count",1).forGetter(SpriteData::frameCount),Codec.FLOAT.optionalFieldOf("frame_seconds",0f).forGetter(SpriteData::frameSeconds),Codec.BOOL.optionalFieldOf("unshaded",false).forGetter(SpriteData::unshaded),Codec.BOOL.optionalFieldOf("no_rot",false).forGetter(SpriteData::noRot)).apply(i,SpriteData::new));
  public SpriteData { if(frameWidth<=0||frameHeight<=0||frameCount<=0||!Float.isFinite(frameSeconds)||frameSeconds<0) throw new IllegalArgumentException("invalid projectile sprite data"); }
  }
  /** Supported presentation subset of AmmoComponent.muzzleFlash and its effect prototype. */
  public record ProjectileSpriteEffectData(SpriteData sprite, float lifetimeSeconds) {
   public static final Codec<ProjectileSpriteEffectData> CODEC=RecordCodecBuilder.create(i->i.group(SpriteData.CODEC.fieldOf("sprite").forGetter(ProjectileSpriteEffectData::sprite),Codec.FLOAT.optionalFieldOf("lifetime_seconds",.4f).forGetter(ProjectileSpriteEffectData::lifetimeSeconds)).apply(i,ProjectileSpriteEffectData::new));
   public ProjectileSpriteEffectData { if(sprite==null||!Float.isFinite(lifetimeSeconds)||lifetimeSeconds<=0)throw new IllegalArgumentException("invalid projectile sprite effect data"); }
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
     FlyByData.CODEC.optionalFieldOf("fly_by_sound").forGetter(p->java.util.Optional.ofNullable(p.flyBySound()))
    ).apply(i,(d,r,c,l,s,m,impact,hit,fly)->new ProjectileProfile(d,r,c,l,s.orElse(null),m.orElse(null),impact.orElse(null),hit.orElse(null),fly.orElse(null))));
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,null,null,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,null,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage, boolean ignoreResistances, boolean deleteOnCollide, float lifetimeSeconds, SpriteData sprite, ProjectileSpriteEffectData muzzleFlash) { this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,null,null,null); }
   public ProjectileProfile(Map<String,Float> damage,boolean ignoreResistances,boolean deleteOnCollide,float lifetimeSeconds,SpriteData sprite,ProjectileSpriteEffectData muzzleFlash,ProjectileSpriteEffectData impactEffect,PositionalSoundCue hitSound){this(damage,ignoreResistances,deleteOnCollide,lifetimeSeconds,sprite,muzzleFlash,impactEffect,hitSound,null);}
 public ProjectileProfile { damage=com.juicyslew.moonstation14.ms14.damage.DamageKeys.validateState(damage); if(!Float.isFinite(lifetimeSeconds)||lifetimeSeconds<=0) throw new IllegalArgumentException("lifetime must be positive"); }
}
