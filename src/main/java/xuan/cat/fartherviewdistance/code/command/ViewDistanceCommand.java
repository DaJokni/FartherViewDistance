package xuan.cat.fartherviewdistance.code.command;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.PlayerArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import xuan.cat.fartherviewdistance.code.ChunkIndex;
import xuan.cat.fartherviewdistance.code.ChunkServer;
import xuan.cat.fartherviewdistance.code.data.ConfigData;
import xuan.cat.fartherviewdistance.code.data.CumulativeReport;

import java.util.ArrayList;
import java.util.List;

public final class ViewDistanceCommand {
    private final ChunkServer chunkServer;
    private final ConfigData configData;

    public ViewDistanceCommand(ChunkServer chunkServer, ConfigData configData) {
        this.chunkServer = chunkServer;
        this.configData = configData;
    }

    public void registerCommands() {
        List<Argument<?>> arguments = new ArrayList<>();
        arguments.add(new StringArgument("reload"));
        arguments.add(new StringArgument("report"));
        arguments.add(new StringArgument("start"));
        arguments.add(new StringArgument("stop"));
        arguments.add(new StringArgument("permissionCheck"));
        arguments.add(new StringArgument("debug"));
        new CommandAPICommand("viewdistance")
            .withSubcommand(new CommandAPICommand("reload")
                .executes((sender, args) -> {
                    try {
                        configData.reload();
                        ChunkIndex.getChunkServer().reloadMultithreaded();
                        sender.sendMessage(ChatColor.YELLOW + chunkServer.lang.get(sender, "command.reread_configuration_successfully"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        sender.sendMessage(ChatColor.RED + chunkServer.lang.get(sender, "command.reread_configuration_error"));
                    }
                }))
                .withSubcommand(new CommandAPICommand("server")
                    .withSubcommand(new CommandAPICommand("server")
                            .executes((sender, args) -> {
                                sendReportHead(sender);
                                sendReportCumulative(sender, "*SERVER", chunkServer.serverCumulativeReport);
                }))
                    .withSubcommand(new CommandAPICommand("thread")
                            .executes((sender, args) -> {
                                sendReportHead(sender);
                                chunkServer.threadsCumulativeReport.forEach(((threadNumber, cumulativeReport) -> sendReportCumulative(sender, "*THREAD#" + threadNumber, cumulativeReport)));
                }))
                    .withSubcommand(new CommandAPICommand("world")
                            .executes((sender, args) -> {
                                sendReportHead(sender);
                                chunkServer.worldsCumulativeReport.forEach(((world, cumulativeReport) -> sendReportCumulative(sender, world.getName(), cumulativeReport)));
                }))
                    .withSubcommand(new CommandAPICommand("player")
                            .executes((sender, args) -> {
                                sendReportHead(sender);
                                chunkServer.playersViewMap.forEach(((player, view) -> sendReportCumulative(sender, player.getName(), view.cumulativeReport)));
                })))
                .withSubcommand(new CommandAPICommand("start")
                    .executes((sender, args) -> {
                        chunkServer.globalPause = false;
                        sender.sendMessage(ChatColor.YELLOW + chunkServer.lang.get(sender, "command.continue_execution"));
                }))
                .withSubcommand(new CommandAPICommand("stop")
                    .executes((sender, args) -> {
                        chunkServer.globalPause = true;
                        sender.sendMessage(ChatColor.YELLOW + chunkServer.lang.get(sender, "command.suspension_execution"));
                }))
                .withSubcommand(new CommandAPICommand("permissionCheck")
                    .withArguments(new PlayerArgument("player"))
                    .executes((sender, args) -> {
                        Player player = (Player) args.get("player");
                        if (player == null) {
                            // Player doesnt exist
                            sender.sendMessage(ChatColor.RED + chunkServer.lang.get(sender, "command.players_do_not_exist"));
                        } else {
                            chunkServer.getView(player).permissionsNeed = true;
                            // Player permissions rechecked
                            sender.sendMessage(ChatColor.YELLOW + chunkServer.lang.get(sender, "command.rechecked_player_permissions"));
                        }
                }))
                .withSubcommand(new CommandAPICommand("debug")
                    .withSubcommand(new CommandAPICommand("view")
                        .withArguments(new PlayerArgument("player"))
                        .executes((sender, args) -> {
                            Player player = (Player) args.get("player");
                            if (player == null) {
                                // Player doesnt exist
                                sender.sendMessage(ChatColor.RED + chunkServer.lang.get(sender, "command.players_do_not_exist"));
                            } else {
                                chunkServer.getView(player).getMap().debug(sender);
                            }
                })))
                .register();
    }

    private void sendReportHead(CommandSender sender) {
        // Source | Fast 5 seconds/1 minute/5 minutes | Slow 5 seconds/1 minute/5 minutes | Traffic 5 seconds/1 minute/5 minutes
        String timeSegment = chunkServer.lang.get(sender, "command.report.5s") + "/" + chunkServer.lang.get(sender, "command.report.1m") + "/" + chunkServer.lang.get(sender, "command.report.5m");
        sender.sendMessage(ChatColor.YELLOW + chunkServer.lang.get(sender, "command.report.source") + ChatColor.WHITE + " | " + ChatColor.GREEN + chunkServer.lang.get(sender, "command.report.fast") + " " + timeSegment + ChatColor.WHITE + " | " + ChatColor.RED + chunkServer.lang.get(sender, "command.report.slow") + " " + timeSegment + ChatColor.WHITE + " | " + ChatColor.GOLD + chunkServer.lang.get(sender, "command.report.flow") + " " + timeSegment);
    }
    private void sendReportCumulative(CommandSender sender, String source, CumulativeReport cumulativeReport) {
        sender.sendMessage(ChatColor.YELLOW + source + ChatColor.WHITE + " | " + ChatColor.GREEN + cumulativeReport.reportLoadFast5s() + "/" + cumulativeReport.reportLoadFast1m() + "/" + cumulativeReport.reportLoadFast5m() + ChatColor.WHITE + " | " + ChatColor.RED + cumulativeReport.reportLoadSlow5s() + "/" + cumulativeReport.reportLoadSlow1m() + "/" + cumulativeReport.reportLoadSlow5m() + ChatColor.WHITE + " | " + ChatColor.GOLD + cumulativeReport.reportConsume5s() + "/" + cumulativeReport.reportConsume1m() + "/" + cumulativeReport.reportConsume5m());
    }
}