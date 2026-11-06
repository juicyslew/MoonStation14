package com.juicyslew.moonstation14.ms14.shuttle.sable;

import org.joml.Quaterniondc;
import org.joml.Vector3d;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;

/** Test-only deterministic plan/state for the bounded native corner neighborhood driver. */
public final class CornerCauseDiagnosticPlan {
    public static final int MAX_TICKS = 80;
    private CornerCauseDiagnosticPlan() { }
    public record Input(boolean jump, int wishX, int wishZ) { }
    public static Input input(boolean grounded, int groundedApproachTicks, boolean jumpAlreadyAttempted) {
        boolean jump = !jumpAlreadyAttempted && grounded && groundedApproachTicks >= 8;
        return new Input(jump, 0, 1000);
    }

    /** Runtime input yaw for local-forward diagonal approach, normalized to the motor's accepted range. */
    public static float inputYawForDeck(Quaterniondc frameRotation) {
        Vector3d localXAxis = frameRotation.transform(new Vector3d(1, 0, 0));
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(localXAxis.z, localXAxis.x)) - 45f);
    }
    public static boolean supportContextValid(boolean groundedBefore, boolean actorPointPresent,
                                              UUID fixtureFrame, UUID beforeFrame,
                                              UUID frozenAirReferenceFrame) {
        if (!actorPointPresent || fixtureFrame == null) return false;
        if (groundedBefore) return fixtureFrame.equals(beforeFrame);
        return (beforeFrame == null || fixtureFrame.equals(beforeFrame))
                && (frozenAirReferenceFrame == null || fixtureFrame.equals(frozenAirReferenceFrame));
    }

    /** Input yaw follows the deck while grounded and is frozen from the actual departure interval through air. */
    public static final class LookLatch {
        private Float frozenBaseInputYaw;
        public float select(boolean grounded, float currentDeckLook) {
            if (grounded) { frozenBaseInputYaw = null; return currentDeckLook; }
            if (frozenBaseInputYaw == null) frozenBaseInputYaw = currentDeckLook;
            return frozenBaseInputYaw;
        }
        public void latchDeparture(float actualDepartureLook) {
            if (!Float.isFinite(actualDepartureLook)) throw new IllegalArgumentException("departure look must be finite");
            frozenBaseInputYaw = actualDepartureLook;
        }
        public Float frozenBaseInputYaw() { return frozenBaseInputYaw; }
    }

    public record Contacts(int sideRows, boolean compound) { }
    public static boolean pureAirCompound(boolean sameMoveCompound, boolean groundedAfter,
                                          NativeContactProofDiagnostic.Branch branch) {
        return sameMoveCompound && !groundedAfter
                && branch == NativeContactProofDiagnostic.Branch.PURE_SIDE;
    }
    private record SideRow(String face, BlockPos pos, Vector3d normal, int rowIndex) { }
    /** Coverage-only classification from recorded native rows; it does not authorize or alter collision. */
    public static Contacts classify(List<CurrentFrameCollisionScope.NativeCollisionTrace> rows, UUID frame,
                                    int storageX, int storageZ) {
        if (rows == null) return new Contacts(0, false);
        int sideCount = 0;
        java.util.ArrayList<SideRow> sideRows = new java.util.ArrayList<>();
        java.util.HashSet<String> faces = new java.util.HashSet<>();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            var row = rows.get(rowIndex);
            var geometry = row.geometry();
            if (!frame.equals(row.frameId()) || geometry == null || row.selectedPos() == null) continue;
            int x = row.selectedPos().getX(), z = row.selectedPos().getZ();
            boolean xFace = x == storageX + 19 && z != storageZ + 12;
            boolean zFace = z == storageZ + 12 && x != storageX + 19;
            if (!xFace && !zFace) continue;
            var mtv = geometry.maxMtv();
            double horizontal = Math.hypot(mtv.x(), mtv.z());
            if (horizontal < 1e-8 || Math.abs(mtv.y()) > horizontal * .5) continue;
            String face = xFace ? "x" : "z";
            String uniqueRow = face + ":" + row.selectedPos() + ":" + row.substep() + ":" + row.iteration();
            if (!faces.add(uniqueRow)) continue;
            sideCount++;
            sideRows.add(new SideRow(face, row.selectedPos(),
                    new Vector3d(mtv.x() / horizontal, 0, mtv.z() / horizontal), rowIndex));
        }
        for (int i = 0; i < sideRows.size(); i++) for (int j = i + 1; j < sideRows.size(); j++) {
            SideRow a = sideRows.get(i), b = sideRows.get(j);
            if (a.rowIndex() != b.rowIndex() && !a.pos().equals(b.pos()) && !a.face().equals(b.face())
                    && Math.abs(a.normal().dot(b.normal())) < .7) return new Contacts(sideCount, true);
        }
        return new Contacts(sideCount, false);
    }
}
