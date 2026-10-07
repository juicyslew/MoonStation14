package com.juicyslew.moonstation14.ms14.disposal.ui.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DisposalPlacementIssueTest {
    @Test void oneInvalidFrameSurvivesNoTargetAndExpiresAtEightyTicks() {
        var issue=new DisposalPlacementIssue(); var body=UUID.randomUUID();
        issue.invalid("blocked",100,body,2);
        assertEquals("blocked",issue.visible(179,body,2));
        assertNull(issue.visible(180,body,2));
    }
    @Test void validFrameDoesNotEraseNoticeButExitSelectionAndBodyChangeDo() {
        var issue=new DisposalPlacementIssue(); var body=UUID.randomUUID();
        issue.invalid("blocked",4,body,7);
        assertEquals("blocked",issue.visible(5,body,7)); // a valid/no-target frame makes no write
        assertNull(issue.visible(5,UUID.randomUUID(),7));
        issue.invalid("blocked",6,body,7); issue.clear();
        assertNull(issue.visible(7,body,7));
    }
}
