package com.juicyslew.moonstation14.ms14.shuttle.sable;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Matrix3dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

/** Shared test-only instantaneous local-inertia/native-world-angular-velocity contract probe. */
final class NativeAngularImpulseProbe {
    private NativeAngularImpulseProbe() { }
    record AxisResult(Vector3d axis,Vector3d impulse,Vector3d before,Vector3d after,
                      Vector3d expected,Vector3d restored,boolean responseOk,boolean restoredOk) { }
    record Result(java.util.List<AxisResult> axes) {
        boolean passed(){return axes.size()==3&&axes.stream().allMatch(a->a.responseOk&&a.restoredOk);}
        String details(){return axes.toString();}
    }
    static Result run(RigidBodyHandle handle,Matrix3dc localInertia,Quaterniondc orientation){
        var results=new java.util.ArrayList<AxisResult>(3);
        Vector3d[] axes={new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)};
        for(var axis:axes){
            var before=handle.getAngularVelocity(new Vector3d());
            var impulse=localInertia.transform(new Vector3d(axis).mul(.1));
            Vector3d after=null;
            try{
                handle.applyLinearAndAngularImpulse(new Vector3d(),new Vector3d(impulse));
                after=handle.getAngularVelocity(new Vector3d());
            }finally{
                handle.applyLinearAndAngularImpulse(new Vector3d(),new Vector3d(impulse).negate());
            }
            var restored=handle.getAngularVelocity(new Vector3d());
            var expected=orientation.transform(new Vector3d(axis).mul(.1),new Vector3d());
            results.add(new AxisResult(axis,impulse,before,after,expected,restored,
                    after!=null&&new Vector3d(after).sub(before).distance(expected)<=1e-5,
                    restored.distance(before)<=1e-5));
        }
        return new Result(java.util.List.copyOf(results));
    }
}
