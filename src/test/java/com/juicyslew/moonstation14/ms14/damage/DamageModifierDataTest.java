package com.juicyslew.moonstation14.ms14.damage;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DamageModifierDataTest {
 @Test void glassTableFlatFirstAndZeroCoefficient() {
  var m = new DamageModifierData(Map.of("blunt",1.2f,"slash",.5f,"piercing",1f,"heat",.8f,"shock",0f), Map.of("slash",5f,"piercing",5f,"heat",5f));
  assertEquals(Map.of("blunt",.6f), DamageModifierData.apply(Map.of("blunt",.5f,"slash",.5f,"piercing",.5f,"heat",.5f,"shock",.5f),m));
  assertEquals(Map.of("blunt",6f), DamageModifierData.apply(Map.of("blunt",5f,"slash",5f,"piercing",5f,"heat",5f,"shock",5f),m));
  assertEquals(Map.of("blunt",12f,"slash",2.5f,"piercing",5f,"heat",4f), DamageModifierData.apply(Map.of("blunt",10f,"slash",10f,"piercing",10f,"heat",10f,"shock",10f),m));
 }
 @Test void scaleThenFlatHealingAndIdentity() {
  var m = new DamageModifierData(Map.of("heat",.8f),Map.of("heat",5f));
  assertTrue(DamageModifierData.apply(Map.of("heat",5f),m).isEmpty());
  assertEquals(Map.of("heat",-1f), DamageModifierData.apply(Map.of("heat",-1f),m));
  assertEquals(Map.of("heat",.12345f), DamageModifierData.apply(Map.of("heat",.12345f),DamageModifierData.EMPTY));
 }
}
