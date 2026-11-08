package com.juicyslew.moonstation14.ms14.combat;
import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import net.minecraft.resources.ResourceLocation;
public final class UnarmedMeleeProfileResolver {
 private UnarmedMeleeProfileResolver() { }
 public static MeleeWeaponData resolve(MeleeAttackComponent a) {
  if (a == null) return null;
  // CommonMeleeWeapon defaults: blunt stamina factor .5, reset on hand selection,
  // animation offset 1, click modifier 1, and alternate disarm enabled.
  // Keep the project's accepted wide-arc adaptation (120 degrees, .6 origin).
  return new MeleeWeaponData(a.damage().types(), a.attackRate(), a.range(), false, false, 1f, false,
          a.audio(), a.arc(), 1f, 0f, .5f, true, 120f,
          ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash"), true, .6f);
 }
}
