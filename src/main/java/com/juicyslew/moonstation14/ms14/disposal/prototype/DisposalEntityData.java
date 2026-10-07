package com.juicyslew.moonstation14.ms14.disposal.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

/** Supported disposal-tube composition. This intentionally excludes upstream components without local consumers. */
public final class DisposalEntityData {
    public record TubeGeometry(double diameter) {
        public static final double DEFAULT_DIAMETER = 14.0 / 16.0;
        public TubeGeometry { if (!Double.isFinite(diameter) || diameter <= 0 || diameter > 1) throw new IllegalArgumentException("invalid disposal tube diameter"); }
        /** Core and logical port arms plus the Minecraft unit interface for trunks. */
        public List<AABB> boxes(DisposalEntityData.Tube tube, com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame) {
            return com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(this,tube,frame).boxes();
        }
        public List<AABB> buildBoxes(DisposalEntityData.Tube tube, com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame) {
            double r=diameter/2, lo=.5-r, hi=.5+r;
            var result=new ArrayList<AABB>();
            var ports=EnumSet.copyOf(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting.connections(tube,frame));
            if (tube.kind()==Kind.TRUNK) ports.add(DisposalDirection.UP);
            // Both mesh and collision derive their join from the same physical port set.
            boolean hub=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.needsHub(ports.stream().map(DisposalDirection::toMC).collect(java.util.stream.Collectors.toSet()));
            if(hub)addHub(result,r);
            for (var port : ports) {
                Direction d=port.toMC();
                double start=d.getStepX()<0?0:d.getStepX()>0?1:d.getStepY()<0?0:d.getStepY()>0?1:d.getStepZ()<0?0:d.getStepZ()>0?1:.5;
                double finish=.5+(hub?Math.signum(d.getStepX()+d.getStepY()+d.getStepZ())*com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.PORT_START:0);
                addCylinder(result,d,r,Math.min(start,finish),Math.max(start,finish));
            }
            return List.copyOf(result);
        }
        private static void addCylinder(List<AABB> boxes,Direction axis,double r,double alongMin,double alongMax){
            addCrossSection(boxes,axis,r,alongMin,alongMax);
        }
        private static void addCrossSection(List<AABB> boxes,Direction axis,double r,double alongMin,double alongMax){
            // Eight strips across one radial coordinate. The half-width uses the near strip edge
            // so the voxel approximation is conservative while retaining the circular corner gaps.
            double lo=.5-r;int slices=8;
            for(int i=0;i<slices;i++){
                double u0=lo+2*r*i/slices,u1=lo+2*r*(i+1)/slices;
                double nearest=Math.max(0,Math.max(u0-.5,.5-u1));double half=Math.sqrt(Math.max(0,r*r-nearest*nearest));
                switch(axis.getAxis()){
                    case X -> boxes.add(new AABB(alongMin,.5-half,u0,alongMax,.5+half,u1));
                    case Y -> boxes.add(new AABB(u0,alongMin,.5-half,u1,alongMax,.5+half));
                    case Z -> boxes.add(new AABB(u0,.5-half,alongMin,u1,.5+half,alongMax));
                }
            }
        }
        private static void addHub(List<AABB> boxes,double r){
            double bevel=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.HUB_BEVEL_INSET_FACTOR;
            double end=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.HUB_END_RADIUS_FACTOR;
            double h=r*com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.HUB_HALF_HEIGHT_FACTOR;
            double[] ys={.5-h,.5-h*bevel,.5+h*bevel,.5+h};double[] factors={end,1,1,end};
            for(int band=0;band<ys.length-1;band++){
                int slices=4;for(int i=0;i<slices;i++){
                    double y0=ys[band]+(ys[band+1]-ys[band])*i/slices,y1=ys[band]+(ys[band+1]-ys[band])*(i+1)/slices;
                    double radius=r*Math.max(factors[band]+(factors[band+1]-factors[band])*i/slices,factors[band]+(factors[band+1]-factors[band])*(i+1)/slices);
                    addCrossSection(boxes,Direction.UP,radius,y0,y1);
                }
            }
        }
        public List<com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.Quad> mesh(Tube tube, com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame) {
            return com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeGeometryCache.get(this,tube,frame).mesh();
        }
        public List<com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.Quad> buildMesh(Tube tube, com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation frame) {
            var ports=EnumSet.noneOf(Direction.class);
            for(var p:com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting.connections(tube,frame))ports.add(p.toMC());
            if(tube.kind()==Kind.TRUNK)ports.add(Direction.UP);
            return com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.mesh(ports,diameter);
        }
    }
    public record Unit(double powerWatts, double airLiters, boolean powerSwitch, String containerId, double entrySeconds, double draggedEntrySeconds,
                       double manualFlushSeconds, double automaticEngageSeconds, double rechargeSeconds,
                        double flushedVisualSeconds, double exitAttemptCooldownSeconds, String insertionSound) {
        public static final String DEFAULT_INSERTION_SOUND = "moonstation14:disposal/insert";
        public static final Codec<Unit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("powerWatts").forGetter(Unit::powerWatts), Codec.DOUBLE.fieldOf("airLiters").forGetter(Unit::airLiters),
                Codec.BOOL.fieldOf("powerSwitch").forGetter(Unit::powerSwitch), Codec.STRING.fieldOf("containerId").forGetter(Unit::containerId),
                Codec.DOUBLE.fieldOf("entrySeconds").forGetter(Unit::entrySeconds), Codec.DOUBLE.fieldOf("draggedEntrySeconds").forGetter(Unit::draggedEntrySeconds),
                Codec.DOUBLE.fieldOf("manualFlushSeconds").forGetter(Unit::manualFlushSeconds), Codec.DOUBLE.fieldOf("automaticEngageSeconds").forGetter(Unit::automaticEngageSeconds),
                Codec.DOUBLE.fieldOf("rechargeSeconds").forGetter(Unit::rechargeSeconds), Codec.DOUBLE.fieldOf("flushedVisualSeconds").forGetter(Unit::flushedVisualSeconds),
                Codec.DOUBLE.fieldOf("exitAttemptCooldownSeconds").forGetter(Unit::exitAttemptCooldownSeconds),
                Codec.STRING.optionalFieldOf("insertionSound", DEFAULT_INSERTION_SOUND).forGetter(Unit::insertionSound)
        ).apply(i, Unit::new));
        public Unit {
            for (double v : new double[]{powerWatts,airLiters,entrySeconds,draggedEntrySeconds,manualFlushSeconds,automaticEngageSeconds,rechargeSeconds,flushedVisualSeconds,exitAttemptCooldownSeconds})
                if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("invalid disposal unit timing/composition");
            if (powerWatts <= 0 || airLiters <= 0 || containerId == null || containerId.isBlank()
                    || insertionSound == null || insertionSound.isBlank()
                    || net.minecraft.resources.ResourceLocation.tryParse(insertionSound) == null)
                throw new IllegalArgumentException("unit power, air volume and insertion sound must be valid");
        }
    }
    /** Holder prototype is intentionally minimal: no copied Physics or Item composition. */
    public record Holder(double fallbackAirLiters, double segmentSeconds, String containerId) {
        public static final Codec<Holder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("fallbackAirLiters").forGetter(Holder::fallbackAirLiters),
                Codec.DOUBLE.fieldOf("segmentSeconds").forGetter(Holder::segmentSeconds),
                Codec.STRING.fieldOf("containerId").forGetter(Holder::containerId)
        ).apply(i, Holder::new));
        public Holder {
            if (!Double.isFinite(fallbackAirLiters) || fallbackAirLiters <= 0 || !Double.isFinite(segmentSeconds)
                    || segmentSeconds <= 0 || containerId == null || containerId.isBlank())
                throw new IllegalArgumentException("invalid disposal holder component defaults");
        }
    }
    /** Local placement-step adaptation of the supported SS14 construction recipe. */
    public record ConstructionStep(int steelCount, double timeSeconds, String material) {
        public static final Codec<ConstructionStep> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("steelCount").forGetter(ConstructionStep::steelCount),
                Codec.DOUBLE.fieldOf("timeSeconds").forGetter(ConstructionStep::timeSeconds),
                Codec.STRING.fieldOf("material").forGetter(ConstructionStep::material)
        ).apply(i, ConstructionStep::new));
        public ConstructionStep {
            if (steelCount < 1 || !Double.isFinite(timeSeconds) || timeSeconds < 0
                    || material == null || material.isBlank()) throw new IllegalArgumentException("invalid disposal construction step");
        }
    }
    /** Common Minecraft contained-body presentation configuration; not an SS14 component claim. */
    public record ContainedViewData(boolean cameraCollision) {
        public static final ContainedViewData DEFAULT = new ContainedViewData(true);
        public static final Codec<ContainedViewData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("cameraCollision", true).forGetter(ContainedViewData::cameraCollision)
        ).apply(i, ContainedViewData::new));
    }
    public enum Kind { STRAIGHT, BEND, TRUNK, THREE_WAY, THREE_WAY_FLIPPED, Y_JUNCTION, X_JUNCTION, TAGGER, ROUTER }
    public enum Topology {
        STRAIGHT(List.of(DisposalDirection.NORTH, DisposalDirection.SOUTH)),
        BEND(List.of(DisposalDirection.NORTH, DisposalDirection.WEST)),
        TRUNK(List.of(DisposalDirection.NORTH)),
        THREE_WAY(List.of(DisposalDirection.NORTH, DisposalDirection.WEST, DisposalDirection.SOUTH)),
        THREE_WAY_FLIPPED(List.of(DisposalDirection.NORTH, DisposalDirection.EAST, DisposalDirection.SOUTH)),
        Y_JUNCTION(List.of(DisposalDirection.NORTH, DisposalDirection.EAST, DisposalDirection.WEST)),
        X_JUNCTION(List.of(DisposalDirection.NORTH, DisposalDirection.WEST, DisposalDirection.SOUTH, DisposalDirection.EAST));
        private final List<DisposalDirection> directions;
        Topology(List<DisposalDirection> directions) { this.directions = directions; }
        public List<DisposalDirection> directions() { return directions; }
    }
    public record Tube(Kind kind, Topology topology, int rotationQuarters, double segmentSeconds,
                       double turnDamage, String tag, Set<String> routerTags) {
        public static final Codec<Tube> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Tube::kind),
                Codec.STRING.xmap(Topology::valueOf, Topology::name).fieldOf("topology").forGetter(Tube::topology),
                Codec.INT.optionalFieldOf("rotationQuarters", 0).forGetter(Tube::rotationQuarters),
                Codec.DOUBLE.optionalFieldOf("segmentSeconds", 0.1).forGetter(Tube::segmentSeconds),
                Codec.DOUBLE.optionalFieldOf("turnDamage", 0.0).forGetter(Tube::turnDamage),
                Codec.STRING.optionalFieldOf("tag", "").forGetter(Tube::tag),
                Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).optionalFieldOf("routerTags", Set.of()).forGetter(Tube::routerTags)
        ).apply(i, Tube::new));
        public Tube {
            if (kind == null || topology == null || rotationQuarters < 0 || rotationQuarters > 3
                    || !Double.isFinite(segmentSeconds) || segmentSeconds <= 0
                    || !Double.isFinite(turnDamage) || turnDamage < 0 || tag == null || routerTags == null)
                throw new IllegalArgumentException("invalid disposal tube composition");
            if ((kind == Kind.STRAIGHT && topology != Topology.STRAIGHT) || (kind == Kind.BEND && topology != Topology.BEND)
                    || (kind == Kind.TRUNK && topology != Topology.TRUNK) || (kind == Kind.THREE_WAY && topology != Topology.THREE_WAY)
                    || (kind == Kind.THREE_WAY_FLIPPED && topology != Topology.THREE_WAY_FLIPPED)
                    || (kind == Kind.Y_JUNCTION && topology != Topology.Y_JUNCTION) || (kind == Kind.X_JUNCTION && topology != Topology.X_JUNCTION)
                    || (kind == Kind.TAGGER && topology != Topology.STRAIGHT) || (kind == Kind.ROUTER && topology != Topology.THREE_WAY && topology != Topology.THREE_WAY_FLIPPED))
                throw new IllegalArgumentException("tube kind/topology mismatch");
        }
    }
    public static final Codec<DisposalEntityData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("anchored", false).forGetter(d -> d.anchored),
            Codec.DOUBLE.optionalFieldOf("tubeDiameter", TubeGeometry.DEFAULT_DIAMETER).forGetter(d -> d.geometry.diameter()),
            Codec.STRING.xmap(Kind::valueOf, Kind::name).optionalFieldOf("kind").forGetter(d -> Optional.ofNullable(d.tube).map(Tube::kind)),
            Codec.STRING.xmap(Topology::valueOf, Topology::name).optionalFieldOf("topology").forGetter(d -> Optional.ofNullable(d.tube).map(Tube::topology)),
            Codec.INT.optionalFieldOf("rotationQuarters", 0).forGetter(d -> d.tube == null ? 0 : d.tube.rotationQuarters()),
            Codec.DOUBLE.optionalFieldOf("segmentSeconds", .1).forGetter(d -> d.tube == null ? .1 : d.tube.segmentSeconds()),
            Codec.DOUBLE.optionalFieldOf("turnDamage", 0.0).forGetter(d -> d.tube == null ? 0.0 : d.tube.turnDamage()),
            Codec.STRING.optionalFieldOf("tag", "").forGetter(d -> d.tube == null ? "" : d.tube.tag()),
            Codec.STRING.listOf().xmap(Set::copyOf, ArrayList::new).optionalFieldOf("routerTags", Set.of()).forGetter(d -> d.tube == null ? Set.of() : d.tube.routerTags()),
            Unit.CODEC.optionalFieldOf("unit").forGetter(d -> Optional.ofNullable(d.unit)),
            Holder.CODEC.optionalFieldOf("holder").forGetter(d -> Optional.ofNullable(d.holder)),
             ConstructionStep.CODEC.optionalFieldOf("construction").forGetter(d -> Optional.ofNullable(d.construction)),
             ContainedViewData.CODEC.optionalFieldOf("containedView", ContainedViewData.DEFAULT).forGetter(d -> d.containedView)
    ).apply(i, (anchored, diameter, kind, topology, rotation, seconds, damage, tag, tags, unit, holder, construction, containedView) -> {
        Tube tube = kind.isPresent() && topology.isPresent() ? new Tube(kind.get(), topology.get(), rotation, seconds, damage, tag, tags) : null;
        return new DisposalEntityData(anchored, tube, unit.orElse(null), holder.orElse(null), new TubeGeometry(diameter), construction.orElse(null), containedView);
    }));
    private final boolean anchored;
    private final Tube tube;
    private final Unit unit;
    private final Holder holder;
    private final TubeGeometry geometry;
    private final ConstructionStep construction;
    private final ContainedViewData containedView;
    public DisposalEntityData(Tube tube) { this(true, tube, null); }
    public DisposalEntityData(boolean anchored, Tube tube) { this(anchored, tube, null); }
    public DisposalEntityData(boolean anchored, Tube tube, Unit unit) { this(anchored, tube, unit, null); }
    public DisposalEntityData(boolean anchored, Tube tube, Unit unit, Holder holder) { this(anchored,tube,unit,holder,new TubeGeometry(TubeGeometry.DEFAULT_DIAMETER)); }
    public DisposalEntityData(boolean anchored, Tube tube, Unit unit, Holder holder, TubeGeometry geometry) { this(anchored,tube,unit,holder,geometry,null); }
    public DisposalEntityData(boolean anchored, Tube tube, Unit unit, Holder holder, TubeGeometry geometry, ConstructionStep construction) { this(anchored, tube, unit, holder, geometry, construction, ContainedViewData.DEFAULT); }
    public DisposalEntityData(boolean anchored, Tube tube, Unit unit, Holder holder, TubeGeometry geometry, ConstructionStep construction, ContainedViewData containedView) { this.anchored = anchored; this.tube = tube; this.unit = unit; this.holder = holder; this.geometry=Objects.requireNonNull(geometry); this.construction=construction; this.containedView=Objects.requireNonNull(containedView); }
    public boolean anchored() { return anchored; }
    public Tube tube() { if (tube == null) throw new IllegalStateException("prototype has no tube component"); return tube; }
    public Unit unit() { return unit; }
    public Holder holder() { return holder; }
    public TubeGeometry geometry() { return geometry; }
    public ConstructionStep construction() { return construction; }
    public ContainedViewData containedView() { return containedView; }
}
