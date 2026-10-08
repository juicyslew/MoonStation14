package com.juicyslew.moonstation14.ms14.examine.client;

import com.juicyslew.moonstation14.ms14.examine.ExamineTarget;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExamineLatchSessionTest {
    private final ExamineLatchSession session = new ExamineLatchSession();
    private final UUID actor = UUID.randomUUID();
    private final ExamineTarget a = ExamineTarget.block(new BlockPos(1, 2, 3));
    private final ExamineTarget b = ExamineTarget.block(new BlockPos(4, 5, 6));

    @Test void previewAndShiftReleaseDoNotChangeClickedTargetAndRefreshKeepsIt() {
        session.begin(a, actor, 7, 1); // Shift-click A; preview movement/cycling is intentionally not a session operation.
        session.refreshed(2);
        assertTrue(session.accepts(a, actor, 7, 2));
        assertFalse(session.accepts(b, actor, 7, 2));
    }

    @Test void dismissDropsDelayedReplyAndNewClickOrAuthorityEpochRejectsOldReply() {
        session.begin(a, actor, 7, 1);
        session.dismiss();
        assertFalse(session.accepts(a, actor, 7, 1));
        assertNull(session.target());
        session.begin(b, actor, 7, 2);
        assertFalse(session.accepts(a, actor, 7, 1));
        assertFalse(session.accepts(b, actor, 8, 2));
        assertTrue(session.accepts(b, actor, 7, 2));
    }

    @Test void rapidExplicitTargetReplacementRejectsDelayedReplyForPriorTarget() {
        session.begin(a, actor, 7, 11);
        session.begin(b, actor, 7, 12);
        assertFalse(session.accepts(a, actor, 7, 11));
        assertFalse(session.accepts(b, actor, 7, 11));
        assertTrue(session.accepts(b, actor, 7, 12));
    }
}
