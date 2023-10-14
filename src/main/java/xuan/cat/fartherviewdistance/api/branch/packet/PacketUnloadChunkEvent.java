package xuan.cat.fartherviewdistance.api.branch.packet;

import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class PacketUnloadChunkEvent extends PacketEvent {
    private static final HandlerList handlers = new HandlerList();
    private final int chunkX;
    private final int chunkZ;

    public PacketUnloadChunkEvent(Player player, ChunkPos chunkPos) {
        super(player);
        this.chunkX = chunkPos.x;
        this.chunkZ = chunkPos.z;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public HandlerList getHandlers() {
        return handlers;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }
}