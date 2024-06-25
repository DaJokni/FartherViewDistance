package com.jokni.fartherviewdistance.api.event;

import com.jokni.fartherviewdistance.api.data.PlayerView;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

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
