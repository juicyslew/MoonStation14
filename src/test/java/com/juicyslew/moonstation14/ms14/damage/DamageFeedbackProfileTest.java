package com.juicyslew.moonstation14.ms14.damage;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DamageFeedbackProfileTest {
    @Test
    void independentFieldsDefaultOffAndDecodeSeparately() {
        var quiet = DamageFeedbackProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{}")).getOrThrow();
        assertEquals(DamageFeedbackProfile.QUIET, quiet);
        var custom = DamageFeedbackProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"red_flash\":true,\"interrupt_actions\":true}")).getOrThrow();
        assertEquals(new DamageFeedbackProfile(true, false, true, false), custom);
        assertEquals(new DamageFeedbackProfile(false, false, false, true), DamageFeedbackProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"hurt_sound\":true}")).getOrThrow());
        assertEquals(DamageFeedbackProfile.PHYSICAL, DamageFeedbackProfile.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"red_flash\":true,\"camera_shake\":true,\"interrupt_actions\":true}"))
                .getOrThrow());
        var redOnly = new DamageFeedbackProfile(true, false, true, false);
        assertEquals(true, DamageFeedbackPresentationPolicy.allowRedFlash(redOnly));
        assertEquals(false, DamageFeedbackPresentationPolicy.allowCameraShake(redOnly));
        assertEquals(false, DamageFeedbackPresentationPolicy.useNativeDamageEvent(redOnly));
        assertEquals(false, DamageFeedbackPresentationPolicy.allowRedOverlay(DamageFeedbackProfile.QUIET,
                0, 20, true, false), "quiet retained corpse must not keep the vanilla death tint");
        assertEquals(true, DamageFeedbackPresentationPolicy.allowRedOverlay(DamageFeedbackProfile.PHYSICAL,
                10, 20, true, true), "an active physical hit remains visible on a retained corpse");
        assertEquals(false, DamageFeedbackPresentationPolicy.allowRedOverlay(DamageFeedbackProfile.PHYSICAL,
                0, 20, true, false), "an expired physical hurt pulse does not tint a retained corpse forever");
        assertEquals(true, DamageFeedbackPresentationPolicy.allowRedOverlay(redOnly,
                10, 20, true, true), "red tint remains allowed independently of camera shake");
        assertEquals(false, DamageFeedbackPresentationPolicy.allowHurtCamera(redOnly,
                10, 20, true, true), "camera policy remains independent for a corpse hit");
        DamageFeedbackProfile cameraOnly = new DamageFeedbackProfile(false, true, true, false);
        assertEquals(false, DamageFeedbackPresentationPolicy.allowRedOverlay(cameraOnly,
                10, 20, true, true), "camera-only feedback does not tint the retained body");
        assertEquals(true, DamageFeedbackPresentationPolicy.allowHurtCamera(cameraOnly,
                10, 20, true, true), "camera-only feedback shakes without adding a tint");
        assertEquals(false, DamageFeedbackPresentationPolicy.allowRedOverlay(DamageFeedbackProfile.QUIET,
                0, 20, false, true), "managed living bodies do not inherit stale native death tint");
        assertEquals(true, DamageFeedbackPresentationPolicy.allowRedOverlay(redOnly,
                10, 0, false, true), "active medically-managed hits still follow their cause profile");
    }

    @Test
    void registeredCatalogResolvesInheritanceForTheDamageCauseConsumer() {
        var manager = new PrototypeManager();
        manager.register(ModDamageFeedback.TYPE);
        JsonObject quiet = json("{\"id\":\"moonstation14:quiet\",\"abstract\":true,"
                + "\"red_flash\":false,\"camera_shake\":false,\"interrupt_actions\":false}");
        JsonObject chemical = json("{\"id\":\"moonstation14:chemical\",\"parent\":\"moonstation14:quiet\"}");
        JsonObject physical = json("{\"id\":\"moonstation14:physical\",\"abstract\":true,"
                + "\"red_flash\":true,\"camera_shake\":true,\"interrupt_actions\":true}");
        JsonObject combat = json("{\"id\":\"moonstation14:combat\",\"parent\":\"moonstation14:physical\"}");
        JsonObject radiation = json("{\"id\":\"moonstation14:radiation\",\"parent\":\"moonstation14:quiet\"}");
        manager.reload(ModDamageFeedback.TYPE, java.util.Map.of(
                ModDamageFeedback.id("quiet"), quiet,
                ModDamageFeedback.id("chemical"), chemical,
                ModDamageFeedback.id("physical"), physical,
                ModDamageFeedback.id("combat"), combat,
                DamageSystem.Cause.RADIATION.profileId(), radiation));
        var catalog = manager.snapshot(ModDamageFeedback.TYPE);
        assertEquals(DamageFeedbackProfile.QUIET, ModDamageFeedback.resolve(catalog,
                DamageSystem.Cause.CHEMICAL.profileId()));
        assertEquals(DamageFeedbackProfile.PHYSICAL, ModDamageFeedback.resolve(catalog,
                DamageSystem.Cause.COMBAT.profileId()));
        assertEquals(false, ModDamageFeedback.resolve(catalog, DamageSystem.Cause.COMBAT.profileId()).hurtSound());
        assertEquals(DamageFeedbackProfile.QUIET, ModDamageFeedback.resolve(catalog,
                DamageSystem.Cause.RADIATION.profileId()), "radiation inherits quiet feedback without physical flash, shake, or timed-action interruption");
        assertThrows(IllegalStateException.class,
                () -> ModDamageFeedback.resolve(catalog, ModDamageFeedback.id("missing")));
    }

    @Test
    void damageTransactionFeedbackRemainsAvailableUntilItsOuterIdentityIsRemoved() {
        var stack = new DamageTransactionStack<Object, Object>();
        Object source = new Object();
        Object transaction = new Object();
        stack.push(source, transaction);
        assertEquals(transaction, stack.peekMatching(source));
        assertEquals(transaction, stack.peekMatching(source), "native hurt-packet lookup must not consume transaction context");
        assertEquals(transaction, stack.removeMatching(source), "outer hurt finally removes the exact source transaction");
        assertEquals(null, stack.peekMatching(source), "no semantic feedback survives the outer hurt scope");
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
}
