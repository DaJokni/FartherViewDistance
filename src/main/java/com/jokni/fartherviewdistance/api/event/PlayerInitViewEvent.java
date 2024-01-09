package com.jokni.fartherviewdistance.api.event;

import com.jokni.fartherviewdistance.api.data.PlayerView;
import org.bukkit.event.HandlerList;

public final class PlayerInitViewEvent extends ExtendChunkEvent {
    private static final HandlerList handlers = new HandlerList();


    public PlayerInitViewEvent(PlayerView view) {
        super(view);
    }


    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
