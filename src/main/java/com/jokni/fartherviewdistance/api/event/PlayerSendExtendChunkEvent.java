package com.jokni.fartherviewdistance.api.event;

import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import com.jokni.fartherviewdistance.api.branch.BranchChunk;
import com.jokni.fartherviewdistance.api.data.PlayerView;

/**
 * Event to be called when extended chunks are sent to the player
 */
public final class PlayerSendExtendChunkEvent extends ExtendChunkEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private       boolean       cancel  = false;
    private final BranchChunk   chunk;
    private final World         world;


    public PlayerSendExtendChunkEvent(PlayerView view, BranchChunk chunk, World world) {
        super(view);
        this.chunk  = chunk;
        this.world  = world;
    }


    public BranchChunk getChunk() {
        return chunk;
    }

    public World getWorld() {
        return world;
    }

    public boolean isCancelled() {
        return cancel;
    }

    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
    }


    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
