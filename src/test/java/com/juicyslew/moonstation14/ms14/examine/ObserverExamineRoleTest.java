package com.juicyslew.moonstation14.ms14.examine;

import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent;
import com.juicyslew.moonstation14.ms14.medical_observer.ObserverSession;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObserverExamineRoleTest {
    @Test void corpseModeUsesResolvedCorpseExaminerWhileGhostModeUsesResolvedObserverExaminer() {
        var human = new CharacterData(List.of(), List.of(ExaminerComponent.DEFAULT));
        var observerExaminer = new ExaminerComponent(true, true);

        ExamineAuthority.ObserverRole corpse = ExamineAuthority.observerRole(
                ObserverSession.Mode.CORPSE, human, observerExaminer);
        assertNotNull(corpse);
        assertFalse(corpse.ghost());
        assertEquals(ExaminerComponent.DEFAULT, corpse.examiner());
        var corpsePolicy = new ExaminationPolicy.Examiner(corpse.examiner().skipChecks(),
                corpse.examiner().checkInRangeUnOccluded());
        assertEquals(ExaminationPolicy.DEAD_RANGE, ExaminationPolicy.range(corpsePolicy,
                ExaminationPolicy.State.DEAD, false, 0));
        assertFalse(ExaminationPolicy.detailsAllowed(corpsePolicy, ExaminationPolicy.State.DEAD,
                corpse.ghost(), .5, true, true));

        ExamineAuthority.ObserverRole ghost = ExamineAuthority.observerRole(
                ObserverSession.Mode.GHOST, human, observerExaminer);
        assertNotNull(ghost);
        assertTrue(ghost.ghost());
        assertEquals(observerExaminer, ghost.examiner());
        var ghostPolicy = new ExaminationPolicy.Examiner(ghost.examiner().skipChecks(),
                ghost.examiner().checkInRangeUnOccluded());
        assertTrue(ExaminationPolicy.detailsAllowed(ghostPolicy, ExaminationPolicy.State.DEAD,
                ghost.ghost(), 100, false, false));
    }

    @Test void corpseModeFailsClosedWithoutResolvedBodyExaminer() {
        assertNull(ExamineAuthority.observerRole(ObserverSession.Mode.CORPSE,
                new CharacterData(), new ExaminerComponent(true, true)));
        assertNull(ExamineAuthority.observerRole(ObserverSession.Mode.GHOST,
                new CharacterData(), null));
    }
}
