package com.jokni.fartherviewdistance.code.branch;

import com.jokni.fartherviewdistance.api.branch.BranchChunk;
import com.jokni.fartherviewdistance.api.branch.BranchChunkLight;
import com.jokni.fartherviewdistance.api.branch.BranchMinecraft;
import com.jokni.fartherviewdistance.api.branch.BranchNBT;
import io.netty.channel.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class MinecraftCode implements BranchMinecraft {
    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchNBT getChunkNBTFromDisk(World world, int chunkX, int chunkZ) throws IOException {
        CompoundTag nbt = null;
        try {
            CompletableFuture<Optional<CompoundTag>> futureNBT = ((CraftWorld) world).getHandle().getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ));
            Optional<CompoundTag> optionalNBT = futureNBT.get();
            nbt = optionalNBT.orElse(null);
        } catch (InterruptedException | ExecutionException ignored) {
        }
        return nbt != null ? new NBTCode(nbt) : null;
    }

    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunk getChunkFromMemoryCache(World world, int chunkX, int chunkZ) {
        try {
            ServerLevel level = ((CraftWorld) world).getHandle();
            ChunkHolder playerChunk = level.getChunkSource().chunkMap.getVisibleChunkIfPresent((long) chunkZ << 32 | (long) chunkX & 4294967295L);
            if (playerChunk != null) {
                ChunkAccess chunk = playerChunk.getAvailableChunkNow();
                if (chunk != null && !(chunk instanceof EmptyLevelChunk) && chunk instanceof LevelChunk) {
                    return new ChunkCode(level, (LevelChunk) chunk);
                }
            }
            return null;
        } catch (NoSuchMethodError ignored) {
            return null;
        }
    }

    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunk fromChunk(World world, int chunkX, int chunkZ, BranchNBT nbt, boolean integralHeightmap) {
        return ChunkRegionLoaderCode.loadChunk(((CraftWorld) world).getHandle(), chunkX, chunkZ, ((NBTCode) nbt).getNMSTag(), integralHeightmap);
    }

    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunkLight fromLight(World world, BranchNBT nbt) {
        return ChunkRegionLoaderCode.loadLight(((CraftWorld) world).getHandle(), ((NBTCode) nbt).getNMSTag());
    }
    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunkLight fromLight(World world) {
        return new ChunkLightCode(((CraftWorld) world).getHandle());
    }

    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunk.Status fromStatus(BranchNBT nbt) {
        return ChunkRegionLoaderCode.loadStatus(((NBTCode) nbt).getNMSTag());
    }

    /**
     * Refer to: XuanCatAPI.CodeExtendWorld
     */
    public BranchChunk fromChunk(World world, org.bukkit.Chunk chunk) {
        return new ChunkCode(((CraftChunk) chunk).getCraftWorld().getHandle(), (LevelChunk) ((CraftChunk) chunk).getHandle(ChunkStatus.FULL));
    }

    public void injectPlayer(Player player) {
        ServerPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        ServerGamePacketListenerImpl connection = entityPlayer.connection;
        Channel channel = connection.connection.channel;
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addAfter("packet_handler", "farther_view_distance_write", new ChannelDuplexHandler() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof Packet) {
                    if (!ProxyPlayerConnectionCode.write(player, (Packet<?>) msg))
                        return;
                }
                super.write(ctx, msg, promise);
            }
        });
        pipeline.addAfter("encoder", "farther_view_distance_read", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof Packet) {
                    if (!ProxyPlayerConnectionCode.read(player, (Packet<?>) msg))
                        return;
                }
                super.channelRead(ctx, msg);
            }
        });
    }
}
