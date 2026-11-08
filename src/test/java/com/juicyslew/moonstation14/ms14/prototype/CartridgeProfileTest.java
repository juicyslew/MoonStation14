package com.juicyslew.moonstation14.ms14.prototype;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CartridgeProfileTest {
 private static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("test",s);}
 @Test void resolvesInheritanceDefaultsAndPublishesCatalog(){
  var manager=new PrototypeManager(); manager.register(ModCartridgeProfiles.TYPE);
  var base=JsonParser.parseString("{\"projectile\":\"test:bullet\",\"tags\":[\"test:cartridge\",\"test:magnum\"]}").getAsJsonObject();
  var derived=JsonParser.parseString("{\"parent\":\"test:base\",\"projectile\":\"test:ap\"}").getAsJsonObject();
  manager.commit(manager.stage(Map.of(ModCartridgeProfiles.TYPE,Map.of(id("base"),base,id("derived"),derived))));
  var profile=manager.snapshot(ModCartridgeProfiles.TYPE).get(id("derived"));
  assertEquals(id("ap"),profile.projectile()); assertFalse(profile.deleteOnSpawn()); assertEquals(Set.of(id("cartridge"),id("magnum")),profile.tags());
  var client=new PrototypeManager(); client.register(ModCartridgeProfiles.TYPE); client.publishEncodedCatalogs(manager.encodePublishedCatalogs());
  assertEquals(profile,client.snapshot(ModCartridgeProfiles.TYPE).get(id("derived")));
  }

 @Test void spentPresentationInheritsAndRetainsDefaultMotionAndCueVolume(){
  var manager=new PrototypeManager(); manager.register(ModCartridgeProfiles.TYPE);
  var base=JsonParser.parseString("{\"projectile\":\"test:bullet\",\"spent_presentation\":{\"sprite\":\"test:spent\",\"landing_cue\":{\"event\":\"test:fall\",\"volume_db\":-1}}}").getAsJsonObject();
  var child=JsonParser.parseString("{\"parent\":\"test:base\",\"projectile\":\"test:child_bullet\"}").getAsJsonObject();
  manager.commit(manager.stage(Map.of(ModCartridgeProfiles.TYPE,Map.of(id("base"),base,id("child"),child))));
  var p=manager.snapshot(ModCartridgeProfiles.TYPE).get(id("child")).spentPresentation();
  assertNotNull(p); assertEquals(id("spent"),p.sprite()); assertEquals(id("fall"),p.landingCue().event());
  assertEquals(-1f,p.landingCue().volumeDb()); assertEquals(.16f,p.initialSpeed()); assertEquals(1.5f,p.lifetimeSeconds());
 }

 @Test void casingPresentationAssetsAndSoundEventsCloseOverBundledResources() throws Exception {
  try(var in=getClass().getClassLoader().getResourceAsStream("assets/moonstation14/sounds.json")) {
  assertNotNull(in); var sounds=JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  for(String event:List.of("casing_fall_1","casing_fall_2","casing_fall_3","shotgun_fall")) {
   var entries=sounds.getAsJsonObject("ss14/weapons/guns/casings/"+event).getAsJsonArray("sounds");
   var id=ResourceLocation.parse(entries.get(0).getAsString());
   assertNotNull(getClass().getClassLoader().getResource("assets/"+id.getNamespace()+"/sounds/"+id.getPath()+".ogg"),event);
  }
  }
  for(String texture:List.of("ss14/guns/cartridge_spent_body","ss14/guns/shell_spent_body",
          "ss14/weapons/objects/weapons/guns/ammunition/casings/ammo_casing.rsi/base-spent"))
   assertNotNull(getClass().getClassLoader().getResource("assets/moonstation14/textures/"+texture+".png"),texture);
 }

 @Test void bundledSelectedSpentProfileDataUsesInheritedMagnumPresentation() throws Exception {
  var loader=getClass().getClassLoader();
  var base=readProfile(loader,"base_cartridge_magnum.json");
  var ap=readProfile(loader,"cartridge_magnum_ap.json");
  assertTrue(base.has("spent_presentation")); assertFalse(ap.has("spent_presentation"));
  var merged=new CartridgeProfileMergeStrategy().merge("spent_presentation",id("ap"),ap,List.of(base));
  assertNotNull(merged);
  var decoded=SpentCartridgePresentation.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,merged).getOrThrow(IllegalStateException::new);
   assertEquals(ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/weapons/objects/weapons/guns/ammunition/casings/ammo_casing.rsi/base-spent"),decoded.sprite());
  assertEquals(-1f,decoded.landingCue().volumeDb());
 }

 private static com.google.gson.JsonObject readProfile(ClassLoader loader,String file) throws Exception {
  String path="data/moonstation14/moonstation14/cartridge_profile/"+file;
  try(var in=loader.getResourceAsStream(path)){assertNotNull(in,path);return JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}
 }
}
