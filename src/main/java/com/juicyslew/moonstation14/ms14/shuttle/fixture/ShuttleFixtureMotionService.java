package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Vector3d;
import org.joml.Quaterniond;

import java.util.*;

/** Bounded native impulse controller for explicitly registered admin diagnostic fixtures only. */
public final class ShuttleFixtureMotionService {
    private static final Map<ServerLevel, Map<UUID, Entry>> LEVELS = new HashMap<>();
    private ShuttleFixtureMotionService() { }
    private static final class Entry {
        final ServerSubLevel owner; ShuttleMotionRequest request; long deadline; Vector3d gravity; double drag; int callbacks;
        boolean freightFlight; double profileSeconds; int profileDurationTicks;
        /** Last command actually passed to native impulse application, regardless of selected controller mode. */
        ShuttleMotionMath.CommandAcceleration lastAppliedCommand;
        Vector3d freightOriginCom;
        boolean freightLimitsMaintained=true;
        double maxFreightLinearAcceleration,maxFreightAngularAcceleration,maxFreightLinearJerk,maxFreightAngularJerk;
        boolean brakePending;
        Vector3d previousNativeCom; Vector3d previousRotationPoint; double previousStepSeconds; Vector3d transportVelocity;
        Vector3d lastPostActuationWorldAngularVelocity;
        Quaterniond lastPostActuationNativeOrientation;
        boolean angularObservationEnabled;
        AngularControlObservation latestAngularObservation;
        Entry(ServerSubLevel owner) { this.owner = owner; }
    }
    /** Immutable measured/observed controller state for diagnostics and native fixture proofs. */
    public record Snapshot(ShuttleMotionRequest request, long remainingTicks, int appliedSubsteps,
                           ShuttleMotionMath.Vector worldVelocity, ShuttleMotionMath.Vector transportVelocity,
                           ShuttleMotionMath.Vector worldAngularVelocity,
                            ShuttleMotionMath.Vector configuredGravity, double configuredUniversalDrag,
                            int sdkSubsteps, double mergedMass, boolean freightFlight, double profileElapsedSeconds,
                            ShuttleMotionMath.CommandAcceleration commandedAcceleration, boolean freightLimitsMaintained,
                            double maxFreightLinearAcceleration,double maxFreightAngularAcceleration,
                            double maxFreightLinearJerk,double maxFreightAngularJerk) {
        public boolean active() { return request != null; }
    }

    public record QuaternionValue(double x, double y, double z, double w) {
        static QuaternionValue copy(org.joml.Quaterniondc q) { return new QuaternionValue(q.x(), q.y(), q.z(), q.w()); }
    }
    public record InertiaValue(double m00, double m01, double m02, double m10, double m11, double m12,
                               double m20, double m21, double m22) {
        static InertiaValue copy(org.joml.Matrix3dc m) {
            return new InertiaValue(m.m00(),m.m01(),m.m02(),m.m10(),m.m11(),m.m12(),m.m20(),m.m21(),m.m22());
        }
    }
    /**
     * Latest-only immutable telemetry copied in the controller's NORMAL post-physics callback before its
     * per-substep actuation. The native orientation/angular velocity fields are pre-actuation inputs for that
     * callback, not observations from Forge's PRE-physics event. The measured alpha remains distinct from
     * the body-frame-aligned one-interval forecast actually passed to compute; neither telemetry field feeds back.
     */
    public record AngularControlObservation(UUID frameId, long sourceWorldGameTime, long controllerSubstepOrdinal,
            double dtSeconds, QuaternionValue nativePreOrientation, ShuttleMotionMath.Vector sdkLinearVelocity,
            ShuttleMotionMath.Vector worldTransportVelocity, ShuttleMotionMath.Vector preWorldAngularVelocity,
            ShuttleMotionMath.Vector previousPostWorldAngularVelocity, QuaternionValue previousPostNativeOrientation,
            ShuttleMotionMath.Vector measuredUncontrolledAlpha, ShuttleMotionMath.Vector uncontrolledAlphaInput,
            ShuttleMotionMath.Vector worldGravity, ShuttleMotionMath.Vector actualLocalAngularImpulse,
            ShuttleMotionMath.Vector immediatePostWorldAngularVelocity, InertiaValue localInertia,
            double mergedMass, double universalDrag, ShuttleMotionRequest request) { }

    public static synchronized void registerFixture(ServerSubLevel owner) {
        ServerLevel level = owner.getLevel();
        if (level == null || !level.getServer().isSameThread() || !live(level, owner)) return;
        LEVELS.computeIfAbsent(level, ignored -> new HashMap<>()).putIfAbsent(owner.getUniqueId(), new Entry(owner));
    }

    /** Explicit opt-in for one registered native fixture; ordinary registrations remain allocation-free for telemetry. */
    public static synchronized boolean enableAngularControlObservation(ServerLevel level, ServerSubLevel owner) {
        if (level == null || owner == null || !level.getServer().isSameThread() || !live(level, owner)) return false;
        Entry e = LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        if (e == null || e.owner != owner) return false;
        e.latestAngularObservation = null;
        e.angularObservationEnabled = true;
        return true;
    }

    public static synchronized AngularControlObservation latestAngularControlObservation(ServerLevel level, ServerSubLevel owner) {
        if (level == null || owner == null || !live(level, owner)) return null;
        Entry e = LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        return e != null && e.owner == owner && e.request != null && e.angularObservationEnabled
                ? e.latestAngularObservation : null;
    }

    public static synchronized boolean angularControlObservationEnabled(ServerLevel level, ServerSubLevel owner) {
        if (level == null || owner == null || !live(level, owner)) return false;
        Entry e = LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        return e != null && e.owner == owner && e.request != null && e.angularObservationEnabled;
    }

    public static synchronized boolean disableAngularControlObservation(ServerLevel level, ServerSubLevel owner) {
        Entry e = level == null || owner == null ? null : LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        if (e == null || e.owner != owner) return false;
        clearAngularObservation(e, true);
        return true;
    }

    /** Retires an explicitly registered fixture and clears its latest observation. */
    public static synchronized boolean unregisterFixture(ServerLevel level, ServerSubLevel owner) {
        if (level == null || owner == null || !level.getServer().isSameThread()) return false;
        Map<UUID, Entry> entries = LEVELS.get(level);
        Entry e = entries == null ? null : entries.get(owner.getUniqueId());
        if (e == null || e.owner != owner) return false;
        transition(level, e, "fixture unregister");
        entries.remove(owner.getUniqueId());
        if (entries.isEmpty()) LEVELS.remove(level);
        return true;
    }

    public static synchronized boolean start(ServerLevel level, ServerSubLevel owner, ShuttleMotionRequest request) {
        if (level == null || !level.getServer().isSameThread() || request == null || !live(level, owner)) return false;
        Entry entry = LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        if (entry == null || entry.owner != owner) return false;
        try {
            RigidBodyHandle h = RigidBodyHandle.of(owner);
            var physics = dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
            if (physics == null) return false;
            var mass = owner.getMassTracker();
            if (h == null || !h.isValid()) return false;
            Vector3d velocity=h.getLinearVelocity(new Vector3d()), omega=h.getAngularVelocity(new Vector3d());
            Pose3d pose=physics.getPipeline().readPose(owner,new Pose3d());
            double mergedMass=mass.getMass();
            var inertia=mass.getInertiaTensor();
            Vector3d gravity=new Vector3d(dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level));
            double drag=dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getUniversalDrag(level);
            long deadline=Math.addExact(level.getGameTime(), request.durationTicks());
            double dt=1.0/(20.0*Math.max(1,physics.getConfig().substepsPerTick));
            if (!Double.isFinite(drag) || !finite(gravity)
                    || ShuttleMotionMath.compute(request,velocity,omega,pose.orientation(),mergedMass,inertia,gravity,dt,
                    new Vector3d(),drag)==null) return false;
            // Publish the replacement only after every native/configuration/actuation precondition succeeds.
            boolean wasActive = entry.request != null;
            if (!wasActive) {
                entry.previousNativeCom = null;
                entry.previousRotationPoint = null;
                entry.previousStepSeconds = 0;
                entry.transportVelocity = new Vector3d(velocity);
                entry.lastPostActuationWorldAngularVelocity = null;
                entry.lastPostActuationNativeOrientation = null;
                entry.lastAppliedCommand = null;
            }
            entry.request=request; entry.deadline=deadline; entry.gravity=gravity; entry.drag=drag;
            entry.freightFlight=false; entry.profileSeconds=0; entry.freightOriginCom=null;
            entry.latestAngularObservation = null;
            entry.brakePending=false;
            entry.callbacks = 0;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[fixture motion start] dimension={} owner={} target={} durationTicks={} configuredGravity={} configuredUniversalDrag={}",
                    level.dimension().location(), owner.getUniqueId(), request, request.durationTicks(), entry.gravity, entry.drag);
            return true;
        } catch (RuntimeException failure) { return false; }
    }

    public static synchronized boolean startFreightFlight(ServerLevel level, ServerSubLevel owner, int durationTicks) {
        if (durationTicks < 1 || durationTicks > 2400 || level == null || !level.getServer().isSameThread()) return false;
        ShuttleMotionRequest initial = ShuttleFreightFlightProfile.target(0, durationTicks);
        var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
        if(physics==null||!live(level,owner)) return false;
        Vector3d flightOrigin;
        try {
            Pose3d pose=physics.getPipeline().readPose(owner,new Pose3d());
            flightOrigin=pose.position().get(new Vector3d());
            if(!finite(flightOrigin)) return false;
        } catch(RuntimeException unavailable) { return false; }
        if (!start(level, owner, initial)) return false;
        Entry e=LEVELS.get(level).get(owner.getUniqueId());
        e.freightFlight=true; e.profileSeconds=0; e.profileDurationTicks=durationTicks; e.freightOriginCom=flightOrigin;
        e.freightLimitsMaintained=true; e.maxFreightLinearAcceleration=0; e.maxFreightAngularAcceleration=0;
        e.maxFreightLinearJerk=0; e.maxFreightAngularJerk=0;
        return true;
    }

    public static synchronized boolean stop(ServerLevel level, ServerSubLevel owner) {
        Entry e = level == null || owner == null ? null : LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        if (e == null || e.owner != owner) return false;
        transition(level,e,"admin stop");
        return true;
    }

    public static synchronized boolean queueBrake(ServerLevel level, ServerSubLevel owner) {
        if (level == null || !level.getServer().isSameThread() || !live(level,owner)) return false;
        Entry e=LEVELS.getOrDefault(level,Map.of()).get(owner.getUniqueId());
        if(e==null || e.owner!=owner) return false;
        transition(level,e,"one-shot brake accepted"); e.brakePending=true; return true;
    }

    public static synchronized Snapshot snapshot(ServerLevel level, ServerSubLevel owner) {
        if (level==null || owner==null || !live(level,owner)) return null;
        Entry e = level == null || owner == null ? null : LEVELS.getOrDefault(level, Map.of()).get(owner.getUniqueId());
        if (e == null || e.owner != owner) return null;
        try {
            RigidBodyHandle h = RigidBodyHandle.of(owner);
            if (h == null || !h.isValid()) return null;
            Vector3d v = h.getLinearVelocity(new Vector3d()), w = h.getAngularVelocity(new Vector3d());
            var mass=owner.getMassTracker();
            if(!Double.isFinite(mass.getMass())||mass.getMass()<=0||mass.getInertiaTensor()==null) return null;
            Vector3d gravity=e.gravity==null?new Vector3d(dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getGravity(level)):new Vector3d(e.gravity);
            double drag=e.request==null?dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData.getUniversalDrag(level):e.drag;
            var physics=dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem.get(level);
            if(physics==null) return null;
            return new Snapshot(e.request,e.request==null?0:Math.max(0,e.deadline-level.getGameTime()),e.callbacks,
                    vector(v),e.transportVelocity==null?null:vector(e.transportVelocity),vector(w),vector(gravity),drag,
                    physics.getConfig().substepsPerTick,mass.getMass(),e.freightFlight,e.profileSeconds,e.lastAppliedCommand,
                    e.freightLimitsMaintained,e.maxFreightLinearAcceleration,e.maxFreightAngularAcceleration,
                    e.maxFreightLinearJerk,e.maxFreightAngularJerk);
        } catch (RuntimeException ignored) { return null; }
    }
    public static synchronized String status(ServerLevel level, ServerSubLevel owner) {
        Snapshot s=snapshot(level,owner);
        if(s==null) return "unregistered fixture or native body unavailable";
        Entry e=LEVELS.getOrDefault(level,Map.of()).get(owner.getUniqueId());
        return "worldVelocity="+s.worldVelocity()+" (SDK instantaneous), fixtureTransportVelocity="+s.transportVelocity()
                +", worldAngularVelocity="+s.worldAngularVelocity()+", motion="+s.request()
                +(s.active()?", remainingTicks="+s.remainingTicks():"")+", appliedSubsteps="+s.appliedSubsteps()
                +", configuredGravity="+s.configuredGravity()+", configuredUniversalDrag="+s.configuredUniversalDrag()
                +", sdkSubsteps="+s.sdkSubsteps()+", mergedMass="+s.mergedMass()+", nativeBody=ready"
                +(e.freightFlight?", profile=heavy-freight, elapsedNativeSeconds="+e.profileSeconds+", command="+e.lastAppliedCommand:"");
    }
    private static ShuttleMotionMath.Vector vector(org.joml.Vector3dc v) { return new ShuttleMotionMath.Vector(v.x(),v.y(),v.z()); }
    private static Vector3d vec(ShuttleMotionMath.Vector v) { return new Vector3d(v.x(),v.y(),v.z()); }
    private static boolean finite(org.joml.Vector3dc v) { return Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z()); }
    private static void transition(ServerLevel level,Entry e,String reason) {
        if(e.request==null) { e.freightFlight=false; e.profileSeconds=0; e.freightOriginCom=null; e.lastAppliedCommand=null; e.previousNativeCom=null; e.previousRotationPoint=null; e.previousStepSeconds=0; e.transportVelocity=null; e.lastPostActuationWorldAngularVelocity=null; e.lastPostActuationNativeOrientation=null; clearAngularObservation(e,true); return; }
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("[fixture motion stop] dimension={} owner={} reason={} callbacks={}",level.dimension().location(),e.owner.getUniqueId(),reason,e.callbacks);
        e.request=null;
        e.freightFlight=false; e.profileSeconds=0; e.freightOriginCom=null; e.lastAppliedCommand=null;
        e.previousNativeCom=null; e.previousRotationPoint=null; e.previousStepSeconds=0; e.transportVelocity=null;
        e.lastPostActuationWorldAngularVelocity=null;
        e.lastPostActuationNativeOrientation=null;
        clearAngularObservation(e,true);
    }

    private static void clearAngularObservation(Entry entry, boolean disable) {
        entry.latestAngularObservation = null;
        if (disable) entry.angularObservationEnabled = false;
    }

    public static void onPostPhysics(dev.ryanhcode.sable.neoforge.event.ForgeSablePostPhysicsTickEvent event) {
        ServerLevel level = event.getPhysicsSystem().getLevel();
        synchronized (ShuttleFixtureMotionService.class) {
            Map<UUID, Entry> entries = LEVELS.get(level); if (entries == null) return;
            var it = entries.entrySet().iterator();
            while (it.hasNext()) {
                Entry e = it.next().getValue();
                try {
                    if (!live(level,e.owner)) { transition(level,e,"owner removed/replaced"); it.remove(); continue; }
                    if (e.request == null && !e.brakePending) continue;
                    if(e.brakePending) {
                        e.brakePending=false;
                        var brakePhysics=event.getPhysicsSystem();
                        RigidBodyHandle brakeHandle=RigidBodyHandle.of(e.owner);
                        var brakeMass=e.owner.getMassTracker(); Pose3d brakePose=new Pose3d();
                        if(brakeHandle==null||!brakeHandle.isValid()||brakePhysics.getLevel()!=level) continue;
                        brakePhysics.getPipeline().readPose(e.owner,brakePose);
                        var impulse=ShuttleMotionMath.counterImpulse(brakeHandle.getLinearVelocity(new Vector3d()),
                                brakeHandle.getAngularVelocity(new Vector3d()),brakePose.orientation(),brakeMass.getMass(),brakeMass.getInertiaTensor());
                        if(impulse!=null) brakeHandle.applyLinearAndAngularImpulse(
                                new Vector3d(impulse.linearLocal().x(),impulse.linearLocal().y(),impulse.linearLocal().z()),
                                new Vector3d(impulse.angularLocal().x(),impulse.angularLocal().y(),impulse.angularLocal().z()));
                        continue;
                    }
                    if (level.getGameTime() >= e.deadline) { transition(level,e,"deadline expired"); continue; }
                    RigidBodyHandle h=RigidBodyHandle.of(e.owner);
                    var mass=e.owner.getMassTracker();
                    if(h==null||!h.isValid()) { transition(level,e,"native handle invalid"); continue; }
                    Pose3d pose=new Pose3d();
                    var physics=event.getPhysicsSystem();
                    if(physics.getLevel()!=level) { transition(level,e,"physics level mismatch"); continue; }
                    physics.getPipeline().readPose(e.owner,pose);
                    Vector3d nativeCom = pose.position().get(new Vector3d());
                    Vector3d rotationPoint = pose.rotationPoint().get(new Vector3d());
                    Vector3d sdkVelocity = h.getLinearVelocity(new Vector3d());
                    double dt = event.getTimeStep();
                    if (e.freightFlight) {
                        if (e.freightOriginCom==null||!ShuttleFreightFlightBounds.within(e.freightOriginCom,nativeCom)) {
                            transition(level,e,"freight profile excursion bound"); continue;
                        }
                        e.profileSeconds += dt;
                        e.request = ShuttleFreightFlightProfile.target(e.profileSeconds, e.profileDurationTicks);
                    }
                    Vector3d worldOmega = h.getAngularVelocity(new Vector3d());
                    Vector3d previousPostWorldOmega = e.lastPostActuationWorldAngularVelocity;
                    Quaterniond previousPostNativeOrientation = e.lastPostActuationNativeOrientation;
                    Vector3d measuredUncontrolledAlpha = ShuttleMotionMath.measureUncontrolledAngularAcceleration(
                            previousPostWorldOmega, dt, worldOmega);
                    Vector3d observedFreeAlpha = ShuttleMotionMath.forecastNextWorldAngularAcceleration(
                            measuredUncontrolledAlpha, previousPostNativeOrientation, pose.orientation());
                    Vector3d transportVelocity = measureTransportVelocity(e.previousNativeCom, e.previousStepSeconds,
                            e.previousRotationPoint, rotationPoint, nativeCom, sdkVelocity);
                    if (transportVelocity == null || !finite(nativeCom) || !finite(rotationPoint) || !Double.isFinite(dt) || dt <= 0) {
                        transition(level,e,"invalid native COM transport measurement"); continue;
                    }
                    double mergedMass = mass.getMass();
                    var inertia = mass.getInertiaTensor();
                    ShuttleMotionMath.ControlLimits controlLimits=e.freightFlight
                            ? ShuttleFreightFlightProfile.CONTROL_LIMITS : ShuttleMotionMath.LEGACY_LIMITS;
                    var controlled=ShuttleMotionMath.computeControlled(e.request,transportVelocity,sdkVelocity,
                            worldOmega,pose.orientation(),mergedMass,inertia,e.gravity,dt,observedFreeAlpha,e.drag,
                            controlLimits,e.freightFlight?e.lastAppliedCommand:null);
                    if(controlled==null) { transition(level,e,"invalid native state or impulse math"); continue; }
                    ShuttleMotionMath.CommandAcceleration nextCommand=controlled.commanded();
                    ShuttleMotionMath.Impulses result=controlled.impulses();
                    if (e.freightFlight) {
                        Vector3d nextLinear=vec(nextCommand.linearWorld()),nextAngular=vec(nextCommand.angularWorld());
                        double linearMagnitude=nextLinear.length(),angularMagnitude=nextAngular.length();
                        e.maxFreightLinearAcceleration=Math.max(e.maxFreightLinearAcceleration,linearMagnitude);
                        e.maxFreightAngularAcceleration=Math.max(e.maxFreightAngularAcceleration,angularMagnitude);
                        ShuttleMotionMath.CommandAcceleration beforeCommand=e.lastAppliedCommand;
                        if(beforeCommand!=null) {
                            double linearJerk=nextLinear.distance(vec(beforeCommand.linearWorld()))/dt;
                            double angularJerk=nextAngular.distance(vec(beforeCommand.angularWorld()))/dt;
                            e.maxFreightLinearJerk=Math.max(e.maxFreightLinearJerk,linearJerk);
                            e.maxFreightAngularJerk=Math.max(e.maxFreightAngularJerk,angularJerk);
                            if(linearJerk>ShuttleFreightFlightProfile.LINEAR_JERK_LIMIT+1e-8
                                    ||angularJerk>ShuttleFreightFlightProfile.ANGULAR_JERK_LIMIT+1e-8)
                                e.freightLimitsMaintained=false;
                        }
                        boolean linearTakeover=beforeCommand!=null&&vec(beforeCommand.linearWorld()).length()>ShuttleFreightFlightProfile.LINEAR_ACCELERATION_LIMIT+1e-12;
                        boolean angularTakeover=beforeCommand!=null&&vec(beforeCommand.angularWorld()).length()>ShuttleFreightFlightProfile.ANGULAR_ACCELERATION_LIMIT+1e-12;
                        if((!linearTakeover&&linearMagnitude>ShuttleFreightFlightProfile.LINEAR_ACCELERATION_LIMIT+1e-8)
                                ||(!angularTakeover&&angularMagnitude>ShuttleFreightFlightProfile.ANGULAR_ACCELERATION_LIMIT+1e-8))
                            e.freightLimitsMaintained=false;
                    }
                    if(result==null) { transition(level,e,"invalid native state or impulse math"); continue; }
                    h.applyLinearAndAngularImpulse(new Vector3d(result.linearLocal().x(),result.linearLocal().y(),result.linearLocal().z()),
                            new Vector3d(result.angularLocal().x(),result.angularLocal().y(),result.angularLocal().z()));
                    e.lastAppliedCommand=nextCommand;
                    Vector3d immediatePostWorldOmega = h.getAngularVelocity(new Vector3d());
                    e.lastPostActuationWorldAngularVelocity = immediatePostWorldOmega;
                    // The local impulse changes velocity, not the already-read native pose; pair this
                    // completed-step orientation with the immediate POST omega for the next interval.
                    Quaterniond immediatePostNativeOrientation = new Quaterniond(pose.orientation());
                    e.lastPostActuationNativeOrientation = immediatePostNativeOrientation;
                    e.callbacks++;
                    e.previousNativeCom = nativeCom;
                    e.previousRotationPoint = rotationPoint;
                    e.previousStepSeconds = dt;
                    e.transportVelocity = new Vector3d(transportVelocity);
                    if (e.angularObservationEnabled) {
                        try {
                            e.latestAngularObservation = new AngularControlObservation(e.owner.getUniqueId(), level.getGameTime(),
                                    e.callbacks, dt, QuaternionValue.copy(pose.orientation()), vector(sdkVelocity),
                                    vector(transportVelocity), vector(worldOmega),
                                    previousPostWorldOmega == null ? null : vector(previousPostWorldOmega),
                                    previousPostNativeOrientation == null ? null : QuaternionValue.copy(previousPostNativeOrientation),
                                    vector(measuredUncontrolledAlpha), vector(observedFreeAlpha), vector(e.gravity), result.angularLocal(),
                                    vector(immediatePostWorldOmega), InertiaValue.copy(inertia), mergedMass,
                                    e.drag, e.request);
                        } catch (RuntimeException telemetryFailure) {
                            clearAngularObservation(e, true);
                            com.juicyslew.moonstation14.MoonStation14.LOGGER.error(
                                    "[fixture angular observation disabled] owner={} telemetry copy failed; native actuation retained",
                                    e.owner.getUniqueId(), telemetryFailure);
                        }
                    }
                } catch (RuntimeException failure) { transition(level,e,"native state/impulse failure"); }
            }
            if(entries.isEmpty()) LEVELS.remove(level);
        }
    }
    static Vector3d measureTransportVelocity(Vector3d previousNativeCom, double previousStepSeconds,
                                             Vector3d previousRotationPoint, Vector3d rotationPoint,
                                             Vector3d nativeCom, Vector3d sdkVelocity) {
        boolean changedBasis = previousRotationPoint != null && rotationPoint != null
                && !previousRotationPoint.equals(rotationPoint);
        return ShuttleMotionMath.measureTransportVelocity(changedBasis ? null : previousNativeCom,
                changedBasis ? 0 : previousStepSeconds, nativeCom, sdkVelocity);
    }
    public static synchronized void onLevelUnload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level) {
            Map<UUID, Entry> entries = LEVELS.remove(level);
            if (entries != null) for (Entry entry : entries.values()) {
                entry.request = null; entry.brakePending = false; entry.callbacks = 0;
                entry.freightFlight=false; entry.profileSeconds=0; entry.freightOriginCom=null; entry.lastAppliedCommand=null;
                entry.previousNativeCom = null; entry.previousRotationPoint = null; entry.previousStepSeconds = 0;
                entry.transportVelocity = null; entry.lastPostActuationWorldAngularVelocity = null;
                entry.lastPostActuationNativeOrientation = null; clearAngularObservation(entry, true);
            }
        }
    }
    private static boolean live(ServerLevel level, ServerSubLevel owner) {
        return owner != null && !owner.isRemoved() && owner.getLevel()==level
                && SubLevelContainer.getContainer(level).getSubLevel(owner.getUniqueId())==owner;
    }
}
