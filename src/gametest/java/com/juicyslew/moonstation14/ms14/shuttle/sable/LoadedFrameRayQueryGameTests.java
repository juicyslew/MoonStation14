package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.BoundHumanFixture;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.BodyInteractionAccess;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Loaded query proof against actual plot pose/identity and a real bound body fixture. */
@GameTestTags({"shuttle-query", "shuttle-access", "shuttle-examine", "shuttle"})
@PrefixGameTestTemplate(false)
public final class LoadedFrameRayQueryGameTests {
    private LoadedFrameRayQueryGameTests() { }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void bodyPairWorldLosAndProjectedHullOcclusion(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos site=new BlockPos(400,300,400);
        var scene=SableFixtureScene.acquire(helper,site,8,3,8);
        SableFixtureScene.awaitEmpty(helper,scene,40,()->{
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(400.5,300,400.5),plot->{
                BlockPos origin=plot.storageOrigin(); plot.ensureStorageFootprint(origin,8,3,8);
                plot.setStorageBlock(origin.offset(3,1,2),Blocks.IRON_BLOCK.defaultBlockState());
                plot.setStorageBlock(origin.offset(2,1,2),Blocks.GLASS.defaultBlockState());
                plot.setStorageBlock(origin.offset(2,1,2),Blocks.GLASS.defaultBlockState());
                Quaterniond rotation=new Quaterniond().rotationY(Math.PI/2);
                plot.subLevel().logicalPose().orientation().set(rotation);
                var pose=plot.subLevel().logicalPose();
                RigidBodyHandle.of(plot.subLevel()).teleport(pose.position(),rotation);
                plot.subLevel().updateLastPose(); plot.subLevel().updateBoundingBox(); return origin;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            var actor=BoundHumanFixture.create(level,new Vec3(410.5,300,400.5),"access-pair-a");
            var target=BoundHumanFixture.create(level,new Vec3(412.5,300,400.5),"access-pair-b");
            Object actorHands=actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            Object targetHands=target.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
            require(BodyInteractionAccess.inRange(actor,target,5),"clear ordinary-world body eyes admit within root range");
            var pose=authored.plot().subLevel().logicalPose();
            actor.setPos(pose.transformPosition(new Vec3(authored.result().getX()+1.5,authored.result().getY(),authored.result().getZ()+2.5)));
            target.setPos(pose.transformPosition(new Vec3(authored.result().getX()+5.5,authored.result().getY(),authored.result().getZ()+2.5)));
            require(!BodyInteractionAccess.inRange(actor,target,5),"projected steel hull blocks body-to-body eye LOS");
            require(level.getEntity(actor.getUUID())==actor && level.getEntity(target.getUUID())==target
                            && actor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())==actorHands
                            && target.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get())==targetHands,
                    "body root and hand authorities remain exact across world and projected LOS checks");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void translatedFrameUpdatesCurrentTargetPointForReachAndLos(GameTestHelper helper) {
        var level=helper.getLevel(); BlockPos site=new BlockPos(440,300,440);
        var scene=SableFixtureScene.acquire(helper,site,8,3,8);
        SableFixtureScene.awaitEmpty(helper,scene,40,()->{
            var human=BoundHumanFixture.create(level,new Vec3(440.5,300,440.5),"access-moving-frame-human");
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(440.5,300,440.5),plot->{
                BlockPos origin=plot.storageOrigin(); plot.ensureStorageFootprint(origin,8,3,8);
                plot.setStorageBlock(origin.offset(3,1,2),Blocks.IRON_BLOCK.defaultBlockState()); return origin;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            var wall=authored.result().offset(3,1,2);
            var before=LoadedFrameRayQuery.currentTargetPoint(level,wall);
            require(before!=null && BodyBlockUseTargetProbe.withinReach(level,human,wall)
                            && BodyBlockUseTargetProbe.lineOfSight(level,human,wall),
                    "bound human initially reaches the current world-space frame target");
            var handle=RigidBodyHandle.of(authored.plot().subLevel());
            var velocity=handle.getLinearVelocity(new Vector3d());
            handle.addLinearAndAngularVelocity(new Vector3d(1.25,0,0),new Vector3d());
            BoundedGameTestWait.safeDelay(helper,12,()->{
                var after=LoadedFrameRayQuery.currentTargetPoint(level,wall);
                require(after!=null && before.frameId().equals(after.frameId()) && before.worldPoint().distanceTo(after.worldPoint())>.1,
                        "native velocity changes observed pose and current target point without changing storage block or frame UUID");
                require(BodyBlockUseTargetProbe.withinReach(level,human,wall)
                                && BodyBlockUseTargetProbe.lineOfSight(level,human,wall),
                        "body reach/LOS continue at the translated current target, not stale initial world coordinates");
                require(handle.getLinearVelocity(new Vector3d()).distance(velocity)>.1,"bounded native frame velocity remains observable");
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-query", "hands-captured-target-regression"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void capturedFrameHandTargetTracksNativeMotion(GameTestHelper helper) {
        var level=helper.getLevel(); BlockPos site=new BlockPos(460,300,460);
        var scene=SableFixtureScene.acquire(helper,site,8,3,8);
        SableFixtureScene.awaitEmpty(helper,scene,40,()->{
            var human=BoundHumanFixture.create(level,new Vec3(460.5,300,460.5),"captured-frame-target-human");
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(460.5,300,460.5),plot->{
                BlockPos origin=plot.storageOrigin(); plot.ensureStorageFootprint(origin,8,3,8);
                plot.setStorageBlock(origin.offset(3,1,2),Blocks.IRON_BLOCK.defaultBlockState()); return origin;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            BlockPos cell=authored.result().offset(3,1,2);
            var pose=authored.plot().subLevel().logicalPose();
            Vec3 from=pose.transformPosition(new Vec3(cell.getX()-2.5,cell.getY()+.5,cell.getZ()+.5));
            Vec3 to=pose.transformPosition(new Vec3(cell.getX()+1.5,cell.getY()+.5,cell.getZ()+.5));
            var hit=LoadedFrameRayQuery.clip(level,from,to,16,.125,1,
                    (g,a,b)->new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,human));
            var captured=LoadedFrameRayQuery.capture(hit,to);
            require(hit.frameHit()!=null && captured!=null && captured.frameId().equals(authored.plot().engineId())
                            && captured.frameCell().equals(cell),"native clip captures exact frame UUID and hit cell");
            Vec3 before=LoadedFrameRayQuery.resolveCapturedTarget(level,captured);
            require(before!=null && before.distanceTo(pose.transformPosition(captured.point()))<1e-7,
                    "captured storage point resolves through current native pose");
            var fixedWorld=com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.world(
                    Vec3.atCenterOf(authored.result().offset(1,1,2)));
            Vec3 fixedWorldBefore=LoadedFrameRayQuery.resolveCapturedTarget(level,fixedWorld);
            require(fixedWorldBefore!=null,"nearby loaded world target resolves before native motion");
            Vec3 fixed=captured.point();
            var handle=RigidBodyHandle.of(authored.plot().subLevel());
            Quaterniond orientationBefore=new Quaterniond(pose.orientation());
            handle.addLinearAndAngularVelocity(new Vector3d(1.25,0,0),new Vector3d(0,.35,0));
            BoundedGameTestWait.safeDelay(helper,12,()->{
                var current=authored.plot().subLevel().logicalPose();
                Vec3 after=LoadedFrameRayQuery.resolveCapturedTarget(level,captured);
                Vec3 fixedWorldAfter=LoadedFrameRayQuery.resolveCapturedTarget(level,fixedWorld);
                require(after!=null && after.distanceTo(current.transformPosition(captured.point()))<1e-7
                                && after.distanceTo(before)>.1,"captured target follows actual native frame motion");
                require(fixedWorldAfter!=null && fixedWorldAfter.equals(fixedWorldBefore),
                        "world CapturedHandTarget remains exactly fixed during frame motion");
                 require(new Quaterniond(current.orientation()).difference(orientationBefore).angle()>1e-5,
                        "bounded native angular velocity changes current frame orientation");
                require(captured.point().equals(fixed),"captured storage coordinate remains exactly unchanged");
                helper.succeed();
            });
        });
    }

    @GameTestTags({"shuttle-query", "hands-captured-target-regression"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void rotatedFrameHitAndNearerWorldWall(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos site=new BlockPos(280,300,280);
        var scene=SableFixtureScene.acquire(helper,site,8,3,8);
        SableFixtureScene.awaitEmpty(helper,scene,40,()->{
            var human=BoundHumanFixture.create(level,new Vec3(280.5,300,280.5),"query-bound-human");
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(280.5,300,280.5),plot->{
                BlockPos origin=plot.storageOrigin(); plot.ensureStorageFootprint(origin,8,3,8);
                plot.setStorageBlock(origin.offset(3,1,2),Blocks.IRON_BLOCK.defaultBlockState());
                Quaterniond rotation=new Quaterniond().rotationY(Math.PI/2);
                plot.subLevel().logicalPose().orientation().set(rotation);
                var pose=plot.subLevel().logicalPose();
                RigidBodyHandle.of(plot.subLevel()).teleport(pose.position(),rotation);
                plot.subLevel().updateLastPose(); plot.subLevel().updateBoundingBox();
                return origin;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            BlockPos wall=authored.result().offset(3,1,2);
            var pose=authored.plot().subLevel().logicalPose();
            Vec3 from=pose.transformPosition(new Vec3(authored.result().getX()+.5,authored.result().getY()+1.5,authored.result().getZ()+2.5));
            Vec3 to=pose.transformPosition(new Vec3(authored.result().getX()+5.5,authored.result().getY()+1.5,authored.result().getZ()+2.5));
            Vec3 extendedFrom = pose.transformPosition(new Vec3(authored.result().getX()-8.5,
                    authored.result().getY()+1.5,authored.result().getZ()+2.5));
            var frameContext = LoadedFrameRayQuery.frameRayContexts(level, extendedFrom, to, 1.0f).stream()
                    .filter(context -> context.frameId().equals(authored.plot().engineId())).findFirst().orElse(null);
            require(frameContext != null && frameContext.localFrom().distanceTo(pose.transformPositionInverse(extendedFrom)) < 1e-7
                            && frameContext.traversalFrom().distanceTo(frameContext.localFrom()) > 1.0,
                    "frame traversal crops to its existing plot/native envelope while preserving the physical ray endpoint");
            var result=LoadedFrameRayQuery.clip(level,from,to,16,.125,1,(g,a,b)->new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,human));
            require(result.frameHit()!=null&&result.frameHit().storagePos().equals(wall)
                    &&result.frameHit().frameId().equals(authored.plot().engineId())
                    &&!result.frameHit().worldPoint().equals(Vec3.atCenterOf(wall)),"rotated hull hit retains storage block, transformed world point, and exact frame UUID");
            Vec3 protrudingPoint=result.frameHit().storagePoint().add(1.01,0,0);
            var protruding=com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.frame(
                    protrudingPoint,authored.plot().engineId(),wall);
            var protrudingResolved=LoadedFrameRayQuery.resolveCapturedTarget(level,protruding);
            require(protrudingResolved!=null && protrudingResolved.distanceTo(pose.transformPosition(protrudingPoint))<1e-7,
                    "captured coordinate resolves outside its known loaded source cell cube");
            human.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,result.frameHit().worldPoint());
            Vec3 aim = BodyItemTargetProbe.loadedAimPoint(level, human, BodyItemTargetProbe.MAX_THROW_REACH);
            require(aim != null && aim.distanceTo(result.frameHit().worldPoint()) < .05,
                    "ordinary item-aim margin admits the same minimally allocated projected wall");
            var admitted=BodyBlockUseTargetProbe.probe(level,human,wall,result.frameHit().localFace());
            require(admitted!=null&&authored.plot().engineId().equals(admitted.frameId())
                    &&admitted.pos().equals(wall)&&admitted.worldLocation().distanceTo(result.frameHit().worldPoint())<.01,
                    "normal body block-use probe admits the nominated face in minimal loaded storage without halo allocation");
            var resolved = LoadedFrameRayQuery.currentTargetPoint(level, wall);
            require(resolved != null && authored.plot().engineId().equals(resolved.frameId())
                            && resolved.worldPoint().distanceTo(result.frameHit().worldPoint()) < 1.0
                            && BodyBlockUseTargetProbe.withinReach(level, human, wall),
                    "body reach resolves the exact loaded storage target through its live frame pose");
            human.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, resolved.worldPoint());
            require(BodyBlockUseTargetProbe.lineOfSight(level, human, wall),
                    "frame-qualified LOS admits its current projected target, not raw storage coordinates");
            human.setPos(pose.transformPosition(new Vec3(authored.result().getX()+.5,
                    authored.result().getY(),authored.result().getZ()+2.5)));
            Vec3 examineEye = human.getEyePosition();
            Vec3 examinePoint = pose.transformPosition(Vec3.atCenterOf(wall));
            human.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    examinePoint);
            authored.plot().setStorageBlock(wall.offset(-1, 0, 0), Blocks.GLASS.defaultBlockState());
            var selectedExamine = com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.select(level,
                    human.getEyePosition(), human.getLookAngle(), 16, java.util.Set.of(human.getId()), 1.0f);
            require(selectedExamine.stream().anyMatch(candidate -> candidate.target().frameId() != null
                            && candidate.target().frameId().equals(authored.plot().engineId())
                            && candidate.target().blockPos().equals(wall)),
                    "production shared selector nominates the exact loaded storage cell and frame UUID");
            var examineTarget = com.juicyslew.moonstation14.ms14.examine.ExamineTarget.frameBlock(result.frameHit());
             var examined = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human, examineTarget);
             require(examined.isPresent() && examined.get().target().frameId().equals(authored.plot().engineId())
                             && examined.get().root().equals(wall)
                             && com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.visibility(level, human, examined.get()).visible(),
                     "frame-qualified examination resolves the exact loaded cell and current projected LOS");
            BlockPos examineWorldBlocker = BlockPos.containing(human.getEyePosition().lerp(examined.get().point(), .5));
            require(!examineWorldBlocker.equals(BlockPos.containing(human.getEyePosition()))
                            && !examineWorldBlocker.equals(BlockPos.containing(examined.get().point()))
                            && level.hasChunkAt(examineWorldBlocker),
                    "world examine-ray blocker is a loaded intermediate cell");
            var windowCandidate = selectedExamine.stream().filter(candidate -> candidate.transparent()
                            && candidate.target().frameId().equals(authored.plot().engineId())
                            && candidate.target().blockPos().equals(wall.offset(-1, 0, 0))).findFirst().orElse(null);
            require(windowCandidate != null,
                    "transparent frame window remains a selectable candidate before the opaque target");
            var windowResolved = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human,
                    windowCandidate.target());
            require(windowResolved.isPresent() && com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver
                            .visibility(level, human, windowResolved.get()).visible(),
                    "selected frame window resolves and its own examination visibility is admitted");
            authored.plot().setStorageBlock(wall.offset(-1, 0, 0), Blocks.STONE.defaultBlockState());
            require(!com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.visibility(level, human, examined.get()).visible(),
                    "nearer opaque wall in the same frame blocks the examined cell");
            authored.plot().setStorageBlock(wall.offset(-1, 0, 0), Blocks.GLASS.defaultBlockState());
            require(com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human,
                    com.juicyslew.moonstation14.ms14.examine.ExamineTarget.frameBlock(java.util.UUID.randomUUID(), wall)).isEmpty(),
                    "wrong frame UUID cannot resolve a same-coordinate storage cell");
            BlockPos nearerWorldWall = BlockPos.containing(from.lerp(to, .125));
            require(!nearerWorldWall.equals(BlockPos.containing(from))
                            && !nearerWorldWall.equals(BlockPos.containing(to)),
                    "world blocker occupies an intermediate cell, not an examiner or target endpoint cell");
            level.setBlock(nearerWorldWall, Blocks.STONE.defaultBlockState(), 3);
            var blocked=LoadedFrameRayQuery.clip(level,from,to,16,.125,1,(g,a,b)->new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,human));
            require(blocked.worldHit()!=null&&blocked.worldHit().getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                            && from.distanceToSqr(blocked.worldHit().getLocation())
                            < from.distanceToSqr(result.frameHit().worldPoint()),
                    "nearer ordinary world wall outranks farther rotated hull even if a frame hit is also reported");
            level.setBlock(nearerWorldWall, Blocks.AIR.defaultBlockState(), 3);
            require(level.setBlock(examineWorldBlocker, Blocks.STONE.defaultBlockState(), 3),
                    "world examine-ray blocker is authored into its loaded cell");
            var blockedTarget = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human, examineTarget);
            require(blockedTarget.isPresent() && !com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver
                            .visibility(level, human, blockedTarget.get()).visible(),
                    "nearer world wall blocks examination of the nominated frame cell");
            level.setBlock(examineWorldBlocker, Blocks.AIR.defaultBlockState(), 3);
            var lower = com.juicyslew.moonstation14.block.ModBlocks.AIRLOCK.get().defaultBlockState()
                    .setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.FACING, net.minecraft.core.Direction.WEST)
                    .setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF,
                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
            authored.plot().setStorageBlock(wall, lower);
            authored.plot().setStorageBlock(wall.above(), lower.setValue(
                    com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            var airlockFrame = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level)
                    .getSubLevel(authored.plot().engineId());
            var upperState = LoadedFrameRayQuery.knownServerBlock(level, airlockFrame, wall.above());
            require(com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.canonicalRoot(upperState,
                    wall.above(), pos -> LoadedFrameRayQuery.knownServerBlock(level, airlockFrame, pos)).equals(wall),
                    "upper frame airlock canonicalization reads lower state from its owning known plot chunk");
            var controller = LoadedFrameRayQuery.knownBlockEntity(level, airlockFrame, wall);
            require(controller instanceof com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity,
                    "frame airlock motion query obtains only its existing root controller");
            var door = (com.juicyslew.moonstation14.ms14.doors.AirlockBlockEntity) controller;
            var doorData = door.prototype(); var doorView = door.clientView();
            require(doorData != null && doorView != null, "fixture root controller has resolved prototype and motion state");
            double elapsed = Math.max(0, level.getGameTime() - doorView.transitionStartedAt()) / 20.0;
            double expectedOpenness = com.juicyslew.moonstation14.ms14.doors.AirlockGeometry.openness(doorView.phase(), elapsed,
                    doorData.door().openTimeOneSeconds(), doorData.door().openTimeTwoSeconds(),
                    doorData.door().closeTimeOneSeconds(), doorData.door().closeTimeTwoSeconds());
            require(com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.airlockOpenness(level, wall,
                            lower, controller) == expectedOpenness,
                    "existing frame controller supplies the current animated openness without Level lookup");
            var airlockCandidates = com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.select(level,
                    human.getEyePosition(), human.getLookAngle(), 16, java.util.Set.of(human.getId()), 1.0f);
            var airlockCandidate = airlockCandidates.stream().filter(candidate -> candidate.target().frameId() != null
                    && candidate.target().frameId().equals(authored.plot().engineId())
                    && candidate.target().blockPos().equals(wall)).findFirst().orElse(null);
            require(airlockCandidate != null, "production frame selector nominates visible individual airlock pieces");
            var airlockResolved = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human,
                    airlockCandidate.target());
            require(airlockResolved.isPresent() && airlockResolved.get().root().equals(wall)
                            && com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver
                            .visibility(level, human, airlockResolved.get()).visible(),
                    "selected frame airlock resolves and examines through its canonical lower root");
            BlockPos behindAirlock = wall.east();
            authored.plot().setStorageBlock(behindAirlock, Blocks.IRON_BLOCK.defaultBlockState());
            var doorChunk = level.getChunkSource().getChunkNow(wall.getX() >> 4, wall.getZ() >> 4);
            require(doorChunk != null && doorChunk.getBlockEntity(wall) == controller,
                    "test begins open-aperture fallback from the same known root-controller chunk");
            lower = lower.setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN, true);
            authored.plot().setStorageBlock(wall, lower);
            authored.plot().setStorageBlock(wall.above(), lower.setValue(
                    com.juicyslew.moonstation14.ms14.doors.AirlockBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER).setValue(
                    com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN, true));
            doorChunk.removeBlockEntity(wall);
            require(LoadedFrameRayQuery.knownBlockEntity(level, airlockFrame, wall) == null
                            && com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.airlockOpenness(
                            level, wall, lower.setValue(com.juicyslew.moonstation14.ms14.doors.AirlockBlock.OPEN, true), null) == 1.0,
                    "missing existing frame controller uses known OPEN state without materializing a replacement");
            human.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    authored.plot().subLevel().logicalPose().transformPosition(Vec3.atCenterOf(behindAirlock)));
            var openCandidates = com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.select(level,
                    human.getEyePosition(), human.getLookAngle(), 16, java.util.Set.of(human.getId()), 1.0f);
            var throughOpen = openCandidates.stream().filter(candidate -> candidate.target().frameId() != null
                    && candidate.target().frameId().equals(authored.plot().engineId())
                    && candidate.target().blockPos().equals(behindAirlock)).findFirst().orElse(null);
            require(throughOpen != null, "open airlock aperture does not invent a blocking plane and selects the storage block behind");
            require(LoadedFrameRayQuery.knownBlockEntity(level, airlockFrame, wall) == null,
                    "selector does not materialize a missing airlock controller while querying open geometry");
            var throughOpenResolved = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human,
                    throughOpen.target());
            require(throughOpenResolved.isPresent() && com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver
                            .visibility(level, human, throughOpenResolved.get()).visible(),
                    "open airlock aperture leaves the behind-frame examine target visible");
            require(LoadedFrameRayQuery.revalidate(level,result.frameHit()),"current engine UUID revalidates while live");
            var stale=result.frameHit(); authored.plot().close();
            require(!LoadedFrameRayQuery.revalidate(level,stale),"removed engine UUID cannot revalidate a stale frame target");
             require(LoadedFrameRayQuery.currentTargetPoint(level, wall) == null
                            && !BodyBlockUseTargetProbe.withinReach(level, human, wall)
                            && !BodyBlockUseTargetProbe.lineOfSight(level, human, wall),
                     "removed frame refuses current target resolution and body admission");
             var removedCapture=LoadedFrameRayQuery.capture(result, to);
             require(removedCapture!=null && LoadedFrameRayQuery.resolveCapturedTarget(level,removedCapture)==null
                             && LoadedFrameRayQuery.resolveCapturedTarget(level,
                             com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.frame(
                                     Vec3.atCenterOf(wall),java.util.UUID.randomUUID(),wall))==null,
                     "removed and unknown frame identities refuse captured resolution without world fallback");
            level.setBlock(nearerWorldWall,Blocks.AIR.defaultBlockState(),3);
            helper.succeed();
        });
    }

    @GameTestTags({"shuttle-query", "hands-captured-target-regression"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=100)
    public static void missingProjectedChunkInsideFrameDomainRefusesWithoutLoading(GameTestHelper helper) {
        var level=helper.getLevel();
        BlockPos site=new BlockPos(360,300,360);
        var scene=SableFixtureScene.acquire(helper,site,56,3,8);
        SableFixtureScene.awaitEmpty(helper,scene,40,()->{
            var human=BoundHumanFixture.create(level,new Vec3(360.5,300,360.5),"query-gap-human");
            var authored=SableFixturePlot.allocateAndAuthor(level,new Vector3d(360.5,300,360.5),plot->{
                BlockPos origin=plot.storageOrigin();
                plot.ensureStorageFootprint(origin,8,3,8);
                plot.setStorageBlock(origin.offset(40,1,2),Blocks.IRON_BLOCK.defaultBlockState());
                return origin;
            });
            SableFixtureScene.trackPlot(helper,authored.plot());
            // Expand reserved ownership only after Sable has registered/finalized its native body.
            authored.plot().subLevel().getPlot().setBoundingBox(new dev.ryanhcode.sable.companion.math.BoundingBox3i(
                    authored.result(),authored.result().offset(48,3,8)));
            authored.plot().subLevel().updateBoundingBox();
            BlockPos gap=authored.result().offset(20,1,2);
            require(authored.plot().subLevel().getPlot().contains(new net.minecraft.world.level.ChunkPos(gap)),
                    "missing-chunk gap remains in the reserved plot");
            require(level.getChunkSource().getChunkNow(gap.getX()>>4,gap.getZ()>>4)==null,
                    "nonadjacent authored cells leave a real unloaded storage chunk between them");
            Vec3 from=new Vec3(authored.result().getX()+8.5,authored.result().getY()+1.5,authored.result().getZ()+2.5);
            Vec3 to=new Vec3(authored.result().getX()+45.5,authored.result().getY()+1.5,authored.result().getZ()+2.5);
            var result=LoadedFrameRayQuery.clip(level,from,to,16,.125,0,
                    (g,a,b)->new ClipContext(a,b,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,human));
            require(result.state()==LoadedFrameRayQuery.State.UNKNOWN,
                    "ray through an unallocated gap in candidate frame bounds refuses instead of treating it as air");
            require(level.getChunkSource().getChunkNow(gap.getX()>>4,gap.getZ()>>4)==null,
                    "unknown projected gap query does not force-load the storage chunk");
            var gapTarget=com.juicyslew.moonstation14.ms14.hands.network.CapturedHandTarget.frame(
                    Vec3.atCenterOf(gap),authored.plot().engineId(),gap);
            require(LoadedFrameRayQuery.resolveCapturedTarget(level,gapTarget)==null
                            && level.getChunkSource().getChunkNow(gap.getX()>>4,gap.getZ()>>4)==null,
                    "captured target in owned-but-unloaded frame gap refuses without loading it");
            BlockPos examineTargetPos = authored.result().offset(32,1,2);
            authored.plot().setStorageBlock(examineTargetPos, Blocks.IRON_BLOCK.defaultBlockState());
            var livePose = authored.plot().subLevel().logicalPose();
            Vec3 desiredEye = livePose.transformPosition(new Vec3(authored.result().getX()+18.5,
                    authored.result().getY()+1.5,authored.result().getZ()+2.5));
            human.setPos(desiredEye.subtract(human.getEyePosition().subtract(human.position())));
            Vec3 targetPoint = livePose.transformPosition(Vec3.atCenterOf(examineTargetPos));
            human.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, targetPoint);
            var resolved = com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver.resolve(level, human,
                    com.juicyslew.moonstation14.ms14.examine.ExamineTarget.frameBlock(authored.plot().engineId(), examineTargetPos));
            require(resolved.isPresent() && !com.juicyslew.moonstation14.ms14.examine.ExamineTargetResolver
                            .visibility(level, human, resolved.get()).visible(),
                    "frame examination refuses a loaded nominated target behind an unknown owned storage cell");
            require(level.getChunkSource().getChunkNow(gap.getX()>>4,gap.getZ()>>4)==null,
                    "frame examination unknown-cell refusal does not force-load its gap");
            helper.succeed();
        });
    }

    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
