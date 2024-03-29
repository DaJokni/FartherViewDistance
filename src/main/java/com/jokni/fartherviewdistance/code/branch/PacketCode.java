package com.jokni.fartherviewdistance.code.branch;

import com.jokni.fartherviewdistance.api.branch.BranchChunk;
import com.jokni.fartherviewdistance.api.branch.BranchChunkLight;
import com.jokni.fartherviewdistance.api.branch.BranchPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
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
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.function.Consumer;

public final class PacketCode implements BranchPacket {
    private final PacketHandleLightUpdateCode handleLightUpdate = new PacketHandleLightUpdateCode();
    private final LevelLightEngine noOpLevelLightEngine = new LevelLightEngine(new LightChunkGetter() {
        public LightChunk getChunkForLighting(int chunkX, int chunkZ) {
            return null;
        }

        public BlockGetter getLevel() {
            return null;
        }
    }, false, false) {
        public int getLightSectionCount() {
            return 0;
        }
    };

    private Field chunkPacketLightDataField;

    {
        try {
            chunkPacketLightDataField = ClientboundLevelChunkWithLightPacket.class.getDeclaredField("d");
            chunkPacketLightDataField.setAccessible(true);
        } catch (NoSuchFieldException | SecurityException | InaccessibleObjectException e) {
            e.printStackTrace();
        }
    }

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
        FriendlyByteBuf serializer = new FriendlyByteBuf(Unpooled.buffer().writerIndex(0));
        this.handleLightUpdate.write(serializer, (ChunkLightCode) light);
        consumeTraffic.accept(serializer.readableBytes());
        ClientboundLightUpdatePacketData lightData = new ClientboundLightUpdatePacketData(serializer, chunk.getX(), chunk.getZ());
        LevelChunk levelChunk = ((ChunkCode) chunk).getLevelChunk();
        ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(levelChunk, noOpLevelLightEngine, null, null, levelChunk.getLevel().chunkPacketBlockController.shouldModify(serverPlayer, levelChunk));
        try {
            chunkPacketLightDataField.set(packet, lightData);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return (p) -> sendPacket(p, packet);
    }

    public void sendKeepAlive(Player player, long id) {
        sendPacket(player, new ClientboundKeepAlivePacket(id));
    }
}