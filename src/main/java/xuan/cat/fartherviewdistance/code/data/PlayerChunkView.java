package xuan.cat.fartherviewdistance.code.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import xuan.cat.fartherviewdistance.api.branch.BranchPacket;
import xuan.cat.fartherviewdistance.api.data.PlayerView;
import xuan.cat.fartherviewdistance.api.event.*;
import xuan.cat.fartherviewdistance.code.ChunkServer;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMap;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

import java.util.Map;

/** Player View Calculator */
public final class PlayerChunkView {
    public  final PlayerView viewAPI;
    private final Player player;
    private final BranchPacket branchPacket;
    /** View Calculator */
    private final ViewMap mapView;
    /** Forced Visual Field Distance */
    public Integer forciblyMaxDistance = null;
    /** Compulsory data transfer per second (in bytes) */
    public Integer forciblySendSecondMaxBytes = null;
    /** Final View Distance */
    private int lastDistance = 0;
    private final ConfigData configData;
    /** Delayed timestamp */
    private long delayTime;
    /** Unloaded */
    private boolean isUnload = false;
    /** Player's last world */
    private World lastWorld;
    /** Old location */
    private Location oldLocation = null;
    /** Whether moved to ofast */
    public volatile boolean moveTooFast = false;
    /** Network traffic monitor */
    public final NetworkTraffic networkTraffic = new NetworkTraffic();
    /** Network speed */
    public final NetworkSpeed networkSpeed = new NetworkSpeed();
    /** Waiting to send */
    public volatile boolean waitSend = false;
    /** Sync key */
    public volatile long syncKey;
    /** Create report */
    public final CumulativeReport cumulativeReport = new CumulativeReport();
    /** Check permission */
    private Long permissionsCheck = null;
    /** permission hit */
    private Integer permissionsHit = null;
    /** Permissions needed to be checked */
    public boolean permissionsNeed = true;


    public PlayerChunkView(Player player, ConfigData configData, ViewShape viewShape, BranchPacket branchPacket) {
        this.player = player;
        this.configData = configData;
        this.branchPacket = branchPacket;
        this.mapView = configData.viewDistanceMode.createMap(viewShape);
        this.lastWorld = player.getWorld();
        this.syncKey = ChunkServer.random.nextLong();

        updateDistance();
        delay();

        mapView.setCenter(player.getLocation());

        this.viewAPI = new PlayerView(this);
        Bukkit.getPluginManager().callEvent(new PlayerInitViewEvent(viewAPI));
    }


    private int serverDistance() {
        return configData.serverViewDistance <= -1 ? (Bukkit.getViewDistance() + 1) : configData.serverViewDistance;
    }


    public void updateDistance() {
        updateDistance(false);
    }
    private void updateDistance(boolean forcibly) {
        int newDistance = max();
        synchronized (mapView) {
            mapView.serverDistance = serverDistance();
            if (newDistance < mapView.serverDistance) {
                newDistance = mapView.serverDistance;
            }
        }
        if (forcibly || lastDistance != newDistance) {
            mapView.markOutsideWait(newDistance);
            int gapDistance = lastDistance - newDistance;
            lastDistance = newDistance;
            mapView.extendDistance = newDistance;
            if (gapDistance > 0) {
                mapView.completedDistance.addAndGet(-gapDistance);
            }
            PlayerSendViewDistanceEvent event = new PlayerSendViewDistanceEvent(viewAPI, newDistance);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                branchPacket.sendViewDistance(player, event.getDistance());
            }
        }
    }


    private double square(double num) {
        return num * num;
    }


    public boolean overSpeed() {
        return overSpeed(player.getLocation());
    }
    public boolean overSpeed(Location location) {
        ConfigData.World configWorld = configData.getWorld(lastWorld.getName());
        if (configWorld.speedingNotSend == -1) {
            return false;
        } else {
            double speed = 0;

            if (oldLocation != null && oldLocation.getWorld() == location.getWorld())
                speed = Math.sqrt(square(oldLocation.getX() - location.getX()) + square(oldLocation.getZ() - location.getZ()));
            oldLocation = location;

            // Check if the speed is too fast (Horizontal Flight Speed > ? square)
            return speed > configWorld.speedingNotSend;
        }
    }


    public synchronized boolean move() {
        return move(player.getLocation());
    }
    public synchronized boolean move(Location location) {
        return move(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
    public synchronized boolean move(int chunkX, int chunkZ) {
        if (isUnload)
            return false;

        if (player.getWorld() != lastWorld) {
            unload();
            return false;
        }

        int hitX;
        int hitZ;
        PlayerSendUnloadChunkEvent event;
        for (long chunkKey : mapView.movePosition(chunkX, chunkZ)) {
            hitX = ViewMap.getX(chunkKey);
            hitZ = ViewMap.getZ(chunkKey);
            event = new PlayerSendUnloadChunkEvent(viewAPI, hitX, hitZ);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled())
                branchPacket.sendUnloadChunk(player, hitX, hitZ);
        }

        return true;
    }


    public void delay() {
        delay(System.currentTimeMillis() + configData.getWorld(lastWorld.getName()).delayBeforeSend);
    }
    public void delay(long delayTime) {
        this.delayTime = delayTime;
    }

    public Long next() {
        if (player.getWorld() != lastWorld) {
            unload();
            return null;
        }

        if (isUnload)
            return null;

        if (delayTime >= System.currentTimeMillis())
            return null;

        Long        chunkKey    = mapView.get();
        if (chunkKey == null)
            return null;

        WorldBorder worldBorder         = lastWorld.getWorldBorder();
        int         chunkX              = ViewMap.getX(chunkKey);
        int         chunkZ              = ViewMap.getZ(chunkKey);
        Location    borderCenter        = worldBorder.getCenter();
        int         borderSizeRadius    = (int) worldBorder.getSize() / 2;
        int         borderMinX          = ((borderCenter.getBlockX() - borderSizeRadius) >> 4) - 1;
        int         borderMaxX          = ((borderCenter.getBlockX() + borderSizeRadius) >> 4) + 1;
        int         borderMinZ          = ((borderCenter.getBlockZ() - borderSizeRadius) >> 4) - 1;
        int         borderMaxZ          = ((borderCenter.getBlockZ() + borderSizeRadius) >> 4) + 1;

        return borderMinX <= chunkX && chunkX <= borderMaxX && borderMinZ <= chunkZ && chunkZ <= borderMaxZ ? chunkKey : null;
    }


    public void unload() {
        if (!isUnload) {
            delay();
            syncKey = ChunkServer.random.nextLong();
            isUnload = true;
            branchPacket.sendViewDistance(player, 0);
            branchPacket.sendViewDistance(player, mapView.extendDistance);
            mapView.clear();
        }
    }


    public boolean install() {
        if (isUnload) {
            delay();
            mapView.clear();
            updateDistance(true);

            lastWorld   = player.getWorld();
            isUnload    = false;
            return true;
        }
        return false;
    }


    public void send(int x, int z) {
        PlayerViewMarkSendChunkEvent event = new PlayerViewMarkSendChunkEvent(viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            mapView.markSendPosition(x, z);
    }


    public void remove(int x, int z) {
        PlayerViewMarkWaitChunkEvent event = new PlayerViewMarkWaitChunkEvent(viewAPI, x, z);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled())
            mapView.markWaitPosition(x, z);
    }


    public int max() {
        ConfigData.World configWorld = configData.getWorld(lastWorld.getName());
        int viewDistance = configWorld.maxViewDistance;
        int clientViewDistance = player.getClientViewDistance();
        Integer forciblyViewDistance = forciblyMaxDistance;

        PlayerCheckViewDistanceEvent event = new PlayerCheckViewDistanceEvent(viewAPI, serverDistance(), clientViewDistance, viewDistance);
        Bukkit.getPluginManager().callEvent(event);

        if (event.getForciblyDistance() != null) {
            viewDistance = event.getForciblyDistance();
        } else if (forciblyViewDistance != null) {
            viewDistance = forciblyViewDistance;
        } else if (permissionsNeed || (configData.permissionsPeriodicMillisecondCheck != -1 && (permissionsCheck == null || permissionsCheck <= System.currentTimeMillis() - configData.permissionsPeriodicMillisecondCheck))) {
            permissionsNeed = false;
            permissionsCheck = System.currentTimeMillis();
            permissionsHit = null;
            // Check Permissions Node
            for (Map.Entry<String, Integer> permissionsNodeEntry : configData.permissionsNodeList) {
                int permissionViewDistance = permissionsNodeEntry.getValue();
                if (permissionViewDistance <= configWorld.maxViewDistance && (permissionsHit == null || permissionViewDistance > permissionsHit) && player.hasPermission(permissionsNodeEntry.getKey())) {
                    permissionsHit = permissionViewDistance;
                }
            }
        }

        if (permissionsHit != null)
            viewDistance = permissionsHit;

        if (viewDistance > clientViewDistance)
            viewDistance = clientViewDistance;
        if (viewDistance < 1)
            viewDistance = 1;

        return viewDistance;
    }

    public void clear() {
        mapView.clear();
    }


    public void recalculate() {
        mapView.markOutsideWait(mapView.serverDistance);
    }

    public ViewMap getMap() {
        return mapView;
    }

    public World getLastWorld() {
        return lastWorld;
    }

    public Player getPlayer() {
        return player;
    }

    public long getDelayTime() {
        return delayTime;
    }
}
