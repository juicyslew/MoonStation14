package com.juicyslew.moonstation14.ms14.prototype;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProjectileProfileTest {
 private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("test",s);}
 @Test void resolvesInheritedDamageAndZeroAndPublishesCatalog(){
  var manager=new PrototypeManager(); manager.register(ModProjectileProfiles.TYPE);
  var base=JsonParser.parseString("{\"damage\":{\"piercing\":14},\"lifetime_seconds\":10}").getAsJsonObject();
  var ap=JsonParser.parseString("{\"parent\":\"test:base\",\"damage\":{\"piercing\":26},\"ignore_resistances\":true}").getAsJsonObject();
  var cap=JsonParser.parseString("{\"parent\":\"test:base\",\"damage\":{\"piercing\":0},\"delete_on_collide\":true}").getAsJsonObject();
  manager.commit(manager.stage(Map.of(ModProjectileProfiles.TYPE,Map.of(id("base"),base,id("ap"),ap,id("cap"),cap))));
  var resolved=manager.snapshot(ModProjectileProfiles.TYPE); var armorPiercing=resolved.get(id("ap")); var capBullet=resolved.get(id("cap"));
  assertEquals(Map.of("piercing",26f),armorPiercing.damage()); assertTrue(armorPiercing.ignoreResistances()); assertEquals(10f,armorPiercing.lifetimeSeconds());
  assertTrue(capBullet.damage().isEmpty()); assertTrue(capBullet.deleteOnCollide()); assertFalse(capBullet.ignoreResistances());
  var client=new PrototypeManager(); client.register(ModProjectileProfiles.TYPE); client.publishEncodedCatalogs(manager.encodePublishedCatalogs());
  assertEquals(armorPiercing,client.snapshot(ModProjectileProfiles.TYPE).get(id("ap")));
 }
 @Test void candidateBulletApAndCapVisualCompositionSurvivesCodecRoundTrip(){
  var manager=new PrototypeManager(); manager.register(ModProjectileProfiles.TYPE);
  var base=JsonParser.parseString("{\"abstract\":true,\"damage\":{\"piercing\":14},\"sprite\":{\"texture\":\"moonstation14:ss14/weapons/objects/weapons/guns/projectiles/projectiles2.rsi\",\"state\":\"bullet\",\"frame_count\":2,\"frame_seconds\":0.05,\"unshaded\":true,\"no_rot\":false}}").getAsJsonObject();
  var ap=JsonParser.parseString("{\"parent\":\"test:base\",\"sprite\":{\"texture\":\"moonstation14:ss14/weapons/objects/weapons/guns/projectiles/projectiles2.rsi\",\"state\":\"piercing\",\"frame_count\":2,\"frame_seconds\":0.05,\"unshaded\":false,\"no_rot\":false}}").getAsJsonObject();
  var cap=JsonParser.parseString("{\"parent\":\"test:base\",\"sprite\":{\"texture\":\"moonstation14:ss14/weapons/objects/fun/capgun.rsi\",\"state\":\"capbullet\",\"frame_count\":1,\"frame_seconds\":0,\"unshaded\":false,\"no_rot\":false}}").getAsJsonObject();
  var bullet=JsonParser.parseString("{\"parent\":\"test:base\"}").getAsJsonObject();
  manager.commit(manager.stage(Map.of(ModProjectileProfiles.TYPE,Map.of(id("base"),base,id("bullet"),bullet,id("ap"),ap,id("cap"),cap))));
  var resolved=manager.snapshot(ModProjectileProfiles.TYPE); var bulletSprite=resolved.get(id("bullet")).sprite(); var piercing=resolved.get(id("ap")).sprite(); var capSprite=resolved.get(id("cap")).sprite();
  assertEquals("bullet",bulletSprite.state()); assertEquals(2,bulletSprite.frameCount()); assertEquals(.05f,bulletSprite.frameSeconds()); assertTrue(bulletSprite.unshaded());
  assertEquals("piercing",piercing.state()); assertFalse(piercing.unshaded()); assertEquals(bulletSprite.texture(),piercing.texture());
  assertEquals("capbullet",capSprite.state()); assertEquals(1,capSprite.frameCount()); assertEquals("moonstation14:ss14/weapons/objects/fun/capgun.rsi",capSprite.texture().toString());
  assertEquals(resolved.get(id("ap")),ProjectileProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,ProjectileProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,resolved.get(id("ap"))).getOrThrow()).getOrThrow());
 }
 @Test void packagedProjectileCandidatesResolveTheirAuthoredVisuals(){
  var manager=new PrototypeManager(); manager.register(ModProjectileProfiles.TYPE);
  var raw=new java.util.LinkedHashMap<ResourceLocation,com.google.gson.JsonObject>();
  for(String name:List.of("bullet_magnum","bullet_magnum_ap","bullet_cap","base_bullet","base_bullet_ap")){
   String path="data/moonstation14/moonstation14/projectile_profile/"+name+".json";
   try(var stream=getClass().getClassLoader().getResourceAsStream(path)){assertNotNull(stream,path);raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14",name),JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());}
   catch(java.io.IOException e){throw new AssertionError(e);}
  }
  manager.commit(manager.stage(Map.of(ModProjectileProfiles.TYPE,raw)));
  var catalog=manager.snapshot(ModProjectileProfiles.TYPE);
  assertEquals("bullet",catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).sprite().state());
  assertEquals("piercing",catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum_ap")).sprite().state());
  assertFalse(catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum_ap")).sprite().unshaded());
  assertEquals("capbullet",catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_cap")).sprite().state());
   var inherited=catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).muzzleFlash();
   assertNotNull(inherited); assertEquals("muzzle_bullet",inherited.sprite().state()); assertTrue(inherited.sprite().unshaded()); assertEquals(.4f,inherited.lifetimeSeconds());
   var impact=catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).impactEffect();
   assertNotNull(impact); assertEquals("impact_bullet",impact.sprite().state()); assertEquals(5,impact.sprite().frameCount()); assertEquals(.05f,impact.sprite().frameSeconds()); assertTrue(impact.sprite().unshaded()); assertEquals(.25f,impact.lifetimeSeconds());
    assertEquals("moonstation14:ss14/weapons/guns/hits/bullet_hit",catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).hitSound().event().toString());
    var flyBy=catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).flyBySound(); assertNotNull(flyBy); assertEquals(.1f,flyBy.probability()); assertEquals(1.5f,flyBy.range()); assertEquals("moonstation14:ss14/weapons/guns/miss/bullet_miss",flyBy.cue().event().toString());
   for(String name:List.of("bullet_magnum_ap","bullet_cap")) assertEquals(inherited,catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14",name)).muzzleFlash());
   for(String name:List.of("bullet_magnum_ap","bullet_cap")) { assertEquals(impact,catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14",name)).impactEffect()); assertEquals(catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum")).hitSound(),catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14",name)).hitSound()); }
   var suppressed=JsonParser.parseString("{\"parent\":\"test:base\",\"muzzle_flash\":null,\"impact_effect\":null,\"hit_sound\":null}").getAsJsonObject();
  var custom=new PrototypeManager(); custom.register(ModProjectileProfiles.TYPE);
   custom.commit(custom.stage(Map.of(ModProjectileProfiles.TYPE,Map.of(id("base"),JsonParser.parseString("{\"muzzle_flash\":{\"sprite\":{\"texture\":\"test:fx\",\"state\":\"flash\"},\"lifetime_seconds\":0.4},\"impact_effect\":{\"sprite\":{\"texture\":\"test:fx\",\"state\":\"impact\"},\"lifetime_seconds\":0.25},\"hit_sound\":{\"event\":\"test:hit\"}}").getAsJsonObject(),id("suppressed"),suppressed,id("partial"),JsonParser.parseString("{\"parent\":\"test:base\",\"impact_effect\":{\"lifetime_seconds\":0.5},\"hit_sound\":{\"volume_db\":-3}}").getAsJsonObject()))));
   assertNull(custom.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).muzzleFlash());
   assertNull(custom.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).impactEffect()); assertNull(custom.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).hitSound());
   assertEquals("impact",custom.snapshot(ModProjectileProfiles.TYPE).get(id("partial")).impactEffect().sprite().state()); assertEquals(.5f,custom.snapshot(ModProjectileProfiles.TYPE).get(id("partial")).impactEffect().lifetimeSeconds()); assertEquals(-3f,custom.snapshot(ModProjectileProfiles.TYPE).get(id("partial")).hitSound().volumeDb());
  var synced=new PrototypeManager(); synced.register(ModProjectileProfiles.TYPE); synced.publishEncodedCatalogs(custom.encodePublishedCatalogs());
   assertNull(synced.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).muzzleFlash());
   assertNull(synced.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).impactEffect()); assertNull(synced.snapshot(ModProjectileProfiles.TYPE).get(id("suppressed")).hitSound());
  var encoded=ProjectileProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14","bullet_magnum"))).getOrThrow();
  assertEquals(inherited,ProjectileProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().muzzleFlash());
 }
}
