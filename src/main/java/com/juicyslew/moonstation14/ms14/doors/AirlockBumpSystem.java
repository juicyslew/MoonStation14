package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.components.DoorBumpOpenerComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.LevelPoseProviderExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LevelExtension;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/** Server movement-contact owner: ordinary sweep before movement, native-frame contact after movement. */
public final class AirlockBumpSystem {
    static record ContactMemo(java.util.UUID root, long revision, boolean paidPowerReady) { }
    private static final java.util.Map<Entity, java.util.Map<java.util.UUID, ContactMemo>> LAST_WORLD_CONTACT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Entity, java.util.Map<java.util.UUID, ContactMemo>> LAST_FRAME_CONTACT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private AirlockBumpSystem() { }

    public static boolean isBumpOpener(net.minecraft.world.entity.LivingEntity actor){
        return actor!=null&&CharacterIdentitySystem.resolveForActor(actor)
                .filter(data->data.component(DoorBumpOpenerComponent.class).isPresent()).isPresent();
    }

    /** Read-only admission of doors selected by the actual owned client native move. */
    public static java.util.List<AirlockBlockEntity> clientPredictionCandidates(
            net.minecraft.world.entity.Mob actor, AABB preMoveBounds, Vec3 requested,
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move) {
        if (actor == null) return java.util.List.of();
        net.minecraft.world.level.Level level = actor.level();
        if (!level.isClientSide
                || actor.isRemoved() || actor.noPhysics || preMoveBounds == null || requested == null || move == null
                || !move.collisionInfoFresh() || move.collisionInfo() == null
                || !(move.collisionInfo() instanceof NativeAirlockContactAccess access)
                || move.collisionScope() == null
                || !isBumpOpener(actor) || !Double.isFinite(requested.x) || !Double.isFinite(requested.y)
                || !Double.isFinite(requested.z) || Math.abs(requested.x) > 4 || Math.abs(requested.y) > 4
                || Math.abs(requested.z) > 4) return java.util.List.of();
        AABB verticallyResolved = resolveLoadedVertical(level, actor, preMoveBounds, requested.y);
        if (verticallyResolved == null) return java.util.List.of();
        WorldPlane worldPlane = earliestWorldBlockingPlane(level, actor, verticallyResolved,
                new Vec3(requested.x, 0, requested.z));
        if (worldPlane.unknown() || worldPlane.doors().isEmpty()) {
            // A nearer ordinary obstruction (or unknown loaded boundary) prevents frame activation.
            if (worldPlane.firstT() < 1.0 - 1.0E-9 || worldPlane.unknown()) return java.util.List.of();
        }
        java.util.Map<java.util.UUID, AirlockBlockEntity> admitted = new java.util.LinkedHashMap<>();
        AABB finalBody = actor.getBoundingBox().inflate(1.0E-6);
        for (AirlockBlockEntity door : worldPlane.doors().values())
            if (nativeHullAllowsWorldCandidate(move)
                    && clientWorldDoorTouches(level, door, finalBody))
                admitted.putIfAbsent(door.authoritativeClientView().identity(), door);
        if (!admitted.isEmpty()) return java.util.List.copyOf(admitted.values());
        for (NativeAirlockContact contact : access.moonstation14$airlockContacts()) {
            if (contact == null || !contact.horizontal() || contact.vertical() || contact.diagnostic() == null
                    || !(contact.frame() instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel frame)
                    || frame.getLevel() != level || frame.isRemoved()
                    || dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame)
                continue;
            var selected = LoadedFrameRayQuery.knownClientBlock(level, frame, contact.selectedPos());
            var state = LoadedFrameRayQuery.knownClientBlock(level, frame, contact.storagePos());
            if (!sameCapturedBlockState(contact.selectedState(), selected)
                    || !sameCapturedBlockState(contact.state(), state)
                    || !(state.getBlock() instanceof AirlockBlock)) continue;
            BlockPos root = state.getValue(AirlockBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    ? contact.storagePos().below() : contact.storagePos();
            if (!root.equals(contact.rootPos())) continue;
            var lower = LoadedFrameRayQuery.knownClientBlock(level, frame, root);
            var upper = LoadedFrameRayQuery.knownClientBlock(level, frame, root.above());
            var controller = LoadedFrameRayQuery.knownClientBlockEntity(level, frame, root);
            if (lower == null || upper == null || !(lower.getBlock() instanceof AirlockBlock)
                    || !(upper.getBlock() instanceof AirlockBlock)
                    || lower.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    || upper.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    || lower.getValue(AirlockBlock.OPEN) || upper.getValue(AirlockBlock.OPEN)
                    || !(controller instanceof AirlockBlockEntity door) || !door.hasClientSnapshot()
                    || !clientRootMatches(contact.rootControllerIdentity(), door.authoritativeClientView())
                    || !java.util.Objects.equals(contact.observedClientView(), door.authoritativeClientView())
                    || !nativeOrderingAllows(level, actor, preMoveBounds, contact)) continue;
            admitted.putIfAbsent(door.authoritativeClientView().identity(), door);
        }
        return java.util.List.copyOf(admitted.values());
    }

    /** A horizontal native hull blocker is opaque to world-door admission unless it is itself an airlock. */
    private static boolean nativeHullAllowsWorldCandidate(
            com.juicyslew.moonstation14.ms14.shuttle.sable.SableOwnedBodyMotion.MoveResult move) {
        if (!(move.collisionInfo() instanceof com.juicyslew.moonstation14.ms14.shuttle.sable.NativeSelectedContactAccess access))
            return false;
        if (access.moonstation14$selectedContactsOverflowed()) return false;
        for (var selected : access.moonstation14$selectedContacts())
            if (!selected.verticalCollision()) return false;
        return true;
    }

    static boolean clientRootMatches(java.util.UUID capturedIdentity, AirlockBlockEntity.ClientView view) {
        return capturedIdentity != null && view != null && capturedIdentity.equals(view.identity());
    }

    static boolean sameCapturedBlockState(net.minecraft.world.level.block.state.BlockState captured,
            net.minecraft.world.level.block.state.BlockState current) {
        return captured != null && current != null && captured.equals(current);
    }

    /** Finds only the first ordinary-world blocking plane before movement; effects wait for final-body validation. */
    public static java.util.List<AirlockBlockEntity> beforeMoveContacts(Entity actor, Vec3 requested) {
        if (actor == null || requested == null || !(actor.level() instanceof ServerLevel level)
                || actor.isRemoved() || actor.noPhysics || !level.getServer().isSameThread()
                || Math.abs(requested.x) > 4 || Math.abs(requested.y) > 4 || Math.abs(requested.z) > 4
                || !(actor instanceof net.minecraft.world.entity.LivingEntity living)
                || !isBumpOpener(living)) return java.util.List.of();

        // Match vanilla's vertical-before-horizontal collision ordering.
        return beforeMoveContacts(level, actor, actor.getBoundingBox(), requested);
    }

    private static java.util.List<AirlockBlockEntity> beforeMoveContacts(
            net.minecraft.world.level.Level level, Entity actor, AABB bounds, Vec3 requested) {
        Vec3 horizontal = new Vec3(requested.x, 0, requested.z);
        if (horizontal.lengthSqr() < 1.0E-12) return java.util.List.of();
        AABB verticallyResolved = resolveLoadedVertical(level, actor, bounds, requested.y);
        if (verticallyResolved == null) return java.util.List.of();
        WorldPlane plane = earliestWorldBlockingPlane(level, actor, verticallyResolved, horizontal);
        return plane.unknown() ? java.util.List.of() : java.util.List.copyOf(plane.doors().values());
    }

    record WorldPlane(double firstT, java.util.Map<java.util.UUID, AirlockBlockEntity> doors,
                      boolean unknown) { }
    record PlaneHit(double t, java.util.UUID identity, AirlockBlockEntity door, boolean unknown) { }

    /** Resolve vanilla's Y leg against loaded ordinary-world collision shapes, without loading chunks. */
    private static AABB resolveLoadedVertical(net.minecraft.world.level.Level level, Entity entity, AABB bounds, double requestedY) {
        AABB swept = bounds.expandTowards(0, requestedY, 0).inflate(1.0E-5);
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        java.util.List<net.minecraft.world.phys.shapes.VoxelShape> shapes = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) return null;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof AirlockBlock) continue;
            var shape = state.getCollisionShape(level, pos,
                    net.minecraft.world.phys.shapes.CollisionContext.of(entity));
            if (!shape.isEmpty()) shapes.add(shape.move(pos.getX(), pos.getY(), pos.getZ()));
        }
        double clippedY = net.minecraft.world.phys.shapes.Shapes.collide(
                net.minecraft.core.Direction.Axis.Y, bounds, shapes, requestedY);
        return bounds.move(0, clippedY, 0);
    }

    /** Shared ordinary-world first-plane sweep. Closed airlocks occlude even without a usable controller. */
    private static WorldPlane earliestWorldBlockingPlane(net.minecraft.world.level.Level level, Entity actor, AABB start, Vec3 horizontal) {
        AABB swept = start.expandTowards(horizontal).inflate(1.0E-5);
        BlockPos min = BlockPos.containing(swept.minX, swept.minY, swept.minZ);
        BlockPos max = BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ);
        java.util.List<PlaneHit> hits = new java.util.ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) {
                // Treat the edge of loaded world as opaque for ordering; never activate
                // a visible door by sweeping through an unloaded candidate volume.
                AABB unloadedCell = new AABB(pos.getX(),pos.getY(),pos.getZ(),pos.getX()+1,pos.getY()+1,pos.getZ()+1);
                double t = timeOfImpact(start,horizontal,unloadedCell);
                if (t >= 0) hits.add(new PlaneHit(t, null, null, true));
                continue;
            }
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof AirlockBlock && worldAirlockSolid(level, pos, state)) {
                BlockPos root = state.getValue(AirlockBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos;
                AABB shape = new AABB(root.getX(), root.getY(), root.getZ(), root.getX()+1, root.getY()+2, root.getZ()+1);
                double t = timeOfImpact(start, horizontal, shape);
                if (t >= 0) {
                    var door = knownWorldDoor(level, root);
                    if (door instanceof AirlockBlockEntity candidate && level instanceof ServerLevel
                            && actor != null && !AirlockPassagePolicy.pairCollides(actor, candidate)) continue;
                    AirlockBlockEntity.ClientView clientView = level.isClientSide
                            && door instanceof AirlockBlockEntity d ? d.authoritativeClientView() : null;
                    java.util.UUID identity = door instanceof AirlockBlockEntity d
                            ? level.isClientSide ? clientView == null ? null : clientView.identity() : d.identity() : null;
                    hits.add(new PlaneHit(t, identity, door instanceof AirlockBlockEntity d ? d : null, false));
                }
            }
            if (state.getBlock() instanceof AirlockBlock) continue;
            for (AABB shape : state.getCollisionShape(level, pos).toAabbs()) {
                double t = timeOfImpact(start, horizontal, shape.move(pos));
                if (t >= 0) hits.add(new PlaneHit(t, null, null, false));
            }
        }
        return earliestWorldPlane(hits);
    }

    static WorldPlane earliestWorldPlane(java.util.List<PlaneHit> hits) {
        double first = Double.POSITIVE_INFINITY;
        boolean unknown = false;
        java.util.Map<java.util.UUID, AirlockBlockEntity> doors = new java.util.LinkedHashMap<>();
        for (PlaneHit hit : hits) {
            if (hit.t() < first - 1.0E-5) {
                first = hit.t(); doors.clear(); unknown = hit.unknown();
            }
            if (Math.abs(hit.t() - first) <= 1.0E-5) {
                unknown |= hit.unknown();
                if (hit.door() != null && hit.identity() != null)
                    doors.putIfAbsent(hit.identity(), hit.door());
            }
        }
        return new WorldPlane(first, java.util.Map.copyOf(doors), unknown);
    }

    static PlaneHit planeWall(double time) { return new PlaneHit(time, null, null, false); }
    static PlaneHit planeUnknown(double time) { return new PlaneHit(time, null, null, true); }
    static PlaneHit planeDoor(double time, java.util.UUID identity, AirlockBlockEntity door) {
        return new PlaneHit(time, identity, door, false);
    }

    static boolean worldAirlockSolid(net.minecraft.world.level.Level level, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state) {
        if (!level.isClientSide) return !state.getValue(AirlockBlock.OPEN);
        BlockPos root = state.getValue(AirlockBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? pos.below() : pos;
        var entity = knownWorldDoor(level, root);
        AirlockBlockEntity.ClientView known = entity instanceof AirlockBlockEntity door && door.hasClientSnapshot()
                ? door.clientView() : null;
        return clientWorldAirlockSolid(state.getValue(AirlockBlock.OPEN), known);
    }

    static boolean clientWorldAirlockSolid(boolean replicatedOpen, AirlockBlockEntity.ClientView known) {
        // Keep world ordering identical to the live block collision-shape fallback.
        return !AirlockBlock.selectClientCollisionShape(replicatedOpen, known).isEmpty();
    }

    private static net.minecraft.world.level.block.entity.BlockEntity knownWorldDoor(
            net.minecraft.world.level.Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        return level.getBlockEntity(pos);
    }

    private static boolean clientWorldDoorTouches(net.minecraft.world.level.Level level,
            AirlockBlockEntity door, AABB finalBody) {
        BlockPos root = door.getBlockPos();
        if (door.getLevel() != level || door.isRemoved() || knownWorldDoor(level, root) != door
                || !level.hasChunkAt(root.above())) return false;
        var lowerState = level.getBlockState(root);
        var upperState = level.getBlockState(root.above());
        if (!(lowerState.getBlock() instanceof AirlockBlock) || !(upperState.getBlock() instanceof AirlockBlock)
                || !lowerState.getValue(AirlockBlock.HALF).equals(
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                || !upperState.getValue(AirlockBlock.HALF).equals(
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                || lowerState.getValue(AirlockBlock.OPEN) || upperState.getValue(AirlockBlock.OPEN)) return false;
        AABB leaf = new AABB(root.getX(), root.getY(), root.getZ(), root.getX()+1, root.getY()+2, root.getZ()+1);
        return finalBody.intersects(leaf) && door.hasClientSnapshot()
                && door.authoritativeClientView() != null;
    }

    /** Final-pose ordinary-world admission prevents a nearer native frame wall from being bypassed. */
    public static boolean afterWorldMove(Entity actor, java.util.List<AirlockBlockEntity> nominated) {
        if (actor == null || nominated == null || nominated.isEmpty()
                || !(actor.level() instanceof ServerLevel level) || actor.isRemoved() || actor.noPhysics
                || !level.getServer().isSameThread() || !(actor instanceof net.minecraft.world.entity.LivingEntity living)
                || !isBumpOpener(living)) {
            if (actor != null) LAST_WORLD_CONTACT.remove(actor);
            return false;
        }
        AABB finalBody = actor.getBoundingBox().inflate(1.0E-6);
        java.util.Map<java.util.UUID, AirlockBlockEntity> touching = new java.util.LinkedHashMap<>();
        for (AirlockBlockEntity nominatedDoor : nominated) {
            if (nominatedDoor == null || nominatedDoor.isRemoved() || nominatedDoor.getLevel() != level) continue;
            BlockPos root = nominatedDoor.getBlockPos();
            if (level.getBlockEntity(root) != nominatedDoor) continue;
            var state = level.getBlockState(root);
            var upper = level.getBlockState(root.above());
            if (!(state.getBlock() instanceof AirlockBlock) || !(upper.getBlock() instanceof AirlockBlock)
                    || state.getValue(AirlockBlock.OPEN) || upper.getValue(AirlockBlock.OPEN)
                    || state.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    || upper.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                continue;
            AABB leaf = new AABB(root.getX(), root.getY(), root.getZ(),
                    root.getX() + 1, root.getY() + 2, root.getZ() + 1);
            if (finalBody.intersects(leaf)) touching.putIfAbsent(nominatedDoor.identity(), nominatedDoor);
        }
        if (touching.isEmpty()) {
            LAST_WORLD_CONTACT.remove(actor);
            return false;
        }
        return activateContactSet(actor, living, level, touching.values(), LAST_WORLD_CONTACT);
    }

    /**
     * Consume a fresh Sable collision result after native movement/carry has reached its final pose.
     * Callers must compare this result by reference to the result captured before that exact move.
     */
    public static boolean afterNativeMove(Entity actor, AABB preMoveBounds, Vec3 requested,
                                          SubLevelEntityCollision.CollisionInfo collisionInfo) {
        if (actor == null) return false;
        if (!(actor.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        if (preMoveBounds == null || requested == null || collisionInfo == null || actor.isRemoved() || actor.noPhysics
                || !(actor instanceof net.minecraft.world.entity.LivingEntity living) || !isBumpOpener(living)
                || !Double.isFinite(requested.x) || !Double.isFinite(requested.z)
                || Math.abs(requested.x) > 4 || Math.abs(requested.z) > 4) {
            LAST_FRAME_CONTACT.remove(actor);
            return false;
        }
        if (!(collisionInfo instanceof NativeAirlockContactAccess access)) {
            LAST_FRAME_CONTACT.remove(actor);
            return false;
        }
        var contacts = access.moonstation14$airlockContacts();
        if (contacts.isEmpty()) {
            LAST_FRAME_CONTACT.remove(actor);
            return false;
        }

        java.util.Map<java.util.UUID, AirlockBlockEntity> candidates = new java.util.LinkedHashMap<>();
        for (NativeAirlockContact contact : contacts) {
            if (contact == null || !contact.horizontal() || contact.vertical() || contact.frame().isRemoved()
                    || !Double.isFinite(contact.normalX()) || !Double.isFinite(contact.normalY())
                    || !Double.isFinite(contact.normalZ())
                    || contact.normalX() * contact.normalX() + contact.normalZ() * contact.normalZ() <= 1.0E-12
                     || !nativeOrderingAllows(level, actor, preMoveBounds, contact)
                    || !(contact.frame() instanceof ServerSubLevel frame) || frame.getLevel() != level
                    || frame.getPlot() == null || SubLevelContainer.getContainer(level).getSubLevel(frame.getUniqueId()) != frame)
                continue;
            var selectedBlocker = LoadedFrameRayQuery.knownServerBlock(level, frame, contact.selectedPos());
            if (selectedBlocker == null || !selectedBlocker.equals(contact.selectedState())) continue;
            var current = LoadedFrameRayQuery.knownServerBlock(level, frame, contact.storagePos());
            if (current == null || !current.equals(contact.state()) || !(current.getBlock() instanceof AirlockBlock)) continue;
            BlockPos capturedRoot = current.getValue(AirlockBlock.HALF)
                    == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    ? contact.storagePos().below() : contact.storagePos();
            if (!capturedRoot.equals(contact.rootPos()) || contact.rootControllerIdentity() == null) continue;
            var capturedController = LoadedFrameRayQuery.knownBlockEntity(level, frame, capturedRoot);
            if (!(capturedController instanceof AirlockBlockEntity capturedDoor)
                    || !capturedDoor.identity().equals(contact.rootControllerIdentity())) continue;
            var lower = LoadedFrameRayQuery.knownServerBlock(level, frame, capturedRoot);
            var upper = LoadedFrameRayQuery.knownServerBlock(level, frame, capturedRoot.above());
            if (lower == null || upper == null || !(lower.getBlock() instanceof AirlockBlock)
                    || !(upper.getBlock() instanceof AirlockBlock)
                    || lower.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                    || upper.getValue(AirlockBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                    || lower.getValue(AirlockBlock.OPEN) || upper.getValue(AirlockBlock.OPEN)
                    || capturedDoor.prototype() == null || !capturedDoor.prototype().door().bumpOpen()
                    || (capturedDoor.state().phase() != AirlockState.DoorPhase.CLOSED
                    && capturedDoor.state().phase() != AirlockState.DoorPhase.DENYING)) continue;
            // This metadata is recorded only after Sable selected this exact door piece as
            // a non-interior horizontal collision witness. SS14's bump event is collision-time,
            // before post-resolution SAT can legitimately separate the final body from the leaf.
            candidates.putIfAbsent(capturedDoor.identity(), capturedDoor);
        }
        if (candidates.isEmpty()) {
            LAST_FRAME_CONTACT.remove(actor);
            return false;
        }
        return activateContactSet(actor, living, level, candidates.values(), LAST_FRAME_CONTACT);
    }

    /** Native witness is collision-time proof; otherwise ordinary world must not block the solved endpoint. */
    private static boolean nativeOrderingAllows(net.minecraft.world.level.Level level, Entity actor,
            AABB preMoveBounds, NativeAirlockContact contact) {
        var diagnostic = contact.diagnostic();
        if (diagnostic == null) return false;
        if (contact.preMoveDoorWitness()) return true;
        var center = diagnostic.entityBoundsCenter();
        var mtv = diagnostic.maxMtv();
        Vec3 endpoint = new Vec3(center.x() + mtv.x(), center.y() + mtv.y(), center.z() + mtv.z());
        double requestedY = endpoint.y() - (preMoveBounds.minY + preMoveBounds.maxY) * .5;
        AABB start = resolveLoadedVertical(level, actor, preMoveBounds, requestedY);
        if (start == null) return false;
        Vec3 delta = new Vec3(endpoint.x() - (preMoveBounds.minX + preMoveBounds.maxX) * .5, 0,
                endpoint.z() - (preMoveBounds.minZ + preMoveBounds.maxZ) * .5);
        if (delta.lengthSqr() < 1.0E-12) return true;
        WorldPlane plane = earliestWorldBlockingPlane(level, actor, start, delta);
        return worldEndpointOrderingAllows(plane.firstT(), plane.unknown());
    }

    static boolean worldEndpointOrderingAllows(double firstT, boolean unknown) {
        return !unknown && !(firstT >= 0 && firstT < 1.0 - 1.0E-9);
    }

    /** End this actor's frame-contact episode when a fresh native move had no selected door contact. */
    public static void noNativeContact(Entity actor) {
        if (actor != null) LAST_FRAME_CONTACT.remove(actor);
    }

    private static boolean activateContactSet(Entity actor, net.minecraft.world.entity.LivingEntity living,
                                               ServerLevel level, java.util.Collection<AirlockBlockEntity> doors,
                                               java.util.Map<Entity, java.util.Map<java.util.UUID, ContactMemo>> memoOwner) {
        java.util.Map<java.util.UUID, ContactMemo> previous = memoOwner.getOrDefault(actor, java.util.Map.of());
        java.util.Map<java.util.UUID, ContactMemo> current = new java.util.LinkedHashMap<>();
        boolean activated = false;
        for (AirlockBlockEntity hit : doors) {
            if (hit.prototype() == null || !hit.prototype().door().bumpOpen()) continue;
            var phase = hit.state().phase();
            if (phase != AirlockState.DoorPhase.CLOSED && phase != AirlockState.DoorPhase.DENYING) continue;
            var supply = com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.airlockPower(level, hit);
            boolean paidPowerReady = supply.known()
                    && supply.deliveredWatts() == hit.prototype().apcPowerReceiver().requestedWatts();
            ContactMemo memo = new ContactMemo(hit.identity(), hit.state().revision(), paidPowerReady);
            current.put(hit.identity(), memo);
            if (memo.equals(previous.get(hit.identity()))) continue;
            hit.electrifiedContact(living, AirlockElectrificationSystem.Contact.BUMP);
            activated |= hit.bumpActivate();
        }
        if (current.isEmpty()) memoOwner.remove(actor); else memoOwner.put(actor, current);
        return activated;
    }

    public static boolean sameContactPlane(BlockPos pos, BlockPos selected) {
        if (pos.getY() != selected.getY() && pos.getY() != selected.getY() + 1
                && pos.getY() + 1 != selected.getY()) return false;
        return Math.abs(pos.getX() - selected.getX()) + Math.abs(pos.getZ() - selected.getZ()) <= 1
                || pos.getX() == selected.getX() && pos.getZ() == selected.getZ();
    }

    private static boolean actualShapeTouches(ServerLevel level,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, BlockPos pos,
            net.minecraft.world.level.block.state.BlockState state, AABB body) {
        for (AABB piece : state.getCollisionShape(level, pos).toAabbs())
            if (actualPieceTouches(level, pose, pos, piece, body, null)) return true;
        return false;
    }

    private static boolean actualPieceTouches(ServerLevel level,
            dev.ryanhcode.sable.companion.math.Pose3dc pose, BlockPos pos, AABB piece,
            AABB body, Vec3 expectedNormal) {
        if (!(level instanceof LevelExtension extension)) return false;
        LevelReusedVectors sink = extension.sable$getJOMLSink();
        final double touchTolerance = 1.0E-6;
        var entity = new OrientedBoundingBox3d(new Vector3d((body.minX + body.maxX) * .5,
                (body.minY + body.maxY) * .5, (body.minZ + body.maxZ) * .5),
                new Vector3d(body.getXsize() + 2 * touchTolerance, body.getYsize() + 2 * touchTolerance,
                        body.getZsize() + 2 * touchTolerance),
                new Quaterniond().rotateY(SubLevelEntityCollision.getHitBoxYaw(pose)), sink);
        Vector3d center = new Vector3d(pos.getX() + (piece.minX + piece.maxX) * .5,
                pos.getY() + (piece.minY + piece.maxY) * .5,
                pos.getZ() + (piece.minZ + piece.maxZ) * .5);
        pose.transformPosition(center);
        var shape = new OrientedBoundingBox3d(center,
                new Vector3d((piece.maxX - piece.minX) * pose.scale().x(),
                        (piece.maxY - piece.minY) * pose.scale().y(),
                        (piece.maxZ - piece.minZ) * pose.scale().z()), pose.orientation(), sink);
        Vector3d mtv = new Vector3d(OrientedBoundingBox3d.satToleranced(entity, shape, touchTolerance));
        return mtv.lengthSquared() > 0 && (expectedNormal == null || mtv.normalize().dot(
                new Vector3d(expectedNormal.x, expectedNormal.y, expectedNormal.z).normalize()) > .9);
    }

    /** Slab swept-AABB entry time; negative means no contact. */
    static double timeOfImpact(AABB moving, Vec3 delta, AABB target) {
        double[] x = slab(moving.minX, moving.maxX, target.minX, target.maxX, delta.x);
        double[] y = slab(moving.minY, moving.maxY, target.minY, target.maxY, delta.y);
        double[] z = slab(moving.minZ, moving.maxZ, target.minZ, target.maxZ, delta.z);
        double entry = Math.max(x[0], Math.max(y[0], z[0]));
        double exit = Math.min(x[1], Math.min(y[1], z[1]));
        return entry <= exit && exit >= 0 && entry <= 1 ? Math.max(0, entry) : -1;
    }
    private static double[] slab(double aMin, double aMax, double bMin, double bMax, double d) {
        if (Math.abs(d) < 1.0E-12) return aMax <= bMin || aMin >= bMax
                ? new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}
                : new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        double one = (bMin-aMax)/d, two = (bMax-aMin)/d;
        return new double[]{Math.min(one,two), Math.max(one,two)};
    }
}
