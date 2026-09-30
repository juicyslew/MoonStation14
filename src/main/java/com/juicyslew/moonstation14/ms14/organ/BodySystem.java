package com.juicyslew.moonstation14.ms14.organ;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.BodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.InitialBodyComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Server-side initialization. Never materializes a default on read. */
public final class BodySystem {
    private BodySystem() { }

    /** Pure plan; absent means invalid candidate, not an empty initialized body. */
    public static Optional<BodyState> plan(InitialBodyComponent initial, PrototypeCatalog<OrganData> catalog) {
        if (initial == null || catalog == null) return Optional.empty();
        try {
            BodyState planned = BodyState.EMPTY;
            for (var entry : initial.organs().entrySet()) {
                OrganData prototype = catalog.get(entry.getValue());
                if (prototype == null || prototype.category() != entry.getKey()) return Optional.empty();
                LungOrganState lung = entry.getKey() == OrganCategory.LUNGS ? LungOrganState.EMPTY : null;
                planned = planned.attach(new OrganInstance(UUID.randomUUID(), entry.getValue(), entry.getKey(), lung), catalog);
            }
            return Optional.of(planned);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    public static Optional<BodyState> current(LivingEntity entity) {
        if (entity == null || !entity.hasData(ModDataAttachments.BODY.get())) return Optional.empty();
        BodyAttachment body = entity.getExistingDataOrNull(ModDataAttachments.BODY.get());
        return body == null ? Optional.empty() : Optional.of(body.state());
    }

    public static boolean reconcile(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return false;
        var character = CharacterIdentitySystem.resolveForActor(entity);
        if (character.isEmpty() || character.get().component(BodyComponent.class).isEmpty()) return false;
        BodyAttachment existing = entity.getExistingDataOrNull(ModDataAttachments.BODY.get());
        if (existing != null) {
            // Saved EMPTY is authoritative. Never regenerate from InitialBody.
            return true;
        }
        if (!entity.isAlive()) return false;
        var initial = character.get().component(InitialBodyComponent.class);
        if (initial.isEmpty()) return false;
        PrototypeCatalog<OrganData> catalog = ModOrgans.catalog(entity.level());
        Optional<BodyState> candidate = plan(initial.get(), catalog);
        if (candidate.isEmpty()) return false;
        entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(candidate.get()));
        return true;
    }

    /** Mutations require a present body and a valid current catalog; detached organs retain identity and gas. */
    public static boolean attach(LivingEntity entity, OrganInstance organ) {
        if (!eligible(entity)) return false;
        try {
            BodyState old = current(entity).orElseThrow();
            var catalog = ModOrgans.catalog(entity.level());
            BodyState next = old.attach(organ, catalog);
            entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(next));
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
    public static Optional<OrganInstance> detach(LivingEntity entity, UUID id) {
        if (!eligible(entity)) return Optional.empty();
        try {
            BodyState old = current(entity).orElseThrow();
            OrganInstance removed = old.find(id).orElseThrow();
            entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(old.detach(id)));
            return Optional.of(removed);
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }
    public static boolean updateLung(LivingEntity entity, UUID id, LungOrganState state) {
        if (!eligible(entity)) return false;
        try {
            BodyState old = current(entity).orElseThrow();
            var catalog = ModOrgans.catalog(entity.level());
            entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(old.updateLung(id, state, catalog)));
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
    /** Updates mob physiology without requiring an attached lung; requires an initialized body. */
    public static boolean updateRespiration(LivingEntity entity, RespirationState state) {
        if (!eligible(entity) || state == null) return false;
        try {
            BodyState old = current(entity).orElseThrow();
            entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(old.updateRespiration(state)));
            return true;
        } catch (RuntimeException invalid) { return false; }
    }
    private static boolean eligible(LivingEntity entity) {
        return entity != null && !entity.level().isClientSide && current(entity).isPresent()
                && CharacterIdentitySystem.resolveForActor(entity).filter(c -> c.component(BodyComponent.class).isPresent()).isPresent();
    }

    /** NeoForge copyOnDeath copies immutable records; explicit copy supports clone ordering and tests. */
    public static boolean copyToClone(LivingEntity original, LivingEntity clone) {
        if (clone == null || clone.level().isClientSide) return false;
        Optional<BodyState> source = current(original);
        if (source.isEmpty()) return false;
        if (current(clone).filter(source.get()::equals).isPresent()) return false;
        clone.setData(ModDataAttachments.BODY.get(), new BodyAttachment(new BodyState(source.get().organs(), source.get().respiration())));
        return true;
    }
}
