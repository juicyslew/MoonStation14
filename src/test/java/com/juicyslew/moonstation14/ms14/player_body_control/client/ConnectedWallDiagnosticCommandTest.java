package com.juicyslew.moonstation14.ms14.player_body_control.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedWallDiagnosticCommandTest {
    @Test void exposesDistinctLegacyJumpAndStopCommands() throws Exception {
        ClassNode node=new ClassNode();
        try(var input=ConnectedWallDiagnosticCommand.class.getResourceAsStream("/com/juicyslew/moonstation14/ms14/player_body_control/client/ConnectedWallDiagnosticCommand.class")) {
            if(input==null)throw new IllegalStateException("missing command class");
            new ClassReader(input).accept(node,0);
        }
        var literals=new java.util.HashSet<String>();
        for(var method:node.methods)for(var instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
            if(instruction instanceof LdcInsnNode ldc && ldc.cst instanceof String value)literals.add(value);
        assertTrue(literals.contains("arm")); assertTrue(literals.contains("jump")); assertTrue(literals.contains("stop"));
        assertTrue(literals.contains("prediction")); assertTrue(literals.contains("start"));
        ClassNode client=new ClassNode();
        try(var input=GhostControlClient.class.getResourceAsStream("/com/juicyslew/moonstation14/ms14/player_body_control/client/GhostControlClient.class")) {
            if(input==null)throw new IllegalStateException("missing client class"); new ClassReader(input).accept(client,0);
        }
        var calls=new java.util.HashSet<String>();
        for(var method:client.methods)for(var instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
            if(instruction instanceof org.objectweb.asm.tree.MethodInsnNode call)calls.add(call.name);
        assertTrue(calls.contains("input")); assertTrue(calls.contains("sent")); assertTrue(calls.contains("snapshot"));
        assertTrue(calls.contains("render")); assertTrue(calls.contains("predict"));
        assertTrue(calls.contains("heartbeat"));
        assertTrue(client.methods.stream().anyMatch(m -> java.util.Arrays.stream(m.instructions.toArray()).anyMatch(i ->
                i instanceof org.objectweb.asm.tree.FieldInsnNode field && "HISTORY_LOST".equals(field.name))));
    }
}
