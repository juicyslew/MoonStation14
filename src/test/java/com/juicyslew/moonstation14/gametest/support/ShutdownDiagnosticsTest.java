package com.juicyslew.moonstation14.gametest.support;

import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ShutdownDiagnosticsTest {
    @Test void formatterIncludesFramesBeyondThreadInfoToStringDefault() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        Thread thread=new Thread(()->deep(12,entered,release),"shutdown-diagnostics-deep-stack");
        thread.start();
        try {
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var info=ManagementFactory.getThreadMXBean().getThreadInfo(thread.getId(),64);
            assertNotNull(info);
            assertTrue(info.getStackTrace().length>8);
            String formatted=ShutdownDiagnostics.formatThreadInfo(info);
            assertTrue(formatted.contains("frames["+info.getStackTrace().length+"]"));
            assertTrue(formatted.contains(info.getStackTrace()[info.getStackTrace().length-1].toString()));
        } finally { release.countDown();thread.join(2000); }
    }
    private static void deep(int left,CountDownLatch entered,CountDownLatch release) {
        if(left==0){entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return;}
        deep(left-1,entered,release);
    }
}
