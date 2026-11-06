package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Test-only, bounded server-local cargo support evidence. No render/packet encoding is inferred. */
final class CargoMotionRecorder {
    record Row(int sample, long tick, String item, String frame, Vec3 world, Vec3 local, Boolean settled,
               Vec3 own, Vec3 requested, Vec3 accepted, Vec3 before, Vec3 after,
               Vec3 beforeLocal, Vec3 afterLocal, String beforeFrame, String afterFrame,
               Vec3 linear, Vec3 angular, double qx, double qy, double qz, double qw, String invalid) { }
    private final BodyHandReleaseNativeGameTests.Mode mode;
    private final List<Row> rows = new ArrayList<>(161);
    private boolean dumped, overflow;
    CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode mode) { this.mode = mode; }

    void record(int sample, long tick, LooseItemEntity item, Vec3 local, ServerSubLevel sub) {
        var capture = item == null ? null : item.nativeMoveCapture();
        var point = item == null ? null : LoadedFrameRayQuery.currentActorPoint(item.level(), item);
        var frame = point == null ? null : point.frameId();
        Vec3 linear = null, angular = null; double qx=Double.NaN,qy=Double.NaN,qz=Double.NaN,qw=Double.NaN;
        if (sub != null) {
            var h = RigidBodyHandle.of(sub);
            if (h != null && h.isValid()) {
                var v=h.getLinearVelocity(new Vector3d()); var w=h.getAngularVelocity(new Vector3d());
                linear=new Vec3(v.x,v.y,v.z); angular=new Vec3(w.x,w.y,w.z);
                Quaterniondc q=sub.logicalPose().orientation(); qx=q.x(); qy=q.y(); qz=q.z(); qw=q.w();
            }
        }
        var beforePoint = capture == null ? null : capture.beforeActorPoint();
        var afterPoint = capture == null ? null : capture.afterActorPoint();
        String sourceInvalid = point == null || sub == null || frame == null || !frame.equals(sub.getUniqueId())
                ? "current logical frame point unavailable/mismatched" : null;
        append(new Row(sample,tick,item == null ? null : item.getUUID().toString(),frame == null ? null : frame.toString(),
                item == null ? null : item.position(),point == null ? local : point.storagePoint(),item == null ? null : item.settled(),
                item == null ? null : item.getDeltaMovement(),capture == null ? null : capture.requested(),
                capture == null ? null : capture.acceptedOwnDelta(),capture == null ? null : capture.before(),
                capture == null ? null : capture.atReturn(),beforePoint == null ? null : beforePoint.storagePoint(),
                afterPoint == null ? null : afterPoint.storagePoint(),beforePoint == null ? null : String.valueOf(beforePoint.frameId()),
                afterPoint == null ? null : String.valueOf(afterPoint.frameId()),linear,angular,qx,qy,qz,qw,sourceInvalid));
    }

    void append(Row row) {
        if (rows.size() >= 161) { overflow=true; return; }
        if (!rows.isEmpty() && (row.sample()!=rows.get(rows.size()-1).sample()+1
                || row.tick()!=rows.get(rows.size()-1).tick()+1)) overflow=true;
        String invalid=row.invalid()!=null?row.invalid():invalidReason(row);
        if (invalid!=null) overflow=true;
        if (row.invalid()==null && invalid!=null) row=new Row(row.sample(),row.tick(),row.item(),row.frame(),row.world(),row.local(),
                row.settled(),row.own(),row.requested(),row.accepted(),row.before(),row.after(),row.beforeLocal(),row.afterLocal(),
                row.beforeFrame(),row.afterFrame(),row.linear(),row.angular(),row.qx(),row.qy(),row.qz(),row.qw(),invalid);
        rows.add(row);
    }

    void dump(String reason) {
        if (dumped) return; dumped=true;
        Summary summary=summarize(rows);
        Vec3 min=summary.min(),max=summary.max(),maxStep=summary.maxAbsStep();
        System.out.println("CARGO_SERVER_LOCAL_SUPPORT mode="+mode+" reason="+reason+" samples="+rows.size()
                +" complete="+complete()+" xyzMin="+vec(min)+" xyzMax="+vec(max)+" xyzPeakToPeak="
                +vec(min==null||max==null?null:max.subtract(min))+" maxAbsStep="+vec(maxStep)+" totalDrift="+vec(summary.totalDrift())
                +" packetStoragePoint=unavailable/no existing public packet-point API");
        for(Row r:rows) System.out.println("CARGO_ROW sample="+r.sample()+" tick="+r.tick()+" item="+r.item()+" frame="+r.frame()
                +" world="+vec(r.world())+" local="+vec(r.local())+" settled="+r.settled()+" own="+vec(r.own())+" requested="+vec(r.requested())
                +" accepted="+vec(r.accepted())+" nativeBefore="+vec(r.before())+" nativeAfter="+vec(r.after())+" localBefore="+vec(r.beforeLocal())
                +" localAfter="+vec(r.afterLocal())+" frameBefore="+r.beforeFrame()+" frameAfter="+r.afterFrame()+" linear="+vec(r.linear())
                +" angular="+vec(r.angular())+" q="+Double.toString(r.qx())+","+Double.toString(r.qy())+","+Double.toString(r.qz())+","+Double.toString(r.qw())
                +" invalid="+r.invalid());
    }
    record Summary(Vec3 min, Vec3 max, Vec3 maxAbsStep, Vec3 totalDrift) { }
    static Summary summarize(List<Row> rows) {
        Vec3 min=null,max=null,maxStep=Vec3.ZERO;
        for(int i=0;i<rows.size();i++) {
            Vec3 p=rows.get(i).local(); if(p==null||!finite(p)) continue;
            min=min==null?p:new Vec3(Math.min(min.x,p.x),Math.min(min.y,p.y),Math.min(min.z,p.z));
            max=max==null?p:new Vec3(Math.max(max.x,p.x),Math.max(max.y,p.y),Math.max(max.z,p.z));
            if(i>0&&rows.get(i-1).local()!=null&&finite(rows.get(i-1).local())) { Vec3 d=p.subtract(rows.get(i-1).local());
                maxStep=new Vec3(Math.max(maxStep.x,Math.abs(d.x)),Math.max(maxStep.y,Math.abs(d.y)),Math.max(maxStep.z,Math.abs(d.z))); }
        }
        Vec3 drift=rows.isEmpty()||!finite(rows.get(0).local())||!finite(rows.get(rows.size()-1).local())?null
                :rows.get(rows.size()-1).local().subtract(rows.get(0).local());
        return new Summary(min,max,maxStep,drift);
    }
    boolean complete() {
        if(overflow||rows.size()!=161) return false;
        for(int i=0;i<rows.size();i++) if(rows.get(i).sample()!=i||invalidReason(rows.get(i))!=null) return false;
        return true;
    }
    private static String invalidReason(Row r) {
        if(r==null) return "missing row";
        if(r.sample()<0||r.tick()<0||r.item()==null||!uuid(r.frame())) return "missing/invalid ordinal identity or frame UUID";
        if(!finite(r.world())||!finite(r.local())||!finite(r.own())||!finite(r.linear())||!finite(r.angular())) return "missing/nonfinite required vector";
        double qn=r.qx()*r.qx()+r.qy()*r.qy()+r.qz()*r.qz()+r.qw()*r.qw();
        if(!Double.isFinite(qn)||Math.abs(qn-1)>1e-4) return "invalid native pose quaternion";
        if(r.sample()>0 && (r.requested()==null||r.accepted()==null||r.before()==null||r.after()==null
                ||!finite(r.requested())||!finite(r.accepted())||!finite(r.before())||!finite(r.after())
                ||!finite(r.beforeLocal())||!finite(r.afterLocal())||r.beforeFrame()==null||r.afterFrame()==null
                ||!r.frame().equals(r.beforeFrame())||!r.frame().equals(r.afterFrame()))) return "missing/nonfinite native movement capture";
        return null;
    }
    private static boolean uuid(String text) { if(text==null)return false; try { UUID.fromString(text); return true; }
        catch(IllegalArgumentException invalid) { return false; } }
    static String vec(Vec3 v) { return v==null?"unavailable":"("+Double.toString(v.x)+","+Double.toString(v.y)+","+Double.toString(v.z)+")"; }
    static boolean finite(Vec3 v) { return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z); }
}
