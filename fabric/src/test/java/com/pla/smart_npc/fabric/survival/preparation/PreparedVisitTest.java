package com.pla.smart_npc.fabric.survival.preparation;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PreparedVisitTest {
    final BlockPos destination=new BlockPos(320,80,0);
    boolean ready(String reason,String phase,String dimension,BlockPos arrival,BlockPos feet){return PreparedExpeditions.shouldReturn(reason,phase,"minecraft:overworld",dimension,destination,arrival,feet);}
    @Test void onlyCompletedExplicitVisitsCanRequestReturn(){
        assertTrue(ready("prepared visit","at_destination","minecraft:overworld",destination,destination));
        assertFalse(ready("requested travel","at_destination","minecraft:overworld",destination,destination));
        assertFalse(ready("prepared visit","outbound","minecraft:overworld",destination,destination));
        assertFalse(ready("prepared visit","returning","minecraft:overworld",destination,destination));
        assertFalse(ready("prepared visit","idle","minecraft:overworld",destination,destination));
    }
    @Test void staleCoordinatesAndUnsupportedDimensionsDoNotInventArrival(){
        assertFalse(ready("prepared visit","at_destination","minecraft:the_nether",destination,destination));
        assertFalse(ready("prepared visit","at_destination","minecraft:overworld",null,destination));
        assertFalse(ready("prepared visit","at_destination","minecraft:overworld",destination.east(1),destination));
        assertFalse(ready("prepared visit","at_destination","minecraft:overworld",destination,destination.east(4)));
    }
}
