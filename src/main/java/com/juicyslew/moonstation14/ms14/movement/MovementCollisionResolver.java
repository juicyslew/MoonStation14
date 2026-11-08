package com.juicyslew.moonstation14.ms14.movement;

/** World adapter boundary; implementations resolve requested displacement using their actual collision world. */
@FunctionalInterface
public interface MovementCollisionResolver {
    CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement, boolean wasOnGround);

    /** Context-aware resolution; legacy adapters intentionally receive no idle-probe attestation. */
    default CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                    boolean wasOnGround, boolean idleSupportProbe) {
        return resolve(position, requestedDisplacement, wasOnGround);
    }

    /** Motor-attested jump intent for this tick; it does not imply collision acceptance. */
    default CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                    boolean wasOnGround, boolean idleSupportProbe, boolean motorJumpInitiated) {
        return resolve(position, requestedDisplacement, wasOnGround, idleSupportProbe);
    }


    @FunctionalInterface
    interface ContextResolver {
        CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                boolean wasOnGround, boolean idleSupportProbe);
    }

    @FunctionalInterface
    interface MotorContextResolver {
        CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                boolean wasOnGround, boolean idleSupportProbe, boolean motorJumpInitiated);
    }


    /** Binds legacy calls to false and forwards the motor's attested context exactly once. */
    static MovementCollisionResolver withIdleSupportProbeContext(ContextResolver resolver) {
        java.util.Objects.requireNonNull(resolver, "resolver");
        return new MovementCollisionResolver() {
            @Override
            public CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                           boolean wasOnGround) {
                return resolver.resolve(position, requestedDisplacement, wasOnGround, false);
            }

            @Override
            public CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                           boolean wasOnGround, boolean idleSupportProbe) {
                return resolver.resolve(position, requestedDisplacement, wasOnGround, idleSupportProbe);
            }
        };
    }

    /** Binds legacy calls to false and forwards both motor attestations exactly once. */
    static MovementCollisionResolver withMotorContext(MotorContextResolver resolver) {
        java.util.Objects.requireNonNull(resolver, "resolver");
        return new MovementCollisionResolver() {
            @Override
            public CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                           boolean wasOnGround) {
                return resolver.resolve(position, requestedDisplacement, wasOnGround, false, false);
            }

            @Override
            public CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                           boolean wasOnGround, boolean idleSupportProbe) {
                return resolver.resolve(position, requestedDisplacement, wasOnGround, idleSupportProbe, false);
            }

            @Override
            public CollisionResult resolve(MovementVector position, MovementVector requestedDisplacement,
                                           boolean wasOnGround, boolean idleSupportProbe, boolean motorJumpInitiated) {
                return resolver.resolve(position, requestedDisplacement, wasOnGround, idleSupportProbe,
                        motorJumpInitiated);
            }
        };
    }


    record CollisionResult(MovementVector actualDisplacement, boolean onGround,
                           HorizontalContactEvidence horizontalContactEvidence,
                            ContactPositionCorrectionEvidence positionCorrection) {
        public CollisionResult {
            if (actualDisplacement == null) throw new IllegalArgumentException("actual displacement is required");
        }

        /** Existing adapters retain strict per-axis bounded-resolution semantics. */
        public CollisionResult(MovementVector actualDisplacement, boolean onGround) {
            this(actualDisplacement, onGround, null, null);
        }
        public CollisionResult(MovementVector actualDisplacement, boolean onGround,
                               HorizontalContactEvidence evidence) { this(actualDisplacement, onGround, evidence, null); }
    }
}

