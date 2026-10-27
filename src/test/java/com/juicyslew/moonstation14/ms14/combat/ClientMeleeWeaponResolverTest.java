package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientMeleeWeaponResolverTest {
    private static final UUID BODY = UUID.randomUUID(), ACCOUNT = UUID.randomUUID();
    private static final MeleeWeaponData WEAPON = new MeleeWeaponData(Map.of("blunt", 1f), 1f, 1.5f, false, false, 1f, false);
    private static final MeleeWeaponData ACTOR = new MeleeWeaponData(Map.of("slash", 2f), 1f, 1.5f, false, false, 1f, false);
    private static final MeleeWeaponProfile RICH = new MeleeWeaponProfile(WEAPON, Map.of("blunt", 5f),
            Optional.of(new MeleeWeaponProfile.MeleeChemicalInjectorData("food", 500, false)));
    private static final PrototypeCatalog<MeleeWeaponProfile> CATALOG = new PrototypeCatalog<>(Map.of(
            ResourceLocation.withDefaultNamespace("iron_sword"), RICH));

    private static BodyHandStateSnapshot snapshot(List<BodyHandStateSnapshot.Hand> hands,
            List<BodyHandStateSnapshot.EquipmentSlot> equipment) {
        return new BodyHandStateSnapshot(0, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 4, 9,
                "main", hands, equipment);
    }
    private static BodyHandStateSnapshot.Hand empty() { return new BodyHandStateSnapshot.Hand("off", null, null, 0); }
    private static BodyHandStateSnapshot.EquipmentSlot gloves(String token, String item) {
        return new BodyHandStateSnapshot.EquipmentSlot("gloves", token, item, token == null ? 0 : 1, null, null, 0);
    }
    private static Optional<MeleeWeaponResolver.Selection> project(BodyHandStateSnapshot s, Optional<MeleeWeaponData> actor) {
        return ClientMeleeWeaponResolver.project(BODY, 4, s, CATALOG, actor);
    }

    @Test void dtoHeldAndGlovesMapCatalogProfilesWithTheirOwnTokensAndRevision() {
        var held = project(snapshot(List.of(new BodyHandStateSnapshot.Hand("main", "held-token", "minecraft:iron_sword", 1), empty()), List.of()), Optional.of(ACTOR)).orElseThrow();
        assertEquals(MeleeWeaponResolver.Kind.HELD, held.kind());
        assertEquals(WEAPON, held.weapon()); assertEquals(9, held.handRevision()); assertEquals(Optional.of("held-token"), held.itemToken());
        assertSame(RICH, held.profile());
        var glove = project(snapshot(List.of(new BodyHandStateSnapshot.Hand("main", null, null, 0), empty()),
                List.of(gloves("glove-token", "minecraft:iron_sword"))), Optional.of(ACTOR)).orElseThrow();
        assertEquals(MeleeWeaponResolver.Kind.GLOVES, glove.kind()); assertEquals(Optional.of("glove-token"), glove.itemToken());
        assertSame(RICH, glove.profile());
    }

    @Test void dtoUnarmedAndReservedEmptyHandFallbackAreLegitimate() {
        var reserved = new BodyHandStateSnapshot.Hand("main", null, null, 0, false, null, null, 0, "pull-target");
        var selection = project(snapshot(List.of(reserved, empty()), List.of()), Optional.of(ACTOR)).orElseThrow();
        assertEquals(MeleeWeaponResolver.Kind.UNARMED, selection.kind()); assertEquals(ACTOR, selection.weapon());
        assertTrue(selection.profile().damageOnHit().isEmpty()); assertTrue(selection.profile().chemicalInjector().isEmpty());
    }

    @Test void dtoOrdinaryHeldItemBlocksFallbackAndStaleBodyOrEpochReject() {
        assertTrue(project(snapshot(List.of(new BodyHandStateSnapshot.Hand("main", "ordinary", "minecraft:stick", 1), empty()),
                List.of(gloves("glove-token", "minecraft:iron_sword"))), Optional.of(ACTOR)).isEmpty());
        var valid = snapshot(List.of(new BodyHandStateSnapshot.Hand("main", null, null, 0), empty()), List.of());
        assertTrue(ClientMeleeWeaponResolver.project(UUID.randomUUID(), 4, valid, CATALOG, Optional.of(ACTOR)).isEmpty());
        assertTrue(ClientMeleeWeaponResolver.project(BODY, 5, valid, CATALOG, Optional.of(ACTOR)).isEmpty());
    }

    @Test void dtoIncoherentHandAndGloveOwnershipFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new BodyHandStateSnapshot.Hand("main", null, "minecraft:iron_sword", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BodyHandStateSnapshot.EquipmentSlot("gloves", null, "minecraft:iron_sword", 1, null, null, 0));
    }
}
