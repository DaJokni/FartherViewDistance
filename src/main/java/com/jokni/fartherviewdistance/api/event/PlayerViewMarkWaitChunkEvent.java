package com.jokni.fartherviewdistance.api.event;

import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import com.jokni.fartherviewdistance.api.data.PlayerView;

public final class PlayerViewMarkWaitChunkEvent extends ExtendChunkEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private       boolean       cancel  = false;
    private final int           chunkX;
    private final int           chunkZ;


    public PlayerViewMarkWaitChunkEvent(PlayerView view, int chunkX, int chunkZ) {
        super(view);
        this.chunkX  = chunkX;
        this.chunkZ  = chunkZ;
    }


    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
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