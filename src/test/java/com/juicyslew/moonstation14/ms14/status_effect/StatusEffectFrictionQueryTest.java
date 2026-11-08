package com.juicyslew.moonstation14.ms14.status_effect;

import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectEligibility;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;

class StatusEffectFrictionQueryTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "slip_friction");
    private static final ResourceKey<StatusEffectData> KEY = ModStatusEffects.createKey("slip_friction");
    private static final StatusEffectData DEFINITION = new StatusEffectData("slip", false, 0,
            List.of(StatusEffectBehavior.FRICTION), List.of(StatusEffectEligibility.LIVING_ENTITY), Optional.empty(), Optional.of(.5f));
    private static PrototypeCatalog<StatusEffectData> catalog(StatusEffectData data) {
        return new PrototypeCatalog<>(data == null ? Map.of() : Map.of(ID, data));
    }

    @Test void speechBlockProjectionUsesResolvedDefinitionNotCanonicalId() {
        ResourceKey<StatusEffectData> other = ModStatusEffects.createKey("unrelated_status");
        StatusEffectAttachment attachment = new StatusEffectAttachment();
        StatusEffectData blocking = new StatusEffectData("different", false, 0,
                List.of(StatusEffectBehavior.SPEECH_BLOCK), List.of(StatusEffectEligibility.LIVING_ENTITY));
        var resolved = new PrototypeCatalog<>(Map.of(other.location(), blocking));
        attachment.apply(other, StatusEffectOperation.ADD, OptionalInt.of(2), 0);
        assertTrue(StatusEffectSystem.hasActiveBehavior(attachment, resolved, StatusEffectBehavior.SPEECH_BLOCK));
        attachment.advanceOneTick();
        attachment.advanceOneTick();
        assertFalse(StatusEffectSystem.hasActiveBehavior(attachment, resolved, StatusEffectBehavior.SPEECH_BLOCK));
        attachment.apply(other, StatusEffectOperation.ADD, OptionalInt.of(2), 0);
        assertFalse(StatusEffectSystem.hasActiveBehavior(attachment, catalog(null), StatusEffectBehavior.SPEECH_BLOCK));
        attachment.remove(other);
        assertFalse(StatusEffectSystem.hasActiveBehavior(attachment, resolved, StatusEffectBehavior.SPEECH_BLOCK));
        assertTrue(attachment.isEmpty());
    }

    @Test void projectsActiveDefaultAndPayloadWithLifecycleAndDanglingDefinitionNeutral() {
        StatusEffectAttachment attached = new StatusEffectAttachment();
        attached.apply(KEY, StatusEffectOperation.ADD, OptionalInt.of(10), 0);
        assertEquals(.5d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)));
        attached.apply(KEY, StatusEffectOperation.SET, OptionalInt.of(10), 0,
                new StatusEffectPayload.FrictionModifier(1.4));
        assertEquals(1.4d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)), 1e-6);
        attached.apply(KEY, StatusEffectOperation.REMOVE, OptionalInt.empty(), 0);
        assertEquals(1d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)));
        attached.apply(KEY, StatusEffectOperation.ADD, OptionalInt.of(1), 0);
        StatusEffectSystem.advanceOneTick(attached);
        assertEquals(1d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)));
        attached.apply(KEY, StatusEffectOperation.ADD, OptionalInt.of(10), 0);
        assertEquals(1d, StatusEffectSystem.frictionFactor(attached, catalog(null)));
    }

    @Test void pendingAndIneligibleDefinitionsDoNotContributeAndCompositionIgnoresSlidingLatch() {
        StatusEffectAttachment attached = new StatusEffectAttachment();
        attached.apply(KEY, StatusEffectOperation.ADD, OptionalInt.of(10), 2);
        assertEquals(1d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)));
        StatusEffectData ineligible = new StatusEffectData("slip", false, 0,
                List.of(StatusEffectBehavior.MARKER), List.of(StatusEffectEligibility.LIVING_ENTITY));
        assertEquals(1d, StatusEffectSystem.frictionFactor(attached, catalog(ineligible)));
        // The status projection is queried independently of puddle/sliding admission.
        attached.apply(KEY, StatusEffectOperation.SET, OptionalInt.of(10), 0);
        assertEquals(.5d, StatusEffectSystem.frictionFactor(attached, catalog(DEFINITION)));
        assertEquals(1, attached.snapshot().size());
    }
}
