package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeSoundPolicyTest {
    @Test void explicitHitCueWinsEvenWhenNoDamageCueWouldOtherwiseBeUsed() {
        var hit = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/blade_slice"), -3, .025f);
        assertEquals(.05f, MeleeSoundPolicy.hit(new MeleeSoundData(null, hit, MeleeSoundData.DEFAULT.noDamage())).pitchVariation());
        assertEquals(.05f, MeleeSoundPolicy.hit(MeleeSoundData.DEFAULT).pitchVariation());
    }
    @Test void handledOrRemovedTargetsDoNotPlayHitCue() {
        assertFalse(MeleeSoundPolicy.shouldPlayHit(true, true));
        assertFalse(MeleeSoundPolicy.shouldPlayHit(false, false));
        assertTrue(MeleeSoundPolicy.shouldPlayHit(false, true));
    }
    @Test void overrideWinsWeaponCueAndWeaponCueWinsWeakHit() {
        var override = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/blade_slice"), 1, 0);
        var weapon = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/knife_slice"), 1, 0);
        assertEquals(override.event(), MeleeSoundPolicy.hit(new MeleeSoundData(null, weapon, MeleeSoundData.DEFAULT.noDamage()), override).event());
        assertEquals(.05f, MeleeSoundPolicy.hit(new MeleeSoundData(null, weapon, MeleeSoundData.DEFAULT.noDamage()), override).pitchVariation());
        assertEquals(weapon.event(), MeleeSoundPolicy.hit(new MeleeSoundData(null, weapon, MeleeSoundData.DEFAULT.noDamage()), null).event());
        assertEquals(.05f, MeleeSoundPolicy.hit(MeleeSoundData.DEFAULT, null).pitchVariation());
    }
}
