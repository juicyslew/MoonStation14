import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Small pure-Java synthetic fixture suite; no native library is loaded. */
public final class NativeArtifactTest {
  static int passed, rejected;
  static final String INNER="META-INF/jarjar/sable-sable_rapier-1.21.1-2.0.6.jar", WIN=NativeArtifact.DLL;
  static byte[] pe(int marker) { byte[] b=new byte[512]; b[0]='M';b[1]='Z'; ByteBuffer.wrap(b,0x3c,4).order(ByteOrder.LITTLE_ENDIAN).putInt(64); b[64]='P';b[65]='E'; ByteBuffer.wrap(b,68,2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)0x8664); b[200]=(byte)marker; return b; }
  static byte[] bundle(String platform) throws Exception { var m=new LinkedHashMap<String,byte[]>(); if(!"NO_WIN".equals(platform))m.put(WIN,pe(1));m.put("sable_rapier_aarch64_linux.so",new byte[]{3}); if(platform!=null&&!platform.equals("NO_WIN"))m.put(platform,new byte[]{4}); return NativeArtifact.lz4(NativeArtifact.zip(m)); }
  static byte[] inner(boolean signed,String platform) throws Exception { var m=new LinkedHashMap<String,byte[]>();m.put(NativeArtifact.RESOURCE,bundle(platform));m.put("META-INF/MANIFEST.MF",new byte[]{7});if(signed)m.put("META-INF/TEST.SF",new byte[]{8});return NativeArtifact.zip(m); }
  static byte[] outer(byte[] inside,boolean signed) throws Exception { var m=new LinkedHashMap<String,byte[]>();m.put(INNER,inside);m.put("other.bin",new byte[]{9});if(signed)m.put("META-INF/TEST.RSA",new byte[]{1});return NativeArtifact.zip(m); }
  static byte[] duplicateZip() throws Exception { var o=new ByteArrayOutputStream();var z=new ZipOutputStream(o);for(String n:List.of("A.txt","B.txt")){z.putNextEntry(new ZipEntry(n));z.write(n.getBytes());z.closeEntry();}z.close();byte[] b=o.toByteArray();byte[] from="B.txt".getBytes(),to="A.txt".getBytes();int changes=0;for(int i=0;i<=b.length-from.length;i++){boolean match=true;for(int j=0;j<from.length;j++)if(b[i+j]!=from[j]){match=false;break;}if(match){System.arraycopy(to,0,b,i,to.length);changes++;}}if(changes!=2)throw new AssertionError("expected local and central name occurrences, got "+changes);return b; }
  static void check(boolean b,String label) { if(!b)throw new AssertionError(label);passed++; }
  static void reject(Throwing f,String label) throws Exception { try{f.run();throw new AssertionError("accepted "+label);}catch(IOException expected){rejected++;} }
  static String jsonMap(Map<String,String> m){var b=new StringBuilder("{");boolean first=true;for(var e:m.entrySet()){if(!first)b.append(',');first=false;b.append('"').append(e.getKey()).append("\":\"").append(e.getValue()).append('"');}return b.append('}').toString();}
  static Map<String,String> hashes(Map<String,byte[]> m){var x=new TreeMap<String,String>();m.forEach((k,v)->x.put(k,NativeArtifact.hex(NativeArtifact.sha(v))));return x;}
  static Path[] proofFiles(byte[] source,byte[] dll,byte[] candidate,Path dir) throws Exception {
    Path o=dir.resolve("official.jar"),d=dir.resolve("native.dll"),c=dir.resolve("candidate.jar"),r=dir.resolve("receipt.json"),p=dir.resolve("proof.json");Files.write(o,source);Files.write(d,dll);Files.write(c,candidate);Files.writeString(r,"synthetic receipt");
    var oe=NativeArtifact.entries(source);var ce=NativeArtifact.entries(candidate);String n=oe.keySet().stream().filter(k->k.startsWith("META-INF/jarjar/")).findFirst().orElseThrow();var oi=NativeArtifact.entries(oe.get(n));var ci=NativeArtifact.entries(ce.get(n));var on=NativeArtifact.entries(new net.jpountz.lz4.LZ4FrameInputStream(new ByteArrayInputStream(oi.get(NativeArtifact.RESOURCE))).readAllBytes());var cn=NativeArtifact.entries(new net.jpountz.lz4.LZ4FrameInputStream(new ByteArrayInputStream(ci.get(NativeArtifact.RESOURCE))).readAllBytes());
    String text="{\"scope\":\"Windows x64 only; other five official native payloads unchanged\",\"target_native\":\""+WIN+"\",\"outer_jar_sha256\":\""+NativeArtifact.hex(NativeArtifact.sha(candidate))+"\",\"official_outer_jar_sha256\":\""+NativeArtifact.hex(NativeArtifact.sha(source))+"\",\"nested_jar_sha256_before\":\""+NativeArtifact.hex(NativeArtifact.sha(oe.get(n)))+"\",\"nested_jar_sha256_after\":\""+NativeArtifact.hex(NativeArtifact.sha(ce.get(n)))+"\",\"native_bundle_sha256_before\":\""+NativeArtifact.hex(NativeArtifact.sha(oi.get(NativeArtifact.RESOURCE)))+"\",\"native_bundle_sha256_after\":\""+NativeArtifact.hex(NativeArtifact.sha(ci.get(NativeArtifact.RESOURCE)))+"\",\"windows_dll_sha256\":\""+NativeArtifact.hex(NativeArtifact.sha(dll))+"\",\"build_receipt_sha256\":\""+NativeArtifact.hex(NativeArtifact.sha(Files.readAllBytes(r)))+"\",\"outer_payloads_before\":"+jsonMap(hashes(oe))+",\"outer_payloads_after\":"+jsonMap(hashes(ce))+",\"inner_payloads_before\":"+jsonMap(hashes(oi))+",\"inner_payloads_after\":"+jsonMap(hashes(ci))+",\"native_payloads_before\":"+jsonMap(hashes(on))+",\"native_payloads_after\":"+jsonMap(hashes(cn))+"}";Files.writeString(p,text);return new Path[]{o,d,c,p,r};
  }
  interface Throwing { void run() throws Exception; }
  public static void main(String[] args) throws Exception {
    byte[] source=outer(inner(false,null),false), replacement=pe(2), result=NativeArtifact.pack(source,replacement);
    var before=NativeArtifact.entries(source);var after=NativeArtifact.entries(result);
    check(Arrays.equals(before.get("other.bin"),after.get("other.bin")),"outer unrelated bytes");
    var ib=NativeArtifact.entries(before.get(INNER));var ia=NativeArtifact.entries(after.get(INNER));check(Arrays.equals(ib.get("META-INF/MANIFEST.MF"),ia.get("META-INF/MANIFEST.MF")),"inner manifest bytes");
    var nb=NativeArtifact.entries(new net.jpountz.lz4.LZ4FrameInputStream(new ByteArrayInputStream(ib.get(NativeArtifact.RESOURCE))).readAllBytes());
    var na=NativeArtifact.entries(new net.jpountz.lz4.LZ4FrameInputStream(new ByteArrayInputStream(ia.get(NativeArtifact.RESOURCE))).readAllBytes());
    check(Arrays.equals(na.get("sable_rapier_aarch64_linux.so"),nb.get("sable_rapier_aarch64_linux.so")),"other platform preserved");check(Arrays.equals(na.get(WIN),replacement),"replacement installed");
    reject(()->NativeArtifact.pack(source,new byte[300]),"invalid PE");byte[] wrong=pe(1);ByteBuffer.wrap(wrong,68,2).order(ByteOrder.LITTLE_ENDIAN).putShort((short)0x14c);reject(()->NativeArtifact.pack(source,wrong),"wrong architecture");
    reject(()->NativeArtifact.pack(outer(inner(false,"surprise.bin"),false),replacement),"unknown platform");
    reject(()->NativeArtifact.pack(outer(inner(false,"NO_WIN"),false),replacement),"missing target");reject(()->NativeArtifact.pack(outer(inner(true,null),false),replacement),"signed inner");reject(()->NativeArtifact.pack(outer(inner(false,null),true),replacement),"signed outer");
     reject(()->NativeArtifact.entries(duplicateZip()),"duplicate ZIP entry");
     Path dir=Files.createTempDirectory("native-verify-fixture"); Path[] f=proofFiles(source,replacement,result,dir);
     NativeArtifact.verifyCandidate(f[0],f[1],f[2],f[3],f[4]);check(true,"synthetic fresh candidate verification");
     for(String field:List.of("outer_jar_sha256","target_native","nested_jar_sha256_before","nested_jar_sha256_after","native_bundle_sha256_before","native_bundle_sha256_after")) { var altered=com.google.gson.JsonParser.parseString(Files.readString(f[3])).getAsJsonObject();altered.addProperty(field,"forged-value");Path p=dir.resolve("forged-"+field+".json");Files.writeString(p,altered.toString());reject(()->NativeArtifact.verifyCandidate(f[0],f[1],f[2],p,f[4]),"forged "+field); }
     var badMap=com.google.gson.JsonParser.parseString(Files.readString(f[3])).getAsJsonObject();badMap.getAsJsonObject("native_payloads_after").addProperty(WIN,"forged-value");Path mapProof=dir.resolve("forged-map.json");Files.writeString(mapProof,badMap.toString());reject(()->NativeArtifact.verifyCandidate(f[0],f[1],f[2],mapProof,f[4]),"forged payload map entry");
     var mutated=NativeArtifact.entries(result);mutated.put("other.bin",new byte[]{44});byte[] badOuter=NativeArtifact.zip(mutated);Path[] bad=proofFiles(source,replacement,badOuter,dir);reject(()->NativeArtifact.verifyCandidate(bad[0],bad[1],bad[2],bad[3],bad[4]),"forged provenance for modified unrelated Java payload");
     reject(()->NativeArtifact.verifyCandidate(f[0],f[1],f[0],f[3],f[4]),"official unmodified bundle");
     byte[] wrongDll=pe(3);Path[] wrongProof=proofFiles(source,wrongDll,result,dir);reject(()->NativeArtifact.verifyCandidate(wrongProof[0],wrongProof[1],wrongProof[2],wrongProof[3],wrongProof[4]),"candidate bound to different target DLL");
     String missing=Files.readString(f[3]).replaceAll(",\\\"native_payloads_after\\\":\\{[^}]*\\}","");Files.writeString(f[3],missing);reject(()->NativeArtifact.verifyCandidate(f[0],f[1],f[2],f[3],f[4]),"missing payload provenance map");
     if(args.length>0){Path out=Path.of(args[0]);Files.createDirectories(out);proofFiles(source,replacement,result,out);System.out.println("Synthetic provenance roundtrip fixture emitted under "+out);} else try{for(Path p:Files.newDirectoryStream(dir))Files.deleteIfExists(p);Files.deleteIfExists(dir);}catch(IOException ignored){}
    System.out.println("NativeArtifact fixtures: passed="+passed+" rejected="+rejected+" total="+(passed+rejected));
  }
}
