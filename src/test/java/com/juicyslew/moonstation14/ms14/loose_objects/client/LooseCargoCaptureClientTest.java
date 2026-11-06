package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LooseCargoCaptureClientTest {
    @Test void commandRegistersClientLocalUuidLookupAndOwnsOnlyOneBoundedCapture() throws Exception {
        String manager = read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String client = read("src/main/java/com/juicyslew/moonstation14/MoonStation14Client.java");
        assertTrue(manager.contains("@OnlyIn(Dist.CLIENT)"));
        assertTrue(manager.contains("RegisterClientCommandsEvent"));
        assertTrue(manager.contains("literal(\"ms14_cargo_capture\")"));
        assertTrue(manager.contains("literal(\"start\")"));
        assertTrue(manager.contains("literal(\"stop\")"));
        assertTrue(manager.contains("literal(\"status\")"));
        assertTrue(manager.contains("UuidArgument.uuid()"));
        assertTrue(manager.contains("getEntitiesOfClass(LooseItemEntity.class, camera.getBoundingBox().inflate(16)"),
                "explicit UUID is resolved only by bounded nearby loaded-entity lookup");
        assertTrue(manager.contains("cameraPosition.distanceToSqr(renderedPosition) > 16d * 16d"));
        assertFalse(manager.contains("getAllEntities"));
        assertFalse(manager.contains("PacketDistributor"));
        assertTrue(manager.contains("LooseCargoObservationTape.MAX_CLIENT_TICKS"));
        assertTrue(manager.contains("capture.tape.expired(capture.clientTicks, now)"));
        assertTrue(read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoObservationTape.java")
                .contains("MAX_WALL_NANOS = 8_000_000_000L"));
        assertTrue(manager.contains("entity.clearClientPresentationObserver(capture.observer)"));
        assertTrue(manager.contains("[loose-cargo-capture-row] capture={} row={}/{} {}"));
        assertFalse(manager.contains("System.out"));
        assertTrue(client.contains("LooseCargoCaptureClient.registerClientEvents(NeoForge.EVENT_BUS)"));
    }

    @Test void noArgumentStartTargetsAimedCargoOrBoundedNearestFallbackWithoutChatUuid() throws Exception {
        String manager=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String start=method(manager,"public static void registerCommands(","private record StartResult");
        assertTrue(start.contains(".executes(context ->"));
        assertTrue(start.contains("StartResult result=start();"));
        assertTrue(start.indexOf(".executes(context ->")<start.indexOf("argument(\"entity\""),
                "argument-free start is the primary Brigadier route");
        assertTrue(manager.contains("BodyHandClient.diagnosticAimedLooseItem()"));
        assertTrue(manager.contains("admissible(aimed,mc)?aimed:nearestLoadedCargo(mc)"));
        assertTrue(manager.contains("Look at the jug or stand near it"));
        assertTrue(manager.contains("private static StartResult install(LooseItemEntity loose,Minecraft mc)"));
        assertTrue(manager.contains("return install(localMatches.getFirst(),mc)"),"advanced UUID route shares install path");
        assertTrue(manager.contains("loose.getItem().getHoverName().getString()"));
        assertFalse(manager.contains("clipboard"));
    }

    @Test void diagnosticAimedSelectionReusesReadOnlyRayCandidatesAndFallbackIsBoundedAndDeterministic() throws Exception {
        String hands=read("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        String getter=method(hands,"public static com.juicyslew.moonstation14.entities.LooseItemEntity diagnosticAimedLooseItem()","public static boolean outlineItemPrecedesBlock");
        assertTrue(getter.contains("buildOutlineCandidates(body,false)"));
        assertTrue(getter.contains("PILE.index(ids)"));
        assertTrue(getter.contains(":0"),"falls back to the first sorted visible cargo candidate");
        assertFalse(getter.contains("outlineSelection()"));
        assertFalse(getter.contains("publishPileSnapshot"));
        assertFalse(getter.contains("PILE.update"));
        assertFalse(getter.contains("mc.screen"),"diagnostic selection works while chat is open");

        String manager=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String fallback=method(manager,"private static LooseItemEntity nearestLoadedCargo(","private static boolean admissible");
        assertTrue(fallback.contains("camera.getBoundingBox().inflate(16)"));
        assertTrue(fallback.contains("getEntitiesOfClass(LooseItemEntity.class"));
        assertTrue(fallback.contains("distance<=16d*16d"));
        assertTrue(fallback.contains("Comparator.comparingDouble(Candidate::distanceSquared)"));
        assertTrue(fallback.contains("thenComparing(candidate -> candidate.entity().getUUID())"));
        assertTrue(manager.contains("loose.isAddedToLevel()"));
        assertTrue(manager.contains("loose.level()==mc.level"));
        assertTrue(manager.contains("!loose.getItem().isEmpty()"));
        assertFalse(manager.contains("getAllEntities"));
    }

    @Test void commonEntityObserverIsNullFastRawPacketAndPostProjectionTickTapOnly() throws Exception {
        String entity = read("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java");
        assertTrue(entity.contains("private ClientPresentationObserver clientPresentationObserver;"));
        assertFalse(entity.contains("ms14.loose_objects.client"), "common entity has no client manager dependency");
        assertTrue(entity.contains("if (observer == null) return"));
        assertTrue(entity.contains("recordClientPacketObservation(x, y, z, actuallyInSubLevel"));
        int packet = entity.indexOf("public static void recordClientPacketFrame");
        int packetEnd = entity.indexOf("public void dropUnknownClientFramePacket", packet);
        String packetTap = entity.substring(packet, packetEnd);
        assertEquals(1, occurrences(packetTap, "Sable.HELPER.getContaining("));
        assertEquals(1, occurrences(packetTap, ".inBounds(BlockPos.containing(rawPoint))"));
        assertTrue(packetTap.contains("rawPoint"));
        assertTrue(packetTap.contains("actuallyInSubLevel"));

        int tick = entity.indexOf("@Override public void tick()");
        int server = entity.indexOf("evaluateGrenadeFuse();", tick);
        String tickMethod = entity.substring(tick, server);
        assertTrue(tickMethod.contains("previousClientWorld"));
        assertTrue(tickMethod.contains("projectedFrameWorld = projected"));
        assertTrue(tickMethod.contains("observeClientPresentation(new ClientPresentationSample"));
        assertTrue(tickMethod.indexOf("setPos(projected)") < tickMethod.indexOf("observeClientPresentation(new ClientPresentationSample"));
        assertTrue(tickMethod.indexOf("refreshClientProfileDimensions()") < tickMethod.indexOf("observeClientPresentation(new ClientPresentationSample"));
        assertEquals(1, occurrences(tickMethod, "clientPosition.projectFrame("));
        assertFalse(tickMethod.contains("lerpTo("));
    }

    @Test void captureIsDefaultOffAndEveryEndPathDetachesExactObserver() throws Exception {
        String manager = read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String entity = read("src/main/java/com/juicyslew/moonstation14/entities/LooseItemEntity.java");
        assertTrue(manager.contains("private static Capture active;"));
        assertTrue(entity.contains("private ClientPresentationObserver clientPresentationObserver;"));
        assertTrue(manager.contains("if (active != null) return new StartResult(false"));
        assertTrue(manager.contains("clearClientPresentationObserver(capture.observer)"));
        assertTrue(manager.contains("client level") && manager.contains("entity-removed")
                && manager.contains("observer-disabled"));
        assertTrue(entity.contains("if (clientPresentationObserver == observer) clientPresentationObserver = null"));
    }

    @Test void passiveAdmissionIsSinglePendingNearbyJoinBoundedPerConnectionAndLifecycleReset() throws Exception {
        String manager = read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        assertTrue(manager.contains("EntityJoinLevelEvent"));
        assertTrue(manager.contains("private static LooseItemEntity pendingAutomatic;"));
        assertTrue(manager.contains("automaticAdmissions < 2"));
        assertTrue(manager.contains("pendingAutomatic != null"));
        assertTrue(manager.contains("event.getLevel() != mc.level"));
        assertTrue(manager.contains("camera.position().distanceToSqr(loose.position()) <= 16d * 16d"));
        assertTrue(manager.contains("install(pending, mc, true)"));
        assertTrue(manager.contains("capture.automatic"));
        assertTrue(manager.contains("autoSource={}"));
        assertTrue(manager.indexOf("if (mc.level != automaticLevel") < manager.indexOf("if (capture == null) return"),
                "level/connection teardown resets passive state even with no active capture");
        assertTrue(manager.contains("capture.clearClientPresentationObserver(capture.observer)") || manager.contains("entity.clearClientPresentationObserver(capture.observer)"));
    }

    @Test void serverEntityJoinReturnsBeforeReadingClientState() throws Exception {
        String manager=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String join=method(manager,"private static void onEntityJoinLevel(","private static boolean nearCamera(");
        assertTrue(join.startsWith("private static void onEntityJoinLevel(EntityJoinLevelEvent event) {\n        if (!event.getLevel().isClientSide()) return;"));
        assertTrue(join.indexOf("if (!event.getLevel().isClientSide()) return;")<join.indexOf("Minecraft mc = Minecraft.getInstance()"));
        assertTrue(join.indexOf("if (!event.getLevel().isClientSide()) return;")<join.indexOf("automaticAdmissions = 0"));
        assertTrue(join.contains("event.getLevel() != mc.level") && join.contains("active != null")
                && join.contains("pendingAutomatic != null"));
        String tick=method(manager,"public static void onClientTickPost(","private static void onEntityJoinLevel(");
        assertTrue(tick.contains("Capture captureBeforePending = active"));
        assertTrue(tick.contains("if (capture != captureBeforePending) return;"),
                "a newly-installed automatic observer begins its bounded tick window on the next Post event");
    }

    @Test void modelDrawCapturesOnlyReadOnlyRecomputedProbeContextBesideActualPackedLight() throws Exception {
        String manager=read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoCaptureClient.java");
        String draw=method(manager,"public static void observeModelDraw(","public static void observeModelSkipped(");
        assertTrue(draw.contains("Capture capture = target(entity)"));
        assertTrue(draw.indexOf("if (capture == null) return") < draw.indexOf("recomputedProbeContext(entity, partialTick)"));
        assertTrue(draw.contains("packedLight"));
        assertTrue(draw.contains("recomputedProbeContext(entity, partialTick)"));
        String context=method(manager,"private static LooseCargoObservationTape.LightingContext recomputedProbeContext(","public static void observeModelSkipped(");
        assertTrue(context.contains("entity.getLightProbePosition(partial)"));
        assertTrue(context.contains("entity.committedRenderFrameId(partial)"));
        assertTrue(context.contains("pointAtWorldPosition(entity.level(), frame, probe, true, partial)"));
        assertTrue(context.contains("getLatestSkyLightScale()"));
        assertTrue(context.contains("BlockPos.containing(local)"));
        assertFalse(context.contains("getBrightness"));
        assertFalse(context.contains("getPlotPos"));
        assertFalse(context.contains("renderPose"));
        assertTrue(read("src/main/java/com/juicyslew/moonstation14/ms14/loose_objects/client/LooseCargoObservationTape.java")
                .contains("not the lighting value consumed by the draw"));
    }

    private static String read(String path) throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        while (root != null && !Files.exists(root.resolve(path))) root = root.getParent();
        assertNotNull(root, path);
        return Files.readString(root.resolve(path));
    }

    private static int occurrences(String source, String needle) {
        int count=0, at=0;
        while ((at=source.indexOf(needle,at))>=0) { count++; at+=needle.length(); }
        return count;
    }

    private static String method(String source,String start,String end) {
        int from=source.indexOf(start),to=source.indexOf(end,from);
        assertTrue(from>=0&&to>from,"source section exists: "+start);
        return source.substring(from,to);
    }
}
