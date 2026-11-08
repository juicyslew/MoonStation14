package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Matrix3f;

/** Screen-plane rotation for the 3D billboard adaptation of SS14's velocity-oriented sprite. */
public final class ProjectileSpritePose {
    private ProjectileSpritePose() {}
    public static float rotation(Vec3 velocity, Quaternionf cameraOrientation, boolean noRot) {
        if (noRot || velocity == null || cameraOrientation == null || !Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) return 0f;
        Vector3f view = new Vector3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
        cameraOrientation.transformInverse(view);
        float x = view.x, y = view.y;
        if (!Float.isFinite(x) || !Float.isFinite(y) || x*x + y*y < 1.0e-12f) return 0f;
        // Source bullet art points up; align that axis to projected travel direction.
        return (float) Math.atan2(y, x) - (float) (Math.PI * 0.5);
    }

    /** World-space billboard basis with image +Y aligned to travel; offset is degrees about travel. */
    public static Quaternionf worldRotation(Vec3 velocity, Vec3 cameraPosition, Vec3 center, float offsetDegrees) {
        if(velocity==null||cameraPosition==null||center==null||!finite(velocity)||!finite(cameraPosition)||!finite(center)||velocity.lengthSqr()<1e-12||!Float.isFinite(offsetDegrees))return null;
        Vector3f along=new Vector3f((float)velocity.x,(float)velocity.y,(float)velocity.z).normalize();
        Vector3f toCamera=new Vector3f((float)(cameraPosition.x-center.x),(float)(cameraPosition.y-center.y),(float)(cameraPosition.z-center.z));
        Vector3f width=along.cross(toCamera,new Vector3f());
        if(width.lengthSquared()<1e-10f){ Vector3f fallback=Math.abs(along.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0); width=along.cross(fallback,new Vector3f()); }
        width.normalize(); Vector3f normal=width.cross(along,new Vector3f()).normalize();
        Quaternionf q=new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,width).setColumn(1,along).setColumn(2,normal));
        return q.rotateZ((float)Math.toRadians(offsetDegrees));
    }
    /** Camera-independent volume basis: source +Y follows travel, using referenceUp to fix roll. */
    public static Quaternionf volumeRotation(Vec3 direction, Vec3 referenceUp, float offsetDegrees) {
        if (direction == null || referenceUp == null || !finite(direction) || !finite(referenceUp) || !Float.isFinite(offsetDegrees) || direction.lengthSqr() < 1e-12) return new Quaternionf();
        Vector3f along=new Vector3f((float)direction.x,(float)direction.y,(float)direction.z).normalize();
        Vector3f up=new Vector3f((float)referenceUp.x,(float)referenceUp.y,(float)referenceUp.z);
        up.fma(-up.dot(along),along);
        if(up.lengthSquared()<1e-10f) {
            Vector3f axis=Math.abs(along.y)<.9f?new Vector3f(0,1,0):new Vector3f(1,0,0);
            up=axis.fma(-axis.dot(along),along);
        }
        up.normalize(); Vector3f width=new Vector3f(up).negate();
        Vector3f normal=width.cross(along,new Vector3f()).normalize();
        Quaternionf q=new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,width).setColumn(1,along).setColumn(2,normal));
        return q.rotateZ((float)Math.toRadians(offsetDegrees));
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
