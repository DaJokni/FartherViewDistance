package com.jokni.fartherviewdistance.code.data.viewmap;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * view calculator that can be infinitely increased
 */
public final class LongXInfinitelyViewMap extends ViewMap {
    /*
    Each player has a long array with a maximum size of 255x255 (limited to odd numbers):
        0 represents "waiting."
        1 represents "block sent."
    Since (255 - 1) divided by 2 equals 127, the maximum view distance can effectively extend up to 63 blocks.
    The last digit of each long is reserved for other data markers.

    long[].length = 255
           long                                                                   long                                                                    long                                                                     long
                 0        8        16       24       32       40       48       56       64       72       80       88       96       104      112      120        128      136      144      152      160      168      176      184      192      200      208      216      224      232      240      248
                |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
      long[  0] |                                                                       |                                                                       *|                                                                        |                                                                     |  | Center of vertical axis
                |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
            ... |                                                                       |                                                                        |                                                                        |                                                                     |  |
      long[127] |                                                                       |                                                                        |                                                                        |                                                                     |  | Center of horizontal axis
            ... |                                                                       |                                                                        |                                                                        |                                                                     |  |
      long[254] |--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------- -| --------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|--------|------|--|
     */
    /** View Calculation */
    private final long[] viewData;
    /** Single-line count of long integers. */
    private final int rowStack;
    /** Half-line count of long integers */
    private final int rowStackOffset;
    /** Maximum number of radius blocks */
    private final int maxRadius;
    /** Maximum Diameter Blocks */
    private final int maxDiameter;


    public LongXInfinitelyViewMap(ViewShape viewShape, int row) {
        super(viewShape);
        rowStack = row;
        rowStackOffset = rowStack / 2;
        maxRadius = rowStackOffset * 64 - 1;
        maxDiameter = rowStack * 64 - 1;
        viewData = new long[maxDiameter << rowStackOffset];
    }


    public List<Long> movePosition(Location location) {
        return movePosition(blockToChunk(location.getX()), blockToChunk(location.getZ()));
    }

    /**
     * Move to a block location (center point).
     *
     * @param moveX Block coordinate X
     * @param moveZ Block coordinate Z
     * @return If any blocks are removed, they will be returned here.
     */
    public List<Long> movePosition(int moveX, int moveZ) {
        if (moveX != centerX || moveZ != centerZ) {
            /*
               -      +
               X
            +Z |-------|
               | Chunk |
               | Map   |
            -  |-------|
             */
            int viewDistance = Math.min(maxRadius, extendDistance + 1);
            // Location of the last recorded block (center)
            List<Long> removeKeys = new ArrayList<>();
            // Add blocks that are no longer in range to the cache.
            int hitDistance = Math.max(serverDistance, viewDistance + 1);
            int pointerX;
            int pointerZ;
            int chunkX;
            int chunkZ;
            for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
                for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                    chunkX = (centerX - pointerX) + maxRadius;
                    chunkZ = (centerZ - pointerZ) + maxRadius;
                    // Whether it is no longer within the scope
                    if (isSendSafe(pointerX, pointerZ) && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, hitDistance) && !viewShape.isInside(moveX, moveZ, chunkX, chunkZ, hitDistance) && markWaitSafe(pointerX, pointerZ)) {
                        removeKeys.add(getPositionKey(chunkX, chunkZ));
                    }
                }
            }

            int offsetX = centerX - moveX;
            int offsetZ = centerZ - moveZ;
            // Coordinate X, changes occurred.
            if (offsetX != 0) {
                long[] dataX = new long[rowStack];
                long newX;
                int rowX;
                int migrate;
                int redressX;
                for (pointerZ = 0; pointerZ < maxDiameter; pointerZ++) {
                    for (rowX = 0; rowX < rowStack; rowX++)
                        dataX[rowX] = viewData[(pointerZ << rowStackOffset) | rowX];
                    for (rowX = 0; rowX < rowStack; rowX++) {
                        newX = 0;
                        for (migrate = 0; migrate < rowStack; migrate++) {
                            redressX = -(rowX - migrate) * 64 - offsetX;
                            if (redressX < -64 || redressX > 64) {
                            } else if (redressX < 0) {
                                newX |= dataX[migrate] << -redressX;
                            } else if (redressX > 0) {
                                newX |= dataX[migrate] >>> redressX;
                            }
                        }
                        if (rowX == rowStack - 1)
                            // Mark unused areas as 0 (far right)
                            newX &= 0b1111111111111111111111111111111111111111111111111111111111111110L;
                        viewData[(pointerZ << rowStackOffset) | rowX] = newX;
                    }
                }
            }

            // Coordinate >, changes occurred.
            if (offsetZ < 0) {
                int redressZ;
                int rowX;
                for (pointerZ = maxDiameter - 1; pointerZ >= 0; pointerZ--) {
                    redressZ = pointerZ + offsetZ;
                    if (redressZ >= 0 && redressZ < maxDiameter) {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = viewData[(redressZ << rowStackOffset) | rowX];
                    } else {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = 0;
                    }
                }
            } else if (offsetZ > 0) {
                int redressZ;
                int rowX;
                for (pointerZ = 0; pointerZ < maxDiameter; pointerZ++) {
                    redressZ = pointerZ + offsetZ;
                    if (redressZ >= 0 && redressZ < maxDiameter) {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = viewData[(redressZ << rowStackOffset) | rowX];
                    } else {
                        for (rowX = 0; rowX < rowStack; rowX++)
                            viewData[(pointerZ << rowStackOffset) | rowX] = 0;
                    }
                }
            }

            if (moveX != centerX || moveZ != centerZ) {
                completedDistance.addAndGet(-Math.max(Math.abs(centerX - moveX), Math.abs(centerZ - moveZ)));
            }
            centerX = moveX;
            centerZ = moveZ;

            return removeKeys;
        } else {
            return new ArrayList<>(0);
        }
    }


    /**
     * Get the next block that should be processed.
     *
     * @return The positionKey, or null if there are no blocks to process.
     */
    public Long get() {
        int viewDistance = Math.min(maxRadius, extendDistance + 1);
        int edgeStepCount = 0;  // On each side, move a few times to change directions.
        int readX;
        int readZ;
        int pointerX;
        int pointerZ;
        int stepCount;
        int chunkX;
        int chunkZ;
        boolean notMiss = true;

        for (int distance = 0; distance <= maxRadius && distance <= viewDistance; distance++) {
            if (distance > completedDistance.get()) {
                // There are 4 directions in total
                readX = distance;
                readZ = distance;
                pointerX = maxRadius + distance;
                pointerZ = maxRadius + distance;

                // Z--
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance) && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerZ--;
                    readZ--;
                }
                // X--
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance) && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerX--;
                    readX--;
                }
                // Z++
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance) && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerZ++;
                    readZ++;
                }
                // X++
                for (stepCount = 0; stepCount < edgeStepCount; ++stepCount) {
                    chunkX = centerX - readX;
                    chunkZ = centerZ - readZ;
                    if (!viewShape.isInsideEdge(centerX, centerZ, chunkX, chunkZ, serverDistance) && viewShape.isInside(centerX, centerZ, chunkX, chunkZ, viewDistance)) {
                        if (isWaitSafe(pointerX, pointerZ)) {
                            markSendSafe(pointerX, pointerZ);
                            return getPositionKey(chunkX, chunkZ);
                        } else {
                            notMiss = false;
                        }
                    }

                    pointerX++;
                    readX++;
                }

                if (notMiss) {
                    completedDistance.set(distance);
                }
            }

            // Next cycle.
            edgeStepCount += 2;
        }
        return null;
    }


    private int blockToChunk(double location) {
        return blockToChunk((int) location);
    }

    private int blockToChunk(int blockLocation) {
        return blockLocation >> 4;
    }


    public boolean inPosition(int positionX, int positionZ) {
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        int viewDistance = Math.min(maxRadius, extendDistance);
        return pointerX <= maxRadius + viewDistance && pointerX >= maxRadius - viewDistance && pointerZ <= maxRadius + viewDistance && pointerZ >= maxRadius - viewDistance;
    }


    public boolean isWaitPosition(long positionKey) {
        return isWaitPosition(getX(positionKey), getZ(positionKey));
    }

    public boolean isWaitPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter && isWaitSafe(pointerX, pointerZ);
    }

    public boolean isSendPosition(long positionKey) {
        return isSendPosition(getX(positionKey), getZ(positionKey));
    }

    public boolean isSendPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter && isSendSafe(pointerX, pointerZ);
    }

    public void markWaitPosition(long positionKey) {
        markWaitPosition(getX(positionKey), getZ(positionKey));
    }

    public void markWaitPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter)
            markWaitSafe(pointerX, pointerZ);
    }

    public void markSendPosition(long positionKey) {
        markSendPosition(getX(positionKey), getZ(positionKey));
    }

    public void markSendPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = maxRadius + (centerX - positionX);
        int pointerZ = maxRadius + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < maxDiameter && pointerZ >= 0 && pointerZ < maxDiameter)
            markSendSafe(pointerX, pointerZ);
    }


    private int toViewPointer(int pointerX, int pointerZ) {
        return pointerZ << rowStackOffset | pointerX >>> 6;
    }

    public boolean isWaitSafe(int pointerX, int pointerZ) {
        return !isSendSafe(pointerX, pointerZ);
    }

    public boolean isSendSafe(int pointerX, int pointerZ) {
        return (viewData[toViewPointer(pointerX, pointerZ)] << (pointerX & 0b111111) & 0b1000000000000000000000000000000000000000000000000000000000000000L) == 0b1000000000000000000000000000000000000000000000000000000000000000L;
    }


    public boolean markWaitSafe(int pointerX, int pointerZ) {
        if (isSendSafe(pointerX, pointerZ)) {
            viewData[toViewPointer(pointerX, pointerZ)] ^= (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (pointerX & 0b111111));
            return true;
        } else {
            return false;
        }
    }

    public void markSendSafe(int pointerX, int pointerZ) {
        viewData[toViewPointer(pointerX, pointerZ)] |= (0b1000000000000000000000000000000000000000000000000000000000000000L >>> (pointerX & 0b111111));
    }


    /**
     * @param range The blocks outside the range are marked as waiting.
     */
    public void markOutsideWait(int range) {
        // ensure that it can only be a positive number
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (!viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markWaitSafe(pointerX, pointerZ);
            }
        }
    }

    /**
     * @param range Out-of-range blocks are labeled with the name of the block to which you want to send the message.
     */
    public void markOutsideSend(int range) {
        // Make sure it's only a positive number
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (!viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }


    /**
     * @param range The blocks in the range are marked as waiting.
     */
    public void markInsideWait(int range) {
        // Make sure it's only a positive number
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markWaitSafe(pointerX, pointerZ);
            }
        }
    }

    /**
     * @param range Mark the blocks within the range as "sent."
     */
    public void markInsideSend(int range) {
        // Make sure it's only a positive number
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }


    public List<Long> getAll() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }

    public List<Long> getAllNotServer() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < maxDiameter; ++pointerX) {
            for (pointerZ = 0; pointerZ < maxDiameter; ++pointerZ) {
                chunkX = centerX + pointerX - maxRadius;
                chunkZ = centerZ + pointerZ - maxRadius;
                if (isSendSafe(pointerX, pointerZ) && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, serverDistance))
                    chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }


    public void clear() {
        System.arraycopy(new long[viewData.length], 0, viewData, 0, viewData.length);
        completedDistance.set(-1);
    }


    public void debug(CommandSender sender) {
        StringBuilder builder = new StringBuilder();
        builder.append("LongXInfinitelyViewMap:\n");
        for (int index = 0; index < viewData.length; ++index) {
            if (index != 0 && index % rowStack == 0)
                builder.append('\n');
            long value = viewData[index];
            for (int read = 63; read >= 0; read--)
                builder.append((value >> read & 1) == 1 ? '■' : '□');
        }
        sender.sendMessage(builder.toString());
    }
}
