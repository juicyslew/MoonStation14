package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.DamageSpecifierData;
import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnarmedMeleeProfileResolverTest {
    private static final UUID BODY = UUID.randomUUID(), ACCOUNT = UUID.randomUUID();
    private static final PrototypeCatalog<MeleeWeaponProfile> CATALOG = new PrototypeCatalog<>(Map.of());

    @Test void componentConversionPreservesNpcFieldsAndSharedFallbackDefaults() {
        var component = new MeleeAttackComponent(new DamageSpecifierData(Map.of("slash", 5f, "piercing", 4f)),
                1f, 1.5f, MeleeSoundData.DEFAULT, ResourceLocation.parse("moonstation14:weapon_arc_claw"));
        var converted = UnarmedMeleeProfileResolver.resolve(component);
        assertEquals(component.damage().types(), converted.damage());
        assertEquals(component.attackRate(), converted.attackRate());
        assertEquals(component.range(), converted.range());
        assertEquals(component.audio(), converted.audio());
        assertEquals(component.arc(), converted.arc());
        assertEquals(.5f, converted.bluntStaminaDamageFactor());
        assertTrue(converted.resetOnHandSelected());
        assertEquals(1f, converted.animationOffset());
        assertEquals(1f, converted.clickDamageModifier());
        assertEquals(120f, converted.angleDegrees());
        assertEquals(.6f, converted.wideOriginHeightRatio());
        assertEquals(ResourceLocation.parse("moonstation14:weapon_arc_slash"), converted.wideArc());
        assertTrue(converted.altDisarm());
        assertEquals(Map.of("blunt", 5f), UnarmedMeleeProfileResolver.resolve(new MeleeAttackComponent(
                MeleeAttackComponent.DEFAULT_DAMAGE, MeleeAttackComponent.DEFAULT_ATTACK_RATE,
                MeleeAttackComponent.DEFAULT_RANGE)).damage());
    }

    @Test void controlledHandProjectionUsesResolvedUnarmedComponentButExplicitMeleeWeaponWins() {
        var snapshot = new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 4, 9,
                "main", List.of(new BodyHandStateSnapshot.Hand("main", null, null, 0),
                new BodyHandStateSnapshot.Hand("off", null, null, 0)), List.of());
        var claw = UnarmedMeleeProfileResolver.resolve(new MeleeAttackComponent(
                new DamageSpecifierData(Map.of("slash", 5f, "piercing", 4f)), 1f, 1.5f));
        var projected = ClientMeleeWeaponResolver.project(BODY, 4, snapshot, CATALOG, Optional.of(claw)).orElseThrow();
        assertEquals(MeleeWeaponResolver.Kind.UNARMED, projected.kind());
        assertEquals(claw, projected.weapon());
        var explicitHuman = new MeleeWeaponData(Map.of("blunt", 5f), 1f, 1.5f, false, false, 1f, false);
        assertEquals(explicitHuman, ClientMeleeWeaponResolver.project(BODY, 4, snapshot, CATALOG,
                Optional.of(explicitHuman)).orElseThrow().weapon());
        assertTrue(ClientMeleeWeaponResolver.project(BODY, 4, snapshot, CATALOG, Optional.empty()).isEmpty());
    }

    @Test void meleeHeldAndGlovesRetainPrecedenceOverResolvedUnarmedProfile() {
        var unarmed = UnarmedMeleeProfileResolver.resolve(new MeleeAttackComponent(
                new DamageSpecifierData(Map.of("blunt", 5f)), 1f, 1.5f));
        var heldProfile = new MeleeWeaponProfile(new MeleeWeaponData(Map.of("slash", 10f), 1f, 1.5f, false, false, 1f, false), Map.of(), Optional.empty());
        assertEquals(MeleeWeaponResolver.Kind.HELD, MeleeWeaponResolver.choose(BODY, 1, "main", true,
                Optional.of("held"), Optional.empty(), Optional.of(heldProfile), Optional.empty(), Optional.of(unarmed)).orElseThrow().kind());
        var gloves = new MeleeWeaponProfile(new MeleeWeaponData(Map.of("blunt", 2f), 1f, 1.5f, false, false, 1f, false), Map.of(), Optional.empty());
        assertEquals(MeleeWeaponResolver.Kind.GLOVES, MeleeWeaponResolver.choose(BODY, 1, "main", false,
                Optional.empty(), Optional.of("glove"), Optional.empty(), Optional.of(gloves), Optional.of(unarmed)).orElseThrow().kind());
    }
}
