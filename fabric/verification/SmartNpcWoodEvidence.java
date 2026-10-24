import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import java.util.*;

/** Observes real item spawns near this fixture's tree; never injects or alters drops. */
public final class SmartNpcWoodEvidence implements AutoCloseable {
 private static final List<SmartNpcWoodEvidence> WATCHERS=new ArrayList<>();
 private static boolean registered;
 private final ServerLevel level; private final List<BlockPos> trunks;
 int leafSticks;
 public SmartNpcWoodEvidence(ServerLevel level,List<BlockPos> trunks){
  this.level=level;this.trunks=List.copyOf(trunks);WATCHERS.add(this);
  if(!registered){registered=true;ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
   if(entity instanceof ItemEntity item && item.getItem().is(Items.STICK))for(var watcher:WATCHERS)
    if(watcher.level==world && watcher.trunks.stream().anyMatch(p->p.distSqr(item.blockPosition())<=36))watcher.leafSticks+=item.getItem().getCount();
  });}
 }
 public int plankCost(){return 4+3+(leafSticks>=2?0:2);}
 public int minimumLogs(){return (plankCost()+3)/4;}
 public void close(){WATCHERS.remove(this);}
}
