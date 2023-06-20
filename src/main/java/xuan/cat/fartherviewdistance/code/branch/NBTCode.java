package xuan.cat.fartherviewdistance.code.branch;

import net.minecraft.nbt.CompoundTag;

public final class NBTCode implements xuan.cat.fartherviewdistance.api.branch.BranchNBT {

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
