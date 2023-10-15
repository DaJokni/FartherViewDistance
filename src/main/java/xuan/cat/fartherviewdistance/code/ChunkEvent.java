package xuan.cat.fartherviewdistance.code;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import xuan.cat.fartherviewdistance.api.branch.BranchMinecraft;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketKeepAliveEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketUnloadChunkEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketViewDistanceEvent;
import xuan.cat.fartherviewdistance.code.data.PlayerChunkView;

public final class ChunkEvent implements Listener {
    private final ChunkServer chunkServer;
    private final BranchPacket branchPacket;
    private final BranchMinecraft branchMinecraft;


    public ChunkEvent(ChunkServer chunkServer, BranchPacket branchPacket, BranchMinecraft branchMinecraft) {
        this.chunkServer = chunkServer;
        this.branchPacket = branchPacket;
        this.branchMinecraft = branchMinecraft;
    }

    /**
     * @param event Player join
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void event(PlayerJoinEvent event) {
        // 注入代碼
        branchMinecraft.injectPlayer(event.getPlayer());
    }

    /**
     * @param event Player teleport
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(PlayerTeleportEvent event) {
        chunkServer.unloadView(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /**
     * @param event Player move
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(PlayerMoveEvent event) {
//        chunkServer.unloadView(event.getPlayer(), event.getFrom(), event.getTo());
    }

    /**
     * @param event Player respawn
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void event(PlayerRespawnEvent event) {
        chunkServer.respawnView(event.getPlayer());
    }



    /**
     * @param event Player join
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerJoinEvent event) {
        chunkServer.initView(event.getPlayer());
    }
    /**
     * @param event Player quit
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(PlayerQuitEvent event) {
        chunkServer.clearView(event.getPlayer());
    }



    /**
     * @param event World init
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(WorldInitEvent event) {
        chunkServer.initWorld(event.getWorld());
    }

    /**
     * @param event World unload
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void on(WorldUnloadEvent event) {
        chunkServer.clearWorld(event.getWorld());
    }



    /**
     * @param event Chunk Unloading Packets
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PacketUnloadChunkEvent event) {
        PlayerChunkView view = chunkServer.getView(event.getPlayer());
        if (view.viewAPI.isChunkSend(event.getChunkX(), event.getChunkZ()))
            event.setCancelled(true);
    }

    /**
     * @param event View distance packets
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PacketViewDistanceEvent event) {
        PlayerChunkView view = chunkServer.getView(event.getPlayer());
        int viewDistance = event.getViewDistance();
        if (view != null && view.getMap().extendDistance != viewDistance && viewDistance != 0)
            event.setCancelled(true);
    }

    /**
     * @param event Updating chunk packets
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PacketMapChunkEvent event) {
        chunkServer.packetEvent(event.getPlayer(), event);
    }

//    /**
//     * @param event Update lighting packets
//     */
//    @EventHandler(priority = EventPriority.NORMAL)
//    public void on(PacketLightUpdateEvent event) {
//        chunkServer.packetEvent(event.getPlayer(), event);
//    }

    /**
     * @param event Maintain connection packets
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void on(PacketKeepAliveEvent event) {
        long id = event.getId();
        PlayerChunkView view = chunkServer.getView(event.getPlayer());
        if (view != null) {
            synchronized (view.networkSpeed) {
                if (view.networkSpeed.pingID != null && view.networkSpeed.pingID == id) {
                    view.networkSpeed.lastPing = Math.max(1, (int) (System.currentTimeMillis() - view.networkSpeed.pingTimestamp));
                    view.networkSpeed.pingID = null;
                    event.setCancelled(true);
                } else if (view.networkSpeed.speedID != null && view.networkSpeed.speedID == id) {
                    view.networkSpeed.add(Math.max(1, (int) (System.currentTimeMillis() - view.networkSpeed.speedTimestamp) - view.networkSpeed.lastPing), view.networkSpeed.speedConsume);
                    view.networkSpeed.speedConsume = 0;
                    view.networkSpeed.speedID = null;
                    event.setCancelled(true);
                }
            }
        }
    }
}
