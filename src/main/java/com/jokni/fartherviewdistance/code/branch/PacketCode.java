package com.jokni.fartherviewdistance.code.branch;

import com.jokni.fartherviewdistance.api.branch.BranchChunk;
import com.jokni.fartherviewdistance.api.branch.BranchChunkLight;
import com.jokni.fartherviewdistance.api.branch.BranchPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

public final class PacketCode implements BranchPacket {
    private final PacketHandleLightUpdateCode handleLightUpdate = new PacketHandleLightUpdateCode();

    public void sendPacket(Player player, net.minecraft.network.protocol.Packet<?> packet) {
        try {
            Connection container = ((CraftPlayer) player).getHandle().connection.connection;
            container.send(packet);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public void sendViewDistance(Player player, int viewDistance) {
        sendPacket(player, new ClientboundSetChunkCacheRadiusPacket(viewDistance));
    }

    public void sendUnloadChunk(Player player, int chunkX, int chunkZ) {
        sendPacket(player, new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ)));
    }

    public Consumer<Player> sendChunkAndLight(Player player, BranchChunk chunk, BranchChunkLight light, boolean needTile, Consumer<Integer> consumeTraffic) {
        CraftServer server = (CraftServer) Bukkit.getServer();
        RegistryFriendlyByteBuf serializer = new RegistryFriendlyByteBuf(Unpooled.buffer().writerIndex(0), server.getServer().registryAccess());
        this.handleLightUpdate.write(serializer, (ChunkLightCode) light);
        consumeTraffic.accept(serializer.readableBytes());
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(((ChunkCode) chunk).getLevelChunk(), ((ChunkCode) chunk).getLevelChunk().level.getLightEngine(), null, null, false);
        try {
            packet.setReady(true);
        } catch (NoSuchMethodError e) {
            e.printStackTrace();
        }
        return (p) -> sendPacket(p, packet);
    }

    public void sendKeepAlive(Player player, long id) {
        sendPacket(player, new ClientboundKeepAlivePacket(id));
    }
}