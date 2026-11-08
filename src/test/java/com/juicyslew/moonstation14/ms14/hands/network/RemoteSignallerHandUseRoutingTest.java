package com.juicyslew.moonstation14.ms14.hands.network;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source-wiring regression checks for routes whose MC actor/UI boundary is not headless-testable. */
class RemoteSignallerHandUseRoutingTest {
    @Test void serverRoutesNetworkSignallersBeforeMedicalCatalogAdmission() throws IOException {
        String source = readSource("src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandRequestService.java");
        String apply = method(source, "public static BodyHandActionResult apply(");
        int action = apply.indexOf("request.action() == BodyHandActionRequest.Action.USE_IN_HAND");
        int signaller = apply.indexOf("ModNetworkDeviceItems.isSignaller(stack)", action);
        int activate = apply.indexOf("RemoteSignallerServer.activate(", signaller);
        int medicalTry = apply.indexOf("try {", signaller);
        int medicalRequire = apply.indexOf("ModMedicalItems.require(", signaller);
        assertTrue(action >= 0 && signaller > action);
        assertTrue(activate > signaller && activate < medicalTry,
                "signaller activation must be dispatched outside the medical exception-swallowing try");
        assertTrue(medicalRequire > medicalTry);
        assertTrue(apply.substring(signaller, medicalTry).contains("ModNetworkDeviceItems.isSignaller(stack)"),
                "classification must use the network-device profile predicate, not an item-name special case");
    }

    @Test void popupUseInHandFallsBackToTheSameHandActionForNonmedicalItems() throws IOException {
        String source = readSource("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        String popup = method(source, "static boolean popupUseInHand(");
        assertTrue(popup.contains("var active=view.active()"),
                "popup dispatch must use the resolved active hand");
        assertTrue(popup.contains("HeldUseRequestDispatch.routeActivation("),
                "popup Z dispatch must reuse shared storage/quick-equip/held-use routing");
        String ordinaryPopupUse = method(source, "private static boolean ordinaryPopupHeldUse(");
        assertTrue(ordinaryPopupUse.contains("var kind=medicalKind(active.itemId())"));
        assertTrue(ordinaryPopupUse.contains("else request(BodyHandActionRequest.Action.USE_IN_HAND);"),
                "items without a medical gesture specialization still dispatch USE_IN_HAND");
        assertTrue(popup.contains("if(!USE_IN_HAND.matches(key,scan)"), "preserve key and modifier guard");
    }

    @Test void emptyEAndOccupiedTargetUseShareExactNominatedRemoteAdmissionAndActivationCore() throws IOException {
        String requestService = readSource("src/main/java/com/juicyslew/moonstation14/ms14/hands/network/BodyHandRequestService.java");
        String probe = method(requestService, "private static com.juicyslew.moonstation14.entities.LooseItemEntity nominatedLoose(");
        String compactProbe = probe.replaceAll("\\s+", "");
        assertTrue(compactProbe.contains("BodyItemTargetProbe.probeSelected"));
        assertTrue(probe.contains("request.pickupTarget()"));
        assertFalse(probe.contains("nearestVisible"));
        String apply = method(requestService, "public static BodyHandActionResult apply(");
        String compactApply = apply.replaceAll("\\s+", "");
        assertTrue(compactApply.contains("Action.ACTIVATE_TARGET"));
        assertTrue(compactApply.contains("Action.USE_TARGET"));
        assertTrue(compactApply.contains("ModNetworkDeviceItems.isSignaller(looseTarget.getItem())"));
        assertTrue(compactApply.contains("RemoteSignallerServer.activateLoose("));

        String remote = readSource("src/main/java/com/juicyslew/moonstation14/ms14/devicenet/item/RemoteSignallerServer.java");
        String held = method(remote, "public static BodyHandActionResult.Reason activate(");
        String loose = method(remote, "public static BodyHandActionResult.Reason activateLoose(");
        assertTrue(held.contains("activatePrepared(level, policy, state, source)"));
        assertTrue(loose.contains("activatePrepared(level, policy, state, source)"));
    }

    @Test void ordinaryClientWorldUseClassifiesRemoteThroughClientPrototypeCatalog() throws IOException {
        String source = readSource("src/main/java/com/juicyslew/moonstation14/ms14/hands/client/BodyHandClient.java");
        String ordinaryUse = method(source, "private static void ordinaryWorldUse()");
        assertTrue(ordinaryUse.contains("ModNetworkDeviceItems.resolve(loose.getItem(), true)"),
                "dedicated client routing must resolve the client catalog, never the server catalog");
        assertTrue(ordinaryUse.contains("profile.signallerPort().isPresent()"));
        assertTrue(ordinaryUse.contains("Action.ACTIVATE_TARGET"));
    }

    private static String readSource(String relative) throws IOException {
        Path path = Path.of(relative);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve(path))) root = root.getParent();
        assertNotNull(root, "production source must be available for routing audit: " + relative);
        return Files.readString(root.resolve(path));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing production method " + signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        fail("unterminated production method " + signature);
        return "";
    }
}
