package com.juicyslew.moonstation14.ms14.loose_objects.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LooseCargoObservationTapeTest {
    @Test void tapeScopesOneEntityAndRetainsOnlyImmutableExactIdentityRows() {
        UUID capture = UUID.randomUUID(), entity = UUID.randomUUID();
        var tape = new LooseCargoObservationTape(capture, entity, 41, "minecraft:overworld", 0, 100);
        assertTrue(tape.append(row(entity, 41, 1)));
        assertFalse(tape.append(row(UUID.randomUUID(), 41, 2)));
        assertFalse(tape.complete());
        var snapshot = tape.finish("scope-mismatch", 2, 200);
        assertEquals(capture, snapshot.captureUuid());
        assertEquals(entity, snapshot.entityUuid());
        assertEquals("minecraft:overworld", snapshot.dimension());
        assertEquals("scope-mismatch", snapshot.stopReason());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.rows().add(row(entity,41,3)));
        assertTrue(snapshot.rows().getFirst().toString().contains("1.2345678901234567"),
                "primitive Double.toString retains raw coordinate precision");
        assertSame(snapshot, tape.finish("duplicate-dump", 3, 300));
    }

    @Test void tickAndWallBudgetsAreIndependentAndBounded() {
        UUID entity = UUID.randomUUID();
        var tape = new LooseCargoObservationTape(UUID.randomUUID(), entity, 1, "test:dimension", 50, 1000);
        assertFalse(tape.expired(79, 1000 + LooseCargoObservationTape.MAX_WALL_NANOS - 1));
        assertTrue(tape.expired(130, 1000));
        assertTrue(tape.expired(1, 1000 + LooseCargoObservationTape.MAX_WALL_NANOS));
        assertEquals(80, LooseCargoObservationTape.MAX_CLIENT_TICKS);
        assertEquals(8_000_000_000L, LooseCargoObservationTape.MAX_WALL_NANOS);
    }

    @Test void rowCapIsExplicitOverflowAndDumpIsIdempotent() {
        UUID entity = UUID.randomUUID();
        var tape = new LooseCargoObservationTape(UUID.randomUUID(), entity, 3, "test:level", 0, 0);
        for (int i=0; i<LooseCargoObservationTape.MAX_ROWS; i++) assertTrue(tape.append(row(entity,3,i)));
        assertTrue(tape.overflow());
        assertFalse(tape.complete());
        assertEquals(LooseCargoObservationTape.MAX_ROWS, tape.rows());
        var dump = tape.finish("row-cap", 42, 100);
        assertEquals(LooseCargoObservationTape.MAX_ROWS, dump.rows().size());
        assertTrue(dump.overflow());
        assertEquals(dump, tape.finish("again", 43, 200));
    }

    @Test void worstFinitePrimitiveRowFitsOneSmallLoggerRecord() {
        double max=Double.MAX_VALUE;
        var point=new LooseCargoObservationTape.Point(max,-max,max);
        var row=new LooseCargoObservationTape.Row(UUID.randomUUID(),123,1,Integer.MAX_VALUE,Long.MAX_VALUE,
                Long.MAX_VALUE,Long.MAX_VALUE,point,point,point,"RAW_FRAME_PACKET",true,"FRAME_STORAGE",UUID.randomUUID(),point,point,point,point,
                point,point,point,point,UUID.randomUUID(),point,point,point,true,UUID.randomUUID(),true,false,64,false,point,
                new LooseCargoObservationTape.RenderValues(true,point,point,java.util.Collections.nCopies(4,Float.MAX_VALUE),
                        point,Float.MAX_VALUE,Float.MAX_VALUE,Integer.MAX_VALUE,point,"block_model",null,
                        java.util.Collections.nCopies(16,Float.MAX_VALUE),1L,null,null));
        assertTrue(row.toString().length()<8192);
    }

    @Test void modelDrawLightingContextIsOptionalFiniteAndExplicitlyCarriesUnavailableStatuses() {
        UUID entity=UUID.randomUUID();
        var base=validRow(entity,6,"MODEL_DRAW",1,null);
        var old=base.render();
        var context=new LooseCargoObservationTape.LightingContext(new LooseCargoObservationTape.Point(2,3,4),entity,
                new LooseCargoObservationTape.Point(1,2,3),1,2,3,15,"available","available","available");
        var withContext=new LooseCargoObservationTape.RenderValues(old.shouldRender(),old.cameraPosition(),old.cameraLook(),
                old.cameraRotation(),old.dispatcherCoordinates(),old.dispatcherYaw(),old.partialTick(),old.packedLight(),
                old.actualRenderOffset(),old.renderKind(),old.skipReason(),old.modelPoseMatrix(),old.cullId(),old.dispatchId(),
                old.linkedCullId(),old.actualContainingFrameId(),old.shouldKick(),old.appliedLocalRotation(),context);
        var tape=new LooseCargoObservationTape(UUID.randomUUID(),entity,6,"test:level",0,0);
        appendValidModelDraw(tape,entity,copyRender(base,withContext));
        var snapshot=tape.finish("test",1,1);
        assertTrue(snapshot.rowDataValid());
        var recordedDraw=snapshot.rows().stream().filter(row -> "MODEL_DRAW".equals(row.phase())).findFirst().orElseThrow();
        assertEquals(context,recordedDraw.render().recomputedProbeContextAtModelDraw());
        int recordedDrawIndex=snapshot.rows().indexOf(recordedDraw);
        assertTrue(snapshot.boundedRowLogRecords().get(recordedDrawIndex).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                <= LooseCargoObservationTape.MAX_ROW_LOG_UTF8_BYTES);

        var unavailable=new LooseCargoObservationTape.LightingContext(new LooseCargoObservationTape.Point(2,3,4),null,
                null,null,null,null,null,"available","no-committed-frame","unavailable");
        var unavailableRender=new LooseCargoObservationTape.RenderValues(old.shouldRender(),old.cameraPosition(),old.cameraLook(),
                old.cameraRotation(),old.dispatcherCoordinates(),old.dispatcherYaw(),old.partialTick(),old.packedLight(),
                old.actualRenderOffset(),old.renderKind(),old.skipReason(),old.modelPoseMatrix(),old.cullId(),old.dispatchId(),
                old.linkedCullId(),old.actualContainingFrameId(),old.shouldKick(),old.appliedLocalRotation(),unavailable);
        var nullTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,6,"test:level",0,0);
        appendValidModelDraw(nullTape,entity,copyRender(base,unavailableRender));
        assertTrue(nullTape.finish("test",1,1).rowDataValid());

        var bad=new LooseCargoObservationTape.LightingContext(new LooseCargoObservationTape.Point(Double.NaN,0,0),entity,
                null,null,null,null,null,"available","unavailable","unavailable");
        var badRender=new LooseCargoObservationTape.RenderValues(old.shouldRender(),old.cameraPosition(),old.cameraLook(),
                old.cameraRotation(),old.dispatcherCoordinates(),old.dispatcherYaw(),old.partialTick(),old.packedLight(),
                old.actualRenderOffset(),old.renderKind(),old.skipReason(),old.modelPoseMatrix(),old.cullId(),old.dispatchId(),
                old.linkedCullId(),old.actualContainingFrameId(),old.shouldKick(),old.appliedLocalRotation(),bad);
        var badTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,6,"test:level",0,0);
        appendValidModelDraw(badTape,entity,copyRender(base,badRender));
        var badSnapshot=badTape.finish("test",1,1);
        assertFalse(badSnapshot.rowDataValid());
        assertEquals("invalid-model-draw-fields",badSnapshot.rowDataFailure(),
                "the finite context guard contributes to the existing phase-level model-draw validation result");
    }

    @Test void nonfiniteRequiredPoseDataAndCameraFieldsRemainRowsButMarkDataInvalid() {
        UUID entity=UUID.randomUUID();
        var base=validRow(entity,5,"DISPATCH_RENDER",1,null);
        var original=base.render();
        var badMatrix=new LooseCargoObservationTape.RenderValues(original.shouldRender(),original.cameraPosition(),
                original.cameraLook(),original.cameraRotation(),original.dispatcherCoordinates(),original.dispatcherYaw(),
                original.partialTick(),original.packedLight(),original.actualRenderOffset(),original.renderKind(),
                original.skipReason(),java.util.Collections.nCopies(15,1f),original.cullId(),original.dispatchId(),original.linkedCullId());
        var matrixTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,5,"test:level",0,0);
        matrixTape.append(copyRender(base,badMatrix));
        var matrix=matrixTape.finish("test",1,1L);
        assertFalse(matrix.rowDataValid());
        assertEquals(1,matrix.rows().size(),"bad measurement remains available for diagnostics");
        assertTrue(matrix.rowDataFailure().contains("model-matrix"));

        var badPartial=new LooseCargoObservationTape.RenderValues(original.shouldRender(),original.cameraPosition(),
                original.cameraLook(),original.cameraRotation(),original.dispatcherCoordinates(),original.dispatcherYaw(),
                Float.POSITIVE_INFINITY,original.packedLight(),original.actualRenderOffset(),original.renderKind(),
                original.skipReason(),original.modelPoseMatrix(),original.cullId(),original.dispatchId(),original.linkedCullId());
        var partialTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,5,"test:level",0,0);
        partialTape.append(copyRender(base,badPartial));
        assertFalse(partialTape.finish("test",1,1).rowDataValid());

        var badCamera=new LooseCargoObservationTape.RenderValues(original.shouldRender(),
                new LooseCargoObservationTape.Point(Double.NaN,0,0),original.cameraLook(),original.cameraRotation(),
                original.dispatcherCoordinates(),original.dispatcherYaw(),original.partialTick(),original.packedLight(),
                original.actualRenderOffset(),original.renderKind(),original.skipReason(),original.modelPoseMatrix(),
                original.cullId(),original.dispatchId(),original.linkedCullId());
        var cameraTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,5,"test:level",0,0);
        cameraTape.append(copyRender(base,badCamera));
        assertFalse(cameraTape.finish("test",1,1).rowDataValid());
    }

    @Test void coverageDistinguishesZeroRowsCullRejectionAndExpectedRenderStages() {
        UUID entity=UUID.randomUUID();
        var empty = new LooseCargoObservationTape(UUID.randomUUID(),entity,7,"test:level",0,10).finish("manual-stop",1,20);
        assertFalse(empty.complete());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_OBSERVED,empty.tickCoverage());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_OBSERVED,empty.cullCoverage());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_INSTRUMENTED,empty.spawnRawCoverage());
        assertFalse(empty.windowComplete());

        var allCulled = new LooseCargoObservationTape(UUID.randomUUID(),entity,7,"test:level",0,10);
        allCulled.append(validRow(entity,7,"CLIENT_TICK",1,null));
        allCulled.append(validRow(entity,7,"DISPATCH_CULL",1,false));
        var culledSnapshot=allCulled.finish("manual-stop",3,20);
        assertTrue(culledSnapshot.complete());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_EXPECTED_DUE_TO_CULL,culledSnapshot.dispatcherCoverage());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_EXPECTED_DUE_TO_CULL,culledSnapshot.renderOffsetCoverage());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_EXPECTED_DUE_TO_CULL,culledSnapshot.modelCoverage());
        assertEquals(LooseCargoObservationTape.CoverageStatus.NOT_OBSERVED,culledSnapshot.packetCoverage());
        assertFalse(culledSnapshot.windowComplete(),"short manual stop is a valid subset, not an 80-tick capture");

        var missingDispatch = new LooseCargoObservationTape(UUID.randomUUID(),entity,7,"test:level",0,10);
        missingDispatch.append(validRow(entity,7,"CLIENT_TICK",1,null));
        missingDispatch.append(validRow(entity,7,"DISPATCH_CULL",1,true));
        var missing=missingDispatch.finish("manual-stop",3,20);
        assertFalse(missing.complete());
        assertEquals(LooseCargoObservationTape.CoverageStatus.INCOMPLETE,missing.dispatcherCoverage());

        var missingModel = new LooseCargoObservationTape(UUID.randomUUID(),entity,7,"test:level",0,10);
        missingModel.append(validRow(entity,7,"CLIENT_TICK",1,null));
        missingModel.append(validRow(entity,7,"DISPATCH_CULL",1,true));
        missingModel.append(validRow(entity,7,"DISPATCH_RENDER",1,null));
        missingModel.append(validRow(entity,7,"RENDER_OFFSET",1,null));
        var noModel=missingModel.finish("manual-stop",3,20);
        assertFalse(noModel.complete());
        assertEquals(LooseCargoObservationTape.CoverageStatus.INCOMPLETE,noModel.modelCoverage());

        var skipped = new LooseCargoObservationTape(UUID.randomUUID(),entity,7,"test:level",0,10);
        for (String phase : new String[] {"CLIENT_TICK","DISPATCH_CULL","DISPATCH_RENDER","RENDER_OFFSET","MODEL_SKIPPED"})
            skipped.append(validRow(entity,7,phase,1,"DISPATCH_CULL".equals(phase)?true:null));
        skipped.closeDispatch(1);
        var skippedSnapshot=skipped.finish("manual-stop",5,20);
        assertTrue(skippedSnapshot.complete());
        assertEquals(LooseCargoObservationTape.CoverageStatus.OBSERVED,skippedSnapshot.modelCoverage());
    }

    @Test void nonFiniteRowsAndOversizeUtf8LoggerPayloadsRemainBoundedAndIncomplete() {
        UUID entity=UUID.randomUUID();
        var invalidTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,2,"test:level",0,0);
        invalidTape.append(validRow(entity,2,"CLIENT_TICK",1,null,Double.NaN,"block_model"));
        var invalid=invalidTape.finish("manual-stop",1,1L);
        assertFalse(invalid.complete());
        assertTrue(invalid.rowDataFailure().contains("invalid-client-entity-state"));

        var longTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,2,"test:level",0,0);
        longTape.append(validRow(entity,2,"MODEL_DRAW",1,null,0,"kind".repeat(10000)));
        var longSnapshot=longTape.finish("manual-stop",1,1L);
        assertFalse(longSnapshot.complete());
        assertEquals(1,longSnapshot.truncatedRowLogCount());
        String line=longSnapshot.boundedRowLogRecords().getFirst();
        assertTrue(line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= LooseCargoObservationTape.MAX_ROW_LOG_UTF8_BYTES);
        assertTrue(line.contains("ROW_LOG_TRUNCATED"));
        assertEquals("kind".repeat(10000),longSnapshot.rows().getFirst().render().renderKind(),
                "retained immutable raw row is not silently truncated");
    }

    @Test void coverageIsPerInvocationAndRequiresSameFrameCullLinkOwnOffsetModelAndEnd() {
        UUID entity=UUID.randomUUID();
        var tape=new LooseCargoObservationTape(UUID.randomUUID(),entity,3,"test:level",0,0);
        tape.append(validRow(entity,3,"CLIENT_TICK",1,null));
        tape.append(validRow(entity,3,"DISPATCH_CULL",1,true,11));
        tape.append(validRow(entity,3,"DISPATCH_CULL",1,true,12));
        tape.append(validRow(entity,3,"DISPATCH_RENDER",1,null,21,11L));
        tape.append(validRow(entity,3,"RENDER_OFFSET",1,null,21));
        tape.append(validRow(entity,3,"MODEL_DRAW",1,null,21));
        tape.closeDispatch(21);
        var snapshot=tape.finish("manual-stop",2,1);
        assertFalse(snapshot.complete(),"one dispatch cannot satisfy the second visible cull");
        assertEquals(LooseCargoObservationTape.CoverageStatus.INCOMPLETE,snapshot.dispatcherCoverage());
        assertTrue(snapshot.coverageFailure().contains("true-culls-without-same-frame-dispatch"));

        var twoDispatch=new LooseCargoObservationTape(UUID.randomUUID(),entity,3,"test:level",0,0);
        twoDispatch.append(validRow(entity,3,"CLIENT_TICK",1,null));
        twoDispatch.append(validRow(entity,3,"DISPATCH_CULL",1,true,11));
        twoDispatch.append(validRow(entity,3,"DISPATCH_CULL",1,true,12));
        twoDispatch.append(validRow(entity,3,"DISPATCH_RENDER",1,null,21,11L));
        twoDispatch.append(validRow(entity,3,"RENDER_OFFSET",1,null,21));
        twoDispatch.append(validRow(entity,3,"MODEL_DRAW",1,null,21));
        twoDispatch.append(validRow(entity,3,"DISPATCH_RENDER",1,null,22,12L));
        twoDispatch.append(validRow(entity,3,"RENDER_OFFSET",1,null,22));
        twoDispatch.append(validRow(entity,3,"MODEL_SKIPPED",1,null,22));
        twoDispatch.closeDispatch(21);
        twoDispatch.closeDispatch(22);
        assertTrue(twoDispatch.finish("manual-stop",2,1).complete(),"each dispatch has its own offset and draw/skip");

        var crossFrame=new LooseCargoObservationTape(UUID.randomUUID(),entity,3,"test:level",0,0);
        crossFrame.append(validRow(entity,3,"CLIENT_TICK",1,null));
        crossFrame.append(validRow(entity,3,"DISPATCH_CULL",1,true,11));
        crossFrame.append(validRow(entity,3,"DISPATCH_RENDER",2,null,21,11L));
        crossFrame.append(validRow(entity,3,"RENDER_OFFSET",2,null,21));
        crossFrame.append(validRow(entity,3,"MODEL_DRAW",2,null,21));
        crossFrame.closeDispatch(21);
        var cross=crossFrame.finish("manual-stop",2,1);
        assertFalse(cross.complete());
        assertTrue(cross.coverageFailure().contains("same-frame"));

        var unmatched=new LooseCargoObservationTape(UUID.randomUUID(),entity,3,"test:level",0,0);
        unmatched.append(validRow(entity,3,"CLIENT_TICK",1,null));
        unmatched.append(validRow(entity,3,"DISPATCH_CULL",1,false,11));
        unmatched.append(validRow(entity,3,"RENDER_OFFSET",1,null,99));
        var unmatchedSnapshot=unmatched.finish("manual-stop",2,1);
        assertFalse(unmatchedSnapshot.complete(),"late offset cannot borrow prior/latest dispatch ID");
        assertTrue(unmatchedSnapshot.coverageFailure().contains("unmatched-render-invocation-id"));
    }

    @Test void requiredNullVectorsAndNonFiniteCameraPartialOrMatrixMarkCoverageIncompleteButOptionalWorldFieldsMayBeNull() {
        UUID entity=UUID.randomUUID();
        var validTick=validRow(entity,4,"CLIENT_TICK",1,null);
        var missingTickVector=copyRow(validTick,null,validTick.rawPacketPoint(),validTick.committedRenderFrameId(),
                validTick.storageAnchor(),validTick.localRenderAnchorPrevious(),validTick.localRenderAnchorCurrent(),
                false,validTick.render());
        var tickTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,4,"test:level",0,0);
        tickTape.append(missingTickVector);
        assertFalse(tickTape.finish("test",1,1).complete());

        var optionalWorld=copyRow(validTick,validTick.previousClientWorld(),null,null,null,null,null,true,null);
        var optionalTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,4,"test:level",0,0);
        optionalTape.append(optionalWorld);
        assertTrue(optionalTape.finish("test",1,1).rowDataValid(), "WORLD/paused frame metadata may legitimately be absent");

        var validDispatch=validRow(entity,4,"DISPATCH_RENDER",1,null);
        var original=validDispatch.render();
        var badPartial=new LooseCargoObservationTape.RenderValues(original.shouldRender(),original.cameraPosition(),
                original.cameraLook(),original.cameraRotation(),original.dispatcherCoordinates(),original.dispatcherYaw(),
                Float.NaN,original.packedLight(),original.actualRenderOffset(),original.renderKind(),original.skipReason(),
                original.modelPoseMatrix(),original.cullId(),original.dispatchId(),original.linkedCullId());
        var partialTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,4,"test:level",0,0);
        partialTape.append(copyRow(validDispatch,validDispatch.previousClientWorld(),null,entity,
                validDispatch.storageAnchor(),validDispatch.localRenderAnchorPrevious(),validDispatch.localRenderAnchorCurrent(),false,badPartial));
        assertFalse(partialTape.finish("test",1,1).complete());

        var badMatrix=new LooseCargoObservationTape.RenderValues(original.shouldRender(),original.cameraPosition(),
                original.cameraLook(),original.cameraRotation(),original.dispatcherCoordinates(),original.dispatcherYaw(),
                original.partialTick(),original.packedLight(),original.actualRenderOffset(),original.renderKind(),original.skipReason(),
                java.util.Collections.nCopies(15,1f),original.cullId(),original.dispatchId(),original.linkedCullId());
        var matrixTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,4,"test:level",0,0);
        matrixTape.append(copyRow(validDispatch,validDispatch.previousClientWorld(),null,entity,
                validDispatch.storageAnchor(),validDispatch.localRenderAnchorPrevious(),validDispatch.localRenderAnchorCurrent(),false,badMatrix));
        assertFalse(matrixTape.finish("test",1,1).complete());

        var validPacket=validRow(entity,4,"RAW_FRAME_PACKET",1,null);
        var badPacket=copyRow(validPacket,null,null,null,null,null,null,false,validPacket.render());
        var packetTape=new LooseCargoObservationTape(UUID.randomUUID(),entity,4,"test:level",0,0);
        packetTape.append(badPacket);
        assertFalse(packetTape.finish("test",1,1).complete());
    }

    private static LooseCargoObservationTape.Row validRow(UUID entity, int id, String phase, long serial, Boolean shouldRender) {
        return validRow(entity,id,phase,serial,shouldRender,1,1L);
    }
    private static LooseCargoObservationTape.Row validRow(UUID entity,int id,String phase,long serial,Boolean shouldRender,long token) {
        return validRow(entity,id,phase,serial,shouldRender,token,phase.equals("DISPATCH_RENDER")?token:null);
    }
    private static LooseCargoObservationTape.Row validRow(UUID entity,int id,String phase,long serial,Boolean shouldRender,
            long dispatchId,Long linkedCullId) {
        var row=validRow(entity,id,phase,serial,shouldRender,0,"block_model");
        var original=row.render();
        if(original==null)return row;
        var values=new LooseCargoObservationTape.RenderValues(original.shouldRender(),original.cameraPosition(),original.cameraLook(),
                original.cameraRotation(),original.dispatcherCoordinates(),original.dispatcherYaw(),original.partialTick(),
                original.packedLight(),original.actualRenderOffset(),original.renderKind(),original.skipReason(),original.modelPoseMatrix(),
                "DISPATCH_CULL".equals(phase)?dispatchId:null,
                ("DISPATCH_RENDER".equals(phase)||"RENDER_OFFSET".equals(phase)||phase.startsWith("MODEL_"))?dispatchId:null,
                "DISPATCH_RENDER".equals(phase)?linkedCullId:null);
        return copyRender(row,values);
    }
    private static LooseCargoObservationTape.Row validRow(UUID entity,int id,String phase,long serial,
            Boolean shouldRender,double x,String renderKind) {
        var p=new LooseCargoObservationTape.Point(x,1,2);
        LooseCargoObservationTape.RenderValues render=null;
        boolean packet = "RAW_FRAME_PACKET".equals(phase);
        if (!"CLIENT_TICK".equals(phase) && !packet) {
            render=new LooseCargoObservationTape.RenderValues(shouldRender,p,new LooseCargoObservationTape.Point(0,0,1),
                    java.util.List.of(0f,0f,0f,1f),
                    phase.startsWith("DISPATCH_")?new LooseCargoObservationTape.Point(1,2,3):null,
                    phase.equals("DISPATCH_RENDER")?90f:null,.5f,
                    phase.startsWith("DISPATCH_")||phase.startsWith("MODEL_")?7:null,
                    phase.equals("RENDER_OFFSET")?p:null,
                    phase.equals("MODEL_DRAW")?renderKind:null,
                    phase.equals("MODEL_SKIPPED")?"empty-stack":null,
                    phase.equals("DISPATCH_RENDER")||phase.startsWith("MODEL_")?java.util.Collections.nCopies(16,1f):null,
                    phase.equals("DISPATCH_CULL")?1L:null,
                    phase.equals("DISPATCH_RENDER")||phase.equals("RENDER_OFFSET")||phase.startsWith("MODEL_")?1L:null,
                    phase.equals("DISPATCH_RENDER")?1L:null);
        }
        return new LooseCargoObservationTape.Row(entity,id,1,1,10,20,serial,p,
                new LooseCargoObservationTape.Point(0,0,0),new LooseCargoObservationTape.Point(1,2,3),
                phase,false,packet?"WORLD_OR_ALREADY_SUBLEVEL":null,null,packet?p:null,p,p,p,p,p,p,p,entity,p,p,p,false,entity,
                false,true,1,false,p,render);
    }

    private static LooseCargoObservationTape.Row copyRender(LooseCargoObservationTape.Row row,
            LooseCargoObservationTape.RenderValues render) {
        return new LooseCargoObservationTape.Row(row.entityUuid(),row.entityId(),row.captureClientTick(),row.entityTickCount(),
                row.clientWorldGameTime(),row.monotonicNanos(),row.clientRenderFrameSerial(),row.currentEntityWorldPosition(),
                row.entityBoundsMin(),row.entityBoundsMax(),row.phase(),row.actuallyInSubLevel(),row.packetClassification(),
                row.helperFrameId(),row.rawPacketPoint(),row.previousClientWorld(),row.oldXBeforeTick(),row.xoBeforeTick(),
                row.projectedFrameWorld(),row.finalClientWorld(),row.oldXAfterTick(),row.xoAfterTick(),row.committedRenderFrameId(),
                row.storageAnchor(),row.localRenderAnchorPrevious(),row.localRenderAnchorCurrent(),row.frameProjectionPaused(),
                row.nativeTrackingFrameId(),row.clientSettled(),row.clientOnGround(),row.clientStackCount(),row.clientStackEmpty(),
                row.clientVelocity(),render);
    }

    private static void appendValidModelDraw(LooseCargoObservationTape tape, UUID entity,
            LooseCargoObservationTape.Row modelDraw) {
        tape.append(validRow(entity,6,"CLIENT_TICK",1,null));
        tape.append(validRow(entity,6,"DISPATCH_CULL",1,true,1));
        tape.append(validRow(entity,6,"DISPATCH_RENDER",1,null,1,1L));
        tape.append(validRow(entity,6,"RENDER_OFFSET",1,null,1));
        tape.append(modelDraw);
        tape.closeDispatch(1);
    }

    private static LooseCargoObservationTape.Row copyRow(LooseCargoObservationTape.Row row,
            LooseCargoObservationTape.Point previousWorld, LooseCargoObservationTape.Point rawPacket,
            UUID committedFrame, LooseCargoObservationTape.Point storage, LooseCargoObservationTape.Point anchorPrevious,
            LooseCargoObservationTape.Point anchorCurrent, boolean paused, LooseCargoObservationTape.RenderValues render) {
        return new LooseCargoObservationTape.Row(row.entityUuid(),row.entityId(),row.captureClientTick(),row.entityTickCount(),
                row.clientWorldGameTime(),row.monotonicNanos(),row.clientRenderFrameSerial(),row.currentEntityWorldPosition(),
                row.entityBoundsMin(),row.entityBoundsMax(),row.phase(),row.actuallyInSubLevel(),row.packetClassification(),
                row.helperFrameId(),rawPacket,previousWorld,row.oldXBeforeTick(),row.xoBeforeTick(),row.projectedFrameWorld(),
                row.finalClientWorld(),row.oldXAfterTick(),row.xoAfterTick(),committedFrame,storage,anchorPrevious,anchorCurrent,
                paused,row.nativeTrackingFrameId(),row.clientSettled(),row.clientOnGround(),row.clientStackCount(),
                row.clientStackEmpty(),row.clientVelocity(),render);
    }

    private static LooseCargoObservationTape.Row row(UUID entity, int id, long tick) {
        var point = new LooseCargoObservationTape.Point(1.2345678901234567, -2.5, 3.75);
        return new LooseCargoObservationTape.Row(entity, id, tick, (int)tick, tick, tick*10,
                1L,point,point,point,"CLIENT_TICK", false, null, null, null, point, point, point, point, point,
                point, point, entity, point, point, point, false, null, true, false, 3, false, point, null);
    }
}
