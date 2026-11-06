package com.juicyslew.moonstation14.ms14.hands.live;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.*;

class CargoMotionRecorderTest {
    private static final String FRAME="00000000-0000-0000-0000-000000000001";
    @Test void sourceWiresDiagnosticCadenceAndKeepsOriginalEightSamplePath() throws Exception {
        String source = readSource("BodyHandReleaseNativeGameTests.java");
        assertTrue(source.contains("walkingBodyPlacesJugAtRestOnFastSpinningNativeFloor"));
        assertTrue(source.contains("mode == Mode.ORIGINAL ? 8 : 160"));
        assertEquals(2, occurrences(source, "@GameTestTags({\"cargo-placement-motion-cause-diagnostic\"})"));
        assertTrue(source.indexOf("ShuttleFixtureMotionService.start") < source.indexOf("observeServerTicks"));
        assertTrue(source.contains("unregisterFixture(fixture.level(), controlled)"));
        assertTrue(source.contains("recorder.record(0,"));
    }

    @Test void recorderSourceHasHardCapCadenceAndDumpOnceGuards() throws Exception {
        String source = readSource("CargoMotionRecorder.java");
        assertTrue(source.contains("rows.size() >= 161"));
        assertTrue(source.contains("row.tick()!=rows.get(rows.size()-1).tick()+1"));
        assertTrue(source.contains("if (dumped) return; dumped=true"));
        assertTrue(source.contains("xyzPeakToPeak"));
        assertTrue(source.contains("Double.isFinite"));
    }

    @Test void boundedRowsSummarizeXYZAndRejectCoverageGapsAndDumpOnlyOnce() {
        CargoMotionRecorder recorder=new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
        List<CargoMotionRecorder.Row> values=new ArrayList<>();
        for(int i=0;i<161;i++) {
            Vec3 p=new Vec3(i*.01, i*.02, -i*.03);
            var row=validRow(i,i+100,p);
            values.add(row); recorder.append(row);
        }
        assertTrue(recorder.complete());
        var summary=CargoMotionRecorder.summarize(values);
        assertEquals(new Vec3(0,0,-4.8), summary.min());
        assertEquals(new Vec3(1.6,3.2,0), summary.max());
        assertEquals(new Vec3(1.6,3.2,-4.8), summary.totalDrift());
        assertEquals(.01,summary.maxAbsStep().x,1e-12);
        assertEquals(.02,summary.maxAbsStep().y,1e-12);
        assertEquals(.03,summary.maxAbsStep().z,1e-12);
        var overflow=new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
        for(int i=0;i<162;i++) overflow.append(values.get(Math.min(i,160)));
        assertFalse(overflow.complete());
        var gap=new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
        gap.append(values.get(0)); gap.append(values.get(2));
        assertFalse(gap.complete());
        var bytes=new java.io.ByteArrayOutputStream(); var original=System.out;
        try {
            System.setOut(new java.io.PrintStream(bytes));
            overflow.dump("first"); overflow.dump("second");
        } finally { System.setOut(original); }
        assertEquals(1,bytes.toString().split("CARGO_SERVER_LOCAL_SUPPORT",-1).length-1);
        String precise="(1.2345678901234567,0.0,-0.0)";
        assertEquals(precise,CargoMotionRecorder.vec(new Vec3(1.2345678901234567,0.0,-0.0)));

        for (CargoMotionRecorder.Row bad : List.of(
                new CargoMotionRecorder.Row(0,1,"id",FRAME,Vec3.ZERO,null,true,Vec3.ZERO,null,null,null,null,null,null,null,null,Vec3.ZERO,Vec3.ZERO,0,0,0,1,null),
                new CargoMotionRecorder.Row(0,1,"id",FRAME,new Vec3(Double.NaN,0,0),Vec3.ZERO,true,Vec3.ZERO,null,null,null,null,null,null,null,null,Vec3.ZERO,Vec3.ZERO,0,0,0,1,null),
                new CargoMotionRecorder.Row(0,1,"id",FRAME,Vec3.ZERO,Vec3.ZERO,true,new Vec3(Double.POSITIVE_INFINITY,0,0),null,null,null,null,null,null,null,null,Vec3.ZERO,Vec3.ZERO,0,0,0,1,null),
                new CargoMotionRecorder.Row(0,1,"id",FRAME,Vec3.ZERO,Vec3.ZERO,true,Vec3.ZERO,null,null,null,null,null,null,null,null,Vec3.ZERO,Vec3.ZERO,Double.POSITIVE_INFINITY,0,0,1,null))) {
            var invalid = new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
            for (int i=0;i<161;i++) invalid.append(i==0?bad:validRow(i,i+1,Vec3.ZERO));
            assertFalse(invalid.complete(),"invalid required measurement must fail coverage");
        }
        var missingMove=new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
        missingMove.append(validRow(0,1,Vec3.ZERO));
        missingMove.append(new CargoMotionRecorder.Row(1,2,"id",FRAME,Vec3.ZERO,Vec3.ZERO,true,Vec3.ZERO,
                null,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,FRAME,FRAME,Vec3.ZERO,Vec3.ZERO,0,0,0,1,null));
        for(int i=2;i<161;i++) missingMove.append(validRow(i,i+1,Vec3.ZERO));
        assertFalse(missingMove.complete(),"later native capture fields are required");
        var preciseRecorder=new CargoMotionRecorder(BodyHandReleaseNativeGameTests.Mode.MOVING);
        preciseRecorder.append(validRow(0,1,new Vec3(1.2345678901234567,0,0)));
        var preciseLog=new java.io.ByteArrayOutputStream(); var oldOut=System.out;
        try { System.setOut(new java.io.PrintStream(preciseLog)); preciseRecorder.dump("precision"); }
        finally { System.setOut(oldOut); }
        assertTrue(preciseLog.toString().contains("1.2345678901234567"));
    }

    private static CargoMotionRecorder.Row validRow(int sample,long tick,Vec3 p) {
        boolean release=sample==0;
        return new CargoMotionRecorder.Row(sample,tick,"item",FRAME,p,p,true,Vec3.ZERO,
                release?null:Vec3.ZERO,release?null:Vec3.ZERO,release?null:Vec3.ZERO,release?null:Vec3.ZERO,
                release?null:Vec3.ZERO,release?null:Vec3.ZERO,release?null:FRAME,release?null:FRAME,
                Vec3.ZERO,Vec3.ZERO,0,0,0,1,null);
    }

    private static int occurrences(String value, String needle) {
        int count=0, from=0; while ((from=value.indexOf(needle,from))>=0) { count++; from+=needle.length(); } return count;
    }
    private static String readSource(String name) throws Exception {
        Path path=Path.of(System.getProperty("user.dir"));
        for (int i=0;i<8 && path!=null;i++,path=path.getParent()) {
            Path candidate=path.resolve("src/gametest/java/com/juicyslew/moonstation14/ms14/hands/live").resolve(name);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
        }
        throw new AssertionError("Could not locate GameTest source: "+name);
    }
}
