package xuan.cat.fartherviewdistance.code;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import xuan.cat.fartherviewdistance.code.branch.MinecraftCode;
import xuan.cat.fartherviewdistance.code.branch.PacketCode;
import xuan.cat.fartherviewdistance.code.command.ViewDistanceCommand;
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
        if (bukkitVersion.matches("1\\.20\\.2(?:.*)$")) {
            // 1.20.2
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
//        protocolManager.addPacketListener(new ChunkPacketEvent(plugin, chunkServer));


        // Command
        // register your command executor as normal.
        PluginCommand command = getCommand("mycommand");
        command.setExecutor(new ViewDistanceCommand(chunkServer, configData));

        // check if brigadier is supported
        if (CommodoreProvider.isSupported()) {

            Commodore commodore = CommodoreProvider.getCommodore(this);
            registerCommands(commodore, command);
            registerCompletions(commodore, command);
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


    private static void registerCompletions(Commodore commodore, PluginCommand command) {
        commodore.register(command, LiteralArgumentBuilder.literal("mycommand")
                .then(RequiredArgumentBuilder.argument("some-argument", StringArgumentType.string()))
                .then(RequiredArgumentBuilder.argument("some-other-argument", BoolArgumentType.bool()))
        );
    }

    private static void registerCommands(Commodore commodore, PluginCommand command) {
        LiteralCommandNode<?> vdCommand = LiteralArgumentBuilder.literal("viewdistance")
                .then(LiteralArgumentBuilder.literal("reload")
                .then(LiteralArgumentBuilder.literal("start")
                .then(LiteralArgumentBuilder.literal("stop")
                .then(LiteralArgumentBuilder.literal("report")
                        .then(LiteralArgumentBuilder.literal("server")))
                        .then(LiteralArgumentBuilder.literal("thread")))
                        .then(LiteralArgumentBuilder.literal("world")))
                        .then(LiteralArgumentBuilder.literal("player")))
                .then(LiteralArgumentBuilder.literal("permissionCheck"))
                        .then(RequiredArgumentBuilder.argument("player name", StringArgumentType.string())
                ).build();

        commodore.register(command, vdCommand);
    }

}
