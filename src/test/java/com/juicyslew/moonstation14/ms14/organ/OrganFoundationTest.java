package com.juicyslew.moonstation14.ms14.organ;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.character.components.InitialBodyComponent;
import com.juicyslew.moonstation14.ms14.lung.LungComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OrganFoundationTest {
    private static final ResourceLocation LUNG = ResourceLocation.parse("moonstation14:human_lungs");
    private static final ResourceLocation HEART = ResourceLocation.parse("moonstation14:heart");
    private static final PrototypeCatalog<OrganData> CATALOG = new PrototypeCatalog<>(Map.of(
            LUNG, new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0))),
            HEART, new OrganData(OrganCategory.HEART, Optional.empty())));
    private static final LungOrganState STORED_LUNG = new LungOrganState(Map.of("oxygen", 1.25), 287.5);
    private static final RespirationState STORED_RESPIRATION = new RespirationState(-0.75, true, LungComponent.Phase.EXHALING);
    private static final InitialBodyComponent INITIAL = new InitialBodyComponent(Map.of(OrganCategory.LUNGS, LUNG));

    private static BodyState roundTrip(BodyState state) {
        var json = BodyAttachment.CODEC.encodeStart(JsonOps.INSTANCE, new BodyAttachment(state)).getOrThrow();
        return BodyAttachment.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().state();
    }

    @Test void bodyCodecRoundTripSeparatesExactOrganAndMobStateAndIdentity() {
        BodyState fresh = BodySystem.plan(INITIAL, CATALOG).orElseThrow();
        OrganInstance created = fresh.find(OrganCategory.LUNGS).orElseThrow();
        assertEquals(LungOrganState.EMPTY, created.lung());
        assertEquals(RespirationState.UNINITIALIZED, fresh.respiration());
        BodyState state = fresh.updateLung(created.id(), STORED_LUNG, CATALOG).updateRespiration(STORED_RESPIRATION);
        OrganInstance organ = state.find(OrganCategory.LUNGS).orElseThrow();
        assertEquals(STORED_LUNG, organ.lung());
        assertEquals(STORED_RESPIRATION, state.respiration());
        BodyState restored = roundTrip(state);
        assertEquals(state, restored);
        assertNotSame(state, restored);
        assertEquals(organ.id(), restored.find(OrganCategory.LUNGS).orElseThrow().id());
        assertEquals(STORED_RESPIRATION, restored.respiration());
        assertEquals(STORED_LUNG, restored.find(OrganCategory.LUNGS).orElseThrow().lung());
        assertEquals(Set.of("gas_moles", "temperature_kelvin"),
                BodyAttachment.CODEC.encodeStart(JsonOps.INSTANCE, new BodyAttachment(state)).getOrThrow()
                        .getAsJsonObject().getAsJsonArray("organs").get(0).getAsJsonObject().getAsJsonObject("lung").keySet());

        BodyState emptyGas = fresh.updateLung(created.id(), new LungOrganState(Map.of(), 0), CATALOG)
                .updateRespiration(new RespirationState(0, true, LungComponent.Phase.EXHALING));
        assertEquals(new LungOrganState(Map.of(), 0), emptyGas.organs().getFirst().lung());
        assertEquals(new RespirationState(0, true, LungComponent.Phase.EXHALING), emptyGas.respiration());
        assertEquals(emptyGas, roundTrip(emptyGas));
    }

    @Test void absentIsNotSavedEmpty() {
        assertEquals(BodyState.EMPTY, roundTrip(BodyState.EMPTY));
        assertFalse(BodyState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).isSuccess());
        assertFalse(BodyState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"organs\":[],\"unknown\":true}")).isSuccess());
        BodyState fresh = BodySystem.plan(INITIAL, CATALOG).orElseThrow();
        assertEquals(RespirationState.UNINITIALIZED, fresh.respiration());
        assertEquals(LungOrganState.EMPTY, fresh.organs().getFirst().lung());
    }

    @Test void detachRetainsRespirationAndReattachDoesNotReplaceIdentity() {
        BodyState initial = storedBody();
        OrganInstance lung = initial.organs().getFirst();
        BodyState empty = initial.detach(lung.id());
        assertTrue(empty.organs().isEmpty());
        assertEquals(initial.respiration(), empty.respiration());
        assertEquals(empty, roundTrip(empty));
        assertEquals(initial, empty.attach(lung, CATALOG));
        assertEquals(lung.id(), empty.attach(lung, CATALOG).organs().getFirst().id());
        BodyState clone = new BodyState(initial.organs(), initial.respiration());
        assertNotSame(initial, clone);
        assertEquals(initial, clone.detach(lung.id()).attach(lung, CATALOG));
        LungOrganState changed = new LungOrganState(Map.of("nitrogen", 2.5), 300);
        BodyState updated = clone.updateLung(lung.id(), changed, CATALOG);
        assertEquals(STORED_LUNG, initial.organs().getFirst().lung());
        assertEquals(changed, updated.organs().getFirst().lung());
        assertEquals(initial.respiration(), updated.respiration());
        RespirationState next = new RespirationState(0, false, LungComponent.Phase.INHALING);
        assertEquals(next, empty.updateRespiration(next).respiration());
        assertTrue(empty.updateRespiration(next).organs().isEmpty());
        assertEquals(lung.id(), updated.organs().getFirst().id());
        assertThrows(IllegalArgumentException.class, () -> empty.updateLung(lung.id(), changed, CATALOG));
        assertThrows(IllegalArgumentException.class, () -> clone.updateLung(UUID.randomUUID(), changed, CATALOG));
    }

    @Test void invalidPrototypeCategoryDuplicatesAndCodecsFailClosed() {
        OrganInstance lung = storedBody().organs().getFirst();
        assertThrows(IllegalArgumentException.class, () -> BodyState.EMPTY.attach(lung, new PrototypeCatalog<>(Map.of())));
        assertThrows(IllegalArgumentException.class, () -> BodyState.EMPTY.attach(lung, new PrototypeCatalog<>(Map.of(
                LUNG, new OrganData(OrganCategory.HEART, Optional.empty())))));
        assertThrows(IllegalArgumentException.class, () -> new BodyState(List.of(lung, lung), RespirationState.UNINITIALIZED));
        assertThrows(IllegalArgumentException.class, () -> new BodyState(List.of(lung,
                new OrganInstance(lung.id(), HEART, OrganCategory.HEART, null)), RespirationState.UNINITIALIZED));
        assertThrows(IllegalArgumentException.class, () -> new BodyState(List.of(lung,
                new OrganInstance(UUID.randomUUID(), LUNG, OrganCategory.LUNGS, LungOrganState.EMPTY)), RespirationState.UNINITIALIZED));
        assertThrows(IllegalArgumentException.class, () -> new OrganInstance(UUID.randomUUID(), LUNG, OrganCategory.LUNGS, null));
        assertThrows(IllegalArgumentException.class, () -> new LungOrganState(Map.of("oxygen", -1.0), 0));
        assertThrows(IllegalArgumentException.class, () -> new RespirationState(Double.NaN, false, LungComponent.Phase.INHALING));
        String body = "{\"organs\":[{\"id\":\"" + UUID.randomUUID() + "\",\"prototype\":\"moonstation14:human_lungs\",\"category\":\"Lungs\",\"lung\":{\"gas_moles\":{},\"temperature_kelvin\":0,\"saturation\":0}}],\"respiration\":{\"saturation\":0,\"initialized\":false,\"phase\":\"INHALING\"}}";
        assertFalse(BodyState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(body)).isSuccess());
        assertFalse(RespirationState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"saturation\":0,\"initialized\":false,\"phase\":\"EXHALING\",\"extra\":1}")).isSuccess());
        assertFalse(LungOrganState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"gas_moles\":{},\"temperature_kelvin\":-1}")).isSuccess());
    }

    @Test void planRejectsInvalidPrototypesWithoutCreatingOrgans() {
        assertTrue(BodySystem.plan(INITIAL, new PrototypeCatalog<>(Map.of())).isEmpty());
        assertTrue(BodySystem.plan(new InitialBodyComponent(Map.of(OrganCategory.LUNGS, HEART)), CATALOG).isEmpty());
        assertTrue(BodySystem.plan(null, CATALOG).isEmpty());
        assertTrue(BodySystem.plan(INITIAL, null).isEmpty());
        assertEquals(BodyState.EMPTY, BodySystem.plan(new InitialBodyComponent(Map.of()), CATALOG).orElseThrow());
    }

    @Test void staleCatalogDoesNotBlockIndependentMutationsOrLoseOrphanPayload() {
        BodyState saved = storedBody();
        OrganInstance staleLung = saved.find(OrganCategory.LUNGS).orElseThrow();
        OrganInstance staleHeart = new OrganInstance(UUID.randomUUID(), HEART, OrganCategory.HEART, null);
        saved = saved.attach(staleHeart, CATALOG);
        PrototypeCatalog<OrganData> reloaded = new PrototypeCatalog<>(Map.of(
                ResourceLocation.parse("moonstation14:new_lungs"),
                new OrganData(OrganCategory.LUNGS, Optional.of(new OrganData.Lung(6, 1144, Map.of(), 0)))));
        BodyState orphaned = roundTrip(saved);
        assertThrows(IllegalArgumentException.class, () -> orphaned.validateAll(reloaded));
        RespirationState depleted = new RespirationState(-1, true, LungComponent.Phase.EXHALING);
        assertEquals(orphaned.organs(), orphaned.updateRespiration(depleted).organs());
        assertEquals(depleted, orphaned.updateRespiration(depleted).respiration());
        assertThrows(IllegalArgumentException.class,
                () -> orphaned.updateLung(staleLung.id(), LungOrganState.EMPTY, reloaded));
        assertEquals(staleLung, orphaned.find(staleLung.id()).orElseThrow());
        BodyState withoutLung = orphaned.detach(staleLung.id());
        assertEquals(staleLung, orphaned.find(staleLung.id()).orElseThrow());
        assertEquals(STORED_LUNG, staleLung.lung());
        assertEquals(staleLung, roundTrip(orphaned).find(staleLung.id()).orElseThrow());
        assertEquals(staleHeart, withoutLung.find(OrganCategory.HEART).orElseThrow());
        OrganInstance replacement = new OrganInstance(UUID.randomUUID(),
                ResourceLocation.parse("moonstation14:new_lungs"), OrganCategory.LUNGS, LungOrganState.EMPTY);
        BodyState replaced = withoutLung.attach(replacement, reloaded);
        assertEquals(staleHeart, replaced.find(OrganCategory.HEART).orElseThrow());
        assertEquals(replacement, replaced.find(OrganCategory.LUNGS).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> replaced.attach(replacement, reloaded));
        assertThrows(IllegalArgumentException.class, () -> withoutLung.attach(staleLung, reloaded));
        assertThrows(IllegalArgumentException.class, () -> orphaned.attach(replacement, reloaded));
    }

    @Test void lungInventoryRequiresCanonicalGasIdsWithoutAliasCollisions() {
        for (String id : List.of("OXYGEN", "Oxygen", "oXyGeN")) {
            assertThrows(IllegalArgumentException.class, () -> new LungOrganState(Map.of(id, 1d), 280));
            String json = "{\"gas_moles\":{\"" + id + "\":1},\"temperature_kelvin\":280}";
            assertFalse(LungOrganState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isSuccess());
        }
        assertFalse(LungOrganState.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"gas_moles\":{\"oxygen\":1,\"Oxygen\":2},\"temperature_kelvin\":280}")).isSuccess());
        assertThrows(IllegalArgumentException.class,
                () -> new LungOrganState(Map.of("oxygen", 1d, "Oxygen", 2d), 280));
        assertEquals(Map.of("oxygen", 1d), new LungOrganState(Map.of("oxygen", 1d), 280).gasMoles());
    }

    @Test void respirationAndLungInventoryChangeTogetherWithoutReplacingOtherOrgans() {
        BodyState initial = storedBody();
        OrganInstance lung = initial.find(OrganCategory.LUNGS).orElseThrow();
        OrganInstance heart = new OrganInstance(UUID.randomUUID(), HEART, OrganCategory.HEART, null);
        BodyState withHeart = initial.attach(heart, CATALOG);
        LungOrganState exchanged = new LungOrganState(Map.of("carbon_dioxide", 1.25), 287.5);
        BodyState next = withHeart.updateLung(lung.id(), exchanged, CATALOG)
                .updateRespiration(new RespirationState(3, true, LungComponent.Phase.INHALING));
        assertEquals(heart, next.find(OrganCategory.HEART).orElseThrow());
        assertEquals(lung.id(), next.find(OrganCategory.LUNGS).orElseThrow().id());
        assertEquals(lung.prototype(), next.find(OrganCategory.LUNGS).orElseThrow().prototype());
        assertEquals(exchanged, roundTrip(next).find(OrganCategory.LUNGS).orElseThrow().lung());
        assertEquals(STORED_RESPIRATION, withHeart.respiration());
        assertEquals(STORED_LUNG, withHeart.find(OrganCategory.LUNGS).orElseThrow().lung());
        BodyState detached = next.detach(lung.id());
        assertEquals(next.respiration(), roundTrip(detached).respiration());
        assertEquals(List.of(heart), roundTrip(detached).organs());
    }

    private static BodyState storedBody() {
        BodyState fresh = BodySystem.plan(INITIAL, CATALOG).orElseThrow();
        return fresh.updateLung(fresh.organs().getFirst().id(), STORED_LUNG, CATALOG)
                .updateRespiration(STORED_RESPIRATION);
    }
}
