package com.pla.smart_npc.util;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.sounds.BlockSoundSet;
import net.minecraft.world.level.block.state.BlockState;

public final class PlayerNpcBlockSoundUtil {
    private PlayerNpcBlockSoundUtil() {
    }

    public static void playMiningHitSound(ServerLevel serverLevel, BlockPos pos, BlockState state, PlayerNpcEntity playerNpc) {
        BlockSoundSet soundType = state.getSounds(serverLevel);
        soundType.hitSound().ifPresent(sound -> serverLevel.playSound(
                null,
                pos,
                sound.value(),
                SoundSource.BLOCKS,
                (soundType.volume() + 1.0F) / 8.0F,
                soundType.pitch() * 0.5F
        ));
    }

    public static void playPlaceSound(ServerLevel serverLevel, BlockPos pos, BlockState state, PlayerNpcEntity playerNpc) {
        BlockSoundSet soundType = state.getSounds(serverLevel);
        soundType.placeSound().ifPresent(sound -> serverLevel.playSound(
                null,
                pos,
                sound.value(),
                SoundSource.BLOCKS,
                (soundType.volume() + 1.0F) / 2.0F,
                soundType.pitch() * 0.8F
        ));
    }
}
