import com.pla.smart_npc.clazz.PlayerNpcInterest;
import com.pla.smart_npc.config.SmartNpcNamesConfig;
import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.entity.goal.CheckHomeSuppliesGoal;
import com.pla.smart_npc.entity.goal.ManageHomeBaseGoal;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.Optional;

/** Native supply predicates/transfers, not a claim of autonomous chest navigation. */
public final class SmartNpcHomeSupplyChecks {
    private SmartNpcHomeSupplyChecks() { }
    private static void check(boolean value, String message) { SmartNpcFunctional.check(value, message); }
    private static void withdraw(ServerLevel level, PlayerNpcEntity npc, BlockPos pos) throws ReflectiveOperationException {
        var goal = new CheckHomeSuppliesGoal(npc);
        var target = CheckHomeSuppliesGoal.class.getDeclaredField("targetPos"); target.setAccessible(true); target.set(goal,pos);
        var action = CheckHomeSuppliesGoal.class.getDeclaredMethod("withdrawFromChest",ServerLevel.class); action.setAccessible(true);
        check((boolean)action.invoke(goal,level),"native home supply transfer moves a needed real stack");
    }
    public static void run(ServerLevel level, ServerPlayer player) {
        var npc=SmartNpcModEntities.PLAYER_NPC.get().create(level,EntitySpawnReason.COMMAND);
        var name=SmartNpcNamesConfig.getPlayerNpcNameEntries().stream().map(SmartNpcNamesConfig::parseNameEntry)
                .flatMap(Optional::stream).filter(n->!n.interests().contains(PlayerNpcInterest.FISHING)
                        && !n.interests().contains(PlayerNpcInterest.BUILDING)).findFirst().orElseThrow();
        npc.setUsername(name.skinName());npc.setNoAi(true);npc.setNoGravity(true);
        var pos=new BlockPos(player.getBlockX()+110,305,player.getBlockZ());
        npc.setPos(pos.getX()+0.5,305,pos.getZ()+1.5);npc.getInventory().clearContent();
        for(var slot:EquipmentSlot.values())npc.setItemSlot(slot,ItemStack.EMPTY);
        level.getChunk(pos);level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());npc.setOwnedChestPos(pos);
        var chest=(ChestBlockEntity)level.getBlockEntity(pos);chest.clearContent();
        try {
            check(!npc.hasInterest(PlayerNpcInterest.FISHING),"home supply fixture is a non-fisher");
            chest.setItem(0,new ItemStack(Items.FISHING_ROD));chest.setItem(1,new ItemStack(Items.STRING,8));
            withdraw(level,npc,pos);
            check(npc.getInventory().countItem(Items.FISHING_ROD)==1 && chest.getItem(0).isEmpty(),
                    "non-fisher withdraws a real stored rod for its food need");
            check(npc.getInventory().countItem(Items.STRING)==0 && chest.getItem(1).getCount()==8,
                    "finding a rod avoids withdrawing redundant crafting string from the same snapshot");
            npc.getInventory().clearContent();chest.setItem(0,ItemStack.EMPTY);
            withdraw(level,npc,pos);
            check(npc.getInventory().countItem(Items.STRING)==2 && chest.getItem(1).getCount()==6,
                    "non-fisher takes exactly its two-string rod shortage from real storage");
            var keep=ManageHomeBaseGoal.class.getDeclaredMethod("shouldKeepStack",PlayerNpcEntity.class,ServerLevel.class,ItemStack.class);
            keep.setAccessible(true);
            check((boolean)keep.invoke(null,npc,level,new ItemStack(Items.STRING))
                            && (boolean)keep.invoke(null,npc,level,new ItemStack(Items.OAK_LOG)),
                    "deposit policy preserves string and wood committed to a missing food rod");
            npc.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
            npc.getInventory().addItem(new ItemStack(Items.BREAD,4));
            check((boolean)keep.invoke(null,npc,level,new ItemStack(Items.FISHING_ROD)),
                    "reserve-ready non-fisher preserves its reusable food rod for later trips");
            npc.getInventory().clearContent();npc.getInventory().setItem(0,new ItemStack(Items.COAL,7));
            // Native deposits only run above half capacity; retain that admission condition.
            for(int slot=1;slot<npc.getInventory().getContainerSize();slot++)
                npc.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,4));
            chest.clearContent();
            SurvivalTasks.request(npc,3);
            var deposit=ManageHomeBaseGoal.class.getDeclaredMethod("depositNextStack",Container.class);deposit.setAccessible(true);
            check((int)deposit.invoke(new ManageHomeBaseGoal(npc,true),chest)>0,
                    "native deposit still transfers unrelated actual supplies");
            check(npc.getInventory().countItem(Items.COAL)==7 && chest.countItem(Items.COAL)==0,
                    "native deposit cannot undo a pending manual coal target");
            SurvivalTasks.memory(npc).active=false;
            check((int)deposit.invoke(new ManageHomeBaseGoal(npc,true),chest)==3
                            && npc.getInventory().countItem(Items.COAL)==4 && chest.countItem(Items.COAL)==3,
                    "coal returns to ordinary conserved storage transfers after the task ends");
            System.out.println("SMARTNPC_HOME_SUPPLY_COMPLETE rod, string, recipe supplies and coal retention");
        } catch(ReflectiveOperationException exception){throw new RuntimeException(exception);}
        finally{npc.discard();level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());}
    }
}
