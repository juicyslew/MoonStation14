package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeQuarantineStoreGameTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;

/** Discovery entry point; the adjacent store tests retain package-private fault injection. */
@PrefixGameTestTemplate(false)
@GameTestTags({"hands", "lifecycle", "persistence"})
public final class CreativeQuarantineStoreGameTestEntry {
    private CreativeQuarantineStoreGameTestEntry() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void casPhasesAndIdentity(GameTestHelper helper) throws IOException {
        CreativeQuarantineStoreGameTests.casPhasesAndIdentity(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void invalidEvidenceAndWriterLock(GameTestHelper helper) throws IOException {
        CreativeQuarantineStoreGameTests.invalidEvidenceAndWriterLock(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void injectedFaultsFailClosedAcrossRestart(GameTestHelper helper) throws IOException {
        CreativeQuarantineStoreGameTests.injectedFaultsFailClosedAcrossRestart(helper);
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty")
    public static void boundPathsAndUnsafeRoots(GameTestHelper helper) throws IOException {
        CreativeQuarantineStoreGameTests.boundPathsAndUnsafeRoots(helper);
    }
}
