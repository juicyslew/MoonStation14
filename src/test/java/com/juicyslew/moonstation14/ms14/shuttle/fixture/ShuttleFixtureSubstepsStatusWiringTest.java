package com.juicyslew.moonstation14.ms14.shuttle.fixture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class ShuttleFixtureSubstepsStatusWiringTest {
    @Test void statusReportsTheLiveSdkConfigurationAndHandlesMissingSystem() throws Exception {
        String resource = "/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.class";
        ClassNode node = new ClassNode();
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        MethodNode status = node.methods.stream().filter(m -> m.name.equals("status")).findFirst().orElseThrow();
        boolean readsSdkConfig = false;
        boolean handlesUnavailable = false;
        boolean reportsBodyUp = false;
        boolean reportsTilt = false;
        for (AbstractInsnNode insn : status.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("dev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem")
                    && call.name.equals("get")) readsSdkConfig = true;
            if (insn instanceof FieldInsnNode field && field.owner.equals("dev/ryanhcode/sable/physics/config/PhysicsConfigData")
                    && field.name.equals("substepsPerTick")) readsSdkConfig = true;
            if (insn instanceof LdcInsnNode constant && "unavailable".equals(constant.cst)) handlesUnavailable = true;
            if (insn instanceof LdcInsnNode constant && "bodyUp".equals(constant.cst)) reportsBodyUp = true;
            if (insn instanceof LdcInsnNode constant && "tiltDegrees".equals(constant.cst)) reportsTilt = true;
            if (insn instanceof InvokeDynamicInsnNode dynamic) {
                for (Object argument : dynamic.bsmArgs) {
                    if (!(argument instanceof String recipe)) continue;
                    reportsBodyUp |= recipe.contains("bodyUp");
                    reportsTilt |= recipe.contains("tiltDegrees");
                }
            }
            if (insn instanceof MethodInsnNode call && call.name.equals("require"))
                fail("status must not force initialization via require");
        }
        assertTrue(readsSdkConfig, "status must report the live SDK config field");
        assertTrue(handlesUnavailable, "status must identify an unavailable runtime system");
        assertTrue(reportsBodyUp, "status must report body-up diagnostics");
        assertTrue(reportsTilt, "status must report tilt diagnostics");
    }

    @Test void registersOwnerAuthorizedManualCommands() throws Exception {
        String resource="/com/juicyslew/moonstation14/ms14/shuttle/fixture/ShuttleFixtureCommands.class";
        ClassNode node=new ClassNode();
        try(InputStream in=getClass().getResourceAsStream(resource)){assertNotNull(in);new ClassReader(in).accept(node,0);}
        MethodNode register=node.methods.stream().filter(m->m.name.equals("registerCommands")).findFirst().orElseThrow();
        java.util.Set<String> names=new java.util.HashSet<>();
        for(AbstractInsnNode insn:register.instructions) if(insn instanceof LdcInsnNode c && c.cst instanceof String s) names.add(s);
        assertTrue(names.containsAll(java.util.Set.of("roll","pitch","brake","yaw")));
    }
}
