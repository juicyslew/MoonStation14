package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;

/** Exact owner-only render query context for SDK camera and body endpoint inversions. */
public final class OwnedFramePresentationContext implements AutoCloseable {
    private static final ThreadLocal<ArrayDeque<OwnedFramePresentationContext>> STACK=ThreadLocal.withInitial(ArrayDeque::new);
    private final Entity owner;
    private boolean closed;
    private OwnedFramePresentationContext(Entity owner){this.owner=owner;}
    /** Null when this is not the committed locally predicted owner or has no published body clock. */
    public static OwnedFramePresentationContext open(Entity entity) {
        if(entity==null||!entity.level().isClientSide||!GhostControlClient.ownsSableRemoteFrameInterpolation(entity)
                ||!ClientFrameTimelineStore.hasPresentation(entity.level(),entity.getUUID()))return null;
        var scope=new OwnedFramePresentationContext(entity);STACK.get().push(scope);return scope;
    }
    public static void begin(Entity entity){open(entity);}
    public static void end(Entity entity){
        var stack=STACK.get();if(stack.isEmpty()||stack.peek().owner!=entity)return;
        stack.pop().closed=true;if(stack.isEmpty())STACK.remove();
    }
    public static OwnedFramePresentationContext active(Level level,SubLevel frame) {
        var stack=STACK.get();if(stack.isEmpty())return null;
        var scope=stack.peek();return scope.owner.level()==level&&scope.owner.level().isClientSide
                &&GhostControlClient.ownsSableRemoteFrameInterpolation(scope.owner)?scope:null;
    }
    public Entity owner(){return owner;}
    public Pose3dc from(SubLevel frame){return ClientFrameTimelineStore.presentationEndpoint(owner.level(),owner.getUUID(),frame.getUniqueId(),true);}
    public Pose3dc to(SubLevel frame){return ClientFrameTimelineStore.presentationEndpoint(owner.level(),owner.getUUID(),frame.getUniqueId(),false);}
    static Vec3 projectOwnedInterval(Pose3dc fromFrame,Pose3dc toFrame,Pose3dc renderFrame,
            Vec3 bodyWorldFrom,Vec3 bodyWorldTo,float alpha) {
        Vec3 fromLocal=fromFrame.transformPositionInverse(bodyWorldFrom);
        Vec3 toLocal=toFrame.transformPositionInverse(bodyWorldTo);
        return renderFrame.transformPosition(fromLocal.lerp(toLocal,Math.max(0f,Math.min(1f,alpha))));
    }
    @Override public void close(){if(closed)return;closed=true;var stack=STACK.get();if(!stack.isEmpty()&&stack.peek()==this)stack.pop();else stack.remove(this);if(stack.isEmpty())STACK.remove();}
}
