package com.pla.smart_npc.world;
import com.pla.smart_npc.config.SmartNpcConfig;
import com.pla.smart_npc.init.SmartNpcModEntities;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.MobCategory;
public final class PlayerNpcWorldSpawns {
    public static void register(){var c=SmartNpcConfig.getPlayerNpcSpawnConfig();if(c.weight()>0)BiomeModifications.addSpawn(BiomeSelectors.tag(net.minecraft.tags.BiomeTags.IS_OVERWORLD),MobCategory.CREATURE,SmartNpcModEntities.PLAYER_NPC.get(),c.weight(),c.minCount(),c.maxCount());}
}
