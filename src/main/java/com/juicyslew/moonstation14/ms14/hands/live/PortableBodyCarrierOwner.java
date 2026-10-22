package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.atmos.exposure.ExposureAirSource;
import com.juicyslew.moonstation14.ms14.atmos.exposure.WorldCellExposureAir;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Derived exact owner for a live body represented by one key in a carrier's actual hand ledger. */
public final class PortableBodyCarrierOwner implements ContainedBodyOwner {
    private final LivingEntity carrier;
    private final PortableBodyReference reference;
    private final String handToken;
    private PortableBodyEscapeCustody.EscapeCustody activeEscapeCustody;
    private long activeEscapeOperationId = -1;

    public PortableBodyCarrierOwner(LivingEntity carrier, PortableBodyReference reference, String handToken) {
        this.carrier = java.util.Objects.requireNonNull(carrier);
        this.reference = java.util.Objects.requireNonNull(reference);
        this.handToken = java.util.Objects.requireNonNull(handToken);
        if (handToken.isBlank() || handToken.length() > ItemToken.MAX_LENGTH) throw new IllegalArgumentException("invalid key hand token");
    }

    public LivingEntity carrier() { return carrier; }
    public PortableBodyReference reference() { return reference; }
    public String handToken() { return handToken; }
    PortableBodyEscapeCustody.EscapeCustody activeEscapeCustody() { return activeEscapeCustody; }
    long activeEscapeOperationId() { return activeEscapeOperationId; }
    void bindEscapeCustody(PortableBodyEscapeCustody.EscapeCustody custody, long operationId) {
        activeEscapeCustody = custody;
        activeEscapeOperationId = operationId;
    }
    public LivingEntity referencedBody() {
        if (!(carrier.level() instanceof ServerLevel level) || !reference.dimension().equals(level.dimension().location())) return null;
        return level.getEntity(reference.body()) instanceof LivingEntity living ? living : null;
    }

    /** HAND token or direct STORAGE child token is a locator in the current canonical body ledger. */
    private boolean exactLedgerKey() {
        return handToken.equals(PortableBodyCarrying.currentLocationToken(carrier, reference));
    }

    @Override public UUID identity() { return reference.token(); }
    @Override public ServerLevel level() { return carrier.level() instanceof ServerLevel level ? level : null; }
    @Override public Vec3 position() { return carrier.position(); }
    @Override public Vec3 positionFor(LivingEntity body) {
        // Pin the mouse's feet near the carrier's center, not at the carrier's distant ground origin.
        return carrier.position().add(0, Math.min(.45, Math.max(.15, (carrier.getBbHeight() - body.getBbHeight()) * .5)), 0);
    }
    @Override public com.juicyslew.moonstation14.ms14.containment.ContainedBodyView viewFor(LivingEntity body) {
        return new com.juicyslew.moonstation14.ms14.containment.ContainedBodyView(identity(),
                java.util.Optional.empty(), true, java.util.Optional.of(new com.juicyslew.moonstation14.ms14.containment.ContainedBodyView.RenderParent(
                carrier.getId(), carrier.getUUID(), positionFor(body).subtract(carrier.position()))));
    }
    @Override public Stage stage() { return Stage.STATIONARY; }
    @Override public boolean isValid() {
        if (!(carrier.level() instanceof ServerLevel level) || !exactLedgerKey()
                || !com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(carrier, level)) return false;
        LivingEntity body = referencedBody();
        return body != null && body != carrier
                && com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.isValidBody(body, level);
    }
    @Override public Optional<ExposureAirSource> airSource() {
        if (!(carrier.level() instanceof ServerLevel level) || !carrier.isAddedToLevel()
                || level.getEntity(carrier.getUUID()) != carrier || !level.hasChunkAt(carrier.blockPosition())) return Optional.empty();
        return Optional.of(new WorldCellExposureAir(level, net.minecraft.core.BlockPos.containing(carrier.getEyePosition())));
    }
    @Override public boolean tryEscape(LivingEntity body) { return tryEscape(body, 0L); }
    @Override public boolean tryEscape(LivingEntity body, long originSequence) {
        return PortableBodyEscapeCustody.tryEscape(this, body, originSequence);
    }
}
