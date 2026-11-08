package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldShaderSource;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native-shader-compile")
class SingularityNativeShaderCompileTest {
    private static final String CORE="assets/minecraft/shaders/core/";
    private static final String[] PROGRAMS={
            "rendertype_solid", "rendertype_entity_cutout_no_cull", "particle", "rendertype_lines", "rendertype_text"
    };

    private static int compile(int type,String source) {
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader,source);
        GL20.glCompileShader(shader);
        return shader;
    }

    /** Uses the actual game GlslPreprocessor, resolving vanilla classpath includes just as shader resources do. */
    private static List<String> preprocess(String path) throws IOException {
        String source=readClasspath(path);
        return new GlslPreprocessor() {
            @Override public String applyImport(boolean relative,String directory) {
                String resource=relative ? "assets/minecraft/shaders/core/"+directory : "assets/minecraft/shaders/include/"+directory;
                try { return readClasspath(resource); }
                catch(IOException e) { throw new IllegalStateException("Missing vanilla GLSL import classpath resource: "+resource,e); }
            }
        }.process(source);
    }

    private static String readClasspath(String path) throws IOException {
        try(InputStream stream=SingularityNativeShaderCompileTest.class.getClassLoader().getResourceAsStream(path)) {
            if(stream==null) throw new IOException("Missing vanilla shader classpath resource: "+path);
            return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    @Test void wrappedVanillaWorldProgramsCompileLinkAndReservedWordNegativeControlFails() throws Exception {
        assertTrue(GLFW.glfwInit(),"GLFW native initialization failed");
        long window=0;
        try {
            GLFW.glfwDefaultWindowHints(); GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3); GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
            window=GLFW.glfwCreateWindow(1,1,"vanilla-lens-compile",0,0);
            assertNotEquals(0,window,"hidden OpenGL 3.2 context creation failed");
            GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
            System.out.println("Native OpenGL "+GL20.glGetString(GL20.GL_VERSION)+" / "+GL20.glGetString(GL20.GL_VENDOR)+" / "+GL20.glGetString(GL20.GL_RENDERER));

            String firstWrapped=null;
            for(String name:PROGRAMS) {
                String vertexPath=CORE+name+".vsh", fragmentPath=CORE+name+".fsh";
                String vertex=String.join("\n",SingularityWorldShaderSource.wrapVertexSource(preprocess(vertexPath)));
                String fragment=String.join("\n",preprocess(fragmentPath));
                if(firstWrapped==null)firstWrapped=vertex;
                int vs=compile(GL20.GL_VERTEX_SHADER,vertex), fs=compile(GL20.GL_FRAGMENT_SHADER,fragment), program=GL20.glCreateProgram();
                try {
                    assertEquals(1,GL20.glGetShaderi(vs,GL20.GL_COMPILE_STATUS),vertexPath+"\n"+GL20.glGetShaderInfoLog(vs));
                    assertEquals(1,GL20.glGetShaderi(fs,GL20.GL_COMPILE_STATUS),fragmentPath+"\n"+GL20.glGetShaderInfoLog(fs));
                    GL20.glAttachShader(program,vs); GL20.glAttachShader(program,fs); GL20.glLinkProgram(program);
                    assertEquals(1,GL20.glGetProgrami(program,GL20.GL_LINK_STATUS),name+"\n"+GL20.glGetProgramInfoLog(program));
                    assertNotEquals(-1,GL20.glGetUniformLocation(program,"MS14LensCount"),name+" lens count is linked");
                    assertNotEquals(-1,GL20.glGetUniformLocation(program,"MS14LensProjection"),name+" projection is linked");
                    assertNotEquals(-1,GL20.glGetUniformLocation(program,"MS14LensInverseProjection"),name+" inverse projection is linked");
                    System.out.println("Wrapped vanilla shader linked: "+name);
                } finally { GL20.glDeleteProgram(program); GL20.glDeleteShader(vs); GL20.glDeleteShader(fs); }
            }

            assertNotNull(firstWrapped);
            String mutant=firstWrapped.replace("MS14LensSmooth","smooth");
            assertNotEquals(firstWrapped,mutant,"reserved-word mutant must alter the wrapped valid source");
            assertFalse(mutant.contains("MS14LensSmooth"),"all helper definition/call identifiers are mutated");
            int broken=compile(GL20.GL_VERTEX_SHADER,mutant);
            try {
                assertEquals(0,GL20.glGetShaderi(broken,GL20.GL_COMPILE_STATUS),"reserved interpolation qualifier must fail as a function identifier");
                String log=GL20.glGetShaderInfoLog(broken);
                System.out.println("Reserved-word negative-control compiler log: "+log);
                assertTrue(log.toLowerCase(java.util.Locale.ROOT).contains("smooth"),"expected reserved identifier in compiler diagnostic");
            } finally { GL20.glDeleteShader(broken); }
        } finally { if(window!=0)GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate(); }
    }
}
