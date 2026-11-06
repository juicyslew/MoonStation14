package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Regression guard derives the WrapOperation ABI from the pinned Sable class bytes, not source guesses. */
class SableTemporalCollisionMixinContractTest {
    private static final String SDK="dev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision";
    private static final String MIXIN="com/juicyslew/moonstation14/mixin/compat/SableTemporalCollisionCandidatesMixin";

    @Test void candidateWrapperDescriptorMatchesActualSdkReceiverCallAndFullEnclosingArguments() throws Exception {
        ClassNode sdk=read(SDK),mixin=read(MIXIN);
        MethodNode collide=sdk.methods.stream().filter(m->m.name.equals("collide")
                &&m.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;"))
                .findFirst().orElseThrow();
        assertTrue((collide.access&Opcodes.ACC_STATIC)!=0,"pinned collide is static");
        MethodInsnNode call=null;
        for(AbstractInsnNode insn:collide.instructions)if(insn instanceof MethodInsnNode method
                &&method.name.equals("getAllIntersecting")) {call=method;break;}
        assertNotNull(call,"pinned narrowphase performs the candidate lookup");
        assertEquals(Opcodes.INVOKEVIRTUAL,call.getOpcode(),"receiver is part of WrapOperation ABI");
        assertEquals("dev/ryanhcode/sable/ActiveSableCompanion",call.owner);

        Type[] called=Type.getArgumentTypes(call.desc);
        Type[] enclosing=Type.getArgumentTypes(collide.desc);
        Type[] handlerArgs=new Type[1+called.length+1+enclosing.length];
        int i=0;handlerArgs[i++]=Type.getObjectType(call.owner);
        for(Type arg:called)handlerArgs[i++]=arg;
        handlerArgs[i++]=Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation");
        for(Type arg:enclosing)handlerArgs[i++]=arg;
        String expected=Type.getMethodDescriptor(Type.getReturnType(call.desc),handlerArgs);
        MethodNode handler=mixin.methods.stream().filter(m->m.name.equals("moonstation14$temporalCandidates"))
                .findFirst().orElseThrow();
        assertTrue((handler.access&Opcodes.ACC_STATIC)!=0,"handler matches static collide method");
        assertEquals(expected,handler.desc,"must capture actual receiver, called parameters, Operation and every collide arg");
    }

    @Test void mixinTargetsExactCollisionMethodAndRequiredCandidateLookup() throws Exception {
        ClassNode sdk=read(SDK),mixin=read(MIXIN);
        var collide=sdk.methods.stream().filter(m->m.name.equals("collide")
                &&m.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;"))
                .findFirst().orElseThrow();
        var annotations=mixin.visibleAnnotations==null?mixin.invisibleAnnotations:mixin.visibleAnnotations;
        var annotation=annotations.stream().filter(a->a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst().orElseThrow();
        assertNotNull(annotation,"mixin targets the stock collision class");
        assertTrue(Files.readString(projectRoot().resolve(
                "src/main/java/com/juicyslew/moonstation14/mixin/compat/SableTemporalCollisionCandidatesMixin.java"))
                .contains("@Mixin(targets=\"dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision\""),
                "mixin remains on stock collision class");
        assertEquals(1,Arrays.stream(collide.instructions.toArray()).filter(insn->insn instanceof MethodInsnNode m
                &&m.name.equals("getAllIntersecting")).count(),"one actual candidate lookup remains wrapped");
        assertTrue(Files.readString(projectRoot().resolve(
                "src/main/java/com/juicyslew/moonstation14/mixin/compat/SableTemporalCollisionCandidatesMixin.java"))
                .contains("require=1"),"do not silently disable this broadphase hook");
    }

    @Test void temporalGetterAndPacketCaptureHooksResolvePinnedSdkDescriptorsAndLocals() throws Exception {
        ClassNode subLevel=read("dev/ryanhcode/sable/sublevel/SubLevel");
        ClassNode clientSubLevel=read("dev/ryanhcode/sable/sublevel/ClientSubLevel");
        ClassNode snapshot=read("dev/ryanhcode/sable/network/packets/ClientboundSableSnapshotDualPacket");
        ClassNode start=read("dev/ryanhcode/sable/network/packets/tcp/ClientboundStartTrackingSubLevelPacket");
        ClassNode stop=read("dev/ryanhcode/sable/network/packets/tcp/ClientboundStopMovingSubLevelPacket");
        assertMethod(subLevel,"logicalPose","()Ldev/ryanhcode/sable/companion/math/Pose3d;");
        assertMethod(subLevel,"lastPose","()Ldev/ryanhcode/sable/companion/math/Pose3dc;");
        assertMethod(subLevel,"onRemove","()V");
        assertMethod(clientSubLevel,"renderPose","()Ldev/ryanhcode/sable/companion/math/Pose3dc;");
        assertMethod(clientSubLevel,"renderPose","(F)Ldev/ryanhcode/sable/companion/math/Pose3dc;");

        MethodNode snapshotHandler=method(snapshot,"handleClient","(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V");
        MethodInsnNode receive=invoke(snapshotHandler,"receiveSnapshot");
        assertEquals("dev/ryanhcode/sable/network/client/ClientSableInterpolationState",receive.owner);
        assertEquals("(Ldev/ryanhcode/sable/sublevel/ClientSubLevel;ILdev/ryanhcode/sable/companion/math/Pose3dc;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V",receive.desc);
        var entry=snapshotHandler.localVariables.stream().filter(v->v.name.equals("entry")).findFirst().orElseThrow();
        assertEquals(5,entry.index,"packet entry is actual slot 5 at receiveSnapshot bytecode");
        assertEquals("Ldev/ryanhcode/sable/network/packets/ClientboundSableSnapshotDualPacket$Entry;",entry.desc);

        MethodNode startHandler=method(start,"handle","(Lfoundry/veil/api/network/handler/PacketContext;)V");
        var startUpdate=invoke(startHandler,"updateRenderData");
        assertEquals("dev/ryanhcode/sable/sublevel/ClientSubLevel",startUpdate.owner);
        assertEquals("()V",startUpdate.desc);
        assertTrue(startHandler.localVariables.stream().anyMatch(v->v.name.equals("level")&&v.index==2
                &&v.desc.equals("Lnet/minecraft/world/level/Level;")));
        assertTrue(startHandler.localVariables.stream().anyMatch(v->v.name.equals("subLevel")&&v.index==5
                &&v.desc.equals("Ldev/ryanhcode/sable/sublevel/ClientSubLevel;")));
        MethodNode stopHandler=method(stop,"handle","(Lfoundry/veil/api/network/handler/PacketContext;)V");
        MethodInsnNode stopLevel=invoke(stopHandler,"level");
        assertEquals("foundry/veil/api/network/handler/PacketContext",stopLevel.owner);
        assertEquals("()Lnet/minecraft/world/level/Level;",stopLevel.desc);

        Path sourceRoot=projectRoot().resolve("src/main/java/com/juicyslew/moonstation14/mixin/client");
        String snapshotMixin=Files.readString(sourceRoot.resolve("SableAuthoritativeSnapshotCaptureMixin.java"));
        String startMixin=Files.readString(sourceRoot.resolve("SableStartTrackingTimelineSeedMixin.java"));
        String stopMixin=Files.readString(sourceRoot.resolve("SableAuthoritativeStopCaptureMixin.java"));
        assertTrue(snapshotMixin.contains("handleClient(Lnet/minecraft/world/level/Level;Ldev/ryanhcode/sable/network/packets/PacketReceiveMode;)V"));
        assertTrue(snapshotMixin.contains("@Local(name = \"entry\") ClientboundSableSnapshotDualPacket.Entry"));
        assertTrue(startMixin.contains("handle(Lfoundry/veil/api/network/handler/PacketContext;)V"));
        assertTrue(startMixin.contains("@Local(name = \"level\") Level")&&startMixin.contains("@Local(name = \"subLevel\") ClientSubLevel"));
        assertTrue(stopMixin.contains("handle(Lfoundry/veil/api/network/handler/PacketContext;)V"));
    }

    private static ClassNode read(String name) throws Exception {
        if(name.startsWith("dev/ryanhcode/sable/"))return readPinnedSdk(name);
        String resource="/"+name+".class";
        try(InputStream in=SableTemporalCollisionMixinContractTest.class.getResourceAsStream(resource)) {
            assertNotNull(in,"class bytes available: "+resource);
            ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
    }
    /** Reads pinned dependency bytes through the test runtime classpath without loading/initializing the class. */
    static ClassNode readRuntime(String name) throws Exception {
        String resource=name+".class";
        InputStream classBytes=null;
        if(name.equals("dev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision"))
            classBytes=dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision.class
                    .getResourceAsStream("SubLevelEntityCollision.class");
        if(classBytes==null)classBytes=Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if(classBytes==null)classBytes=SableTemporalCollisionMixinContractTest.class.getClassLoader().getResourceAsStream(resource);
        if(classBytes!=null)try(InputStream in=classBytes) {
            ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
        // Jar-in-Jar runtime classloaders may not expose nested dependency resources. In that
        // case use the existing hash-pinned SDK artifact reader, never a generated javap dump.
        if(name.startsWith("dev/ryanhcode/sable/"))return readPinnedSdk(name);
        throw new AssertionError("runtime class bytes available: "+resource);
    }
    private static ClassNode readPinnedSdk(String name) throws Exception {
        Path cache=Path.of(System.getProperty("user.home"),".gradle/caches/modules-2/files-2.1/maven.modrinth/sable/fg9dTRz9");
        assertTrue(Files.isDirectory(cache),"pinned Sable artifact cache exists");
        Path jar;
        try(var paths=Files.walk(cache)) {
            jar=paths.filter(path->path.getFileName().toString().equals("sable-fg9dTRz9.jar"))
                    .filter(SableTemporalCollisionMixinContractTest::isPinnedJar).findFirst().orElseThrow();
        }
        try(JarFile archive=new JarFile(jar.toFile());InputStream in=archive.getInputStream(archive.getJarEntry(name+".class"))) {
            assertNotNull(in,"pinned SDK class exists: "+name);
            ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);return node;
        }
    }
    private static boolean isPinnedJar(Path path) {
        try {
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            String hash=java.util.HexFormat.of().formatHex(bytes);
            return hash.equals("9c199157843bf953a47dff625fc5bb41c01a90538d35ad18e6996bbcb8a617c2");
        } catch(Exception ignored){return false;}
    }
    private static Path projectRoot(){
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14")))root=root.getParent();
        assertNotNull(root,"project source root is available");return root;
    }
    private static MethodNode method(ClassNode owner,String name,String desc){return owner.methods.stream()
            .filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    private static void assertMethod(ClassNode owner,String name,String desc){assertNotNull(method(owner,name,desc));}
    private static MethodInsnNode invoke(MethodNode method,String name){
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals(name))return call;
        return fail("missing invocation "+name+" in "+method.name);
    }
}
