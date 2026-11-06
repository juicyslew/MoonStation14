package com.juicyslew.moonstation14.mixinvalidation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class VanillaWorldCollisionObservationMixinTest {
    @Test void hooksTheExactStockEntityCollisionMethodAndIsRegistered() throws Exception {
        Path root=Path.of(System.getProperty("user.dir"));
        while(root!=null&&!Files.exists(root.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                +"VanillaWorldCollisionObservationMixin.java")))root=root.getParent();
        assertNotNull(root);
        String source=Files.readString(root.resolve("src/main/java/com/juicyslew/moonstation14/mixin/compat/"
                +"VanillaWorldCollisionObservationMixin.java"));
        assertTrue(source.contains("@Mixin(Entity.class)"));
        assertEquals(2,count(source,"@Inject(method = \"collide(Lnet/minecraft/world/phys/Vec3;"));
        assertEquals(1,count(source,"at = @At(\"HEAD\"), require = 1"));
        assertEquals(1,count(source,"at = @At(\"RETURN\"), require = 1"));
        String config=Files.readString(root.resolve("src/main/resources/moonstation14.mixins.json"));
        assertTrue(config.contains("compat.VanillaWorldCollisionObservationMixin"));
    }

    private static int count(String source,String needle) {
        int count=0,index=0;
        while((index=source.indexOf(needle,index))>=0){count++;index+=needle.length();}
        return count;
    }
}
