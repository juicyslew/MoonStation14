import net.jpountz.lz4.LZ4FrameOutputStream;
import net.jpountz.lz4.LZ4FrameInputStream;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Replaces exactly the x86_64 Windows native in Sable's nested native resource bundle. */
public final class NativeArtifact {
  static final String DLL="sable_rapier_x86_64_windows.dll", RESOURCE="natives/sable_rapier/sable_rapier_binaries.zip.l4z";
  static byte[] read(InputStream in) throws IOException { return in.readAllBytes(); }
  static byte[] sha(byte[] b) { try { return MessageDigest.getInstance("SHA-256").digest(b); } catch(NoSuchAlgorithmException e) { throw new AssertionError(e); } }
  static String hex(byte[] b) { return HexFormat.of().formatHex(b); }
  static LinkedHashMap<String,byte[]> entries(byte[] data) throws IOException {
    var map=new LinkedHashMap<String,byte[]>(); var z=new ZipInputStream(new ByteArrayInputStream(data)); ZipEntry e;
    while((e=z.getNextEntry())!=null) { if(e.isDirectory()) continue; if(map.putIfAbsent(e.getName(),read(z))!=null) throw new IOException("duplicate ZIP entry: "+e.getName()); }
    return map;
  }
  static byte[] zip(Map<String,byte[]> entries) throws IOException {
    var out=new ByteArrayOutputStream(); var z=new ZipOutputStream(out);
    for(var e:entries.entrySet()) { var ze=new ZipEntry(e.getKey()); ze.setTime(315532800000L); ze.setMethod(ZipEntry.STORED); ze.setSize(e.getValue().length); var crc=new CRC32(); crc.update(e.getValue()); ze.setCrc(crc.getValue()); z.putNextEntry(ze); z.write(e.getValue()); z.closeEntry(); }
    z.close(); return out.toByteArray();
  }
  static void validatePe(byte[] b) throws IOException {
    if(b.length<256 || b[0]!='M' || b[1]!='Z') throw new IOException("not a PE image");
    long off=Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(b,0x3c,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt());
    if(off<64 || off+6>b.length || b[(int)off]!='P' || b[(int)off+1]!='E' || b[(int)off+2]!=0 || b[(int)off+3]!=0 || Short.toUnsignedInt(java.nio.ByteBuffer.wrap(b,(int)off+4,2).order(java.nio.ByteOrder.LITTLE_ENDIAN).getShort())!=0x8664) throw new IOException("not AMD64 Windows PE");
  }
  static byte[] lz4(byte[] in) throws IOException { var o=new ByteArrayOutputStream(); try(var l=new LZ4FrameOutputStream(o,LZ4FrameOutputStream.BLOCKSIZE.SIZE_4MB,in.length,LZ4FrameOutputStream.FLG.Bits.BLOCK_INDEPENDENCE)){l.write(in);} return o.toByteArray(); }
  public static void main(String[] a) throws Exception {
    if(a.length==2 && a[0].equals("--inspect")) { inspect(Files.readAllBytes(Path.of(a[1]))); return; }
    if(a.length==6 && a[0].equals("--verify")) { verifyCandidate(Path.of(a[1]),Path.of(a[2]),Path.of(a[3]),Path.of(a[4]),Path.of(a[5])); return; }
    if(a.length!=8) throw new IllegalArgumentException("outer.jar dll output.jar provenance.json receipt.json unit-passed unit-failed unit-filtered");
    Path outer=Path.of(a[0]).toRealPath(), dll=Path.of(a[1]).toRealPath(), output=Path.of(a[2]).toAbsolutePath().normalize(), provenance=Path.of(a[3]).toAbsolutePath().normalize(), receipt=Path.of(a[4]).toRealPath();
    if(output.equals(outer)||output.equals(dll)||output.equals(receipt)||provenance.equals(outer)||provenance.equals(dll)||provenance.equals(receipt)||output.equals(provenance)) throw new IOException("output paths must not overwrite any input or each other");
    byte[] outerBytes=Files.readAllBytes(outer), dllBytes=Files.readAllBytes(dll); validatePe(dllBytes);
    byte[] packaged=pack(outerBytes,dllBytes); Files.createDirectories(output.getParent()); Files.createDirectories(provenance.getParent()); Files.write(output,packaged);
    var outerEntries=entries(outerBytes); var packagedEntries=entries(packaged);
    var before=new TreeMap<String,String>(); outerEntries.forEach((k,v)->before.put(k,hex(sha(v))));
    var after=new TreeMap<String,String>(); packagedEntries.forEach((k,v)->after.put(k,hex(sha(v))));
    String nestedName=outerEntries.keySet().stream().filter(k->k.startsWith("META-INF/jarjar/")&&k.endsWith("sable-sable_rapier-1.21.1-2.0.6.jar")).findFirst().orElseThrow();
    var beforeInner=entries(outerEntries.get(nestedName)); var afterInner=entries(packagedEntries.get(nestedName));
    var beforeNative=entries(new LZ4FrameInputStream(new ByteArrayInputStream(beforeInner.get(RESOURCE))).readAllBytes()); var afterNative=entries(new LZ4FrameInputStream(new ByteArrayInputStream(afterInner.get(RESOURCE))).readAllBytes());
    var nativeBefore=new TreeMap<String,String>(); beforeNative.forEach((k,v)->nativeBefore.put(k,hex(sha(v)))); var nativeAfter=new TreeMap<String,String>(); afterNative.forEach((k,v)->nativeAfter.put(k,hex(sha(v))));
    var innerPayloads=new TreeMap<String,String>(); beforeInner.forEach((k,v)->innerPayloads.put(k,hex(sha(v))));
    var innerAfter=new TreeMap<String,String>(); afterInner.forEach((k,v)->innerAfter.put(k,hex(sha(v))));
    var record=new LinkedHashMap<String,Object>(); record.put("scope","Windows x64 only; other five official native payloads unchanged"); record.put("target_native",DLL); record.put("outer_jar_sha256",hex(sha(packaged))); record.put("official_outer_jar_sha256",hex(sha(outerBytes))); record.put("nested_jar_sha256_before",hex(sha(outerEntries.get(nestedName)))); record.put("nested_jar_sha256_after",hex(sha(packagedEntries.get(nestedName)))); record.put("native_bundle_sha256_before",hex(sha(beforeInner.get(RESOURCE)))); record.put("native_bundle_sha256_after",hex(sha(afterInner.get(RESOURCE)))); record.put("windows_dll_sha256",hex(sha(dllBytes))); record.put("build_receipt_sha256",hex(sha(Files.readAllBytes(receipt)))); record.put("unit_passed",Integer.parseInt(a[5])); record.put("unit_failed",Integer.parseInt(a[6])); record.put("unit_filtered",Integer.parseInt(a[7])); record.put("outer_payloads_before",before); record.put("outer_payloads_after",after); record.put("inner_payloads_before",innerPayloads); record.put("inner_payloads_after",innerAfter); record.put("native_payloads_before",nativeBefore); record.put("native_payloads_after",nativeAfter);
    Path source=Path.of("tooling/native-physics/NativeArtifact.java"); record.put("pack_helper_sha256",hex(sha(Files.readAllBytes(source))));
    String json=toJson(record); Files.writeString(provenance,json+"\n"); System.out.println("packaged="+output+" outerSha256="+hex(sha(packaged))+" receiptSha256="+record.get("build_receipt_sha256"));
  }
  static void verifyCandidate(Path official,Path dll,Path candidate,Path provenance,Path receipt) throws Exception {
    byte[] source=Files.readAllBytes(official), replacement=Files.readAllBytes(dll), actual=Files.readAllBytes(candidate); validatePe(replacement);
    byte[] expected=pack(source,replacement);
    var src=entries(source); var got=entries(actual); var exp=entries(expected);
    String nested=src.keySet().stream().filter(k->k.startsWith("META-INF/jarjar/")&&k.endsWith("sable-sable_rapier-1.21.1-2.0.6.jar")).findFirst().orElseThrow();
    var originalInner=entries(src.get(nested)); var actualInner=entries(got.get(nested));
    var originalNatives=entries(new LZ4FrameInputStream(new ByteArrayInputStream(originalInner.get(RESOURCE))).readAllBytes());
    var actualNatives=entries(new LZ4FrameInputStream(new ByteArrayInputStream(actualInner.get(RESOURCE))).readAllBytes());
    verifyOnly(hashMap(src),got,nested); verifyOnly(hashMap(originalInner),actualInner,RESOURCE);
    var expectedNatives=new TreeMap<String,String>(); originalNatives.forEach((k,v)->expectedNatives.put(k,hex(sha(v)))); expectedNatives.put(DLL,hex(sha(replacement))); verifyOnly(expectedNatives,actualNatives,null);
    if(!Arrays.equals(actual,expected)) { /* ZIP metadata may differ; payload-level proofs above are authoritative. */ }
    JsonObject p;
    try(Reader reader=Files.newBufferedReader(provenance)){p=JsonParser.parseReader(reader).getAsJsonObject();}
    requireProvenanceHash(p,"outer_jar_sha256",hex(sha(actual))); requireProvenanceHash(p,"official_outer_jar_sha256",hex(sha(source)));
    requireProvenanceHash(p,"windows_dll_sha256",hex(sha(replacement))); requireProvenanceHash(p,"build_receipt_sha256",hex(sha(Files.readAllBytes(receipt))));
    requireProvenanceHash(p,"nested_jar_sha256_before",hex(sha(src.get(nested)))); requireProvenanceHash(p,"nested_jar_sha256_after",hex(sha(got.get(nested))));
    requireProvenanceHash(p,"native_bundle_sha256_before",hex(sha(originalInner.get(RESOURCE)))); requireProvenanceHash(p,"native_bundle_sha256_after",hex(sha(actualInner.get(RESOURCE))));
    checkMap(p,"outer_payloads_before",hashMap(src)); checkMap(p,"outer_payloads_after",hashMap(got));
    checkMap(p,"inner_payloads_before",hashMap(originalInner)); checkMap(p,"inner_payloads_after",hashMap(actualInner));
    checkMap(p,"native_payloads_before",hashMap(originalNatives)); checkMap(p,"native_payloads_after",hashMap(actualNatives));
    if(!p.has("target_native")||!DLL.equals(p.get("target_native").getAsString())||!p.has("scope")||!"Windows x64 only; other five official native payloads unchanged".equals(p.get("scope").getAsString()))throw new IOException("provenance target/scope mismatch");
    System.out.println("Fresh payload verification PASS: only nested native bundle and Windows x64 DLL changed; all other official payloads preserved.");
  }
  static Map<String,String> hashMap(Map<String,byte[]> entries){var m=new TreeMap<String,String>();entries.forEach((k,v)->m.put(k,hex(sha(v))));return m;}
  static void requireProvenanceHash(JsonObject json,String key,String value)throws IOException {if(!json.has(key)||!value.equals(json.get(key).getAsString()))throw new IOException("provenance binding mismatch: "+key);}
  static void checkMap(JsonObject json,String key,Map<String,String> values)throws IOException {if(!json.has(key)||!json.get(key).isJsonObject())throw new IOException("provenance map missing: "+key);JsonObject map=json.getAsJsonObject(key);if(map.size()!=values.size())throw new IOException("provenance payload map entry count mismatch: "+key);for(var e:values.entrySet())if(!map.has(e.getKey())||!e.getValue().equals(map.get(e.getKey()).getAsString()))throw new IOException("provenance payload binding mismatch: "+key+"/"+e.getKey());}
  static String toJson(Object x) { if(x instanceof Map<?,?> m){var b=new StringBuilder("{");boolean first=true;for(var e:m.entrySet()){if(!first)b.append(',');first=false;b.append('"').append(e.getKey()).append("\":").append(toJson(e.getValue()));}return b.append('}').toString();} if(x instanceof Number||x instanceof Boolean)return x.toString(); return "\""+x.toString().replace("\\","\\\\").replace("\"","\\\"")+"\""; }
  static void inspect(byte[] outer) throws Exception {
    var o=entries(outer);var nested=o.entrySet().stream().filter(e->e.getKey().startsWith("META-INF/jarjar/")&&e.getKey().endsWith("sable-sable_rapier-1.21.1-2.0.6.jar")).toList();
    if(nested.size()!=1)throw new IOException("expected one nested Rapier JAR");var inner=entries(nested.get(0).getValue());if(!inner.containsKey(RESOURCE))throw new IOException("missing resource");var natives=entries(new LZ4FrameInputStream(new ByteArrayInputStream(inner.get(RESOURCE))).readAllBytes());
    for(var e:new TreeMap<>(natives).entrySet()){if(!e.getKey().matches("sable_rapier_(x86_64|aarch64)_(windows|linux|macos)\\.(dll|so|dylib)"))throw new IOException("unknown platform: "+e.getKey());System.out.println(e.getKey()+" "+hex(sha(e.getValue())));}
    if(!natives.containsKey(DLL))throw new IOException("Windows x64 native missing");System.out.println("inventory=PASS entries="+natives.size()+" outerSha256="+hex(sha(outer)));
  }
  static byte[] pack(byte[] outer, byte[] dll) throws Exception {
    validatePe(dll);
    var outerEntries=entries(outer); for(String n:outerEntries.keySet()) if(n.matches("(?i).*META-INF/.*\\.(SF|RSA|DSA|EC)")) throw new IOException("signed outer input rejected: "+n);
    var nested=outerEntries.entrySet().stream().filter(e->e.getKey().startsWith("META-INF/jarjar/")&&e.getKey().endsWith("sable-sable_rapier-1.21.1-2.0.6.jar")).toList();
    if(nested.size()!=1) throw new IOException("expected exactly one official nested Sable Rapier JAR; found "+nested.size());
    var inner=entries(nested.get(0).getValue()); for(String n:inner.keySet()) if(n.matches("(?i).*META-INF/.*\\.(SF|RSA|DSA|EC)")) throw new IOException("signed inner input rejected: "+n);
    if(!inner.containsKey(RESOURCE)) throw new IOException("missing native bundle resource");
    var natives=entries(new LZ4FrameInputStream(new ByteArrayInputStream(inner.get(RESOURCE))).readAllBytes());
    for(String n:natives.keySet()) if(!n.matches("sable_rapier_(x86_64|aarch64)_(windows|linux|macos)\\.(dll|so|dylib)")) throw new IOException("unknown native platform entry: "+n);
    if(!natives.containsKey(DLL)) throw new IOException("missing Windows x86_64 target");
    var originalOuter=new TreeMap<String,String>(); outerEntries.forEach((k,v)->originalOuter.put(k,hex(sha(v))));
    var originalInner=new TreeMap<String,String>(); inner.forEach((k,v)->originalInner.put(k,hex(sha(v))));
    var originalNatives=new TreeMap<String,String>(); natives.forEach((k,v)->originalNatives.put(k,hex(sha(v))));
    if(Arrays.equals(natives.get(DLL),dll)) throw new IOException("Windows x64 target unchanged; replacement not demonstrated");
    natives.put(DLL,dll); byte[] bundle=lz4(zip(natives)); inner.put(RESOURCE,bundle); byte[] innerBytes=zip(inner); outerEntries.put(nested.get(0).getKey(),innerBytes); byte[] out=zip(outerEntries);
    var checkedOuter=entries(out); verifyOnly(originalOuter,checkedOuter,nested.get(0).getKey());
    var checkedInner=entries(checkedOuter.get(nested.get(0).getKey())); verifyOnly(originalInner,checkedInner,RESOURCE);
    var checkedNative=entries(new LZ4FrameInputStream(new ByteArrayInputStream(checkedInner.get(RESOURCE))).readAllBytes());
    var expected=new TreeMap<>(originalNatives); expected.put(DLL,hex(sha(dll))); verifyOnly(expected,checkedNative,null);
    return out;
  }
  static void verifyOnly(Map<String,String> original, Map<String,byte[]> actual, String allowed) throws Exception {
    if(!original.keySet().equals(actual.keySet())) throw new IOException("ZIP entry set changed");
    for(var e:original.entrySet()) if(!e.getKey().equals(allowed) && !e.getValue().equals(hex(sha(actual.get(e.getKey()))))) throw new IOException("unexpected entry content change: "+e.getKey());
  }
}
