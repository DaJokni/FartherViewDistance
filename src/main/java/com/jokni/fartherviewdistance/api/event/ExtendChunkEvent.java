package com.jokni.fartherviewdistance.api.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import com.jokni.fartherviewdistance.api.data.PlayerView;

public abstract class ExtendChunkEvent extends Event {
    private final PlayerView    view;


    public ExtendChunkEvent(PlayerView view) {
        super(!Bukkit.isPrimaryThread());
        this.view   = view;
    }


    public PlayerView getView() {
        return view;
    }
}
