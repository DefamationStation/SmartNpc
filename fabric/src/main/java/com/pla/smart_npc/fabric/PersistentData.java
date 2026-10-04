package com.pla.smart_npc.fabric;

import net.minecraft.nbt.CompoundTag;

/** Saved attachments for the shared gameplay code, supplied by entity/block-entity mixins. */
public interface PersistentData {
    CompoundTag smartNpc$data();

    static CompoundTag get(Object owner) {
        return ((PersistentData) owner).smartNpc$data();
    }
}
