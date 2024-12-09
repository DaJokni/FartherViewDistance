package com.jokni.fartherviewdistance.code;

import com.jokni.fartherviewdistance.api.branch.BranchMinecraft;
import com.jokni.fartherviewdistance.api.branch.BranchPacket;
import de.tr7zw.changeme.nbtapi.NBTContainer;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.jokni.fartherviewdistance.code.branch.MinecraftCode;
import com.jokni.fartherviewdistance.code.branch.PacketCode;
import com.jokni.fartherviewdistance.code.command.ViewDistanceCommand;
import com.jokni.fartherviewdistance.code.data.ConfigData;
import com.jokni.fartherviewdistance.code.data.viewmap.ViewShape;

public final class ChunkIndex extends JavaPlugin {
    private static Plugin plugin;
    private static ChunkServer chunkServer;
    private static ConfigData configData;
    private static BranchPacket branchPacket;
    private static BranchMinecraft branchMinecraft;


    public void onEnable() {
        plugin          = this;

        saveDefaultConfig();
        configData      = new ConfigData(this, getConfig());

        // Check version
        String bukkitVersion = Bukkit.getBukkitVersion();
        if (bukkitVersion.matches("1\\.20\\.4(?:.*)$")) {
            // 1.20.4
            branchPacket    = new PacketCode();
            branchMinecraft = new MinecraftCode();
            chunkServer     = new ChunkServer(configData, this, ViewShape.ROUND, branchMinecraft, branchPacket);
        } else {
            throw new IllegalArgumentException("Unsupported MC version: " + bukkitVersion);
        }

        // Initialize some data
        for (Player player : Bukkit.getOnlinePlayers())
            chunkServer.initView(player);
        for (World world : Bukkit.getWorlds())
            chunkServer.initWorld(world);

        Bukkit.getPluginManager().registerEvents(new ChunkEvent(chunkServer, branchPacket, branchMinecraft), this);

        // Command
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this).verboseOutput(false).silentLogs(true).initializeNBTAPI(NBTContainer.class, NBTContainer::new));

        ViewDistanceCommand viewDistanceCommand = new ViewDistanceCommand(chunkServer, configData);
        viewDistanceCommand.registerCommands();
    }

    public void onDisable() {
        CommandAPI.unregister("viewdistance");
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
