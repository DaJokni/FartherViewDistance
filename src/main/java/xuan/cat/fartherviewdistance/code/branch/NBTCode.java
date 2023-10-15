package xuan.cat.fartherviewdistance.code.branch;

import net.minecraft.nbt.CompoundTag;
import xuan.cat.fartherviewdistance.api.branch.BranchNBT;

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
