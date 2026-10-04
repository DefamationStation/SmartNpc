package com.pla.smart_npc.fabric;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigSpecTest {
    @TempDir Path directory;

    @Test void loadsNumericTomlTypesAndPreservesValidValues() throws Exception {
        var path=directory.resolve("npc.toml");
        Files.writeString(path,"[npc]\nspeed=2\nlimit=12\n");
        var builder=new ConfigSpec.Builder();
        builder.push("npc");
        var speed=builder.defineInRange("speed",1.5,0,10);
        var limit=builder.defineInRange("limit",4,0,100);
        builder.build().load(path);
        assertEquals(2.0,speed.get());
        assertEquals(12,limit.get());
        speed.set(3.5);
        assertTrue(Files.readString(path).contains("3.5"));
    }

    @Test void repairsInvalidValuesWithoutDeletingOtherSettings() throws Exception {
        var path=directory.resolve("npc.toml");
        Files.writeString(path,"limit=-4\nnames=[\"ok\",3]\ncustom=\"keep\"\n");
        var builder=new ConfigSpec.Builder();
        var limit=builder.comment("Maximum NPC count").defineInRange("limit",8,0,100);
        var names=builder.defineList("names",List.of("default"),v->v instanceof String);
        builder.build().load(path);
        assertEquals(8,limit.get());
        assertEquals(List.of("default"),names.get());
        assertThrows(IllegalArgumentException.class,()->limit.set(101));
        String saved=Files.readString(path);
        assertTrue(saved.contains("keep"));
        assertTrue(saved.contains("Maximum NPC count"));
    }
}
