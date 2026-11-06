package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded parsed R34 capture. STONE is only a known-state test predicate token, not source identity. */
final class R34NativeTraceFixture {
    static final UUID FRAME = UUID.fromString("854d65e1-bfb6-4173-95c3-3adf931d1e66");
    record Row(int ordinal, NativeCollisionGeometry geometry, MovementVector selected,
               MovementVector applied, MovementVector response, MovementVector before,
               int substep, int iteration, boolean vertical, BlockPos selectedPos,
               CurrentFrameCollisionScope.NativeCollisionTrace trace) { }

    private R34NativeTraceFixture() { }

    static List<Row> rows() {
        try (var stream = Objects.requireNonNull(R34NativeTraceFixture.class.getResourceAsStream(
                     "/com/juicyslew/moonstation14/ms14/shuttle/sable/r34-native-correction-trace.tsv"));
             var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).map(line -> {
                String[] v = line.split("\\|");
                if (v.length != 53) throw new IllegalArgumentException("unexpected R34 fixture column count " + v.length);
                double[] n = new double[v.length];
                for (int i = 0; i < v.length; i++) if (i != 3) n[i] = Double.parseDouble(v[i]);
                boolean vertical = Boolean.parseBoolean(v[3]);
                BlockPos pos = new BlockPos((int)n[4], (int)n[5], (int)n[6]);
                double[] matrix = new double[16];
                System.arraycopy(n, 17, matrix, 0, 16);
                var selected = vector(n,39);
                var before = vector(n,48);
                var applied = vector(n,42);
                var response = vector(n,45);
                var geometry = new NativeCollisionGeometry(vector(n,7), vector(n,10),
                        new NativeCollisionGeometry.QuaternionValue(n[13],n[14],n[15],n[16]),
                        new NativeFrameInterval.PoseValue(matrix), new AABB(n[33],n[34],n[35],n[36],n[37],n[38]),
                        selected, before, (int)n[51], (int)n[52]);
                var trace = new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME, (int)n[1], 10, (int)n[2],
                        pos, Blocks.STONE.defaultBlockState(), "captured steel wall identity not bootstrapped", "test known-state token",
                        selected.x(), selected.y(), selected.z(), before.x(), before.y(), before.z(),
                        applied.x(), applied.y(), applied.z(), response.x(), response.y(), response.z(), geometry, vertical);
                return new Row((int)n[0],geometry,selected,applied,response,before,(int)n[1],(int)n[2],vertical,pos,trace);
            }).toList();
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static MovementVector vector(double[] values, int at) {
        return new MovementVector(values[at], values[at+1], values[at+2]);
    }
}
