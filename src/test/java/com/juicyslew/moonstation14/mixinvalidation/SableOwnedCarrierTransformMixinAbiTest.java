package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the pinned SDK ABI from runtime class bytes and the compiled live WrapOperation handlers. */
class SableOwnedCarrierTransformMixinAbiTest {
    private static final String COLLIDE="(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;"
            +"Lnet/minecraft/world/phys/Vec3;Ldev/ryanhcode/sable/api/math/LevelReusedVectors;)"
            +"Ldev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision$CollisionInfo;";
    private static final String POSE="dev/ryanhcode/sable/companion/math/Pose3d";
    private static final String VECTOR="org/joml/Vector3d";
    private static final String OP="com/llamalad7/mixinextras/injector/wrapoperation/Operation";
    private static final String ENTITY="net/minecraft/world/entity/Entity";
    private static final String SUBLEVEL="dev/ryanhcode/sable/sublevel/SubLevel";

    @Test void pinnedTransformSeamAndCompiledHooksRetainTheirAbi() throws Exception {
        ClassNode sdk=SableTemporalCollisionMixinContractTest.readRuntime(
                "dev/ryanhcode/sable/sublevel/entity_collision/SubLevelEntityCollision");
        ClassNode mixin=SableTemporalCollisionMixinContractTest.readRuntime(
                "com/juicyslew/moonstation14/mixin/compat/SableOwnedNativeCollisionTraceMixin");
        MethodNode collide=method(sdk,"collide",COLLIDE);
        assertTrue((collide.access&Opcodes.ACC_STATIC)!=0);

        MethodInsnNode inverse=invoke(collide,POSE,"transformPositionInverse",
                "(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;");
        MethodInsnNode forward=invoke(collide,POSE,"transformPosition",
                "(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;");
        assertEquals(1,countInvokes(collide,POSE,"transformPositionInverse"),
                "the wrapped inverse is the trackingPosition last-pose transform");
        assertEquals(2,countInvokes(collide,POSE,"transformPosition"));
        assertEquals(0,ordinal(collide,inverse));
        assertEquals(0,ordinal(collide,forward),"ordinal zero is the base transport, before later eye-position projection");
        assertEquals(Opcodes.INVOKEVIRTUAL,inverse.getOpcode());
        assertEquals(Opcodes.INVOKEVIRTUAL,forward.getOpcode());
        assertFalse(inverse.itf);
        assertFalse(forward.itf);
        assertTrue(hasFieldBefore(collide,inverse,"dev/ryanhcode/sable/api/math/LevelReusedVectors","trackingPosition"));
        assertTrue(hasFieldBefore(collide,forward,"dev/ryanhcode/sable/api/math/LevelReusedVectors","trackingPosition"));

        assertLocalAt(collide,inverse,"lastSubLevelPose",25);
        assertLocalAt(collide,forward,"subLevelPose",26);
        assertLocalAt(collide,forward,"subLevel",46);
        assertLocalAt(collide,forward,"i",39);
        assertLocalAt(collide,forward,"substeps",18);

        MethodNode capture=method(mixin,"moonstation14$captureSelectedFrameCarrierPoint",
                Type.getMethodDescriptor(Type.getObjectType(VECTOR),Type.getObjectType(POSE),Type.getObjectType(VECTOR),
                        Type.getObjectType(OP),Type.getObjectType(ENTITY)));
        MethodNode select=method(mixin,"moonstation14$selectStartingBasisCarrier",
                Type.getMethodDescriptor(Type.getObjectType(VECTOR),Type.getObjectType(POSE),Type.getObjectType(VECTOR),
                        Type.getObjectType(OP),Type.getObjectType(ENTITY),Type.getObjectType(SUBLEVEL),Type.INT_TYPE,Type.INT_TYPE));
        assertWrapTarget(capture,"transformPositionInverse");
        assertWrapTarget(select,"transformPosition");
        assertTrue(hasInvoke(capture,"com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScope",
                "captureStartingWorldPoint"));
        assertTrue(hasInvoke(select,"com/juicyslew/moonstation14/ms14/shuttle/sable/CurrentFrameCollisionScope",
                "selectStartingBasisTransport"));

        Path root=projectRoot();
        String mover=Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/ms14/shuttle/sable/SableOwnedBodyMotion.java"));
        assertTrue(mover.contains("LoadedFrameRayQuery.ActorPoint velocityBasis = options.velocityBasis() == null ? beforePoint : options.velocityBasis()"));
        int scopeCall=mover.indexOf("CurrentFrameCollisionScope.withTrackedCharacterPose(body,");
        assertTrue(scopeCall>=0&&mover.indexOf("velocityBasis,",scopeCall)>scopeCall,
                "the exact chosen starting basis is passed into the live movement scope");
    }

    private static void assertWrapTarget(MethodNode handler,String method) {
        String desc="Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
        var annotations=handler.visibleAnnotations==null?handler.invisibleAnnotations:handler.visibleAnnotations;
        if(annotations==null||annotations.stream().noneMatch(a->a.desc.equals(desc)))
            annotations=handler.invisibleAnnotations;
        assertNotNull(annotations,"compiled handler has WrapOperation annotation");
        var wrap=annotations.stream().filter(a->a.desc.equals(desc)).findFirst().orElseThrow();
        assertNotNull(wrap,"handler is a live WrapOperation, not an unused helper");
        Object targetedMethod=value(wrap,"method");
        String target="collide"+COLLIDE;
        assertTrue(targetedMethod instanceof String s&&s.equals(target)
                        ||targetedMethod instanceof java.util.List<?> list&&list.contains(target),
                "compiled hook targets exact collide descriptor, found="+targetedMethod);
        Object atValue=value(wrap,"at");
        AnnotationNode at=atValue instanceof AnnotationNode direct?direct
                :atValue instanceof java.util.List<?> list&&!list.isEmpty()&&list.getFirst() instanceof AnnotationNode nested
                    ?nested:null;
        assertNotNull(at,"compiled hook has an @At instruction selector: "+atValue);
        assertEquals("INVOKE",value(at,"value"));
        assertEquals("L"+POSE+";"+method+"(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",value(at,"target"));
        assertEquals(0,value(at,"ordinal"));
        String source=readMixinSource();
        assertTrue(source.contains("moonstation14$"+handler.name.substring("moonstation14$".length())));
        assertTrue(source.contains("target = \"L"+POSE+";"+method+"(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;\", ordinal = 0)"));
        assertTrue(source.contains("remap = false, require = 1"));
    }

    private static Object value(AnnotationNode annotation,String key) {
        if(annotation.values==null)return null;
        for(int i=0;i<annotation.values.size();i+=2)
            if(annotation.values.get(i).equals(key))return annotation.values.get(i+1);
        return null;
    }

    private static String readMixinSource() {
        try { return Files.readString(projectRoot().resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/SableOwnedNativeCollisionTraceMixin.java")); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static Path projectRoot() {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14")))root=root.getParent();
        assertNotNull(root,"project source root is available");return root;
    }
    private static MethodNode method(ClassNode owner,String name,String desc) {
        return owner.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();
    }
    private static MethodInsnNode invoke(MethodNode method,String owner,String name,String desc) {
        for(AbstractInsnNode insn:method.instructions)if(insn instanceof MethodInsnNode call
                &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc))return call;
        return fail("missing invoke "+owner+"."+name+desc);
    }
    private static int countInvokes(MethodNode method,String owner,String name) {
        return (int)java.util.Arrays.stream(method.instructions.toArray()).filter(i->i instanceof MethodInsnNode call
                &&call.owner.equals(owner)&&call.name.equals(name)).count();
    }
    private static int ordinal(MethodNode method,MethodInsnNode selected) {
        int result=0;
        for(AbstractInsnNode insn:method.instructions) {
            if(insn==selected)return result;
            if(insn instanceof MethodInsnNode call&&call.owner.equals(selected.owner)&&call.name.equals(selected.name)
                    &&call.desc.equals(selected.desc))result++;
        }
        return -1;
    }
    private static boolean hasFieldBefore(MethodNode method,MethodInsnNode target,String owner,String name) {
        int remaining=12;
        for(AbstractInsnNode insn=target.getPrevious();insn!=null&&remaining-->0;insn=insn.getPrevious()) {
            if(insn instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD
                    &&field.owner.equals(owner)&&field.name.equals(name))return true;
        }
        return false;
    }
    private static void assertLocalAt(MethodNode method,AbstractInsnNode target,String name,int slot) {
        assertNotNull(method.localVariables,"pinned class must retain its LocalVariableTable");
        int index=method.instructions.indexOf(target);
        assertTrue(method.localVariables.stream().anyMatch(local->local.name.equals(name)&&local.index==slot
                        &&method.instructions.indexOf(local.start)<=index&&index<method.instructions.indexOf(local.end)),
                "local "+name+" in slot "+slot+" is live at wrapped SDK invocation");
    }
    private static boolean hasInvoke(MethodNode method,String owner,String name) {
        return java.util.Arrays.stream(method.instructions.toArray()).anyMatch(i->i instanceof MethodInsnNode call
                &&call.owner.equals(owner)&&call.name.equals(name));
    }
    private static <T> T fail(String message) { throw new AssertionError(message); }
}
