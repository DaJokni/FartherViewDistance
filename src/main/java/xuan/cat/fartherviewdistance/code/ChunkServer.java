package xuan.cat.fartherviewdistance.code;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import xuan.cat.fartherviewdistance.api.branch.*;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketEvent;
import xuan.cat.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import xuan.cat.fartherviewdistance.api.event.PlayerSendExtendChunkEvent;
import xuan.cat.fartherviewdistance.code.data.*;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewMap;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Block server
 */
public final class ChunkServer {
    private final ConfigData configData;
    private final Plugin plugin;
    private boolean running = true;
    public final BranchMinecraft branchMinecraft;
    public final BranchPacket branchPacket;
    private final Set<BukkitTask> bukkitTasks = ConcurrentHashMap.newKeySet();
    /** Random Number Generator */
    public static final Random random = new Random();
    /** Multithreaded services */
    private ScheduledExecutorService multithreadedService;
    /** Allow running threads */
    private AtomicBoolean multithreadedCanRun;
    /** View calculator for each player */
    public final Map<Player, PlayerChunkView> playersViewMap = new ConcurrentHashMap<>();
    /** Server Network Traffic */
    private final NetworkTraffic serverNetworkTraffic = new NetworkTraffic();
    /** Network traffic per world */
    private final Map<World, NetworkTraffic> worldsNetworkTraffic = new ConcurrentHashMap<>();
    /** 最後一次的全部世界 ("the last worlds" or something)*/
    private List<World> lastWorldList = new ArrayList<>();
    /** Number of server generated chunks */
    private final AtomicInteger serverGeneratedChunk = new AtomicInteger(0);
    /** Server reports */
    public final CumulativeReport serverCumulativeReport = new CumulativeReport();
    /** Number of generated chunks per world */
    private final Map<World, AtomicInteger> worldsGeneratedChunk = new ConcurrentHashMap<>();
    /** Every World Report */
    public final Map<World, CumulativeReport> worldsCumulativeReport = new ConcurrentHashMap<>();
    /** Waiting to go to the main thread */
    private final Set<Runnable> waitMoveSyncQueue = ConcurrentHashMap.newKeySet();
    /** Time spent per thread */
    public final Map<Integer, CumulativeReport> threadsCumulativeReport = new ConcurrentHashMap<>();
    /** All threads */
    public final Set<Thread> threadsSet = ConcurrentHashMap.newKeySet();
    /** Global stop */
    public volatile boolean globalPause = false;
    /** Language */
    public final LangFiles lang = new LangFiles();
    /** Shape of view */
    private final ViewShape viewShape;


    public ChunkServer(ConfigData configData, Plugin plugin, ViewShape viewShape, BranchMinecraft branchMinecraft, BranchPacket branchPacket) {
        this.configData = configData;
        this.plugin = plugin;
        this.branchMinecraft = branchMinecraft;
        this.branchPacket = branchPacket;
        this.viewShape = viewShape;

        BukkitScheduler scheduler = Bukkit.getScheduler();
        bukkitTasks.add(scheduler.runTaskTimer(plugin, this::tickSync, 0, 1));
        bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickAsync, 0, 1));
        bukkitTasks.add(scheduler.runTaskTimerAsynchronously(plugin, this::tickReport, 0, 20));
        reloadMultithreaded();
    }


    /**
     * Initialize the player chunk view
     */
    public PlayerChunkView initView(Player player) {
        PlayerChunkView view = new PlayerChunkView(player, configData, viewShape, branchPacket);
        playersViewMap.put(player, view);
        return view;
    }
    /**
     * Clear player chunk view
     */
    public void clearView(Player player) {
        playersViewMap.remove(player);
    }
    /**
     * @return Player Chunk View
     */
    public PlayerChunkView getView(Player player) {
        return playersViewMap.get(player);
    }


    /**
     * Reload the multithreaded
     */
    public synchronized void reloadMultithreaded() {
        // Stop processing the last execution sequel first
        if (multithreadedCanRun != null)
            multithreadedCanRun.set(false);
        if (multithreadedService != null) {
            multithreadedService.shutdown();
        }
        threadsCumulativeReport.clear();
        threadsSet.clear();

        playersViewMap.values().forEach(view -> view.waitSend = false);

        // Create new executors
        AtomicBoolean canRun = new AtomicBoolean(true);
        multithreadedCanRun = canRun;
        multithreadedService = Executors.newScheduledThreadPool(configData.asyncThreadAmount + 1);

        multithreadedService.schedule(() -> {
            Thread thread = Thread.currentThread();
            thread.setName("FartherViewDistance View thread");
            thread.setPriority(3);
            threadsSet.add(thread);
            runView(canRun);
        }, 0, TimeUnit.MILLISECONDS);

        for (int index = 0 ; index < configData.asyncThreadAmount ; index++) {
            int threadNumber = index;
            CumulativeReport threadCumulativeReport = new CumulativeReport();
            threadsCumulativeReport.put(index, threadCumulativeReport)  ;
            // 每個執行續每 50 毫秒響應一次
            multithreadedService.schedule(() -> {
                Thread thread = Thread.currentThread();
                thread.setName("FartherViewDistance AsyncTick thread #" + threadNumber);
                thread.setPriority(2);
                threadsSet.add(thread);
                runThread(canRun, threadCumulativeReport);
            }, 0, TimeUnit.MILLISECONDS);
        }
    }


    /**
     * Initialize the world
     */
    public void initWorld(World world) {
        worldsNetworkTraffic.put(world, new NetworkTraffic());
        worldsCumulativeReport.put(world, new CumulativeReport());
        worldsGeneratedChunk.put(world, new AtomicInteger(0));
    }
    /**
     * Clear the World
     */
    public void clearWorld(World world) {
        worldsNetworkTraffic.remove(world);
        worldsCumulativeReport.remove(world);
        worldsGeneratedChunk.remove(world);
    }



    /**
     * Synchronized ticking
     * Mainly used to handle some non-asynchronous operations
     */
    private void tickSync() {
        List<World> worldList = Bukkit.getWorlds();
        Collections.shuffle(worldList);
        lastWorldList = worldList;
        waitMoveSyncQueue.removeIf(runnable -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return true;
        });
    }


    /**
     * Asynchronous ticking
     */
    private void tickAsync() {
        // 將所有網路流量歸零
        serverNetworkTraffic.next();
        worldsNetworkTraffic.values().forEach(NetworkTraffic::next);
        playersViewMap.values().forEach(view -> {
            view.networkTraffic.next();
            view.networkSpeed.next();
        });
        serverGeneratedChunk.set(0);
        worldsGeneratedChunk.values().forEach(generatedChunk -> generatedChunk.set(0));
    }


    /**
     * Synchronized Report Ticker
     */
    private void tickReport() {
        serverCumulativeReport.next();
        worldsCumulativeReport.values().forEach(CumulativeReport::next);
        playersViewMap.values().forEach(view -> view.cumulativeReport.next());
        threadsCumulativeReport.values().forEach(CumulativeReport::next);
    }


    /**
     * Steady execution every 50 milliseconds
     */
    private void runView(AtomicBoolean canRun) {
        // Main loop
        while (canRun.get()) {
            // Start time
            long startTime = System.currentTimeMillis();

            try {
                // The view of each player
                playersViewMap.forEach((player, view) -> {
                    if (!view.install())
                        view.updateDistance();
                    view.moveTooFast = view.overSpeed();
                });
            } catch (Exception exception) {
                exception.printStackTrace();
            }

            // End time
            long endTime = System.currentTimeMillis();
            // Maximum time consumption 50 ms
            long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    /**
     * Multi-execution continuous ticking
     */
    private void runThread(AtomicBoolean canRun, CumulativeReport threadCumulativeReport) {
        // Main loop
        while (canRun.get()) {
            // Start time
            long startTime = System.currentTimeMillis();
            // Effective time
            long effectiveTime = startTime + 50;

            if (!globalPause) {
                try {
                    // All Worlds
                    List<World> worldList = lastWorldList;
                    // All player views
                    List<PlayerChunkView> viewList = Arrays.asList(playersViewMap.values().toArray(new PlayerChunkView[0]));
                    Collections.shuffle(viewList);
                    // Moving player
                    for (PlayerChunkView view : viewList) {
                        view.move();
                    }
                    // Each player view of each world
                    Map<World, List<PlayerChunkView>> worldsViews = new HashMap<>();
                    for (PlayerChunkView view : viewList) {
                        worldsViews.computeIfAbsent(view.getLastWorld(), key -> new ArrayList<>()).add(view);
                    }

                    handleServer: {
                        for (World world : worldList) {
                            // World Configuration
                            ConfigData.World configWorld = configData.getWorld(world.getName());
                            if (!configWorld.enable)
                                continue;
                            // World Report
                            CumulativeReport worldCumulativeReport = worldsCumulativeReport.get(world);
                            if (worldCumulativeReport == null)
                                continue;
                            // World Network Traffic
                            NetworkTraffic worldNetworkTraffic = worldsNetworkTraffic.get(world);
                            if (worldNetworkTraffic == null)
                                continue;
                            if (serverNetworkTraffic.exceed(configData.getServerSendTickMaxBytes()))
                                break handleServer;
                            if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                continue;

                            /// Number of chunks generated in the world
                            AtomicInteger worldGeneratedChunk = worldsGeneratedChunk.getOrDefault(world, new AtomicInteger(Integer.MAX_VALUE));

                            handleWorld: {
                                // All players are loaded of network traffic
                                boolean playersFull = false;
                                while (!playersFull && effectiveTime >= System.currentTimeMillis()) {
                                    playersFull = true;
                                    for (PlayerChunkView view : worldsViews.getOrDefault(world, new ArrayList<>(0))) {
                                        if (serverNetworkTraffic.exceed(configData.getServerSendTickMaxBytes()))
                                            break handleServer;
                                        if (worldNetworkTraffic.exceed(configWorld.getWorldSendTickMaxBytes()))
                                            break handleWorld;
                                        synchronized (view.networkTraffic) {
                                            Integer forciblySendSecondMaxBytes = view.forciblySendSecondMaxBytes;
                                            if (view.networkTraffic.exceed(forciblySendSecondMaxBytes != null ? (int) (forciblySendSecondMaxBytes * configData.playerNetworkSpeedUseDegree) / 20 : configWorld.getPlayerSendTickMaxBytes()))
                                                continue;
                                            if (configData.autoAdaptPlayerNetworkSpeed && view.networkTraffic.exceed(Math.max(1, view.networkSpeed.avg() * 50)))
                                                continue;
                                        }
                                        if (view.waitSend) {
                                            playersFull = false;
                                            continue;
                                        }
                                        if (view.moveTooFast)
                                            continue;
                                        view.waitSend = true;
                                        long syncKey = view.syncKey;
                                        Long chunkKey = view.next();
                                        if (chunkKey == null) {
                                            view.waitSend = false;
                                            continue;
                                        }
                                        playersFull = false;
                                        int chunkX = ViewMap.getX(chunkKey);
                                        int chunkZ = ViewMap.getZ(chunkKey);

                                        handlePlayer: {
                                            if (!configData.disableFastProcess) {
                                                // Read the latest
                                                try {
                                                    if (configWorld.readServerLoadedChunk) {
                                                        BranchChunk chunk = branchMinecraft.getChunkFromMemoryCache(world, chunkX, chunkZ);
                                                        if (chunk != null) {
                                                            // Read & write
                                                            serverCumulativeReport.increaseLoadFast();
                                                            worldCumulativeReport.increaseLoadFast();
                                                            view.cumulativeReport.increaseLoadFast();
                                                            threadCumulativeReport.increaseLoadFast();
                                                            List<Runnable> asyncRunnable = new ArrayList<>();
                                                            BranchChunkLight chunkLight = branchMinecraft.fromLight(world);
                                                            BranchNBT chunkNBT = chunk.toNBT(chunkLight, asyncRunnable);
                                                            asyncRunnable.forEach(Runnable::run);
                                                            sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX, chunkZ, chunkNBT, chunkLight, syncKey, worldCumulativeReport, threadCumulativeReport);
                                                            break handlePlayer;
                                                        }
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (Exception ignored) {
                                                }

                                                // Read the fastest
                                                try {
                                                    BranchNBT chunkNBT = branchMinecraft.getChunkNBTFromDisk(world, chunkX, chunkZ);
                                                    if (chunkNBT != null && branchMinecraft.fromStatus(chunkNBT).isAbove(BranchChunk.Status.FULL)) {
                                                        // Read region files
                                                        serverCumulativeReport.increaseLoadFast();
                                                        worldCumulativeReport.increaseLoadFast();
                                                        view.cumulativeReport.increaseLoadFast();
                                                        threadCumulativeReport.increaseLoadFast();
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX, chunkZ, chunkNBT, branchMinecraft.fromLight(world, chunkNBT), syncKey, worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    }
                                                } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError exception) {
                                                    exception.printStackTrace();
                                                } catch (Exception ignored) {
                                                }
                                            }

                                            boolean canGenerated = serverGeneratedChunk.get() < configData.serverTickMaxGenerateAmount && worldGeneratedChunk.get() < configWorld.worldTickMaxGenerateAmount;
                                            if (canGenerated) {
                                                serverGeneratedChunk.incrementAndGet();
                                                worldGeneratedChunk.incrementAndGet();
                                            }

                                            // Generate
                                            try {
                                                // paper
                                                Chunk chunk = world.getChunkAtAsync(chunkX, chunkZ, canGenerated, true).get();
                                                if (chunk != null) {
                                                    serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        List<Runnable> asyncRunnable = new ArrayList<>();
                                                        BranchChunkLight chunkLight = branchMinecraft.fromLight(world);
                                                        BranchNBT chunkNBT = branchMinecraft.fromChunk(world, chunk).toNBT(chunkLight, asyncRunnable);
                                                        asyncRunnable.forEach(Runnable::run);
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX, chunkZ, chunkNBT, chunkLight, syncKey, worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (Exception ignored) {
                                                    }
                                                } else if (configData.serverTickMaxGenerateAmount > 0 && configWorld.worldTickMaxGenerateAmount > 0) {
                                                    view.remove(chunkX, chunkZ);
                                                    break handlePlayer;
                                                }
                                            } catch (ExecutionException ignored) {
                                                view.remove(chunkX, chunkZ);
                                                break handlePlayer;
                                            } catch (NoSuchMethodError methodError) {
                                                // spigot (not recommended)
                                                if (canGenerated) {
                                                    serverCumulativeReport.increaseLoadSlow();
                                                    worldCumulativeReport.increaseLoadSlow();
                                                    view.cumulativeReport.increaseLoadSlow();
                                                    threadCumulativeReport.increaseLoadSlow();
                                                    try {
                                                        List<Runnable> asyncRunnable = new ArrayList<>();
                                                        BranchChunkLight chunkLight = branchMinecraft.fromLight(world);
                                                        CompletableFuture<BranchNBT> syncNBT = new CompletableFuture<>();
                                                        waitMoveSyncQueue.add(() -> syncNBT.complete(branchMinecraft.fromChunk(world, world.getChunkAt(chunkX, chunkZ)).toNBT(chunkLight, asyncRunnable)));
                                                        BranchNBT chunkNBT = syncNBT.get();
                                                        asyncRunnable.forEach(Runnable::run);
                                                        sendChunk(world, configWorld, worldNetworkTraffic, view, chunkX, chunkZ, chunkNBT, chunkLight, syncKey, worldCumulativeReport, threadCumulativeReport);
                                                        break handlePlayer;
                                                    } catch (NullPointerException | NoClassDefFoundError | NoSuchMethodError | NoSuchFieldError exception) {
                                                        exception.printStackTrace();
                                                    } catch (Exception ignored) {
                                                    }
                                                }
                                            } catch (InterruptedException ignored) {
                                            } catch (Exception ex) {
                                                ex.printStackTrace();
                                            }
                                        }

                                        view.waitSend = false;
                                    }

                                    try {
                                        Thread.sleep(0L);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            // End time
            long endTime = System.currentTimeMillis();
            // Maximum time consumption 50 ms
            long needSleep = 50 - (endTime - startTime);
            if (needSleep > 0) {
                try {
                    Thread.sleep(needSleep);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
    private void sendChunk(World world, ConfigData.World configWorld, NetworkTraffic worldNetworkTraffic, PlayerChunkView view, int chunkX, int chunkZ, BranchNBT chunkNBT, BranchChunkLight chunkLight, long syncKey, CumulativeReport worldCumulativeReport, CumulativeReport threadCumulativeReport) {
        BranchChunk chunk = branchMinecraft.fromChunk(world, chunkX, chunkZ, chunkNBT, configData.calculateMissingHeightMap);
        // Call to send chunk events
        PlayerSendExtendChunkEvent event = new PlayerSendExtendChunkEvent(view.viewAPI, chunk, world);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled())
            return;

        // Anti-xray
        if (configWorld.preventXray != null && configWorld.preventXray.size() > 0) {
            // Replace all specified materials
            for (Map.Entry<BlockData, BlockData[]> conversionMap : configWorld.preventXray.entrySet())
                chunk.replaceAllMaterial(conversionMap.getValue(), conversionMap.getKey());
        }

        AtomicInteger consumeTraffic = new AtomicInteger(0);
        Consumer<Player> chunkAndLightPacket = branchPacket.sendChunkAndLight(view.getPlayer(), chunk, chunkLight, configWorld.sendTitleData, consumeTraffic::addAndGet);

        // Measurement speed required (min. every 1 second, 30 seconds timeout)
        synchronized (view.networkSpeed) {
            // Check if you are currently in the server block
            Location nowLoc = view.getPlayer().getLocation();
            int nowChunkX = nowLoc.getBlockX() >> 4;
            int nowChunkZ = nowLoc.getBlockZ() >> 4;
            ViewMap viewMap = view.getMap();
            if (world != nowLoc.getWorld()) {
                view.getMap().markWaitPosition(chunkX, chunkZ);
                return;
            }
            if (view.getMap().isWaitPosition(chunkX, chunkZ))
                return;
            if (viewShape.isInsideEdge(nowChunkX, nowChunkZ, chunkX, chunkZ, viewMap.serverDistance))
                return;
            if (view.syncKey != syncKey)
                return;
            if (!running)
                return;

            boolean needMeasure = configData.autoAdaptPlayerNetworkSpeed && ((view.networkSpeed.speedID == null && view.networkSpeed.speedTimestamp + 1000 <= System.currentTimeMillis()) || view.networkSpeed.speedTimestamp + 30000 <= System.currentTimeMillis());
            // Measure PING
            if (needMeasure) {
                if (view.networkSpeed.speedID != null) {
                    view.networkSpeed.add(30000, 0);
                }
                long pingID = random.nextLong();
                view.networkSpeed.pingID = pingID;
                view.networkSpeed.pingTimestamp = System.currentTimeMillis();
                branchPacket.sendKeepAlive(view.getPlayer(), pingID);
            }

            // Officially send
            chunkAndLightPacket.accept(view.getPlayer());
            serverNetworkTraffic.use(consumeTraffic.get());
            worldNetworkTraffic.use(consumeTraffic.get());
            view.networkTraffic.use(consumeTraffic.get());
            serverCumulativeReport.addConsume(consumeTraffic.get());
            worldCumulativeReport.addConsume(consumeTraffic.get());
            view.cumulativeReport.addConsume(consumeTraffic.get());
            threadCumulativeReport.addConsume(consumeTraffic.get());

            // Measure speed
            if (needMeasure) {
                long speedID = random.nextLong();
                view.networkSpeed.speedID = speedID;
                view.networkSpeed.speedConsume = consumeTraffic.get();
                view.networkSpeed.speedTimestamp = System.currentTimeMillis();
                branchPacket.sendKeepAlive(view.getPlayer(), speedID);
            }
        }
    }


    /**
     * Chunk Packet Events
     */
    public void packetEvent(Player player, PacketEvent event) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        if (event instanceof PacketMapChunkEvent) {
            PacketMapChunkEvent chunkEvent = (PacketMapChunkEvent) event;
            view.send(chunkEvent.getChunkX(), chunkEvent.getChunkZ());
        }
    }


    /**
     * Player respawn
     */
    public void respawnView(Player player) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        view.delay();
        waitMoveSyncQueue.add(() -> branchPacket.sendViewDistance(player, view.getMap().extendDistance));
    }
    /**
     * Switching worlds / long distance teleport / death and respawn, then wait a while
     */
    public void unloadView(Player player, Location from, Location move) {
        PlayerChunkView view = getView(player);
        if (view == null)
            return;
        int blockDistance = view.getMap().extendDistance << 4;
        if (from.getWorld() != move.getWorld())
            view.unload();
        else if (Math.abs(from.getX() - move.getX()) >= blockDistance || Math.abs(from.getZ() - move.getZ()) >= blockDistance)
            view.unload();
    }


    /**
     * End operations
     */
    void close() {
        running = false;
        for (BukkitTask task : bukkitTasks)
            task.cancel();
        multithreadedService.shutdown();
    }
}