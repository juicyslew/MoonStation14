package com.juicyslew.moonstation14.ms14.player_body_control;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.EntityArgument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityArgumentUuidParseTest {
    @Test
    void entityArgumentConsumesACompleteUuidLiteral() throws Exception {
        String literal = "123e4567-e89b-12d3-a456-426614174000";
        StringReader reader = new StringReader(literal);

        EntityArgument.entity().parse(reader);

        assertEquals(literal.length(), reader.getCursor());
    }
}
