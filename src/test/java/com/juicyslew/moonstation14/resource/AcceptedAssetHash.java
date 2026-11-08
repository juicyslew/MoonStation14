package com.juicyslew.moonstation14.resource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.HexFormat;

/** Test-only hash helpers for accepted source assets. */
public final class AcceptedAssetHash {
    private static final Set<String> LINE_ENDING_TEXT_EXTENSIONS = Set.of(".json", ".mcmeta", ".obj", ".mtl");

    private AcceptedAssetHash() {}

    public static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static boolean matchesAcceptedHash(byte[] bytes, String path, String expected) throws Exception {
        if (sha256(bytes).equalsIgnoreCase(expected)) return true;
        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (LINE_ENDING_TEXT_EXTENSIONS.stream().noneMatch(normalizedPath::endsWith)) return false;
        String text = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n", "\r\n");
        return sha256(text.getBytes(StandardCharsets.UTF_8)).equalsIgnoreCase(expected);
    }
}
