package com.juicyslew.moonstation14.ms14.loose_objects.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Pure bounded value tape for one explicitly watched client loose item. */
public final class LooseCargoObservationTape {
    public static final int MAX_CLIENT_TICKS = 80;
    public static final long MAX_WALL_NANOS = 8_000_000_000L;
    public static final int MAX_ROWS = 4096;
    /** Leaves room for the ASCII logger prefix under an 8 KiB UTF-8 appender limit. */
    public static final int MAX_ROW_LOG_UTF8_BYTES = 7000;
    private static final String TRUNCATED_MARKER = "...[ROW_LOG_TRUNCATED]";

    public record Point(double x, double y, double z) { }
    /** Recomputed render-probe context, not the lighting value consumed by the draw (packedLight is authoritative). */
    public record LightingContext(Point recomputedProbeWorld, UUID frameId, Point localProbe,
            Integer localCellX, Integer localCellY, Integer localCellZ, Integer latestSkyLightScale,
            String probeStatus, String projectionStatus, String scaleStatus) { }
    /** modelPoseMatrix is an immutable copy of the 16 column-major float components from the actual PoseStack. */
    public record RenderValues(Boolean shouldRender, Point cameraPosition, Point cameraLook,
            List<Float> cameraRotation, Point dispatcherCoordinates, Float dispatcherYaw, Float partialTick,
            Integer packedLight, Point actualRenderOffset, String renderKind, String skipReason,
            List<Float> modelPoseMatrix, Long cullId, Long dispatchId, Long linkedCullId,
            UUID actualContainingFrameId, Boolean shouldKick, List<Float> appliedLocalRotation,
            LightingContext recomputedProbeContextAtModelDraw) {
        public RenderValues(Boolean shouldRender, Point cameraPosition, Point cameraLook,
                List<Float> cameraRotation, Point dispatcherCoordinates, Float dispatcherYaw, Float partialTick,
                Integer packedLight, Point actualRenderOffset, String renderKind, String skipReason,
                List<Float> modelPoseMatrix, Long cullId, Long dispatchId, Long linkedCullId,
                UUID actualContainingFrameId, Boolean shouldKick, List<Float> appliedLocalRotation) {
            this(shouldRender,cameraPosition,cameraLook,cameraRotation,dispatcherCoordinates,dispatcherYaw,partialTick,
                    packedLight,actualRenderOffset,renderKind,skipReason,modelPoseMatrix,cullId,dispatchId,linkedCullId,
                    actualContainingFrameId,shouldKick,appliedLocalRotation,null);
        }
        public RenderValues(Boolean shouldRender, Point cameraPosition, Point cameraLook,
                List<Float> cameraRotation, Point dispatcherCoordinates, Float dispatcherYaw, Float partialTick,
                Integer packedLight, Point actualRenderOffset, String renderKind, String skipReason,
                List<Float> modelPoseMatrix, Long cullId, Long dispatchId, Long linkedCullId) {
            this(shouldRender,cameraPosition,cameraLook,cameraRotation,dispatcherCoordinates,dispatcherYaw,partialTick,
                    packedLight,actualRenderOffset,renderKind,skipReason,modelPoseMatrix,cullId,dispatchId,linkedCullId,null,null,null,null);
        }
        public RenderValues(Boolean shouldRender, Point cameraPosition, Point cameraLook,
                List<Float> cameraRotation, Point dispatcherCoordinates, Float dispatcherYaw, Float partialTick,
                Integer packedLight, Point actualRenderOffset, String renderKind, String skipReason,
                List<Float> modelPoseMatrix) {
            this(shouldRender,cameraPosition,cameraLook,cameraRotation,dispatcherCoordinates,dispatcherYaw,partialTick,
                    packedLight,actualRenderOffset,renderKind,skipReason,modelPoseMatrix,null,null,null,null,null,null,null);
        }
        public RenderValues {
            cameraRotation = cameraRotation == null ? null : List.copyOf(cameraRotation);
            modelPoseMatrix = modelPoseMatrix == null ? null : List.copyOf(modelPoseMatrix);
            appliedLocalRotation = appliedLocalRotation == null ? null : List.copyOf(appliedLocalRotation);
        }
    }
    public record Row(UUID entityUuid, int entityId, long captureClientTick, int entityTickCount,
            long clientWorldGameTime, long monotonicNanos, Long clientRenderFrameSerial,
            Point currentEntityWorldPosition, Point entityBoundsMin, Point entityBoundsMax,
            String phase, boolean actuallyInSubLevel,
            String packetClassification, UUID helperFrameId, Point rawPacketPoint,
            Point previousClientWorld, Point oldXBeforeTick, Point xoBeforeTick, Point projectedFrameWorld,
            Point finalClientWorld, Point oldXAfterTick, Point xoAfterTick, UUID committedRenderFrameId,
            Point storageAnchor, Point localRenderAnchorPrevious, Point localRenderAnchorCurrent,
            boolean frameProjectionPaused, UUID nativeTrackingFrameId, boolean clientSettled, boolean clientOnGround,
            int clientStackCount, boolean clientStackEmpty, Point clientVelocity, RenderValues render) { }
    public record Lifecycle(boolean entityRemoved, String removalReason, boolean clientLevelLookupSameInstance) { }
    public enum CoverageStatus { OBSERVED, NOT_OBSERVED, NOT_EXPECTED_DUE_TO_CULL, NOT_INSTRUMENTED, INCOMPLETE }
    public record Snapshot(UUID captureUuid, UUID entityUuid, int entityId, String dimension,
            long startClientTick, long startMonotonicNanos, int elapsedClientTicks, long elapsedNanos,
            String stopReason, int packetRows, int tickRows, int cullRows, int dispatcherRows,
            int renderOffsetRows, int modelDrawRows, int modelSkippedRows, int truncatedRowLogCount,
            boolean rowDataValid, String rowDataFailure, boolean complete, boolean windowComplete,
            boolean overflow, String coverageFailure,
            CoverageStatus spawnRawCoverage,
            CoverageStatus packetCoverage, CoverageStatus tickCoverage, CoverageStatus cullCoverage,
            CoverageStatus dispatcherCoverage, CoverageStatus renderOffsetCoverage,
            CoverageStatus modelCoverage, CoverageStatus renderFrameCoverage, int renderFrameCount,
            Long firstRenderFrameSerial, Long lastRenderFrameSerial, Lifecycle lifecycle,
            List<Row> rows, List<String> boundedRowLogRecords) {
        public Snapshot { rows = List.copyOf(rows); boundedRowLogRecords = List.copyOf(boundedRowLogRecords); }
    }

    private final UUID captureUuid, entityUuid;
    private final int entityId;
    private final String dimension;
    private final long startClientTick, startMonotonicNanos;
    private final ArrayList<Row> rows = new ArrayList<>();
    private int packetRows, tickRows, cullRows, dispatcherRows, renderOffsetRows, modelDrawRows, modelSkippedRows;
    private boolean complete = true, rowDataValid = true, overflow, closed;
    private String stopReason, dataFailure, coverageFailure;
    private int truncatedRowLogCount;
    private int missingRenderFrameRows;
    private final TreeSet<Long> renderFrameSerials = new TreeSet<>();
    private final java.util.Map<Long, Long> trueCullFrames = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> linkedDispatchCull = new java.util.HashMap<>();
    private final java.util.Map<Long, Integer> dispatchOffsets = new java.util.HashMap<>();
    private final java.util.Map<Long, Integer> dispatchModels = new java.util.HashMap<>();
    private final java.util.Map<Long, Long> dispatchFrames = new java.util.HashMap<>();
    private final java.util.Set<Long> closedDispatches = new java.util.HashSet<>();
    private Snapshot finalSnapshot;

    public LooseCargoObservationTape(UUID captureUuid, UUID entityUuid, int entityId, String dimension,
                                    long startClientTick, long startMonotonicNanos) {
        this.captureUuid = java.util.Objects.requireNonNull(captureUuid);
        this.entityUuid = java.util.Objects.requireNonNull(entityUuid);
        if (entityId < 0 || dimension == null || dimension.isBlank()) throw new IllegalArgumentException("invalid cargo capture scope");
        this.entityId = entityId;
        this.dimension = dimension;
        this.startClientTick = startClientTick;
        this.startMonotonicNanos = startMonotonicNanos;
    }

    public boolean append(Row row) {
        if (closed || row == null) return false;
        if (!entityUuid.equals(row.entityUuid()) || entityId != row.entityId()) {
            markIncomplete("scope-mismatch");
            return false;
        }
        if (rows.size() >= MAX_ROWS) {
            overflow = true;
            markIncomplete("row-cap");
            return false;
        }
        rows.add(row);
        String invalid = validate(row);
        if (invalid != null) markIncomplete(invalid);
        if (isRenderPhase(row.phase()) && row.clientRenderFrameSerial() != null && row.clientRenderFrameSerial() > 0)
            renderFrameSerials.add(row.clientRenderFrameSerial());
        else if (isRenderPhase(row.phase())) missingRenderFrameRows++;
        if ("RAW_FRAME_PACKET".equals(row.phase())) packetRows++;
        else if ("CLIENT_TICK".equals(row.phase())) tickRows++;
        else if ("DISPATCH_CULL".equals(row.phase())) cullRows++;
        else if ("DISPATCH_RENDER".equals(row.phase())) dispatcherRows++;
        else if ("RENDER_OFFSET".equals(row.phase())) renderOffsetRows++;
        else if ("MODEL_DRAW".equals(row.phase())) modelDrawRows++;
        else if ("MODEL_SKIPPED".equals(row.phase())) modelSkippedRows++;
        else markIncomplete("unknown-phase");
        trackInvocation(row);
        if (rows.size() == MAX_ROWS) { overflow = true; markIncomplete("row-cap"); }
        return true;
    }

    private void trackInvocation(Row row) {
        RenderValues render=row.render();
        if (render == null) return;
        if ("DISPATCH_CULL".equals(row.phase()) && Boolean.TRUE.equals(render.shouldRender()) && render.cullId()!=null)
            trueCullFrames.put(render.cullId(),row.clientRenderFrameSerial());
        else if ("DISPATCH_RENDER".equals(row.phase()) && render.dispatchId()!=null) {
            dispatchFrames.put(render.dispatchId(),row.clientRenderFrameSerial());
            if (render.linkedCullId()!=null) linkedDispatchCull.put(render.dispatchId(),render.linkedCullId());
        } else if ("RENDER_OFFSET".equals(row.phase()) && render.dispatchId()!=null)
            dispatchOffsets.merge(render.dispatchId(),1,Integer::sum);
        else if (("MODEL_DRAW".equals(row.phase()) || "MODEL_SKIPPED".equals(row.phase())) && render.dispatchId()!=null)
            dispatchModels.merge(render.dispatchId(),1,Integer::sum);
    }

    public void closeDispatch(long dispatchId) {
        if (!closed && dispatchFrames.containsKey(dispatchId)) closedDispatches.add(dispatchId);
    }

    private static String validate(Row row) {
        if (row.captureClientTick() < 0 || row.entityTickCount() < 0) return "negative-client-clock";
        if (row.clientRenderFrameSerial() != null && row.clientRenderFrameSerial() <= 0) return "invalid-render-frame-serial";
        if (!finite(row.currentEntityWorldPosition()) || !finite(row.entityBoundsMin()) || !finite(row.entityBoundsMax())
                || row.entityBoundsMin().x() > row.entityBoundsMax().x() || row.entityBoundsMin().y() > row.entityBoundsMax().y()
                || row.entityBoundsMin().z() > row.entityBoundsMax().z() || !finite(row.clientVelocity())
                || row.clientStackCount() < 0)
            return "invalid-client-entity-state";
        if (!optionalFinite(row.storageAnchor()) || !optionalFinite(row.localRenderAnchorPrevious())
                || !optionalFinite(row.localRenderAnchorCurrent()) || !optionalFinite(row.previousClientWorld())
                || !optionalFinite(row.oldXBeforeTick()) || !optionalFinite(row.xoBeforeTick())
                || !optionalFinite(row.projectedFrameWorld()) || !optionalFinite(row.finalClientWorld())
                || !optionalFinite(row.oldXAfterTick()) || !optionalFinite(row.xoAfterTick()))
            return "nonfinite-optional-point";
        return switch (row.phase()) {
            case "RAW_FRAME_PACKET" -> finite(row.rawPacketPoint()) && row.packetClassification() != null
                    && row.render() == null ? null : "invalid-raw-frame-packet-fields";
            case "CLIENT_TICK" -> finite(row.previousClientWorld()) && finite(row.oldXBeforeTick())
                    && finite(row.xoBeforeTick()) && finite(row.finalClientWorld())
                    && finite(row.oldXAfterTick()) && finite(row.xoAfterTick()) && row.render() == null
                    ? null : "invalid-client-tick-fields";
            case "DISPATCH_CULL" -> row.render() == null || row.render().shouldRender() == null
                    ? "missing-cull-result" : row.render().cullId()==null
                    ? "missing-cull-id" : validRender(row, true, false, false, false, true, false);
            case "DISPATCH_RENDER" -> row.render()==null || row.render().dispatchId()==null
                    ? "missing-dispatch-id" : validRender(row, true, true, true, false, true, true);
            case "RENDER_OFFSET" -> row.render()==null || row.render().dispatchId()==null
                    ? "unmatched-render-offset" : validRender(row, true, false, false, true, false, false);
            case "MODEL_DRAW" -> validRender(row, true, true, true, false, false, false)
                    != null ? "invalid-model-draw-fields" : row.render().dispatchId()==null ? "unmatched-model-draw"
                    : row.render().renderKind() == null || row.render().renderKind().isBlank()
                    ? "missing-model-render-kind" : null;
            case "MODEL_SKIPPED" -> validRender(row, true, true, true, false, false, false)
                    != null ? "invalid-model-skip-fields" : row.render().dispatchId()==null ? "unmatched-model-skip"
                    : row.render().skipReason() == null || row.render().skipReason().isBlank()
                    ? "missing-model-skip-reason" : null;
            default -> "unknown-row-phase";
        };
    }

    /** Checks phase-specific actual inputs; null is accepted only for fields not required in that phase. */
    private static String validRender(Row row, boolean requireCamera, boolean requireMatrix,
                                      boolean requireLight, boolean requireOffset, boolean requireDispatcherPosition,
                                      boolean requireDispatcherYaw) {
        RenderValues render=row.render();
        if (render == null) return "missing-render-values";
        if (requireCamera && (!finite(render.cameraPosition()) || !finite(render.cameraLook())
                || !finiteQuaternion(render.cameraRotation()))) return "invalid-camera-values";
        if (render.partialTick() == null || !Float.isFinite(render.partialTick())
                || render.partialTick() < 0f || render.partialTick() > 1f) return "invalid-partial-tick";
        if (requireDispatcherPosition && !finite(render.dispatcherCoordinates())) return "missing-dispatcher-position";
        if (render.dispatcherCoordinates() != null && !finite(render.dispatcherCoordinates())) return "invalid-dispatcher-coordinates";
        if (requireDispatcherYaw && render.dispatcherYaw() == null) return "missing-dispatcher-yaw";
        if (render.dispatcherYaw() != null && !Float.isFinite(render.dispatcherYaw())) return "invalid-dispatcher-yaw";
        if (requireLight && render.packedLight() == null) return "missing-packed-light";
        if (requireOffset && !finite(render.actualRenderOffset())) return "missing-render-offset";
        if (render.actualRenderOffset() != null && !finite(render.actualRenderOffset())) return "nonfinite-render-offset";
        if (requireMatrix && !finiteMatrix(render.modelPoseMatrix())) return "invalid-model-matrix";
        if (render.modelPoseMatrix() != null && !finiteMatrix(render.modelPoseMatrix())) return "nonfinite-model-matrix";
        if (render.cameraRotation() != null && !finiteQuaternion(render.cameraRotation())) return "invalid-camera-rotation";
        if (render.appliedLocalRotation()!=null && !finiteQuaternion(render.appliedLocalRotation())) return "invalid-applied-local-rotation";
        LightingContext lighting=render.recomputedProbeContextAtModelDraw();
        if (lighting!=null && (!optionalFinite(lighting.recomputedProbeWorld()) || !optionalFinite(lighting.localProbe())))
            return "nonfinite-recomputed-light-probe-context";
        if (lighting!=null && (lighting.probeStatus()==null || lighting.projectionStatus()==null || lighting.scaleStatus()==null
                || (lighting.localCellX()==null)!=(lighting.localCellY()==null)
                || (lighting.localCellX()==null)!=(lighting.localCellZ()==null))) return "invalid-recomputed-light-probe-context";
        if (row.clientRenderFrameSerial() == null || row.clientRenderFrameSerial() <= 0) return "missing-render-frame-serial";
        return null;
    }

    private static boolean finiteMatrix(List<Float> values) {
        return values != null && values.size() == 16 && values.stream().allMatch(Float::isFinite);
    }
    private static boolean finiteQuaternion(List<Float> values) {
        if (values == null || values.size() != 4 || !values.stream().allMatch(Float::isFinite)) return false;
        double normSquared=0;
        for (float value : values) normSquared+=(double)value*value;
        return Double.isFinite(normSquared) && normSquared > 1e-24;
    }
    private static boolean optionalFinite(Point value) { return value == null || finite(value); }
    private static boolean finite(Point value) {
        return value != null && Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
    }

    private static boolean isRenderPhase(String phase) {
        return "DISPATCH_CULL".equals(phase) || "DISPATCH_RENDER".equals(phase) || "RENDER_OFFSET".equals(phase)
                || "MODEL_DRAW".equals(phase) || "MODEL_SKIPPED".equals(phase);
    }

    public boolean expired(long clientTick, long nowNanos) {
        long ticks = clientTick - startClientTick;
        long nanos = nowNanos - startMonotonicNanos;
        return ticks >= MAX_CLIENT_TICKS || (nanos >= 0 && nanos >= MAX_WALL_NANOS);
    }

    public void markIncomplete(String reason) {
        if (closed) return;
        complete = false;
        rowDataValid = false;
        if (dataFailure == null) dataFailure = reason == null ? "incomplete" : reason;
        if (stopReason == null) stopReason = reason == null ? "incomplete" : reason;
    }

    public Snapshot finish(String reason, long clientTick, long nowNanos) {
        return finish(reason,clientTick,nowNanos,null);
    }

    public Snapshot finish(String reason, long clientTick, long nowNanos, Lifecycle lifecycle) {
        if (finalSnapshot != null) return finalSnapshot;
        if (stopReason == null) stopReason = reason == null ? "stopped" : reason;
        CoverageStatus packetCoverage = packetRows > 0 ? CoverageStatus.OBSERVED : CoverageStatus.NOT_OBSERVED;
        CoverageStatus tickCoverage = tickRows > 0 ? CoverageStatus.OBSERVED : CoverageStatus.NOT_OBSERVED;
        CoverageStatus cullCoverage = cullRows > 0 ? CoverageStatus.OBSERVED : CoverageStatus.NOT_OBSERVED;
        boolean anyCullVisible = rows.stream().anyMatch(row -> "DISPATCH_CULL".equals(row.phase())
                && row.render() != null && Boolean.TRUE.equals(row.render().shouldRender()));
        boolean unknownCullOutcome = rows.stream().anyMatch(row -> "DISPATCH_CULL".equals(row.phase())
                && row.render() != null && row.render().shouldRender() == null);
        CoverageStatus dispatcherCoverage = dispatcherRows > 0 ? CoverageStatus.OBSERVED
                : cullRows > 0 && !anyCullVisible && !unknownCullOutcome
                ? CoverageStatus.NOT_EXPECTED_DUE_TO_CULL : CoverageStatus.NOT_OBSERVED;
        CoverageStatus offsetCoverage = renderOffsetRows > 0 ? CoverageStatus.OBSERVED
                : dispatcherRows == 0 && dispatcherCoverage == CoverageStatus.NOT_EXPECTED_DUE_TO_CULL
                ? CoverageStatus.NOT_EXPECTED_DUE_TO_CULL : CoverageStatus.NOT_OBSERVED;
        CoverageStatus modelCoverage = modelDrawRows + modelSkippedRows > 0 ? CoverageStatus.OBSERVED
                : dispatcherRows == 0 && dispatcherCoverage == CoverageStatus.NOT_EXPECTED_DUE_TO_CULL
                ? CoverageStatus.NOT_EXPECTED_DUE_TO_CULL : CoverageStatus.NOT_OBSERVED;
        int renderPhaseRows = cullRows + dispatcherRows + renderOffsetRows + modelDrawRows + modelSkippedRows;
        CoverageStatus renderFrameCoverage = renderPhaseRows == 0 ? CoverageStatus.NOT_OBSERVED
                : missingRenderFrameRows > 0 || renderFrameSerials.isEmpty()
                ? CoverageStatus.INCOMPLETE : CoverageStatus.OBSERVED;
        var missingCullDispatch = new ArrayList<Long>();
        for (var cull : trueCullFrames.entrySet()) {
            boolean linked=linkedDispatchCull.entrySet().stream().anyMatch(entry -> entry.getValue().equals(cull.getKey())
                    && java.util.Objects.equals(dispatchFrames.get(entry.getKey()),cull.getValue()));
            if (!linked) missingCullDispatch.add(cull.getKey());
        }
        var missingOffsets = new ArrayList<Long>();
        var missingModels = new ArrayList<Long>();
        var unmatchedInvocationIds = new ArrayList<Long>();
        var missingEnds = new ArrayList<Long>();
        for (long dispatch : dispatchFrames.keySet()) {
            if (dispatchOffsets.getOrDefault(dispatch,0)==0) missingOffsets.add(dispatch);
            if (dispatchModels.getOrDefault(dispatch,0)==0) missingModels.add(dispatch);
            if (!closedDispatches.contains(dispatch)) missingEnds.add(dispatch);
        }
        for (long id : dispatchOffsets.keySet()) if (!dispatchFrames.containsKey(id)) unmatchedInvocationIds.add(id);
        for (long id : dispatchModels.keySet()) if (!dispatchFrames.containsKey(id) && !unmatchedInvocationIds.contains(id))
            unmatchedInvocationIds.add(id);
        if (!missingCullDispatch.isEmpty()) dispatcherCoverage=CoverageStatus.INCOMPLETE;
        if (!missingOffsets.isEmpty()) offsetCoverage=CoverageStatus.INCOMPLETE;
        if (!missingModels.isEmpty()) modelCoverage=CoverageStatus.INCOMPLETE;
        if (!unmatchedInvocationIds.isEmpty()) offsetCoverage=CoverageStatus.INCOMPLETE;
        boolean invocationCoverageComplete = missingCullDispatch.isEmpty() && missingOffsets.isEmpty()
                && missingModels.isEmpty() && missingEnds.isEmpty() && unmatchedInvocationIds.isEmpty();
        boolean phaseCoverageComplete = tickCoverage == CoverageStatus.OBSERVED
                && cullCoverage == CoverageStatus.OBSERVED
                && isObservedOrNotExpected(dispatcherCoverage)
                && isObservedOrNotExpected(offsetCoverage)
                && isObservedOrNotExpected(modelCoverage)
                && renderFrameCoverage == CoverageStatus.OBSERVED && invocationCoverageComplete;
        if (!phaseCoverageComplete && coverageFailure == null) {
            if (tickCoverage != CoverageStatus.OBSERVED) coverageFailure = "client-tick-phase-not-observed";
            else if (cullCoverage != CoverageStatus.OBSERVED) coverageFailure = "dispatcher-cull-phase-not-observed";
            else if (!missingCullDispatch.isEmpty()) coverageFailure = "true-culls-without-same-frame-dispatch:"+boundedIds(missingCullDispatch);
            else if (dispatcherCoverage == CoverageStatus.NOT_OBSERVED) coverageFailure = "visible-cull-without-dispatch-render";
            else if (!missingOffsets.isEmpty()) coverageFailure = "dispatch-without-own-render-offset:"+boundedIds(missingOffsets);
            else if (!unmatchedInvocationIds.isEmpty()) coverageFailure = "unmatched-render-invocation-id:"+boundedIds(unmatchedInvocationIds);
            else if (offsetCoverage == CoverageStatus.NOT_OBSERVED) coverageFailure = "dispatch-without-render-offset";
            else if (!missingModels.isEmpty()) coverageFailure = "dispatch-without-own-model-terminal:"+boundedIds(missingModels);
            else if (modelCoverage == CoverageStatus.NOT_OBSERVED) coverageFailure = "dispatch-without-model-draw-or-skip";
            else if (!missingEnds.isEmpty()) coverageFailure = "dispatch-without-end:"+boundedIds(missingEnds);
            else coverageFailure = "render-frame-serial-not-observed";
        }
        complete = rowDataValid && phaseCoverageComplete && !overflow;
        int elapsedClientTicks = (int)Math.max(0, clientTick-startClientTick);
        long elapsedNanos = Math.max(0, nowNanos-startMonotonicNanos);
        boolean windowComplete = elapsedClientTicks >= MAX_CLIENT_TICKS || elapsedNanos >= MAX_WALL_NANOS;
        var boundedRows = new ArrayList<String>(rows.size());
        for (Row row : rows) {
            String formatted = row.toString();
            String bounded = boundedUtf8(formatted, MAX_ROW_LOG_UTF8_BYTES);
            if (!bounded.equals(formatted)) {
                truncatedRowLogCount++;
                markIncomplete("row-log-payload-truncated");
            }
            boundedRows.add(bounded);
        }
        complete = complete && truncatedRowLogCount == 0;
        finalSnapshot = new Snapshot(captureUuid, entityUuid, entityId, dimension, startClientTick,
                startMonotonicNanos, elapsedClientTicks, elapsedNanos,
                stopReason, packetRows, tickRows, cullRows, dispatcherRows, renderOffsetRows, modelDrawRows,
                modelSkippedRows, truncatedRowLogCount, rowDataValid, dataFailure, complete, windowComplete, overflow, coverageFailure,
                CoverageStatus.NOT_INSTRUMENTED, packetCoverage, tickCoverage, cullCoverage, dispatcherCoverage,
                offsetCoverage, modelCoverage, renderFrameCoverage, renderFrameSerials.size(),
                renderFrameSerials.isEmpty() ? null : renderFrameSerials.iterator().next(),
                renderFrameSerials.isEmpty() ? null : renderFrameSerials.last(),
                lifecycle, rows, boundedRows);
        closed = true;
        return finalSnapshot;
    }

    private static String boundedIds(List<Long> ids) {
        return ids.stream().limit(8).toList()+(ids.size()>8?"...(total="+ids.size()+")":"");
    }

    private static boolean isObservedOrNotExpected(CoverageStatus status) {
        return status == CoverageStatus.OBSERVED || status == CoverageStatus.NOT_EXPECTED_DUE_TO_CULL;
    }

    /** Returns the full row when within budget or a code-point-safe explicit truncation otherwise. */
    private static String boundedUtf8(String text, int maxBytes) {
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int width = utf8Width(codePoint);
            if (bytes + width > maxBytes) return truncateUtf8(text, maxBytes);
            bytes += width;
            offset += Character.charCount(codePoint);
        }
        return text;
    }

    private static String truncateUtf8(String text, int maxBytes) {
        int markerBytes = TRUNCATED_MARKER.length();
        int limit = Math.max(0, maxBytes-markerBytes);
        StringBuilder result = new StringBuilder(Math.min(text.length(), limit));
        int bytes = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int width = utf8Width(codePoint);
            if (bytes + width > limit) break;
            result.appendCodePoint(codePoint);
            bytes += width;
            offset += Character.charCount(codePoint);
        }
        return result.append(TRUNCATED_MARKER).toString();
    }

    private static int utf8Width(int codePoint) {
        return codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
    }

    public int rows() { return rows.size(); }
    public int packetRows() { return packetRows; }
    public int tickRows() { return tickRows; }
    public int cullRows() { return cullRows; }
    public int dispatcherRows() { return dispatcherRows; }
    public int renderOffsetRows() { return renderOffsetRows; }
    public int modelDrawRows() { return modelDrawRows; }
    public int modelSkippedRows() { return modelSkippedRows; }
    public boolean complete() { return complete; }
    public boolean overflow() { return overflow; }
    public boolean closed() { return closed; }
    public String stopReason() { return stopReason; }
    public UUID captureUuid() { return captureUuid; }
    public UUID entityUuid() { return entityUuid; }
    public int entityId() { return entityId; }
}
