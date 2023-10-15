package xuan.cat.fartherviewdistance.code;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuan.cat.fartherviewdistance.code.branch.MinecraftCode;
import xuan.cat.fartherviewdistance.code.branch.PacketCode;
import xuan.cat.fartherviewdistance.code.command.Command;
import xuan.cat.fartherviewdistance.code.command.CommandSuggest;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.viewmap.ViewShape;

public final class ChunkIndex extends JavaPlugin {
//    private static ProtocolManager protocolManager;
    private static Plugin plugin;
    private static ChunkServer chunkServer;
    private static ConfigData configData;
    private static xuan.cat.fartherviewdistance.api.branch.BranchPacket branchPacket;
    private static xuan.cat.fartherviewdistance.api.branch.BranchMinecraft branchMinecraft;


    public void onEnable() {
        plugin          = this;
//        protocolManager = ProtocolLibrary.getProtocolManager();

        saveDefaultConfig();
        configData      = new ConfigData(this, getConfig());

        // Check version
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion.matches("^1\\.20\\.2$\n")) {
            // 1.20.2
            branchPacket    = new PacketCode();
            branchMinecraft = new MinecraftCode();
            chunkServer     = new ChunkServer(configData, this, ViewShape.SQUARE, branchMinecraft, branchPacket);
        } else {
            throw new IllegalArgumentException("Unsupported MC version: " + bukkitVersion);
        }

        // Initialize some data
        for (Player player : Bukkit.getOnlinePlayers())
            chunkServer.initView(player);
        for (World world : Bukkit.getWorlds())
            chunkServer.initWorld(world);

        Bukkit.getPluginManager().registerEvents(new ChunkEvent(chunkServer, branchPacket, branchMinecraft), this);
//        protocolManager.addPacketListener(new ChunkPacketEvent(plugin, chunkServer));


        // Command
        PluginCommand command = getCommand("viewdistance");
        if (command != null) {
            command.setExecutor(new Command(chunkServer, configData));
            command.setTabCompleter(new CommandSuggest(chunkServer, configData));
        }
    }

    public void onDisable() {
//        ChunkPlaceholder.unregisterPlaceholder();
        if (chunkServer != null)
            chunkServer.close();
    }

    public static ChunkServer getChunkServer() {
        return chunkServer;
    }

    public static ConfigData getConfigData() {
        return configData;
    }

    public static Plugin getPlugin() {
        return plugin;
    }

}
