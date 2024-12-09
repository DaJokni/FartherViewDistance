package com.jokni.fartherviewdistance.code.branch;

import com.jokni.fartherviewdistance.api.branch.packet.PacketKeepAliveEvent;
import com.jokni.fartherviewdistance.api.branch.packet.PacketMapChunkEvent;
import com.jokni.fartherviewdistance.api.branch.packet.PacketUnloadChunkEvent;
import com.jokni.fartherviewdistance.api.branch.packet.PacketViewDistanceEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public final class ProxyPlayerConnectionCode {
    public static boolean read(Player player, Packet<?> packet) {
        if (packet instanceof ServerboundKeepAlivePacket) {
            PacketKeepAliveEvent event = new PacketKeepAliveEvent(player, ((ServerboundKeepAlivePacket) packet).getId());
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } else {
            return true;
        }
    }

    public static boolean write(Player player, Packet<?> packet) {
        try {
            switch (packet) {
                case ClientboundForgetLevelChunkPacket clientboundForgetLevelChunkPacket -> {
                    PacketUnloadChunkEvent event = new PacketUnloadChunkEvent(player, clientboundForgetLevelChunkPacket.pos());
                    Bukkit.getPluginManager().callEvent(event);
                    return !event.isCancelled();
                }
                case ClientboundSetChunkCacheRadiusPacket clientboundSetChunkCacheRadiusPacket -> {
                    PacketViewDistanceEvent event = new PacketViewDistanceEvent(player, clientboundSetChunkCacheRadiusPacket.getRadius());
                    Bukkit.getPluginManager().callEvent(event);
                    return !event.isCancelled();
                }
                case ClientboundLevelChunkWithLightPacket clientboundLevelChunkWithLightPacket -> {
                    PacketMapChunkEvent event = new PacketMapChunkEvent(player, clientboundLevelChunkWithLightPacket.getX(), clientboundLevelChunkWithLightPacket.getZ());
                    Bukkit.getPluginManager().callEvent(event);
                    return !event.isCancelled();
                }
                case null, default -> {
                    return true;
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return true;
        }
    }
}
