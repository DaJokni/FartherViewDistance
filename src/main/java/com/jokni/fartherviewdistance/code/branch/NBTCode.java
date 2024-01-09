package com.jokni.fartherviewdistance.code.branch;

import com.jokni.fartherviewdistance.api.branch.BranchNBT;
import net.minecraft.nbt.CompoundTag;

public final class NBTCode implements BranchNBT {

    protected CompoundTag tag;

    public NBTCode() {
        this.tag = new CompoundTag();
    }

    public NBTCode(CompoundTag tag) {
        this.tag = tag;
    }


    public CompoundTag getNMSTag() {
        return tag;
    }

    @Override
    public String toString() {
        return tag.toString();
    }
}
