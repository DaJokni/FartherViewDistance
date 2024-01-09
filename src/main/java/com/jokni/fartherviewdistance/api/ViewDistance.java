package com.jokni.fartherviewdistance.api;

import com.jokni.fartherviewdistance.code.ChunkIndex;
import com.jokni.fartherviewdistance.code.data.PlayerChunkView;
import org.bukkit.entity.Player;
import com.jokni.fartherviewdistance.api.data.PlayerView;

public final class ViewDistance {
    private ViewDistance() {
    }


    public static PlayerView getPlayerView(Player player) {
        PlayerChunkView view = ChunkIndex.getChunkServer().getView(player);
        return view != null ? view.viewAPI : null;
    }
}
