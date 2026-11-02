package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.live.BodyHeldItemMutation;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the exact commit delegate used by BodyWorldItemActions after BE preparation. */
class FirelockInstallationCommitTest {
    @Test void successfulHeldRemovalPublishesPreparedAttachmentOnce(){var published=new AtomicInteger();var consumed=new AtomicInteger();
        var result=FirelockInstallationCommit.commit(()->true,()->{consumed.incrementAndGet();return BodyHeldItemMutation.Result.SUCCESS;},published::incrementAndGet);
        assertEquals(BodyHeldItemMutation.Result.SUCCESS,result);assertEquals(1,consumed.get());assertEquals(1,published.get());}
    @Test void rejectedPreparationDoesNotConsumeOrPublish(){var called=new AtomicBoolean();var result=FirelockInstallationCommit.commit(()->false,()->{called.set(true);return BodyHeldItemMutation.Result.SUCCESS;},()->fail("must not publish"));assertEquals(BodyHeldItemMutation.Result.DENIED,result);assertFalse(called.get());}
    @Test void failedHeldMutationNeverLeavesFreeAttachment(){var published=new AtomicBoolean();var result=FirelockInstallationCommit.commit(()->true,()->BodyHeldItemMutation.Result.DENIED,()->published.set(true));assertEquals(BodyHeldItemMutation.Result.DENIED,result);assertFalse(published.get());}
    @Test void stalePreparedTargetIsRejectedBeforeHeldMutation(){var heldCalled=new AtomicBoolean();var current=new AtomicBoolean(false);var result=FirelockInstallationCommit.commit(current::get,()->{heldCalled.set(true);return BodyHeldItemMutation.Result.SUCCESS;},()->fail("stale target must not publish"));assertEquals(BodyHeldItemMutation.Result.DENIED,result);assertFalse(heldCalled.get());}
    @Test void alreadyAttachedPreparationIsRejectedBeforeHeldMutation(){boolean alreadyAttached=true;var heldCalled=new AtomicBoolean();var result=FirelockInstallationCommit.commit(()->!alreadyAttached,()->{heldCalled.set(true);return BodyHeldItemMutation.Result.SUCCESS;},()->fail("existing attachment must not publish"));assertEquals(BodyHeldItemMutation.Result.DENIED,result);assertFalse(heldCalled.get());}
}
