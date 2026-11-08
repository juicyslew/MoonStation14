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
}
