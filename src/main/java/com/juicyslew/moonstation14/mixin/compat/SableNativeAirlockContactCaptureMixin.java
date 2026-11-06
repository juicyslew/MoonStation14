package com.juicyslew.moonstation14.mixin.compat;

import com.juicyslew.moonstation14.ms14.doors.AirlockBlock;
import com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity;
import com.juicyslew.moonstation14.ms14.doors.AirlockBumpSystem;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContact;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeCollisionGeometry;
import com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess;
import com.juicyslew.moonstation14.ms14.doors.NativeAirlockContactAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3d;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.function.Function;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/** Observes selected blockers after Sable's interior-face rejection; nominations do not participate in SAT. */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
public abstract class SableNativeAirlockContactCaptureMixin {
    @WrapOperation(method = "collide(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            remap = false, require = 1)
    private static Object moonstation14$observeNativeAirlockSelection(
            Map<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> firstCollisions,
            Object key, Function<SubLevel, SubLevelEntityCollision.FirstCollisionInfo> factory,
            Operation<Object> original,
            Entity entity, net.minecraft.world.phys.Vec3 collisionMotion,
            net.minecraft.world.phys.Vec3 velocityMotion,
            dev.ryanhcode.sable.api.math.LevelReusedVectors sink,
            @Local(name = "collisionInfo") SubLevelEntityCollision.CollisionInfo collisionInfo,
            @Local(name = "subLevel") SubLevel subLevel,
            @Local(name = "maxBlockPos") BlockPos.MutableBlockPos selectedPos,
             @Local(name = "maxBlockState") BlockState selectedState,
             @Local(name = "normalizedMtv") Vector3d normalizedMtv,
             @Local(name = "verticalCollision") boolean verticalCollision,
             // Pinned collide() LVT keeps these locals live at computeIfAbsent (BCI 2408); cubeOBB is deliberately excluded.
             @Local(name = "entityBoundsOBB") OrientedBoundingBox3d entityBoundsOBB,
             @Local(name = "entityBounds") net.minecraft.world.phys.AABB originalBounds,
            @Local(name = "subLevelPose") Pose3d subLevelPose,
            @Local(name = "maxAABB") BoundingBox3d maxAABB,
            @Local(name = "maxMTV") Vector3d maxMTV,
            @Local(name = "collisionMotion") Vector3d nativeCollisionMotion,
             @Local(name = "i") int iteration,
             @Local(name = "maxIter") int maxIteration,
             @Local(name = "substeps") int substeps) {
          Object result = original.call(firstCollisions, key, factory);
           if ((entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity
                   || entity instanceof net.minecraft.world.entity.LivingEntity)
                   && entity.level() == subLevel.getLevel()
                   && collisionInfo instanceof NativeSelectedContactAccess selectedContacts) {
                long serverTick = entity.level() instanceof ServerLevel server ? server.getServer().getTickCount() : Long.MIN_VALUE;
                selectedContacts.moonstation14$recordSelectedContact(subLevel.getUniqueId(), selectedPos,
                        new net.minecraft.world.phys.Vec3(normalizedMtv.x, normalizedMtv.y, normalizedMtv.z), verticalCollision,
                        entity.position(), entity.getDeltaMovement(), serverTick, entity.level().getGameTime(),
                       entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose && loose.hasPendingNativeMoveCapture());
          }
         if (entity instanceof com.juicyslew.moonstation14.entities.LooseItemEntity loose
                 && entity.level() instanceof ServerLevel server
                 && subLevel instanceof ServerSubLevel frame && frame.getLevel() == server
                 && selectedState != null && Double.isFinite(normalizedMtv.x)
                 && Double.isFinite(normalizedMtv.y) && Double.isFinite(normalizedMtv.z)
                 && normalizedMtv.lengthSquared() > 1.0E-12) {
              loose.observeNativeFrameContact(collisionInfo, frame.getUniqueId(), selectedPos,
                      normalizedMtv.x, normalizedMtv.y, normalizedMtv.z, verticalCollision);
         }
         boolean eligibleBumpActor = entity instanceof net.minecraft.world.entity.LivingEntity living
                && AirlockBumpSystem.isBumpOpener(living);
         if (selectedState != null && eligibleBumpActor
                && entity.level() instanceof ServerLevel server
                && subLevel instanceof ServerSubLevel frame && frame.getLevel() == server
                && collisionInfo instanceof NativeAirlockContactAccess contacts
                && !verticalCollision
                && Double.isFinite(normalizedMtv.x) && Double.isFinite(normalizedMtv.y)
                && Double.isFinite(normalizedMtv.z)
                && normalizedMtv.x * normalizedMtv.x + normalizedMtv.z * normalizedMtv.z > 1.0E-12
                ) {
            BlockPos selected = selectedPos.immutable();
            boolean selectionClaimed = false;
             NativeCollisionGeometry diagnostic = null;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos candidatePos = selected.offset(dx, dy, dz);
                if (!AirlockBumpSystem.sameContactPlane(candidatePos, selected)) continue;
                BlockState candidate = LoadedFrameRayQuery.knownServerBlock(server, frame, candidatePos);
                if (candidate == null || !(candidate.getBlock() instanceof AirlockBlock)) continue;
                BlockPos rootPos = candidate.getValue(AirlockBlock.HALF) == DoubleBlockHalf.UPPER
                        ? candidatePos.below().immutable() : candidatePos.immutable();
                BlockState lower = LoadedFrameRayQuery.knownServerBlock(server, frame, rootPos);
                BlockState upper = LoadedFrameRayQuery.knownServerBlock(server, frame, rootPos.above());
                var controller = LoadedFrameRayQuery.knownBlockEntity(server, frame, rootPos);
                if (lower == null || upper == null || !(lower.getBlock() instanceof AirlockBlock)
                        || !(upper.getBlock() instanceof AirlockBlock)
                        || lower.getValue(AirlockBlock.HALF) != DoubleBlockHalf.LOWER
                        || upper.getValue(AirlockBlock.HALF) != DoubleBlockHalf.UPPER
                        || !(controller instanceof AirlockBlockEntity door)) continue;
                boolean directWitness = candidatePos.equals(selected) && candidate.equals(selectedState);
                if (!directWitness && !collisionTimeWitness(server, frame, candidatePos, candidate,
                        entityBoundsOBB, subLevelPose, normalizedMtv)) continue;
                if (!selectionClaimed) {
                    if (!contacts.moonstation14$claimAirlockSelection(frame, selectedPos, selectedState,
                            normalizedMtv.x, normalizedMtv.y, normalizedMtv.z, iteration, maxIteration)) break;
                    selectionClaimed = true;
                }
                 if (diagnostic == null) diagnostic = NativeCollisionGeometry.capture(entityBoundsOBB,
                        subLevelPose, maxAABB, maxMTV, nativeCollisionMotion, iteration, substeps);
                OrientedBoundingBox3d preMoveBody = preMoveBody(originalBounds, entityBoundsOBB, subLevelPose);
                boolean preMoveDoorWitness = collisionTimeWitness(server, frame, candidatePos, candidate,
                        preMoveBody, subLevelPose, normalizedMtv);
                 contacts.moonstation14$recordAirlockContact(new NativeAirlockContact(frame, selected,
                         selectedState, candidatePos, candidate, rootPos, door.identity(), normalizedMtv.x,
                         normalizedMtv.y, normalizedMtv.z, diagnostic, true, false, preMoveDoorWitness, null));
                 door.traceNativeBumpContact(entity.getUUID(),frame.getUniqueId(),selected);
             }
         }
          if(entity.level().isClientSide&&selectedState!=null&&!verticalCollision
                  &&(com.juicyslew.moonstation14.ms14.doors.AirlockContactCaptureGate.activeFor(entity)
                    || com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.isTrackedCharacter(entity))
                 &&subLevel instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel frame&&frame.getLevel()==entity.level()
                 &&collisionInfo instanceof NativeAirlockContactAccess contacts
                 &&selectedState.getBlock() instanceof AirlockBlock
                 &&Double.isFinite(normalizedMtv.x)&&Double.isFinite(normalizedMtv.y)&&Double.isFinite(normalizedMtv.z)
                 &&normalizedMtv.x*normalizedMtv.x+normalizedMtv.z*normalizedMtv.z>1.0E-12){
             BlockPos selected=selectedPos.immutable();
             BlockPos root=selectedState.getValue(AirlockBlock.HALF)==DoubleBlockHalf.UPPER?selected.below():selected;
              var controller=LoadedFrameRayQuery.knownClientBlockEntity(entity.level(),frame,root);
              AirlockBlockEntity.ClientView view=controller instanceof AirlockBlockEntity door?door.authoritativeClientView():null;
              java.util.UUID controllerId=view==null?null:view.identity();
              boolean tracked=com.juicyslew.moonstation14.ms14.shuttle.sable.CurrentFrameCollisionScope.isTrackedCharacter(entity);
              NativeCollisionGeometry clientGeometry=tracked?NativeCollisionGeometry.capture(entityBoundsOBB,
                      subLevelPose,maxAABB,maxMTV,nativeCollisionMotion,iteration,substeps):null;
              OrientedBoundingBox3d clientPreBody=tracked?preMoveBody(originalBounds,entityBoundsOBB,subLevelPose):null;
              boolean clientWitness=tracked&&collisionTimeWitness(entity.level(),frame,selected,selectedState,
                      clientPreBody,subLevelPose,normalizedMtv);
              if(contacts.moonstation14$airlockContacts().size()<4){
              contacts.moonstation14$recordAirlockContact(new NativeAirlockContact(frame,selected,selectedState,
                      selected,selectedState,root,controllerId,normalizedMtv.x,normalizedMtv.y,normalizedMtv.z,
                      clientGeometry,true,false,clientWitness,view));
             if(controller instanceof AirlockBlockEntity observedDoor&&view!=null)
                 observedDoor.traceClientNativeBumpContact(entity.getUUID(),frame.getUniqueId(),root,selected);
             }
         }
         return result;
    }

    private static OrientedBoundingBox3d preMoveBody(net.minecraft.world.phys.AABB bounds,
            OrientedBoundingBox3d capturedOrientation, Pose3d pose) {
        if (bounds == null || capturedOrientation == null || pose == null) return null;
        Vector3d center = new Vector3d((bounds.minX + bounds.maxX) * .5,
                (bounds.minY + bounds.maxY) * .5, (bounds.minZ + bounds.maxZ) * .5);
        return new OrientedBoundingBox3d(center,
                new Vector3d(bounds.maxX - bounds.minX,
                        bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ),
                new org.joml.Quaterniond(capturedOrientation.getOrientation()), new LevelReusedVectors());
    }

    private static boolean collisionTimeWitness(net.minecraft.world.level.Level level, SubLevel frame, BlockPos pos,
            BlockState state, OrientedBoundingBox3d body, Pose3d pose, Vector3d normal) {
        if (body == null || pose == null || !(level instanceof dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension))
            return false;
        for (net.minecraft.world.phys.AABB piece : state.getCollisionShape(level, pos).toAabbs()) {
            Vector3d center = new Vector3d(pos.getX() + (piece.minX + piece.maxX) * .5,
                    pos.getY() + (piece.minY + piece.maxY) * .5, pos.getZ() + (piece.minZ + piece.maxZ) * .5);
            pose.transformPosition(center);
            LevelReusedVectors candidateScratch = new LevelReusedVectors();
            var shape = new OrientedBoundingBox3d(center,
                    new Vector3d((piece.maxX - piece.minX) * pose.scale().x(),
                            (piece.maxY - piece.minY) * pose.scale().y(),
                            (piece.maxZ - piece.minZ) * pose.scale().z()), pose.orientation(), candidateScratch);
            LevelReusedVectors bodyScratch = new LevelReusedVectors();
            OrientedBoundingBox3d bodyCopy = new OrientedBoundingBox3d(
                    new Vector3d(body.getPosition()), new Vector3d(body.getDimensions()),
                    new org.joml.Quaterniond(body.getOrientation()), bodyScratch);
            Vector3d mtv = new Vector3d(OrientedBoundingBox3d.satToleranced(bodyCopy, shape, 0));
            if (mtv.lengthSquared() > 0 && mtv.normalize().dot(normal) > .9) return true;
        }
        return false;
    }
}
