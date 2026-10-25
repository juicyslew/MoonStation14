package com.juicyslew.moonstation14.ms14.damage;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.loose_objects.ModLooseObjectProfiles;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class ItemDamageKernelTest {
    private static final Set<String> BIOLOGICAL = Set.of("brute", "burn", "toxin", "airloss", "genetic");

    @Test void horizontalOwnSpeedScalesAndUsesInclusiveCooldown() {
        var first = ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 10, .1 / 20, true, 1f);
        assertEquals(.5f, first.state().damage().get("blunt"));
        assertFalse(first.destructionDue());
        assertFalse(ItemDamageKernel.highSpeedImpact(first.state(), Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 49, .1 / 20, true, 1f).applied());
        var second = ItemDamageKernel.highSpeedImpact(first.state(), Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 50, .1 / 20, true, 1f);
        assertTrue(second.destructionDue());
        assertEquals(1f, DamageReducer.total(second.state().damage()));
    }

    @Test void noHorizontalContactNoThresholdAndUnsupportedDamageAreIgnored() {
        assertFalse(ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 10, 0, false, 1f).applied());
        assertFalse(ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 10, .099 / 20, true, 1f).applied());
        assertFalse(ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 10, Math.nextDown(.1f) / 20.0, true, 1f).applied());
        assertTrue(ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, Map.of("blunt", 1f), BIOLOGICAL,
                .1f, .5f, 40, 10, .1f / 20.0, true, 1f).applied());
        var excluded = ItemDamageKernel.apply(ItemDamageState.EMPTY, Map.of("holy", 1f), BIOLOGICAL);
        assertTrue(excluded.damage().isEmpty());
    }

    @Test void stackStateIsDefensivelyImmutableAndRejectsInvalidDamage() {
        var raw = new java.util.HashMap<String, Float>(); raw.put("blunt", 1f);
        var state = new ItemDamageState(raw, 4);
        raw.put("blunt", 2f);
        assertEquals(1f, state.damage().get("blunt"));
        assertThrows(UnsupportedOperationException.class, () -> state.damage().put("blunt", 3f));
        assertThrows(IllegalArgumentException.class, () -> new ItemDamageState(Map.of("holy", Float.NaN), 0));
        assertThrows(IllegalArgumentException.class, () -> new ItemDamageState(
                Map.of("blunt", Float.MAX_VALUE, "heat", Float.MAX_VALUE), 0));
        var encoded = ItemDamageState.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, ItemDamageState.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        var buffer = io.netty.buffer.Unpooled.buffer();
        ItemDamageState.STREAM_CODEC.encode(buffer, state);
        assertEquals(state, ItemDamageState.STREAM_CODEC.decode(buffer));
    }

    @Test void invalidScaledModifierAwareImpactIsRejectedWithoutMutatingLedger() {
        var state = new ItemDamageState(Map.of("blunt", 1f), 9);
        var zeroOverZero = ItemDamageKernel.highSpeedImpact(state, Map.of("blunt", 1f), BIOLOGICAL, Set.of(),
                0f, 1f, 40, 10, 0, true, Float.MAX_VALUE, DamageModifierData.EMPTY);
        var overflow = ItemDamageKernel.highSpeedImpact(state, Map.of("blunt", Float.MAX_VALUE), BIOLOGICAL, Set.of(),
                .1f, Float.MAX_VALUE, 40, 10, 1.0, true, Float.MAX_VALUE, DamageModifierData.EMPTY);
        assertFalse(zeroOverZero.applied());
        assertFalse(overflow.applied());
        assertSame(state, zeroOverZero.state());
        assertSame(state, overflow.state());
        assertEquals(Map.of("blunt", 1f), state.damage());
        assertEquals(9, state.nextImpactDeadline());
    }

    @Test void zeroMinimumImpactSpeedIsRejectedByCompositionAndCodec() {
        assertThrows(IllegalArgumentException.class, () -> new LooseObjectProfile.ItemDamageComposition(
                BIOLOGICAL, Map.of("blunt", 1f), 0f, 1f, 0, Map.of(), 1f, null, false, java.util.List.of()));
        assertThrows(IllegalArgumentException.class, () -> LooseObjectProfile.ItemDamageComposition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"allowed_groups\":[\"brute\"],\"minimum_impact_speed\":0}")));
    }

    @Test void landingDeltaIsIndependentOfImpactDeadlineAndAccumulatesForThreshold() {
        var inFlight = new ItemDamageState(Map.of("blunt", .5f), 100);
        var landed = ItemDamageKernel.apply(inFlight, Map.of("blunt", 1f), BIOLOGICAL);
        assertEquals(1.5f, DamageReducer.total(landed.damage()));
        assertEquals(100, landed.nextImpactDeadline());
    }

    @Test void unsupportedZeroAndEmptyDeltasAreNotApplicable() {
        assertFalse(ItemDamageKernel.hasApplicableDamage(Map.of("holy", 1f), BIOLOGICAL));
        assertFalse(ItemDamageKernel.hasApplicableDamage(Map.of("blunt", 0f), BIOLOGICAL));
        assertFalse(ItemDamageKernel.hasApplicableDamage(Map.of(), BIOLOGICAL));
        assertTrue(ItemDamageKernel.hasApplicableDamage(Map.of("blunt", .1f), BIOLOGICAL));
    }

    @Test void groupAndTypeAllowListsUseOrPolicyAcrossAllConsumers() {
        Set<String> groups = Set.of("brute");
        Set<String> types = Set.of(DamageKeys.HEAT, DamageKeys.SHOCK);
        Map<String, Float> all = Map.of(DamageKeys.BLUNT, 1f, DamageKeys.SLASH, 1f,
                DamageKeys.PIERCING, 1f, DamageKeys.HEAT, 1f, DamageKeys.SHOCK, 1f,
                DamageKeys.COLD, 1f, DamageKeys.CAUSTIC, 1f, DamageKeys.POISON, 1f,
                DamageKeys.ASPHYXIATION, 1f);
        Set<String> accepted = Set.of(DamageKeys.BLUNT, DamageKeys.SLASH, DamageKeys.PIERCING,
                DamageKeys.HEAT, DamageKeys.SHOCK);
        assertEquals(accepted, ItemDamageKernel.filterAllowed(all, groups, types).keySet());
        assertEquals(Set.of(DamageKeys.BLUNT, DamageKeys.SLASH, DamageKeys.PIERCING),
                ItemDamageKernel.filterAllowed(all, groups).keySet());
        assertEquals(Set.of(DamageKeys.HEAT, DamageKeys.SHOCK),
                ItemDamageKernel.filterAllowed(all, Set.of(), types).keySet());
        for (String rejected : Set.of(DamageKeys.COLD, DamageKeys.CAUSTIC, DamageKeys.POISON, DamageKeys.ASPHYXIATION)) {
            Map<String, Float> input = Map.of(rejected, 1f);
            assertFalse(ItemDamageKernel.hasApplicableDamage(input, groups, types));
            assertTrue(ItemDamageKernel.apply(ItemDamageState.EMPTY, input, groups, types).damage().isEmpty());
            var impact = ItemDamageKernel.highSpeedImpact(ItemDamageState.EMPTY, input, groups, types,
                    .1f, 1f, 0, 1, .1, true, 10f);
            assertFalse(ItemDamageKernel.hasApplicableDamage(input, groups, types));
            assertTrue(impact.state().damage().isEmpty());
        }
        assertTrue(ItemDamageKernel.hasApplicableDamage(Map.of(DamageKeys.HEAT, 1f), groups, types));
        assertEquals(1f, ItemDamageKernel.apply(ItemDamageState.EMPTY, Map.of(DamageKeys.HEAT, 1f), groups, types).damage().get(DamageKeys.HEAT));
    }

    @Test void realTomatoProfileResolvesAndNestedOverridesRetainInheritedImpact() {
        var genericId = ResourceLocation.fromNamespaceAndPath("moonstation14", "generic");
        var jugId = ResourceLocation.fromNamespaceAndPath("moonstation14", "jug");
        var tomatoId = ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato");
        JsonObject generic = resource("generic"), jug = resource("jug"), tomato = resource("tomato");
        JsonObject override = new JsonObject();
        override.addProperty("id", "moonstation14:tomato_threshold_override");
        override.addProperty("parent", "moonstation14:tomato");
        JsonObject damage = new JsonObject(); damage.addProperty("destruction_threshold", 2f);
        override.add("item_damage", damage);
        var raw = Map.of(genericId, generic, jugId, jug, tomatoId, tomato,
                ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato_threshold_override"), override);
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModLooseObjectProfiles.PROFILE_TYPE);
        manager.reload(ModLooseObjectProfiles.PROFILE_TYPE, raw);
        var resolved = manager.snapshot(ModLooseObjectProfiles.PROFILE_TYPE);
        var tomatoProfile = resolved.get(tomatoId);
        var genericProfile = resolved.get(genericId);
        var jugProfile = resolved.get(jugId);
        var overridden = resolved.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "tomato_threshold_override"));
        assertEquals(.1f, tomatoProfile.itemDamage().minimumImpactSpeed());
        assertEquals(Map.of("blunt", 1f), tomatoProfile.itemDamage().highSpeedImpact());
        assertEquals(1f, tomatoProfile.itemDamage().destructionThreshold());
        assertNull(genericProfile.itemDamage()); assertNull(jugProfile.itemDamage());
        assertEquals(2f, overridden.itemDamage().destructionThreshold());
        assertEquals(Map.of("blunt", 1f), overridden.itemDamage().highSpeedImpact());
        assertEquals(Set.of("brute", "burn", "toxin", "airloss", "genetic"), overridden.itemDamage().allowedGroups());
    }

    private static JsonObject resource(String name) {
        String path = "/data/moonstation14/moonstation14/loose_object_profile/" + name + ".json";
        try (var stream = ItemDamageKernelTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("missing " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
}
