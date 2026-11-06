package com.juicyslew.moonstation14.ms14.loose_objects.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Explicit client-local owner for one bounded loose-cargo observation; never sends a packet. */
@OnlyIn(Dist.CLIENT)
public final class LooseCargoCaptureClient {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static Capture active;
    private static String lastSummary = "No cargo capture has run on this client.";
    private static LooseItemEntity pendingAutomatic;
    private static net.minecraft.client.multiplayer.ClientLevel automaticLevel;
    private static net.minecraft.client.multiplayer.ClientPacketListener automaticConnection;
    private static int automaticAdmissions;

    private LooseCargoCaptureClient() { }

    public static void registerClientEvents(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener(LooseCargoCaptureClient::registerCommands);
        bus.addListener(LooseCargoCaptureClient::onClientTickPost);
        bus.addListener(LooseCargoCaptureClient::onEntityJoinLevel);
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("ms14_cargo_capture")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("start")
                        .executes(context -> {
                            StartResult result=start();
                            context.getSource().sendSuccess(() -> Component.literal(result.message()),false);
                            return result.started()?1:0;
                        })
                        .then(RequiredArgumentBuilder.<CommandSourceStack, java.util.UUID>argument("entity", UuidArgument.uuid())
                                .executes(context -> {
                                    StartResult result = start(context.getArgument("entity", java.util.UUID.class));
                                    context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
                                    return result.started() ? 1 : 0;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop").executes(context -> {
                    boolean stopped = finish("manual-stop");
                    context.getSource().sendSuccess(() -> Component.literal(stopped
                            ? "Cargo capture stopped; bounded client-local rows written to latest.log."
                            : "No cargo capture is active on this client."), false);
                    return stopped ? 1 : 0;
                }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status").executes(context -> {
                    String status = active == null ? lastSummary : active.status();
                    context.getSource().sendSuccess(() -> Component.literal(status), false);
                    return active == null ? 0 : 1;
                })));
    }

    private record StartResult(boolean started, String message) { }

    private static StartResult start() {
        Minecraft mc=Minecraft.getInstance();
        if (active!=null) return new StartResult(false,"Cargo capture already active: "+active.status());
        if (mc.level==null||mc.getConnection()==null)
            return new StartResult(false,"Requires a connected client and current level.");
        LooseItemEntity aimed=com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.diagnosticAimedLooseItem();
        LooseItemEntity selected=admissible(aimed,mc)?aimed:nearestLoadedCargo(mc);
        if (selected==null)
            return new StartResult(false,"Look at the jug or stand near it, then run /ms14_cargo_capture start.");
        return install(selected,mc);
    }

    private static StartResult start(java.util.UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        if (active != null) return new StartResult(false, "Cargo capture already active: " + active.status());
        if (uuid == null || mc.level == null || mc.getConnection() == null)
            return new StartResult(false, "Requires a connected client and current level.");
        Entity camera = mc.getCameraEntity();
        if (camera == null || camera.isRemoved() || camera.level() != mc.level)
            return new StartResult(false, "Requires a loaded camera entity in the current client level.");
        // Existing bounded loaded-entity AABB query only; no world scan, chunk ticket, or network lookup.
        var localMatches = mc.level.getEntitiesOfClass(LooseItemEntity.class, camera.getBoundingBox().inflate(16),
                loose -> loose.getUUID().equals(uuid) && loose.level() == mc.level
                        && admissible(loose,mc));
        if (localMatches.size() != 1)
            return new StartResult(false, "That item is not a loaded nearby cargo entity in this client level.");
        return install(localMatches.getFirst(),mc);
    }

    private static StartResult install(LooseItemEntity loose,Minecraft mc) {
        return install(loose, mc, false);
    }

    private static StartResult install(LooseItemEntity loose, Minecraft mc, boolean automatic) {
        if (!admissible(loose,mc)) return new StartResult(false,"Cargo target is no longer loaded in the current client level.");
        Entity camera=mc.getCameraEntity();
        if (camera==null||camera.isRemoved()||camera.level()!=mc.level)
            return new StartResult(false,"Requires a loaded camera entity in the current client level.");
        Vec3 cameraPosition = camera.position();
        Vec3 renderedPosition = loose.renderPosition(mc.getTimer().getGameTimeDeltaPartialTick(false));
        if (!finite(cameraPosition) || !finite(renderedPosition) || cameraPosition.distanceToSqr(renderedPosition) > 16d * 16d)
            return new StartResult(false, "Cargo is outside the 16-block camera scope. Look at the jug or stand near it and retry.");
        if (loose.hasClientPresentationObserver())
            return new StartResult(false, "This entity already has a client presentation observer.");

        long now = System.nanoTime();
        java.util.UUID uuid=loose.getUUID();
        var tape = new LooseCargoObservationTape(java.util.UUID.randomUUID(), uuid, loose.getId(),
                mc.level.dimension().location().toString(), 0, now);
        var capture = new Capture(mc.level, mc.getConnection(), loose, tape, now);
        loose.clientPresentationObserver(capture.observer);
        if (!loose.hasClientPresentationObserver(capture.observer))
            return new StartResult(false, "Could not install the client-local entity observer.");
        active = capture;
        capture.automatic = automatic;
        lastSummary = "Cargo capture active for " + loose.getItem().getHoverName().getString()+" (entityId="+loose.getId()+").";
        return new StartResult(true, "Started client-local cargo capture " + tape.captureUuid() + " for "
                + loose.getItem().getHoverName().getString()+" (entityId="+loose.getId()+")"
                + " (80 client ticks / 8 seconds maximum; spawn raw fields are not captured).");
    }

    private static LooseItemEntity nearestLoadedCargo(Minecraft mc) {
        Entity camera=mc.getCameraEntity();
        if (camera==null||camera.isRemoved()||camera.level()!=mc.level) return null;
        Vec3 cameraPosition=camera.position();
        if (!finite(cameraPosition)) return null;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        record Candidate(LooseItemEntity entity,double distanceSquared) { }
        var candidates=new java.util.ArrayList<Candidate>();
        for (LooseItemEntity loose : mc.level.getEntitiesOfClass(LooseItemEntity.class,camera.getBoundingBox().inflate(16),
                item -> admissible(item,mc))) {
            Vec3 rendered=loose.renderPosition(partial);
            if (!finite(rendered)) continue;
            double distance=cameraPosition.distanceToSqr(rendered);
            if (distance<=16d*16d) candidates.add(new Candidate(loose,distance));
        }
        return candidates.stream().min(java.util.Comparator.comparingDouble(Candidate::distanceSquared)
                .thenComparing(candidate -> candidate.entity().getUUID())).map(Candidate::entity).orElse(null);
    }

    private static boolean admissible(LooseItemEntity loose,Minecraft mc) {
        return loose!=null&&mc.level!=null&&loose.level()==mc.level&&!loose.isRemoved()&&loose.isAlive()
                &&loose.isAddedToLevel()&&!loose.getItem().isEmpty();
    }

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != automaticLevel || mc.getConnection() != automaticConnection) {
            pendingAutomatic = null;
            automaticAdmissions = 0;
            automaticLevel = mc.level;
            automaticConnection = mc.getConnection();
        }
        Capture captureBeforePending = active;
        if (pendingAutomatic != null) {
            LooseItemEntity pending = pendingAutomatic;
            pendingAutomatic = null;
            if (active == null && automaticAdmissions < 2 && admissible(pending, mc) && nearCamera(pending, mc)) {
                automaticAdmissions++;
                StartResult result = install(pending, mc, true);
                if (!result.started()) LOGGER.info("[loose-cargo-capture] automatic admission skipped: {}", result.message());
            }
        }
        Capture capture = active;
        if (capture == null) return;
        if (capture != captureBeforePending) return;
        capture.clientTicks++;
        long now = System.nanoTime();
        if (mc.level != capture.level || mc.getConnection() != capture.connection) {
            finish("level-or-connection-changed", now);
            return;
        }
        if (capture.entity.isRemoved() || !capture.entity.isAlive() || capture.entity.level() != capture.level) {
            finish("entity-removed", now);
            return;
        }
        if (!capture.entity.hasClientPresentationObserver(capture.observer)) {
            capture.tape.markIncomplete("entity-observer-disabled");
            finish("observer-disabled", now);
            return;
        }
        if (capture.pendingStopReason != null) {
            finish(capture.pendingStopReason, now);
            return;
        }
        if (capture.tape.expired(capture.clientTicks, now)) {
            finish(capture.clientTicks >= LooseCargoObservationTape.MAX_CLIENT_TICKS ? "client-tick-window" : "wall-time-window", now);
        }
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LooseItemEntity loose)) return;
        Minecraft mc = Minecraft.getInstance();
        if (automaticLevel != mc.level || automaticConnection != mc.getConnection()) {
            automaticLevel = mc.level;
            automaticConnection = mc.getConnection();
            automaticAdmissions = 0;
            pendingAutomatic = null;
        }
        if (event.getLevel() != mc.level || mc.level == null || mc.getConnection() == null || active != null
                || pendingAutomatic != null || automaticAdmissions >= 2 || !nearCamera(loose, mc)) return;
        pendingAutomatic = loose;
    }

    private static boolean nearCamera(LooseItemEntity loose, Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        return camera != null && !camera.isRemoved() && camera.level() == mc.level && finite(camera.position())
                && finite(loose.position()) && camera.position().distanceToSqr(loose.position()) <= 16d * 16d;
    }

    public static void observeDispatcherCull(Entity entity, boolean actualShouldRender,
            double dispatcherCameraX, double dispatcherCameraY, double dispatcherCameraZ) {
        Capture capture = target(entity);
        if (capture == null) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        var camera = cameraValues();
        long cullId=++capture.nextCullId;
        Long frame=clientRenderFrameSerial();
        var render = new LooseCargoObservationTape.RenderValues(actualShouldRender, camera.position(), camera.look(),
                camera.rotation(), new LooseCargoObservationTape.Point(dispatcherCameraX,dispatcherCameraY,dispatcherCameraZ),
                null, partial, null, null, null, null, null, cullId,null,null);
        if (actualShouldRender && frame!=null)
            capture.pendingCullByFrame.computeIfAbsent(frame, ignored -> new java.util.ArrayDeque<>()).addLast(cullId);
        appendRender(capture, "DISPATCH_CULL", render);
    }

    public static void observeDispatcherRender(Entity entity, double x, double y, double z, float entityYaw,
            float partialTick, PoseStack pose, int packedLight) {
        Capture capture = target(entity);
        if (capture == null) return;
        if (capture.invocation != null) {
            capture.tape.markIncomplete("dispatcher-invocation-not-closed:"+capture.invocation.dispatchId);
            capture.invocation=null;
        }
        long dispatchId=++capture.nextDispatchId;
        Long frame=clientRenderFrameSerial();
        java.util.ArrayDeque<Long> pending=frame==null?null:capture.pendingCullByFrame.get(frame);
        Long cullId=pending==null||pending.isEmpty()?null:pending.removeFirst();
        if (pending!=null&&pending.isEmpty()) capture.pendingCullByFrame.remove(frame);
        capture.invocation=new Invocation(dispatchId,frame);
        var camera = cameraValues();
        var render = new LooseCargoObservationTape.RenderValues(null, camera.position(), camera.look(), camera.rotation(),
                new LooseCargoObservationTape.Point(x,y,z), entityYaw, partialTick, packedLight, null, null, null,
                copyModelMatrix(pose),null,dispatchId,cullId);
        appendRender(capture, "DISPATCH_RENDER", render);
    }

    public static void observeDispatcherReturn(Entity entity) {
        Capture capture=target(entity);
        if (capture==null || capture.invocation==null) return;
        long id=capture.invocation.dispatchId;
        capture.invocation=null;
        capture.tape.closeDispatch(id);
    }

    public static void observeRenderOffset(LooseItemEntity entity, float partialTick, Vec3 actualOffset) {
        Capture capture = target(entity);
        if (capture == null) return;
        var camera = cameraValues();
        Invocation invocation=capture.currentInvocation();
        appendRender(capture, "RENDER_OFFSET", new LooseCargoObservationTape.RenderValues(null,
                camera.position(), camera.look(), camera.rotation(), null, null, partialTick, null,
                point(actualOffset), null, null, null,null,invocation==null?null:invocation.dispatchId,null));
    }

    public static void observeModelDraw(LooseItemEntity entity, String renderKind, float partialTick,
            int packedLight, PoseStack actualModelPose, org.joml.Quaternionf appliedLocalRotation) {
        Capture capture = target(entity);
        if (capture == null) return;
        var camera = cameraValues();
        Invocation invocation=capture.currentInvocation();
        var lighting = recomputedProbeContext(entity, partialTick);
        appendRender(capture, "MODEL_DRAW", new LooseCargoObservationTape.RenderValues(null,
                camera.position(), camera.look(), camera.rotation(), null, null, partialTick, packedLight,
                null, renderKind, null, copyModelMatrix(actualModelPose),null,invocation==null?null:invocation.dispatchId,null,
                null,null,quaternion(appliedLocalRotation),lighting));
    }

    /** Diagnostic projection only: the actual draw's packedLight above remains the authoritative lighting input. */
    private static LooseCargoObservationTape.LightingContext recomputedProbeContext(LooseItemEntity entity, float partial) {
        Vec3 probe = entity.getLightProbePosition(partial);
        LooseCargoObservationTape.Point world = point(probe);
        if (probe == null) return new LooseCargoObservationTape.LightingContext(null, null, null,
                null, null, null, null, "unavailable", "unavailable", "unavailable");
        if (!finite(probe)) return new LooseCargoObservationTape.LightingContext(world, null, null,
                null, null, null, null, "invalid", "unavailable", "unavailable");
        java.util.UUID frame = entity.committedRenderFrameId(partial);
        if (frame == null) return new LooseCargoObservationTape.LightingContext(world, null, null,
                null, null, null, null, "available", "no-committed-frame", "unavailable");
        var actorPoint = com.juicyslew.moonstation14.ms14.hands.live.LoadedFrameRayQuery
                .pointAtWorldPosition(entity.level(), frame, probe, true, partial);
        if (actorPoint == null || actorPoint.frameId() == null || !frame.equals(actorPoint.frameId()))
            return new LooseCargoObservationTape.LightingContext(world, frame, null,
                    null, null, null, null, "available", "unavailable", "unavailable");
        Vec3 local = actorPoint.storagePoint();
        if (!finite(local)) return new LooseCargoObservationTape.LightingContext(world, frame, point(local),
                null, null, null, null, "available", "invalid", "unavailable");
        var sub = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(entity.level()).getSubLevel(frame);
        Integer scale = sub instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel clientSub
                ? clientSub.getLatestSkyLightScale() : null;
        var cell = net.minecraft.core.BlockPos.containing(local);
        return new LooseCargoObservationTape.LightingContext(world, frame, point(local), cell.getX(), cell.getY(), cell.getZ(),
                scale, "available", "available", scale == null ? "unavailable" : "available");
    }

    public static void observeModelSkipped(LooseItemEntity entity, String reason, float partialTick,
            int packedLight, PoseStack actualPose) {
        Capture capture = target(entity);
        if (capture == null) return;
        var camera = cameraValues();
        Invocation invocation=capture.currentInvocation();
        appendRender(capture, "MODEL_SKIPPED", new LooseCargoObservationTape.RenderValues(null,
                camera.position(), camera.look(), camera.rotation(), null, null, partialTick, packedLight,
                null, null, reason, copyModelMatrix(actualPose),null,invocation==null?null:invocation.dispatchId,null));
    }

    private record CameraValues(LooseCargoObservationTape.Point position, LooseCargoObservationTape.Point look,
                                java.util.List<Float> rotation) { }

    private static CameraValues cameraValues() {
        var mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
        if (camera == null) return new CameraValues(null,null,null);
        Vec3 position = camera.getPosition();
        var look = camera.getLookVector();
        var rotation = camera.rotation();
        return new CameraValues(point(position), new LooseCargoObservationTape.Point(look.x,look.y,look.z),
                java.util.List.of(rotation.x,rotation.y,rotation.z,rotation.w));
    }

    private static java.util.List<Float> copyModelMatrix(PoseStack pose) {
        if (pose == null) return null;
        float[] components = new float[16];
        pose.last().pose().get(components);
        var copy = new java.util.ArrayList<Float>(16);
        for (float component : components) copy.add(component);
        return java.util.List.copyOf(copy);
    }

    private static Capture target(Entity entity) {
        Capture capture = active;
        if (capture == null || entity == null || entity != capture.entity || entity.getId() != capture.tape.entityId()
                || !capture.tape.entityUuid().equals(entity.getUUID()) || entity.level() != capture.level
                || entity.isRemoved()) return null;
        return capture;
    }

    private static void appendRender(Capture capture, String phase, LooseCargoObservationTape.RenderValues render) {
        Vec3 position = capture.entity.position();
        AABB bounds = capture.entity.getBoundingBox();
        var stack = capture.entity.getItem();
        var clientState = capture.entity.clientRenderState();
        var actualContaining=dev.ryanhcode.sable.Sable.HELPER.getContaining(capture.entity);
        boolean shouldKick=dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.shouldKick(capture.entity);
        render=new LooseCargoObservationTape.RenderValues(render.shouldRender(),render.cameraPosition(),render.cameraLook(),
                render.cameraRotation(),render.dispatcherCoordinates(),render.dispatcherYaw(),render.partialTick(),
                render.packedLight(),render.actualRenderOffset(),render.renderKind(),render.skipReason(),render.modelPoseMatrix(),
                render.cullId(),render.dispatchId(),render.linkedCullId(),actualContaining==null?null:actualContaining.getUniqueId(),
                shouldKick,render.appliedLocalRotation(),render.recomputedProbeContextAtModelDraw());
        var row = new LooseCargoObservationTape.Row(capture.tape.entityUuid(), capture.tape.entityId(),
                capture.clientTicks, capture.entity.tickCount, capture.level.getGameTime(), System.nanoTime(),
                    clientRenderFrameSerial(), point(position),
                new LooseCargoObservationTape.Point(bounds.minX,bounds.minY,bounds.minZ),
                new LooseCargoObservationTape.Point(bounds.maxX,bounds.maxY,bounds.maxZ), phase, false,
                null, null, null, null, null, null, null, null, null, null,
                clientState == null ? null : clientState.committedFrameId(),
                clientState == null ? null : point(clientState.storageAnchor()), null, null,
                clientState != null && clientState.frameProjectionPaused(),
                clientState == null ? null : clientState.nativeTrackingFrameId(), capture.entity.settled(), capture.entity.onGround(),
                stack.getCount(), stack.isEmpty(), point(capture.entity.getDeltaMovement()), render);
        if (render.cameraPosition() == null) capture.tape.markIncomplete("render-camera-unavailable");
        if (("DISPATCH_RENDER".equals(phase) || "MODEL_DRAW".equals(phase)) && render.modelPoseMatrix() == null)
            capture.tape.markIncomplete("render-pose-matrix-unavailable");
        boolean accepted = capture.tape.append(row);
        if (!accepted || capture.tape.rows() >= LooseCargoObservationTape.MAX_ROWS)
            capture.pendingStopReason = accepted ? "row-cap" : capture.tape.stopReason() == null ? "capture-incomplete" : capture.tape.stopReason();
    }

    private static final class Capture {
        final net.minecraft.client.multiplayer.ClientLevel level;
        final net.minecraft.client.multiplayer.ClientPacketListener connection;
        final LooseItemEntity entity;
        final LooseCargoObservationTape tape;
        final long startNanos;
        final LooseItemEntity.ClientPresentationObserver observer;
        long clientTicks;
        String pendingStopReason;
        long nextCullId,nextDispatchId;
        Invocation invocation;
        final java.util.Map<Long,java.util.ArrayDeque<Long>> pendingCullByFrame=new java.util.HashMap<>();
        boolean automatic;

        Capture(net.minecraft.client.multiplayer.ClientLevel level,
                net.minecraft.client.multiplayer.ClientPacketListener connection,
                LooseItemEntity entity, LooseCargoObservationTape tape, long startNanos) {
            this.level=level; this.connection=connection; this.entity=entity;
            this.tape=tape; this.startNanos=startNanos; this.observer=this::observe;
        }

        void observe(LooseItemEntity.ClientPresentationSample sample) {
            if (active != this) return;
            LooseCargoObservationTape.Point packet = point(sample.rawPacketPoint());
            AABB bounds = entity.getBoundingBox();
            var row = new LooseCargoObservationTape.Row(sample.entityUuid(), sample.entityId(), clientTicks,
                    sample.entityTickCount(), sample.clientWorldGameTime(), sample.monotonicNanos(),
                    clientRenderFrameSerial(),
                    point(entity.position()), new LooseCargoObservationTape.Point(bounds.minX,bounds.minY,bounds.minZ),
                    new LooseCargoObservationTape.Point(bounds.maxX,bounds.maxY,bounds.maxZ),
                    sample.phase().name(), sample.actuallyInSubLevel(),
                    sample.packetClassification() == null ? null : sample.packetClassification().name(),
                    sample.helperFrameId(), packet, point(sample.previousClientWorld()), point(sample.oldXBeforeTick()),
                    point(sample.xoBeforeTick()), point(sample.projectedFrameWorld()), point(sample.finalClientWorld()),
                    point(sample.oldXAfterTick()), point(sample.xoAfterTick()), sample.committedRenderFrameId(),
                    point(sample.storageAnchor()), point(sample.localRenderAnchorPrevious()),
                    point(sample.localRenderAnchorCurrent()), sample.frameProjectionPaused(), sample.nativeTrackingFrameId(),
                    sample.clientSettled(), sample.clientOnGround(), sample.clientStackCount(), sample.clientStackEmpty(),
                    point(sample.clientVelocity()), null);
            boolean accepted = tape.append(row);
            if (!accepted || tape.rows() >= LooseCargoObservationTape.MAX_ROWS)
                pendingStopReason = accepted ? "row-cap" : tape.stopReason() == null ? "capture-incomplete" : tape.stopReason();
        }

        Invocation currentInvocation() {
            Invocation current=invocation;
            return current!=null && java.util.Objects.equals(current.frameSerial,clientRenderFrameSerial())?current:null;
        }

        String status() {
            return "Cargo capture " + tape.captureUuid() + " entity=" + tape.entityUuid() + " clientTicks=" + clientTicks
                    + " rows=" + tape.rows() + " packet=" + tape.packetRows() + " tick=" + tape.tickRows()
                    + " cull=" + tape.cullRows() + " dispatch=" + tape.dispatcherRows()
                    + " offset=" + tape.renderOffsetRows() + " model=" + tape.modelDrawRows() + " skipped=" + tape.modelSkippedRows()
                    + " complete=" + tape.complete() + " overflow=" + tape.overflow();
        }
    }

    private record Invocation(long dispatchId, Long frameSerial) { }

    private static LooseCargoObservationTape.Point point(LooseItemEntity.ClientPoint point) {
        return point == null ? null : new LooseCargoObservationTape.Point(point.x(), point.y(), point.z());
    }

    private static LooseCargoObservationTape.Point point(Vec3 point) {
        return point == null ? null : new LooseCargoObservationTape.Point(point.x, point.y, point.z);
    }

    private static java.util.List<Float> quaternion(org.joml.Quaternionf value) {
        return value==null?null:java.util.List.of(value.x,value.y,value.z,value.w);
    }

    private static Long clientRenderFrameSerial() {
        long serial = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.outlineRenderSerial();
        return serial <= 0 ? null : serial;
    }

    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    private static boolean finish(String reason) { return finish(reason, System.nanoTime()); }

    private static boolean finish(String reason, long nowNanos) {
        Capture capture = active;
        if (capture == null) return false;
        active = null;
        if (capture.invocation != null) capture.tape.markIncomplete("capture-ended-with-open-dispatch:"+capture.invocation.dispatchId);
        capture.invocation=null;
        capture.pendingCullByFrame.clear();
        capture.entity.clearClientPresentationObserver(capture.observer);
        var removalReason = capture.entity.getRemovalReason();
        var lifecycle = new LooseCargoObservationTape.Lifecycle(capture.entity.isRemoved(),
                removalReason == null ? null : removalReason.name(),
                capture.level.getEntity(capture.entity.getId()) == capture.entity);
        var snapshot = capture.tape.finish(reason, capture.clientTicks, nowNanos, lifecycle);
        long elapsed = Math.max(0, nowNanos - capture.startNanos);
        LOGGER.info("[loose-cargo-capture] capture={} autoSource={} entity={} entityId={} dimension={} stopReason={} clientTicks={} elapsedNanos={} windowComplete={} rows={} packetRows={} clientTickRows={} cullRows={} dispatcherRows={} renderOffsetRows={} modelDrawRows={} modelSkippedRows={} truncatedRowLogs={} packetCoverage={} tickCoverage={} cullCoverage={} dispatcherCoverage={} renderOffsetCoverage={} modelCoverage={} renderFrameCoverage={} renderFrameCount={} renderFrameSerialRange=[{},{}] rowDataValid={} rowDataFailure={} coverageFailure={} complete={} overflow={} clientRemoved={} clientRemovalReason={} clientLevelLookupSameInstance={} spawnRawCoverage={}",
                snapshot.captureUuid(), capture.automatic, snapshot.entityUuid(), snapshot.entityId(), snapshot.dimension(), snapshot.stopReason(),
                snapshot.elapsedClientTicks(), elapsed, snapshot.windowComplete(), snapshot.rows().size(), snapshot.packetRows(), snapshot.tickRows(),
                snapshot.cullRows(), snapshot.dispatcherRows(), snapshot.renderOffsetRows(), snapshot.modelDrawRows(),
                snapshot.modelSkippedRows(), snapshot.truncatedRowLogCount(), snapshot.packetCoverage(), snapshot.tickCoverage(),
                snapshot.cullCoverage(), snapshot.dispatcherCoverage(), snapshot.renderOffsetCoverage(), snapshot.modelCoverage(),
                snapshot.renderFrameCoverage(), snapshot.renderFrameCount(), snapshot.firstRenderFrameSerial(), snapshot.lastRenderFrameSerial(),
                snapshot.rowDataValid(), snapshot.rowDataFailure(), snapshot.coverageFailure(), snapshot.complete(), snapshot.overflow(), lifecycle.entityRemoved(), lifecycle.removalReason(),
                lifecycle.clientLevelLookupSameInstance(), snapshot.spawnRawCoverage());
        for (int i = 0; i < snapshot.boundedRowLogRecords().size(); i++) LOGGER.info("[loose-cargo-capture-row] capture={} row={}/{} {}",
                snapshot.captureUuid(), i+1, snapshot.boundedRowLogRecords().size(), snapshot.boundedRowLogRecords().get(i));
        lastSummary = "Cargo capture " + snapshot.captureUuid() + " stopped: " + snapshot.stopReason()
                + "; rows=" + snapshot.rows().size() + " packet=" + snapshot.packetRows()
                + " ticks=" + snapshot.tickRows() + " cull="+snapshot.cullRows()+" dispatch="+snapshot.dispatcherRows()
                + " offset="+snapshot.renderOffsetRows()+" model="+snapshot.modelDrawRows()+" skipped="+snapshot.modelSkippedRows()
                + " rowDataValid="+snapshot.rowDataValid()+" coverage="+snapshot.tickCoverage()+"/"+snapshot.cullCoverage()+"/"+snapshot.dispatcherCoverage()
                + " windowComplete="+snapshot.windowComplete()+" complete=" + snapshot.complete()+" overflow="+snapshot.overflow()
                + " removed="+lifecycle.entityRemoved()+" removal="+lifecycle.removalReason()
                + " sameLoadedInstance="+lifecycle.clientLevelLookupSameInstance();
        return true;
    }
}
