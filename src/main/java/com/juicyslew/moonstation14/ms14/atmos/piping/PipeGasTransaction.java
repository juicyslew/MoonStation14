package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Detached two-end raw gas transaction. Endpoint implementations own synchronization and durability. */
public final class PipeGasTransaction {
    private PipeGasTransaction() { }

    /** Identity/version and eligibility are supplied by the endpoint owner, not inferred here. */
    public interface Endpoint {
        Object identity();
        long version();
        GasMixture snapshot();
        boolean eligible();
        /** May mutate before returning false or throwing. Must not publish. */
        boolean writeRaw(GasMixture expectedBefore, GasMixture next);
        /** Called only after both raw writes have succeeded. */
        void publish();
    }

    public static final class Plan {
        private final Object sourceIdentity;
        private final long sourceVersion;
        private final GasMixture sourceBefore;
        private final Object destinationIdentity;
        private final long destinationVersion;
        private final GasMixture destinationBefore;
        private final PipeGasTransfer.Plan transfer;

        private Plan(Object sourceIdentity, long sourceVersion, GasMixture sourceBefore,
                     Object destinationIdentity, long destinationVersion, GasMixture destinationBefore,
                     PipeGasTransfer.Plan transfer) {
            this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
            this.sourceVersion = sourceVersion;
            this.sourceBefore = Objects.requireNonNull(sourceBefore);
            this.destinationIdentity = Objects.requireNonNull(destinationIdentity);
            this.destinationVersion = destinationVersion;
            this.destinationBefore = Objects.requireNonNull(destinationBefore);
            this.transfer = Objects.requireNonNull(transfer);
        }

        public Object sourceIdentity() { return sourceIdentity; }
        public long sourceVersion() { return sourceVersion; }
        public GasMixture sourceBefore() { return sourceBefore; }
        public Object destinationIdentity() { return destinationIdentity; }
        public long destinationVersion() { return destinationVersion; }
        public GasMixture destinationBefore() { return destinationBefore; }
        public PipeGasTransfer.Plan transfer() { return transfer; }
    }

    public static Optional<Plan> plan(Endpoint source, Endpoint destination, double sourceVolume,
                            double destinationVolume, double maximumMoles, double maxPressureKpa,
                            Set<GasType> filter) {
        requireDistinct(source, destination);
        checkEligible(source); checkEligible(destination);
        GasMixture sb = Objects.requireNonNull(source.snapshot());
        GasMixture db = Objects.requireNonNull(destination.snapshot());
        PipeGasTransfer.Plan transfer = PipeGasTransfer.plan(sb, db, sourceVolume, destinationVolume,
                maximumMoles, maxPressureKpa, filter).orElse(null);
        return transfer == null ? Optional.empty() : Optional.of(new Plan(source.identity(), source.version(), sb,
                destination.identity(), destination.version(), db, transfer));
    }

    public static void commit(Endpoint source, Endpoint destination, Plan plan) {
        requireDistinct(source, destination);
        Objects.requireNonNull(plan);
        validate(source, plan.sourceIdentity(), plan.sourceVersion(), plan.sourceBefore());
        validate(destination, plan.destinationIdentity(), plan.destinationVersion(), plan.destinationBefore());

        int attempted = 0;
        try {
            attempted = 1;
            if (!source.writeRaw(plan.sourceBefore(), plan.transfer().sourceAfter()))
                throw new IllegalStateException("source raw write rejected");
            attempted = 2;
            if (!destination.writeRaw(plan.destinationBefore(), plan.transfer().destinationAfter()))
                throw new IllegalStateException("destination raw write rejected");
        } catch (Throwable failure) {
            Throwable rollbackFailure = null;
            if (attempted >= 2) rollbackFailure = rollback(destination, plan.destinationBefore());
            if (attempted >= 1) {
                Throwable sourceRollbackFailure = rollback(source, plan.sourceBefore());
                if (rollbackFailure == null) rollbackFailure = sourceRollbackFailure;
                else if (sourceRollbackFailure != null) rollbackFailure.addSuppressed(sourceRollbackFailure);
            }
            if (rollbackFailure != null) throw new RecoveryRequiredException("raw write failed and rollback was not confirmed", failure, rollbackFailure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException(failure);
        }
        try {
            source.publish();
            destination.publish();
        } catch (Throwable failure) {
            throw new RecoveryRequiredException("publication failed after both raw writes", failure, null);
        }
    }

    private static Throwable rollback(Endpoint endpoint, GasMixture before) {
        try {
            if (!endpoint.writeRaw(null, before)) return new IllegalStateException("rollback rejected");
            return null;
        } catch (Throwable failure) { return failure; }
    }

    private static void validate(Endpoint endpoint, Object identity, long version, GasMixture before) {
        checkEligible(endpoint);
        if (!Objects.equals(identity, endpoint.identity()) || version != endpoint.version()
                || !same(before, endpoint.snapshot())) throw new IllegalStateException("stale transaction endpoint");
    }
    private static void checkEligible(Endpoint endpoint) {
        if (!endpoint.eligible()) throw new IllegalStateException("ineligible transaction endpoint");
    }
    private static void requireDistinct(Endpoint a, Endpoint b) {
        Objects.requireNonNull(a); Objects.requireNonNull(b);
        if (a == b || Objects.equals(a.identity(), b.identity())) throw new IllegalArgumentException("endpoints must be distinct");
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (a == null || b == null || Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0) return false;
        for (GasType gas : GasType.values()) if (Double.compare(a.moles(gas), b.moles(gas)) != 0) return false;
        return true;
    }

    public static final class RecoveryRequiredException extends IllegalStateException {
        public RecoveryRequiredException(String message, Throwable cause, Throwable recoveryFailure) {
            super(message, cause);
            if (recoveryFailure != null) addSuppressed(recoveryFailure);
        }
    }
}

