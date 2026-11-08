package com.juicyslew.moonstation14.resource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PipeInterfaceInternalCapsImportTest {
    private static final String RESOURCE_ROOT = "/assets/moonstation14/models/block/atmos_interfaces/";
    private static final String RECEIPT = "/import-receipts/atmos_pipe_interfaces_internal_caps_r2.json";
    private static final String[] FACINGS = {"north", "east", "south", "west"};

    @Test void receiptBindsOnlyCorrectedObjsAndUnchangedDependencies() throws Exception {
        JsonObject receipt = JsonParser.parseReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(RECEIPT)), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("atmos-pipe-interface-internal-caps-20261101-r2", receipt.get("request_id").getAsString());
        assertEquals("d1c5b45e15ebdb4cbe5907f62ca5af505903ab2acf2e9ef1d0cd6fbc4a428448",
                receipt.get("delivery_digest").getAsString());

        int copied = 0;
        int unchanged = 0;
        Set<String> manualValveObjs = new HashSet<>();
        for (var entry : receipt.getAsJsonArray("files")) {
            JsonObject file = entry.getAsJsonObject();
            String destination = file.get("destination").getAsString();
            String action = file.get("action").getAsString();
            String oldHash = file.get("expected_existing_sha256").getAsString();
            String newHash = file.get("new_sha256").getAsString();
            if (action.equals("copy")) {
                copied++;
                assertTrue(destination.endsWith(".obj"), "Only OBJ files may be replaced: " + destination);
                assertNotEquals(oldHash, newHash, destination);
            } else {
                assertEquals("unchanged", action, destination);
                unchanged++;
                assertEquals(oldHash, newHash, "Receipt says unchanged bytes differ: " + destination);
            }
            String classpathPath = "/" + destination.substring("src/main/resources/".length());
            try (var stream = getClass().getResourceAsStream(classpathPath)) {
                assertNotNull(stream, "Missing imported resource " + classpathPath);
                assertEquals(newHash, sha256(stream.readAllBytes()), "Imported resource hash differs from receipt: " + destination);
            }
            if (destination.contains("/gas_valve/") && destination.endsWith(".obj")
                    && (destination.contains("pumpmanualvalve_l") || destination.contains("pumpmanualvalveon_l"))) {
                manualValveObjs.add(destination.substring(destination.indexOf("models/block/")));
            }
        }
        assertEquals(132, copied, "Only the 132 corrected OBJ resources should be copied");
        assertEquals(903, unchanged, "All remaining runtime dependencies should remain unchanged");
        assertEquals(24, manualValveObjs.size(), "Manual valve coverage: two states × three layers × four facings");
    }

    @Test void importedManualValveHasNoProvenInternalCoincidentCaps() throws Exception {
        for (String state : new String[]{"pumpmanualvalve", "pumpmanualvalveon"}) {
            for (int layer = 0; layer < 3; layer++) for (String facing : FACINGS) {
                String path = RESOURCE_ROOT + "gas_valve/" + state + "_l" + layer + "_" + facing + ".obj";
                try (var stream = getClass().getResourceAsStream(path)) {
                    assertNotNull(stream, "Missing " + path);
                    assertTrue(internalContactPairs(stream.readAllBytes()).isEmpty(), path);
                }
            }
        }
    }

    private record Vec(double x, double y, double z) implements Comparable<Vec> {
        @Override public int compareTo(Vec other) {
            int c = Double.compare(x, other.x); if (c != 0) return c;
            c = Double.compare(y, other.y); return c != 0 ? c : Double.compare(z, other.z);
        }
        double axis(int i) { return i == 0 ? x : i == 1 ? y : z; }
    }
    private record Face(String group, List<Vec> polygon, Vec normal) { }
    private record Bounds(Vec low, Vec high) { }

    private static Set<String> internalContactPairs(byte[] data) {
        Map<String, Set<Vec>> groupVertices = new HashMap<>();
        Map<String, List<Face>> groupFaces = new HashMap<>();
        List<Vec> vertices = new ArrayList<>();
        List<Vec> normals = new ArrayList<>();
        String group = "";
        for (String line : new String(data, StandardCharsets.UTF_8).split("\\R")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 0 || parts[0].isEmpty()) continue;
            switch (parts[0]) {
                case "v" -> vertices.add(new Vec(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                case "vn" -> normals.add(new Vec(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
                case "g" -> {
                    group = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
                    groupVertices.computeIfAbsent(group, ignored -> new HashSet<>());
                    groupFaces.computeIfAbsent(group, ignored -> new ArrayList<>());
                }
                case "f" -> {
                    List<Vec> polygon = new ArrayList<>();
                    Vec normal = null;
                    for (int i = 1; i < parts.length; i++) {
                        String[] ref = parts[i].split("/");
                        Vec vertex = vertices.get(Integer.parseInt(ref[0]) - 1);
                        polygon.add(vertex);
                        groupVertices.get(group).add(vertex);
                        if (ref.length > 2 && !ref[2].isEmpty()) normal = normals.get(Integer.parseInt(ref[2]) - 1);
                    }
                    polygon.sort(Comparator.naturalOrder());
                    groupFaces.get(group).add(new Face(group, List.copyOf(polygon), normal));
                }
                default -> { }
            }
        }

        Map<String, Bounds> cuboids = new HashMap<>();
        for (String name : groupFaces.keySet()) {
            List<Vec> points = new ArrayList<>(groupVertices.get(name));
            List<Face> faces = groupFaces.get(name);
            if (points.size() < 8 || faces.size() != 6 || faces.stream().anyMatch(f -> f.polygon().size() != 4)) continue;
            Vec low = new Vec(points.stream().mapToDouble(Vec::x).min().orElseThrow(), points.stream().mapToDouble(Vec::y).min().orElseThrow(), points.stream().mapToDouble(Vec::z).min().orElseThrow());
            Vec high = new Vec(points.stream().mapToDouble(Vec::x).max().orElseThrow(), points.stream().mapToDouble(Vec::y).max().orElseThrow(), points.stream().mapToDouble(Vec::z).max().orElseThrow());
            Set<Vec> corners = new HashSet<>();
            for (double x : new double[]{low.x(), high.x()}) for (double y : new double[]{low.y(), high.y()}) for (double z : new double[]{low.z(), high.z()}) corners.add(new Vec(x,y,z));
            Set<String> planes = new HashSet<>();
            for (Face face : faces) for (int axis = 0; axis < 3; axis++) {
                int planeAxis = axis;
                double value = face.polygon().getFirst().axis(axis);
                if (face.polygon().stream().allMatch(v -> v.axis(planeAxis) == value)) planes.add(axis + ":" + value);
            }
            if (points.size() == 8 && new HashSet<>(points).equals(corners) && planes.size() == 6) cuboids.put(name, new Bounds(low, high));
        }

        Map<List<Vec>, List<Face>> byPolygon = new HashMap<>();
        for (List<Face> faces : groupFaces.values()) for (Face face : faces) if (face.normal() != null && face.polygon().size() == 4)
            byPolygon.computeIfAbsent(face.polygon(), ignored -> new ArrayList<>()).add(face);
        Set<String> contacts = new HashSet<>();
        for (List<Face> faces : byPolygon.values()) for (int i = 0; i < faces.size(); i++) for (int j = i + 1; j < faces.size(); j++) {
            Face a = faces.get(i), b = faces.get(j);
            if (a.group().equals(b.group()) || !opposites(a.normal(), b.normal())) continue;
            Bounds ba = cuboids.get(a.group()), bb = cuboids.get(b.group());
            if (ba == null || bb == null) continue;
            for (int axis = 0; axis < 3; axis++) {
                int planeAxis = axis;
                double plane = a.polygon().getFirst().axis(axis);
                if (!a.polygon().stream().allMatch(v -> v.axis(planeAxis) == plane)) continue;
                int sa = side(ba, axis, plane), sb = side(bb, axis, plane);
                if (sa != 0 && sb == -sa) contacts.add(a.group() + "/" + b.group() + "/" + axis + "/" + plane);
            }
        }
        return contacts;
    }

    private static int side(Bounds b, int axis, double plane) {
        double lo = b.low().axis(axis), hi = b.high().axis(axis);
        if (hi == plane && lo < plane) return -1;
        if (lo == plane && hi > plane) return 1;
        return 0;
    }
    private static boolean opposites(Vec a, Vec b) { return a.x() == -b.x() && a.y() == -b.y() && a.z() == -b.z(); }
    private static String sha256(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
