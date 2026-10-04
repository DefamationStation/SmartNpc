import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.init.SmartNpcModEntities;
import com.pla.smart_npc.fabric.survival.*;
import com.pla.smart_npc.util.*;
import com.pla.smart_npc.event.PlayerNpcChestProtectEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Real server entities and a client-to-server container click in an isolated test world. */
public final class SmartNpcSurvivalChecks {
    static PlayerNpcEntity owner;
    static BlockPos chestPos;
    static int phase;
    static volatile boolean networkTheftPassed;
    static void check(boolean value, String message) { SmartNpcFunctional.check(value, message); }
    static PlayerNpcEntity npc(ServerLevel level, ServerPlayer player) {
        var npc = SmartNpcModEntities.PLAYER_NPC.get().create(level, EntitySpawnReason.COMMAND);
        npc.setUsername("Dream"); // Includes both player-hunting and prank interests in the default config.
        npc.setPos(player.getX()+2,player.getY(),player.getZ()); npc.setNoAi(true); npc.setNoGravity(true);
        level.addFreshEntity(npc); return npc;
    }
    public static void setup(ServerLevel level, ServerPlayer player) {
        var legacy = PlayerNpcBuildLayoutLoader.getLayout("smart_npc:easy_survival_house").orElseThrow();
        check(!legacy.footprint().isEmpty() && legacy.requiredBlocks()>0,"legacy blueprint loads real blocks instead of air");
        check(legacy.blocks().stream().anyMatch(b -> b.state().getBlock() instanceof StairBlock
            && b.state().getValue(StairBlock.FACING)!=net.minecraft.core.Direction.NORTH),"legacy blueprint orientations survive palette conversion");
        var starter = PlayerNpcBuildLayoutLoader.getLayout("smart_npc:starter_survival_cabin").orElseThrow();
        check(starter.blocks().stream().anyMatch(b->b.state().is(Blocks.CHEST)) && starter.blocks().stream().anyMatch(b->b.state().is(Blocks.FURNACE))
            && starter.blocks().stream().filter(b->b.state().getBlock() instanceof BedBlock).count()==2,"starter cabin contains bed, storage and furnace");
        var victim=npc(level,player); var neighbour=npc(level,player);
        try {
            var field=net.minecraft.world.entity.Mob.class.getDeclaredField("goalSelector");field.setAccessible(true);
            var selector=(net.minecraft.world.entity.ai.goal.GoalSelector)field.get(victim);
            check(selector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof com.pla.smart_npc.entity.goal.TrollHitGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.IronGolemTrollGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.LootNearbyChestGoal
                || g.getGoal() instanceof com.pla.smart_npc.entity.goal.AiBudgetWaitingStrollGoal),"random prank, raid and decorative waiting-stroll goals are not registered");
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        victim.setTarget(player); check(victim.getTarget()==null,"hunter personality cannot acquire an unprovoked player target");
        victim.setTarget(neighbour); check(victim.getTarget()==null && !victim.canAttack(neighbour),"NPC neighbours are peaceful without an offence");
        float health=neighbour.getHealth(); check(!victim.doHurtTarget(level,neighbour) && neighbour.getHealth()==health,"direct neutral melee is blocked");
        var golem=net.minecraft.world.entity.EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
        check(!victim.canAttack(golem),"neutral golems are not prank targets");
        check(!com.pla.smart_npc.entity.ai.CautiousThreatAi.isValidThreat(victim,player),"visitors are not automatic cautious threats");
        check(victim.hurtServer(level,level.damageSources().mobAttack(neighbour),1),"assault fixture applies actual damage");
        check(victim.getTarget()==neighbour && SocialSafety.hasCause(victim,neighbour),"actual assault permits defensive targeting");
        check(com.pla.smart_npc.entity.ai.CautiousThreatAi.isValidThreat(victim,neighbour),"a proven attacker is a cautious threat");
        check(SocialSafety.memory(victim).cause(neighbour.getUUID(),level.getGameTime()+1200).isEmpty(),"defence expires instead of creating a permanent feud");
        victim.discard(); neighbour.discard();

        var vandalOwner=npc(level,player);
        var origin=player.blockPosition().offset(8,0,0);
        PlayerNpcHomeUtil.setHome(vandalOwner,new PlayerNpcHomeUtil.HomeArea(origin,starter.width(),starter.depth()),starter.id());
        level.setBlockAndUpdate(origin,Blocks.DIRT.defaultBlockState());
        check(player.gameMode.destroyBlock(origin),"terrain fixture broken through player game mode");
        check(!SocialSafety.hasCause(vandalOwner,player),"ordinary terrain in a home plot is not treated as vandalism");
        level.setBlockAndUpdate(origin,Blocks.OAK_PLANKS.defaultBlockState());
        check(player.gameMode.destroyBlock(origin),"constructed fixture broken through player game mode");
        check(SocialSafety.hasCause(vandalOwner,player),"damage to a tracked matching base block permits defence");
        vandalOwner.discard();

        owner=npc(level,player); chestPos=player.blockPosition().offset(-2,0,0);
        level.setBlockAndUpdate(chestPos,Blocks.CHEST.defaultBlockState()); owner.setOwnedChestPos(chestPos);
        var chest=(ChestBlockEntity)level.getBlockEntity(chestPos); chest.setItem(0,new ItemStack(Items.COAL,3)); chest.setChanged();
        var recorded=chest.saveWithFullMetadata(level.registryAccess());chest.clearContent();
        try {
            var method=com.pla.smart_npc.entity.goal.BuildHouseGoal.class.getDeclaredMethod("applyBlockEntityData",ServerLevel.class,BlockPos.class,net.minecraft.nbt.CompoundTag.class);
            method.setAccessible(true);method.invoke(new com.pla.smart_npc.entity.goal.BuildHouseGoal(owner),level,chestPos,recorded);
            check(chest.isEmpty(),"constructed storage cannot copy free inventory from a blueprint");
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        chest.setItem(0,new ItemStack(Items.COAL,3));chest.setChanged();
        PlayerNpcChestProtectEvent.reportOffense(level,chestPos,player,"opened");
        check(owner.getTarget()==null && !SocialSafety.hasCause(owner,player),"opening owned storage warns without attacking");
        check(!SocialSafety.removedAny(List.of(new ItemStack(Items.COAL,3)),List.of(new ItemStack(Items.COAL,1),new ItemStack(Items.COAL,2))),"chest rearrangement is not theft");
        check(!SocialSafety.removedAny(List.of(new ItemStack(Items.COAL,3)),List.of(new ItemStack(Items.COAL,4))),"depositing supplies is not theft");
        SocialSafety.opened(player,chestPos); player.openMenu(chest);
    }
    public static void clientTick(Minecraft mc,double elapsed) {
        if(phase==0 && elapsed>1 && mc.player.containerMenu instanceof ChestMenu menu){
            phase=1;
            mc.gameMode.handleContainerInput(menu.containerId,0,0,ContainerInput.QUICK_MOVE,mc.player);
        }
        if(phase==1 && elapsed>3){
            phase=2;mc.player.closeContainer();
            mc.getSingleplayerServer().execute(()->{try {
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                check(SocialSafety.memory(owner).cause(player.getUUID(),owner.level().getGameTime()).orElseThrow()==GrievanceMemory.Cause.THEFT,
                    "real network chest click records actual theft");
                check(((ChestBlockEntity)owner.level().getBlockEntity(chestPos)).getItem(0).isEmpty(),"theft corresponds to actual removed contents");
                networkTheftPassed=true;owner.discard();
            }catch(Throwable t){t.printStackTrace();}});
        }
    }
}
