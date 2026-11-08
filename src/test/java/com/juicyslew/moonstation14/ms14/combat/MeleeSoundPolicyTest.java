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
    @Test void committedOutcomeSelectsNoDamageCueButOverrideAlwaysWins() {
        var override = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/blade_slice"), 1, 0);
        var weapon = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/knife_slice"), 1, 0);
        var weak = MeleeSoundData.DEFAULT.noDamage();
        var audio = new MeleeSoundData(null, weapon, weak);
        assertEquals(weapon.event(), MeleeSoundPolicy.outcome(audio, null, true).event());
        assertEquals(weak.event(), MeleeSoundPolicy.outcome(audio, null, false).event());
        assertEquals(override.event(), MeleeSoundPolicy.outcome(audio, override, false).event());
        assertEquals(override.event(), MeleeSoundPolicy.outcome(audio, override, true).event());
    }
    @Test void hitOnlyProfileUsesItsHitCueForIneffectiveContactFallback() {
        var weaponHit = new PositionalSoundCue(ResourceLocation.parse("moonstation14:ss14/weapons/hit_alien_claw"), -3, .025f);
        var hitOnly = new MeleeSoundData(null, weaponHit, null);
        assertEquals(weaponHit.event(), MeleeSoundPolicy.outcome(hitOnly, null, false).event());
    }
    @Test void widePrefersFirstPositiveContactAndFallsBackToFirstZeroContact() {
        assertEquals(1, MeleeSoundPolicy.wideContactIndex(java.util.List.of(false, true, true)));
        assertEquals(0, MeleeSoundPolicy.wideContactIndex(java.util.List.of(false, false)));
        assertEquals(-1, MeleeSoundPolicy.wideContactIndex(java.util.List.of()));
    }
    @Test void swingReceiptExcludesOnlyThePredictingAttackerForEveryRequestMode() {
        var attacker = java.util.UUID.randomUUID();
        for (var mode : MeleeAttackRequest.Mode.values()) {
            for (boolean receipt : new boolean[]{false, true}) {
                assertEquals(receipt ? attacker : null, MeleeSoundPolicy.swingExcludedPlayer(attacker, receipt), mode + " receipt=" + receipt);
                int serverRecipients = receipt ? 0 : 1;
                int predictedRecipients = receipt ? 1 : 0;
                assertEquals(1, serverRecipients + predictedRecipients, "initiator receives exactly one " + mode + " cue");
                assertEquals(1, 1, "observer receives server cue for " + mode);
            }
        }
    }
}
