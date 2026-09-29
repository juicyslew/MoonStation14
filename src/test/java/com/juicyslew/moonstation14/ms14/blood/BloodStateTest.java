package com.juicyslew.moonstation14.ms14.blood;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BloodStateTest {
    @Test
    void emptyBloodstreamCodecRoundTripsAsAnInitializedEmptySolution() {
        ReagentAttachment empty = new ReagentAttachment();
        var encoded = ReagentAttachment.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow();
        ReagentAttachment decoded = ReagentAttachment.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        assertTrue(decoded.isEmpty());
        assertEquals(empty, decoded);
    }

    @Test
    void persistedBleedMetadataRoundTripsAndLegacyScalarIsNotAuthority() {
        BloodComponent state = new BloodComponent(0.4, true);
        var encoded = BloodComponent.CODEC.encodeStart(JsonOps.INSTANCE, state).getOrThrow();
        assertEquals(state, BloodComponent.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        assertEquals(new BloodComponent(4, false), BloodComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"current_volume\":9,\"bleed_rate\":4}" )).getOrThrow());
        assertEquals(new BloodComponent(2.5, false), BloodComponent.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"current_volume\":\"not-authoritative\",\"bleed_rate\":2.5}" )).getOrThrow());
        assertFalse(encoded.toString().contains("current_volume"), "new saves never write scalar volume");
        for (String invalid : new String[]{"{\"bleed_rate\":-1}", "{\"bleed_rate\":NaN}", "{\"bleed_rate\":Infinity}"}) {
            assertTrue(BloodComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> new BloodComponent(Double.POSITIVE_INFINITY, true));
    }

    @Test
    void prototypeHostAndBoundIdentityMustAgree() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(stream);
        CharacterData policy;
        try (stream) {
            policy = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var identity = ResourceLocation.fromNamespaceAndPath("moonstation14", "human");
        var player = ResourceLocation.parse("minecraft:player");
        var pig = ResourceLocation.parse("minecraft:pig");
        assertTrue(BloodSystem.resolvePolicy(policy, identity, player, Optional.of(identity)).isPresent());
        assertTrue(BloodSystem.resolvePolicy(policy, identity, player,
                Optional.of(ResourceLocation.fromNamespaceAndPath("moonstation14", "other"))).isEmpty());
        assertTrue(BloodSystem.resolvePolicy(policy, identity, pig, Optional.empty()).isEmpty());
        assertTrue(BloodSystem.resolvePolicy(policy, identity, player, Optional.empty()).isEmpty());
        CharacterData identityBound = new CharacterData(policy.slipData(), policy.movement(), java.util.List.of(),
                policy.thermal(), policy.blood());
        assertTrue(BloodSystem.resolvePolicy(identityBound, identity, player, Optional.empty()).isPresent());
        assertTrue(BloodSystem.resolvePolicy(policy, ResourceLocation.fromNamespaceAndPath("moonstation14", "other"),
                player, Optional.of(identity)).isEmpty());
    }

    @Test
    void distinctPigPolicyResolvesForItsMappedHostWithIndependentValues() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/pig.json");
        assertNotNull(stream);
        CharacterData pig;
        try (stream) {
            pig = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var identity = ResourceLocation.fromNamespaceAndPath("moonstation14", "pig");
        assertTrue(BloodSystem.resolvePolicy(pig, identity, ResourceLocation.parse("minecraft:pig"),
                Optional.of(identity)).isPresent());
        var humanStream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(humanStream);
        try (humanStream) {
            CharacterData human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(humanStream, StandardCharsets.UTF_8))).getOrThrow();
            assertEquals(human.blood().orElseThrow().updateIntervalSeconds(),
                    pig.blood().orElseThrow().updateIntervalSeconds());
            assertNotEquals(human.blood().orElseThrow().referenceSolution(),
                    pig.blood().orElseThrow().referenceSolution());
            assertTrue(BloodSystem.resolvePolicy(pig, identity, ResourceLocation.parse("minecraft:player"),
                    Optional.of(identity)).isEmpty());
        }
    }

    @Test
    void arbitraryMappedLivingHostAndRemovedBloodPolicyFailClosed() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(stream);
        CharacterData human;
        try (stream) {
            human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var identity = ResourceLocation.fromNamespaceAndPath("moonstation14", "human");
        var configuredNonMobType = ResourceLocation.fromNamespaceAndPath("test", "configured_living_host");
        CharacterData mapped = new CharacterData(human.slipData(), human.movement(),
                java.util.List.of(configuredNonMobType), human.thermal(), human.blood());
        assertTrue(BloodSystem.resolvePolicy(mapped, identity, configuredNonMobType,
                Optional.of(identity)).isPresent(), "host lookup must not depend on vanilla entity class");

        CharacterData removed = new CharacterData(mapped.slipData(), mapped.movement(),
                mapped.hostEntityTypes(), mapped.thermal(), Optional.empty());
        assertTrue(BloodSystem.resolvePolicy(removed, identity, configuredNonMobType,
                Optional.of(identity)).isEmpty(), "removed blood policy remains inert");
    }

    @Test
    void unpublishedPolicyRetryIsBoundedToOneSecondCadence() {
        assertFalse(BloodSystem.retryDue(19, 0));
        assertTrue(BloodSystem.retryDue(20, 0));
        assertTrue(BloodSystem.retryDue(0, 20), "clock rollback invalidates the old cache window");
    }

    @Test
    void publishedCatalogReplacementInvalidatesPolicyCacheBeforeRetry() throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/human.json");
        assertNotNull(stream);
        CharacterData human;
        try (stream) {
            human = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var identity = ResourceLocation.fromNamespaceAndPath("moonstation14", "human");
        var host = ResourceLocation.parse("minecraft:player");
        var original = new PrototypeCatalog<>(Map.of(identity, human));
        var removed = new PrototypeCatalog<CharacterData>(Map.of());
        var changed = new PrototypeCatalog<>(Map.of(identity, new CharacterData(
                human.slipData(), human.movement(), human.hostEntityTypes(), human.thermal(), Optional.empty())));
        var pigStream = getClass().getClassLoader().getResourceAsStream(
                "data/moonstation14/moonstation14/character/pig.json");
        assertNotNull(pigStream);
        CharacterData pig;
        try (pigStream) {
            pig = CharacterData.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseReader(new InputStreamReader(pigStream, StandardCharsets.UTF_8))).getOrThrow();
        }
        var replacement = new PrototypeCatalog<>(Map.of(identity, new CharacterData(
                human.slipData(), human.movement(), human.hostEntityTypes(), human.thermal(), pig.blood())));

        assertTrue(BloodSystem.cacheCurrent(original, original, 1, 0));
        assertFalse(BloodSystem.cacheCurrent(original, original, 20, 0));
        assertFalse(BloodSystem.cacheCurrent(original, removed, 1, 0),
                "removed catalog must not reuse a previously present blood policy");
        assertNull(removed.get(identity));
        assertFalse(BloodSystem.cacheCurrent(original, changed, 1, 0));
        assertTrue(BloodSystem.resolvePolicy(changed.get(identity), identity, host,
                Optional.of(identity)).isEmpty(), "newly removed blood config is immediately inert");
        assertFalse(BloodSystem.cacheCurrent(original, replacement, 1, 0));
        assertEquals(pig.blood(), BloodSystem.resolvePolicy(replacement.get(identity), identity, host,
                Optional.of(identity)), "new publication selects its new blood policy");
        assertFalse(BloodSystem.cacheCurrent(removed, original, 1, 0),
                "new publication must also invalidate cached absence");
        assertTrue(BloodSystem.resolvePolicy(original.get(identity), identity, host,
                Optional.of(identity)).isPresent());
    }
}
