package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import net.minecraft.world.entity.Entity;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.joml.Vector3dc;
import org.joml.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/** Exact-entity, synchronous scope that lets only opted-in native collisions use current frame geometry. */
public final class CurrentFrameCollisionScope {
    private static final class Entry {
        private final Entity entity;
        private final boolean currentGeometry;
        private final boolean trackedCharacterYaw;
         private final boolean nativeTraceObservation;
         private final boolean nativeTraceGeometryObservation;
        private final Pose3d bodyYawScratch = new Pose3d();
        private Pose3d observedSdkYawSource;
        private Pose3d observedOwnedYawPose;
        private boolean microCarryCutoffRetained;
        private final ArrayList<NativeCollisionTrace> nativeTrace = new ArrayList<>();
        private PendingCollision pendingCollision;
         private boolean traceIncomplete;
         private boolean geometryTraceIncomplete;
         private int vanillaCollisionCalls;
         private boolean vanillaCollisionOpen;
         private boolean vanillaCollisionIncomplete;
         private Vec3 vanillaCollisionInput;
         private Vec3 vanillaCollisionOutput;
         private Vec3 vanillaCollisionPosition;
         private AABB vanillaCollisionBounds;
        private final ArrayList<YawSample> yawSamples = new ArrayList<>();
         private boolean yawSamplesComplete = true;
         private StartingBasis startingBasis;
         private Pose3d startingPoseFrom;
         private Pose3d startingPoseTo;
         private final Vector3d pendingStartingWorldPoint=new Vector3d();
         private boolean hasPendingStartingWorldPoint;
         private TransportObservation transportObservation;
         private boolean startingBasisRequired;

        private Entry(Entity entity, boolean currentGeometry, boolean trackedCharacterYaw, boolean nativeTraceObservation,
                      boolean nativeTraceGeometryObservation) {
            this.entity = entity;
            this.currentGeometry = currentGeometry;
            this.trackedCharacterYaw = trackedCharacterYaw;
            this.nativeTraceObservation = nativeTraceObservation;
            this.nativeTraceGeometryObservation = nativeTraceGeometryObservation;
        }
    }

    /** Movement-entry frame basis. Null frame is explicit WORLD; valid distinguishes bad history from WORLD. */
    public record StartingBasis(UUID frameId, NativeFrameInterval.PosePair poses, boolean valid) {
        public boolean world() { return valid && frameId == null; }
    }
    /** Latest raw SDK versus selected start-basis transport; one summary only, not a per-step ledger. */
    public record TransportObservation(UUID startFrameId,UUID selectedFrameId,int substep,
                                       Vec3 rawCarrier,Vec3 selectedCarrier) { }

    public record ScopedResult<T>(T value, Pose3dc observedSdkYawSource, Pose3dc selectedOwnedYawPose,
                                   boolean microCarryCutoffRetained, List<NativeCollisionTrace> nativeCollisionTrace,
                                    boolean nativeCollisionTraceComplete, List<YawSample> yawSamples,
                                     boolean yawSamplesComplete, boolean geometryObserved, boolean geometryTraceComplete,
                                       WorldClipObservation worldClipObservation, StartingBasis startingBasis,
                                       TransportObservation transportObservation) { }

    /** Sealed witness from exactly one paired Entity.collide call inside this tracked movement scope. */
    public static final class WorldClipObservation {
        private final Entity entity;
        private final Vec3 input;
        private final Vec3 output;
        private final Vec3 positionBefore;
        private final AABB boundsBefore;
        private final boolean complete;

        private WorldClipObservation(Entity entity, Vec3 input, Vec3 output, Vec3 positionBefore,
                                     AABB boundsBefore, boolean complete) {
            this.entity=entity; this.input=input; this.output=output; this.positionBefore=positionBefore;
            this.boundsBefore=boundsBefore; this.complete=complete;
        }
        public Vec3 input() { return input; }
        public Vec3 output() { return output; }
        public boolean complete() { return complete; }
        public Entity entity() { return entity; }
        public Vec3 positionBefore() { return positionBefore; }
        public AABB boundsBefore() { return boundsBefore; }
        public boolean downwardFloorClip() {
            return complete&&input!=null&&output!=null&&input.y<0d&&output.y>input.y&&output.y<=0d
                    &&Math.abs(input.x-output.x)<=NativeCollisionSlop.MAX_RESIDUAL
                    &&Math.abs(input.z-output.z)<=NativeCollisionSlop.MAX_RESIDUAL;
        }
        /** Downward floor clipping with only the stock voxel-shape tiny-axis zeroing adaptation. */
        public boolean downwardFloorClipWithUnchangedOrVanillaZeroedXZ() {
            if (!downwardFloorClip() || !finite(input) || !finite(output)) return false;
            double dx=input.x-output.x, dz=input.z-output.z;
            return axisIdentityOrVanillaZero(input.x,output.x) && axisIdentityOrVanillaZero(input.z,output.z)
                    && Math.hypot(dx,dz)<=NativeCollisionSlop.MAX_RESIDUAL;
        }
        private static boolean finite(Vec3 v) {
            return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);
        }
        private static boolean axisIdentityOrVanillaZero(double input,double output) {
            return Double.compare(input,output)==0 || Math.abs(input)<Shapes.EPSILON && output==0d;
        }
        public boolean matchesCall(Entity candidate, Vec3 expectedInput, Vec3 expectedOutput,
                                   Vec3 expectedPosition, AABB expectedBounds) {
            return complete && entity==candidate && input!=null && output!=null
                    && expectedInput!=null && expectedOutput!=null
                    && near(input.x,expectedInput.x,input.x,expectedInput.x)
                    && near(input.y,expectedInput.y,input.y,expectedInput.y)
                    && near(input.z,expectedInput.z,input.z,expectedInput.z)
                    && near(output.x,expectedOutput.x,positionBefore==null?0:positionBefore.x,
                            positionBefore==null?0:positionBefore.x+expectedOutput.x)
                    && near(output.y,expectedOutput.y,positionBefore==null?0:positionBefore.y,
                            positionBefore==null?0:positionBefore.y+expectedOutput.y)
                    && near(output.z,expectedOutput.z,positionBefore==null?0:positionBefore.z,
                            positionBefore==null?0:positionBefore.z+expectedOutput.z)
                    && positionBefore!=null && positionBefore.equals(expectedPosition)
                    && sameBounds(boundsBefore, expectedBounds);
        }
        private static boolean near(double actual,double expected,double before,double after) {
            return Double.isFinite(actual)&&Double.isFinite(expected)
                    &&Math.abs(actual-expected)<=NativeCollisionSlop.MAX_RESIDUAL
                    +Math.ulp(before)+Math.ulp(after);
        }
        private static boolean sameBounds(AABB a,AABB b) {
            return a!=null&&b!=null&&a.minX==b.minX&&a.minY==b.minY&&a.minZ==b.minZ
                    &&a.maxX==b.maxX&&a.maxY==b.maxY&&a.maxZ==b.maxZ;
        }
    }
    /** Immutable per-substep SDK/source and selected body yaw observation. */
    public record YawSample(int substep, int substeps, UUID trackingFrameId,
                            NativeFrameInterval.PoseValue sdkSourcePose, double sdkSourceYaw,
                            NativeFrameInterval.PoseValue selectedBodyPose, double selectedBodyYaw,
                            boolean currentGeometry) { }
    /** Immutable observation of the SDK's selected MTV and the response actually added to collision motion. */
    public record NativeCollisionTrace(UUID frameId, int substep, int substeps, int iteration, BlockPos selectedPos,
                                       BlockState selectedState, String block,
                                       String blockState, double selectedX, double selectedY, double selectedZ,
                                       double collisionMotionBeforeX, double collisionMotionBeforeY,
                                       double collisionMotionBeforeZ, double appliedX, double appliedY, double appliedZ,
                                          double responseX, double responseY, double responseZ,
                                          NativeCollisionGeometry geometry, boolean verticalCollision) { }
    private record PendingCollision(UUID frameId, int substep, int substeps, int iteration, BlockPos selectedPos,
                                     BlockState selectedState, String block,
                                     String blockState, double selectedX, double selectedY, double selectedZ,
                                       double beforeX, double beforeY, double beforeZ, NativeCollisionGeometry geometry,
                                       boolean verticalCollision) { }
    private static final int MAX_NATIVE_TRACE = 160;
    private static final Pose3d IDENTITY_POSE = new Pose3d();
    private static final ThreadLocal<ArrayDeque<Entry>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    // Null by default: native SDK callbacks outside an owned scope must not allocate a deque.
    private static final ThreadLocal<Entry> ACTIVE = new ThreadLocal<>();

    private CurrentFrameCollisionScope() { }

    public static <T> T withCurrentGeometry(Entity entity, Supplier<T> action) {
        return withScope(entity, true, false, false, false, null, false, action).value();
    }

    public static <T> ScopedResult<T> withTrackedCharacterPose(Entity entity, boolean currentGeometry,
                                                                Supplier<T> action) {
        return withTrackedCharacterPose(entity, currentGeometry, false, action);
    }

    public static <T> ScopedResult<T> withTrackedCharacterPose(Entity entity, boolean currentGeometry,
            boolean observeGeometry, Supplier<T> action) {
        return withScope(entity,currentGeometry,true,true,observeGeometry,null,false,action);
    }

    /** Uses the caller's already-resolved movement-entry basis; never re-queries support/tracker state. */
    public static <T> ScopedResult<T> withTrackedCharacterPose(Entity entity, boolean currentGeometry,
            boolean observeGeometry, LoadedFrameRayQuery.ActorPoint velocityBasis, Supplier<T> action) {
        return withScope(entity, currentGeometry, true, true, observeGeometry, velocityBasis, true, action);
    }

    /** Diagnostic stock solve: observe native MTV response without changing stock geometry or yaw. */
    public static <T> ScopedResult<T> withDiagnosticStockTrace(Entity entity, Supplier<T> action) {
        return withScope(entity, false, false, true, false, null, false, action);
    }

    private static <T> ScopedResult<T> withScope(Entity entity, boolean currentGeometry,
                                                   boolean trackedCharacterYaw, boolean nativeTraceObservation,
                                                   boolean nativeTraceGeometryObservation,
                                                   LoadedFrameRayQuery.ActorPoint velocityBasis,
                                                   boolean requireValidStartingBasis,Supplier<T> action) {
        var stack = STACK.get();
        Entry entry = new Entry(java.util.Objects.requireNonNull(entity), currentGeometry, trackedCharacterYaw,
                nativeTraceObservation, nativeTraceGeometryObservation);
        entry.startingBasisRequired=requireValidStartingBasis;
        if (trackedCharacterYaw) entry.startingBasis=captureStartingBasis(entry,entity,velocityBasis);
        if(requireValidStartingBasis&&(entry.startingBasis==null||!entry.startingBasis.valid()))
            throw invalidStartingBasis();
        Entry previous = ACTIVE.get();
        stack.push(entry);
        ACTIVE.set(entry);
        T result;
        try { result = action.get(); }
        finally {
            stack.pop();
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
            if (stack.isEmpty()) STACK.remove();
        }
        return new ScopedResult<>(result, entry.observedSdkYawSource == null
                ? null : new Pose3d(entry.observedSdkYawSource), entry.observedOwnedYawPose == null
                ? null : new Pose3d(entry.observedOwnedYawPose), entry.microCarryCutoffRetained,
                 List.copyOf(entry.nativeTrace), !entry.traceIncomplete && entry.pendingCollision == null,
                  List.copyOf(entry.yawSamples), entry.yawSamplesComplete, entry.nativeTraceGeometryObservation,
                  !entry.geometryTraceIncomplete && entry.pendingCollision == null,
                  entry.vanillaCollisionCalls==0?null:new WorldClipObservation(entry.entity,
                          entry.vanillaCollisionInput,entry.vanillaCollisionOutput,entry.vanillaCollisionPosition,
                          entry.vanillaCollisionBounds,entry.vanillaCollisionCalls==1&&!entry.vanillaCollisionOpen
                                  &&!entry.vanillaCollisionIncomplete&&entry.vanillaCollisionInput!=null
                                  &&entry.vanillaCollisionOutput!=null&&entry.vanillaCollisionPosition!=null
                                     &&entry.vanillaCollisionBounds!=null), entry.startingBasis,entry.transportObservation);
    }

    private static StartingBasis captureStartingBasis(Entry entry,Entity entity, LoadedFrameRayQuery.ActorPoint basis) {
        if (basis == null || !finite(basis.worldPoint()) || !finite(basis.storagePoint()))
            return new StartingBasis(null,null,false);
        UUID frameId=basis.frameId();
        if (frameId == null) return new StartingBasis(null,null,true);
        var container=SubLevelContainer.getContainer(entity.level());
        SubLevel frame=container==null?null:container.getSubLevel(frameId);
        if(frame==null||frame.isRemoved()||frame.getLevel()!=entity.level()||frame.getPlot()==null
                ||container.getSubLevel(frameId)!=frame) return new StartingBasis(frameId,null,false);
        try {
            OwnedFrameSimulationScope temporal=OwnedFrameSimulationScope.active(entity.level(),frame);
            Pose3dc from=temporal==null?frame.lastPose():temporal.from(frame);
            Pose3dc to=temporal==null?frame.logicalPose():temporal.to(frame);
            entry.startingPoseFrom=new Pose3d(from);
            entry.startingPoseTo=new Pose3d(to);
            return new StartingBasis(frameId,NativeFrameInterval.PosePair.capture(from,to),true);
        } catch (RuntimeException invalidHistory) {
            return new StartingBasis(frameId,null,false);
        }
    }

    /** Captures the exact point immediately before the SDK's inverse/forward selected-frame transform. */
    public static void captureStartingWorldPoint(Entity entity,Vector3dc point) {
        Entry e=ACTIVE.get();
        if(e!=null&&e.entity==entity&&e.trackedCharacterYaw&&e.startingBasisRequired&&point!=null)
            { e.pendingStartingWorldPoint.set(point); e.hasPendingStartingWorldPoint=true; }
    }

    /** Replaces only SDK passenger/base transport; the native own-motion accumulator and SAT remain stock. */
    public static Vector3d selectStartingBasisTransport(Entity entity,SubLevel selectedFrame,
            Vector3d transformedPoint,int substep,int substeps) {
        Entry e=ACTIVE.get();
        if(e==null||e.entity!=entity||!e.trackedCharacterYaw||!e.startingBasisRequired)return transformedPoint;
        if(e.startingBasis==null||!e.startingBasis.valid()||selectedFrame==null||transformedPoint==null
                ||substeps<=0||substep<1||substep>substeps)throw invalidStartingBasis();
        if(!e.hasPendingStartingWorldPoint)throw invalidStartingBasis();
        Vector3d original=e.pendingStartingWorldPoint;
        e.hasPendingStartingWorldPoint=false;
        if(!finite(original)||!finite(transformedPoint))throw invalidStartingBasis();
        Vector3d raw=new Vector3d(transformedPoint);
        Vec3 rawCarrier=new Vec3(raw.x-original.x,raw.y-original.y,raw.z-original.z);
        Vector3d selected=selectStartingBasisTransportPoint(e.startingBasis.frameId(),e.startingBasis.valid(),
                selectedFrame.getUniqueId(),e.startingPoseFrom,e.startingPoseTo,original,transformedPoint,substep,substeps);
        e.transportObservation=new TransportObservation(e.startingBasis.frameId(),selectedFrame.getUniqueId(),substep,
                rawCarrier,new Vec3(selected.x-original.x,selected.y-original.y,selected.z-original.z));
        return selected;
    }

    static Vector3d selectStartingBasisTransportPoint(UUID startFrame,boolean valid,UUID selectedFrame,
            Pose3d startFrom,Pose3d startTo,Vector3dc originalWorldPoint,Vector3d sdkTransformedPoint,
            int substep,int substeps) {
        if(!valid||selectedFrame==null||!finite(originalWorldPoint)||!finite(sdkTransformedPoint)
                ||substeps<=0||substep<1||substep>substeps)throw invalidStartingBasis();
        if(startFrame==null)return sdkTransformedPoint.set(originalWorldPoint);
        if(startFrame.equals(selectedFrame))return sdkTransformedPoint;
        if(startFrom==null||startTo==null)throw invalidStartingBasis();
        try {
            Pose3d from=new Pose3d(startFrom).lerp(startTo,(substep-1d)/substeps);
            Pose3d to=new Pose3d(startFrom).lerp(startTo,substep/(double)substeps);
            sdkTransformedPoint.set(originalWorldPoint);
            from.transformPositionInverse(sdkTransformedPoint);
            to.transformPosition(sdkTransformedPoint);
            if(!finite(sdkTransformedPoint))throw invalidStartingBasis();
            return sdkTransformedPoint;
        } catch(IllegalArgumentException invalidHistory) {
            if("collision resolution must be finite and bounded by requested displacement".equals(invalidHistory.getMessage()))
                throw invalidHistory;
            throw invalidStartingBasis();
        } catch(RuntimeException invalidHistory) {
            throw invalidStartingBasis();
        }
    }

    private static IllegalArgumentException invalidStartingBasis() {
        // Existing caller contract: server movement rejects the step; client prediction pauses
        // behind CharacterPredictionFailurePolicy's recognized collision-rejection barrier.
        return new IllegalArgumentException("collision resolution must be finite and bounded by requested displacement");
    }

    private static boolean finite(Vector3dc vector) {
        return vector!=null&&Double.isFinite(vector.x())&&Double.isFinite(vector.y())&&Double.isFinite(vector.z());
    }
    private static boolean finite(Vec3 vector) {
        return vector!=null&&Double.isFinite(vector.x)&&Double.isFinite(vector.y)&&Double.isFinite(vector.z);
    }

    /** HEAD/RETURN hooks around the one stock Entity.collide call; inert outside the exact tracked scope. */
    public static boolean observesVanillaWorldCollision(Entity entity) {
        Entry entry=ACTIVE.get();
        return entry!=null&&entry.entity==entity&&entry.trackedCharacterYaw;
    }

    /** True only during this entity's exact tracked-character native move. */
    public static boolean isTrackedCharacter(Entity entity) {
        Entry entry = ACTIVE.get();
        return entry != null && entry.entity == entity && entry.trackedCharacterYaw;
    }

    public static void recordVanillaWorldCollisionInput(Entity entity, Vec3 input, Vec3 positionBefore,
                                                         AABB boundsBefore) {
        Entry entry=ACTIVE.get();
        if(entry==null||entry.entity!=entity||!entry.trackedCharacterYaw)return;
        if(entry.vanillaCollisionCalls++!=0||entry.vanillaCollisionOpen) {
            entry.vanillaCollisionIncomplete=true;
            return;
        }
        entry.vanillaCollisionOpen=true;
        entry.vanillaCollisionInput=input;
        entry.vanillaCollisionPosition=positionBefore;
        entry.vanillaCollisionBounds=boundsBefore;
    }

    public static void recordVanillaWorldCollisionOutput(Entity entity, Vec3 output) {
        Entry entry=ACTIVE.get();
        if(entry==null||entry.entity!=entity||!entry.trackedCharacterYaw)return;
        if(!entry.vanillaCollisionOpen||entry.vanillaCollisionOutput!=null) {
            entry.vanillaCollisionIncomplete=true;
            return;
        }
        entry.vanillaCollisionOutput=output;
        entry.vanillaCollisionOpen=false;
    }

    /** Called at Sable's post-interior-rejection selected-contact callback; exact tracked-character scope only. */
    public static void recordNativeCollisionSelection(Entity entity, UUID frameId, BlockPos pos,
            Object state, Vector3dc selected, Vector3dc motion, int substep, int substeps, int iteration) {
        recordNativeCollisionSelection(entity, frameId, pos, state, selected, motion, substep, substeps, iteration,
                null, false);
    }

    public static void recordNativeCollisionSelection(Entity entity, UUID frameId, BlockPos pos,
            Object state, Vector3dc selected, Vector3dc motion, int substep, int substeps, int iteration,
            NativeCollisionGeometry geometry) {
        recordNativeCollisionSelection(entity, frameId, pos, state, selected, motion, substep, substeps, iteration,
                geometry, false);
    }

    public static void recordNativeCollisionSelection(Entity entity, UUID frameId, BlockPos pos,
            Object state, Vector3dc selected, Vector3dc motion, int substep, int substeps, int iteration,
            NativeCollisionGeometry geometry, boolean verticalCollision) {
        Entry entry = currentTraceEntry(entity);
        if (entry == null) return;
        if (entry.nativeTrace.size() >= MAX_NATIVE_TRACE || entry.pendingCollision != null) {
            entry.traceIncomplete = true;
            if (entry.nativeTraceGeometryObservation) entry.geometryTraceIncomplete = true;
            return;
        }
        if (!(state instanceof BlockState blockState)) {
            entry.traceIncomplete = true;
            if (entry.nativeTraceGeometryObservation) entry.geometryTraceIncomplete = true;
            return;
        }
        if (shouldCaptureNativeCollisionGeometry(entity, selected, verticalCollision)
                  && geometry == null) entry.geometryTraceIncomplete = true;
        BlockPos immutablePos = pos.immutable();
        entry.pendingCollision = new PendingCollision(frameId, substep, substeps, iteration,
                immutablePos, blockState, immutablePos.toString(), String.valueOf(state), selected.x(), selected.y(), selected.z(),
                motion.x(), motion.y(), motion.z(), geometry, verticalCollision);
    }

    /** Called immediately around the SDK's collisionMotion.add(maxMTV); always records the true added vector. */
    public static void recordNativeCollisionApplied(Entity entity, Vector3dc receiver, Vector3dc argument) {
        Entry entry = currentTraceEntry(entity);
        if (entry == null || entry.pendingCollision == null) return;
        PendingCollision p = entry.pendingCollision;
        entry.nativeTrace.add(new NativeCollisionTrace(p.frameId(), p.substep(), p.substeps(), p.iteration(),
                p.selectedPos(), p.selectedState(), p.block(), p.blockState(), p.selectedX(), p.selectedY(), p.selectedZ(), p.beforeX(), p.beforeY(),
                p.beforeZ(), argument.x(), argument.y(), argument.z(), receiver.x() - p.beforeX(),
                receiver.y() - p.beforeY(), receiver.z() - p.beforeZ(), p.geometry(), p.verticalCollision()));
        entry.pendingCollision = null;
    }

    private static Entry currentTrackedEntry(Entity entity) {
        Entry entry = ACTIVE.get();
        if (entry == null) return null;
        return entry.entity == entity && entry.trackedCharacterYaw ? entry : null;
    }

    private static Entry currentTraceEntry(Entity entity) {
        Entry entry = ACTIVE.get();
        return entry != null && entry.entity == entity && entry.nativeTraceObservation ? entry : null;
    }

    /** True only for the exact opted-in entity whose scope requested observational collision geometry. */
    public static boolean observesNativeCollisionGeometry(Entity entity) {
        Entry entry = currentTraceEntry(entity);
        return entry != null && entry.nativeTraceGeometryObservation;
    }

    /** Shared exact-scope geometry capture policy for both the native callback and trace completeness. */
    public static boolean shouldCaptureNativeCollisionGeometry(Entity entity, Vector3dc selectedMtv,
            boolean verticalCollision) {
        Entry entry = currentTraceEntry(entity);
        return entry != null && selectedMtv != null
                && (entry.nativeTraceGeometryObservation || entry.trackedCharacterYaw);
    }

    public static boolean usesCurrentGeometry(Entity entity) {
        var stack = STACK.get();
        return !stack.isEmpty() && stack.peek().entity == entity && stack.peek().currentGeometry;
    }

    public static boolean usesTrackedCharacterYaw(Entity entity) {
        var stack = STACK.get();
        return !stack.isEmpty() && stack.peek().entity == entity && stack.peek().trackedCharacterYaw;
    }

    /** Selects Sable's separate inherited-velocity input only for the exact owned tracked-character move. */
    public static Vec3 effectiveVelocityMotion(Entity entity, Vec3 sdkVelocityMotion) {
        Entry entry = ACTIVE.get();
        return entry != null && entry.entity == entity && entry.trackedCharacterYaw
                ? Vec3.ZERO : sdkVelocityMotion;
    }

    /** True only inside the exact owned-character native solve; used for its SDK step-up probe adaptation. */
    public static boolean adaptsOwnedCharacterStepProbe(Entity entity) {
        Entry entry = ACTIVE.get();
        return entry != null && entry.entity == entity && entry.trackedCharacterYaw;
    }

    /** Preserves only this scoped character's finite sub-1e-4 carrier from a genuinely changed native pose interval. */
    public static boolean retainOwnedCharacterMicroCarry(Entity entity,
            SubLevelEntityCollision.CollisionInfo info, Vec3 rawNativeDelta) {
        var stack = STACK.get();
        if (stack.isEmpty()) return false;
        Entry entry = stack.peek();
        if (entry.entity != entity || !entry.trackedCharacterYaw || entry.currentGeometry
                || info == null || rawNativeDelta == null || info.inheritedMotion != rawNativeDelta
                || !Double.isFinite(rawNativeDelta.x) || !Double.isFinite(rawNativeDelta.y)
                || !Double.isFinite(rawNativeDelta.z)) return false;
        double lengthSquared = rawNativeDelta.lengthSqr();
        if (!Double.isFinite(lengthSquared) || !(lengthSquared > 0d) || !(lengthSquared < 1.0e-8d)) return false;
        // This is the collision kernel's current solve frame; native tracking publishes after collide returns.
        SubLevel frame = info.trackingSubLevel;
        if (frame == null || frame.isRemoved() || frame.getLevel() != entity.level()
                || frame.getPlot() == null
                || SubLevelContainer.getContainer(entity.level()).getSubLevel(frame.getUniqueId()) != frame) return false;
        NativeFrameInterval.PosePair interval;
        try { interval = NativeFrameInterval.PosePair.capture(frame.lastPose(), frame.logicalPose()); }
        catch (IllegalArgumentException | NullPointerException invalidPose) { return false; }
        if (interval.last().equals(interval.logical())) return false;
        entry.microCarryCutoffRetained = true;
        return true;
    }

    /** Actual scratch pose passed by Sable before its candidate loop overwrites the shared level sink. */
    public static void observeSdkYawSource(Entity entity, Pose3dc source) {
        var stack = STACK.get();
        if (stack.isEmpty()) return;
        Entry entry = stack.peek();
        if (entry.entity == entity && entry.trackedCharacterYaw && entry.observedSdkYawSource == null)
            entry.observedSdkYawSource = new Pose3d(source);
    }

    /** Records the actual substep inputs without retaining any mutable SDK pose scratch. */
    public static void recordYawSample(Entity entity, SubLevelEntityCollision.CollisionInfo info,
            int substep, int substeps, Pose3dc source, double sourceYaw, Pose3dc selected, double selectedYaw) {
        var stack = STACK.get();
        if (stack.isEmpty()) return;
        Entry entry = stack.peek();
        if (entry.entity != entity || !entry.trackedCharacterYaw) return;
        if (entry.yawSamples.size() >= MAX_NATIVE_TRACE) { entry.yawSamplesComplete = false; return; }
        SubLevel frame = info == null ? null : info.trackingSubLevel;
        UUID frameId = frame == null || frame.isRemoved() || frame.getLevel() != entity.level()
                || frame.getPlot() == null
                || SubLevelContainer.getContainer(entity.level()).getSubLevel(frame.getUniqueId()) != frame
                ? null : frame.getUniqueId();
        entry.yawSamples.add(new YawSample(substep, substeps, frameId,
                NativeFrameInterval.PoseValue.capture(source), sourceYaw,
                NativeFrameInterval.PoseValue.capture(selected), selectedYaw, entry.currentGeometry));
    }

    /** Derives body yaw from this collide call's live native solve frame; the entity tracker publishes only afterward. */
    public static Pose3dc trackedCharacterYawPose(Entity entity, SubLevelEntityCollision.CollisionInfo info,
                                                   int substep, int substeps) {
        var stack = STACK.get();
        if (stack.isEmpty() || stack.peek().entity != entity || !stack.peek().trackedCharacterYaw) return null;
        // CollisionInfo is the in-flight solver's authoritative A/B choice. The extension's published tracker
        // is intentionally not consulted here: Sable updates it after collide() returns.
        SubLevel tracking = info == null ? null : info.trackingSubLevel;
        Entry entry = stack.peek();
        if (tracking == null || tracking.isRemoved()
                || tracking.getLevel() != entity.level() || tracking.getPlot() == null
                || SubLevelContainer.getContainer(entity.level()).getSubLevel(tracking.getUniqueId()) != tracking)
            return recordOwnedPose(entry, entry.bodyYawScratch.set(IDENTITY_POSE));
        if (substeps <= 0 || substep < 1 || substep > substeps)
            return recordOwnedPose(entry, entry.bodyYawScratch.set(IDENTITY_POSE));
        OwnedFrameSimulationScope temporal = OwnedFrameSimulationScope.active(entity.level(), tracking);
        if (temporal != null) {
            Pose3dc from=temporal.from(tracking), to=temporal.to(tracking);
            return recordOwnedPose(entry, entry.bodyYawScratch.set(from)
                    .lerp(to, substep / (double) substeps));
        }
        if (entry.currentGeometry) return recordOwnedPose(entry, tracking.logicalPose());
        // Private per-call pose copy: never mutate LevelReusedVectors or any shared SDK pose.
        return recordOwnedPose(entry, entry.bodyYawScratch.set(tracking.lastPose())
                .lerp(tracking.logicalPose(), substep / (double) substeps));
    }

    private static Pose3dc recordOwnedPose(Entry entry, Pose3dc pose) {
        if (entry.observedOwnedYawPose == null) entry.observedOwnedYawPose = new Pose3d(pose);
        else entry.observedOwnedYawPose.set(pose);
        return pose;
    }
}
