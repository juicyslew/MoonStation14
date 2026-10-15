package com.juicyslew.moonstation14.ms14.singularity.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure source transform for Program's preprocessed shader source; callers MUST invoke it for vertex programs only. */
public final class SingularityWorldShaderSource {
    public static final String HELPER_RESOURCE="/assets/moonstation14/shaders/include/singularity_world_lens.glsl";
    private static final Pattern MAIN=Pattern.compile("\\bvoid\\s+main\\s*\\(");
    private SingularityWorldShaderSource() { }

    public static List<String> wrapVertexSource(List<String> source) {
        Objects.requireNonNull(source,"source");
        return List.of(transformPreprocessedVertex(String.join("\n",source),true));
    }

    /** Returns input byte-for-byte for a non-vertex stage. */
    public static String transformPreprocessedVertex(String source, boolean vertexStage) {
        Objects.requireNonNull(source,"source");
        if(!vertexStage) return source;
        Matcher matcher=MAIN.matcher(source);
        if(!matcher.find()) throw new IllegalArgumentException("preprocessed vertex shader has no main function");
        String renamed=matcher.replaceFirst("void MS14LensOriginalMain(");
        return renamed+"\n#line 1 9001\n"+helperSource()+"\n#line 1 9002\n"
                +"void main() { MS14LensOriginalMain(); gl_Position=MS14LensClip(gl_Position); }\n";
    }

    public static String helperSource() {
        try(var input=SingularityWorldShaderSource.class.getResourceAsStream(HELPER_RESOURCE)) {
            if(input==null) throw new IllegalStateException("Missing shader helper "+HELPER_RESOURCE);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e) { throw new IllegalStateException("Unable to read shader helper",e); }
    }
}
