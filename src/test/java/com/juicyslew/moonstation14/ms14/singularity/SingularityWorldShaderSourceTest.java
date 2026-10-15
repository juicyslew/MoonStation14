package com.juicyslew.moonstation14.ms14.singularity;

import com.juicyslew.moonstation14.ms14.singularity.client.SingularityWorldShaderSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingularityWorldShaderSourceTest {
    @Test void wrapperPreservesPreprocessorDirectivesAndDelegatesOneOriginalMain() {
        String input="#version 150\n#extension GL_ARB_explicit_attrib_location : enable\n#line 7\nin vec3 Position;\nvoid main() { gl_Position=vec4(Position,1.0); }";
        String wrapped=String.join("\n",SingularityWorldShaderSource.wrapVertexSource(List.of(input.split("\n"))));
        assertTrue(wrapped.startsWith("#version 150\n#extension GL_ARB_explicit_attrib_location : enable\n#line 7"));
        assertTrue(wrapped.contains("void MS14LensOriginalMain() { gl_Position=vec4(Position,1.0); }"));
        assertTrue(wrapped.contains("void main() { MS14LensOriginalMain(); gl_Position=MS14LensClip(gl_Position); }"));
        assertEquals(1,wrapped.split("void main\\s*\\(",-1).length-1);
        assertTrue(SingularityWorldShaderSource.helperSource().contains("uniform int MS14LensCount"));
        assertTrue(SingularityWorldShaderSource.helperSource().contains("MS14LensSource4"));
    }
    @Test void helperContractIsVertexOnlyAndRetainsDirectiveSafeSourceInjection() {
        String source="#version 150\nvoid main(){}";
        assertEquals(source,SingularityWorldShaderSource.transformPreprocessedVertex(source,false));
        assertThrows(IllegalArgumentException.class,()->SingularityWorldShaderSource.wrapVertexSource(List.of("#version 150","void other(){}")));
        String helper=SingularityWorldShaderSource.helperSource();
        assertTrue(helper.contains("i=4;i>=0;i--"));
        assertTrue(helper.contains("i>=MS14LensCount"));
        assertTrue(helper.contains("return originalClip"));
        assertTrue(helper.contains("for(int j=0;j<24;j++)"));
        assertFalse(helper.contains("SceneColor"));
    }
}
