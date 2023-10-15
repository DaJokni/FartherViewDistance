package xuan.cat.fartherviewdistance.code.data.viewmap;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示區塊視野
 */
public final class LongX31ViewMap extends ViewMap {
    /*
    Each player has a long array with a maximum size of 63x63 (limited to odd numbers):
        0 represents "waiting."
        1 represents "block sent."
    Since 63 divided by 2 equals 31, the maximum view distance can effectively extend up to 31 blocks.
    The last digit of each long is reserved for other data markers.

    long[].length = 63

                    chunkMap
                    6   6          5          4            3          2          1          0
                    3 2109876 54321098 76543210 98765432 1 0987654 32109876 54321098 76543210  bit shift

                      33          2          1         0 0 0         1          2          33
                      1098765 43210987 65432109 87654321 0 1234567 89012345 67890123 45678901  How far is the block from the center point

                   |-|-------|--------|--------|--------|- -------|--------|--------|--------|
                   | |                                   *                                   | Represents the column center point
                   |*|                                                                       | Unused
                   |-|------- -------- -------- -------- - ------- -------- -------- --------|
          long[ 0] |0|0000000 00000000 00000000 00000000 0 0000000 00000000 00000000 00000000|
          ...      | |                                 ...                                   |
          long[31] | |                                 ...                                   | Represents the center point of the line
          ...      | |                                 ...                                   |
          long[62] |0|0000000 00000000 00000000 00000000 0 0000000 00000000 00000000 00000000|
                   |-|-----------------------------------------------------------------------|


     */
    /** Distance */
    private static final int DISTANCE = 32;
    /** Center */
    private static final int CENTER = 31;
    /** Length */
    private static final int LENGTH = 63;
    /** View Calculation */
    private final long[] chunkMap = new long[LENGTH];


    public LongX31ViewMap(ViewShape viewShape) {
        super(viewShape);
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
            First, do a coordinate shift of the chunkMap.
            Then mark the range of the server's field of view as the area to be loaded.
             */
            // Location of the last recorded block (center)
            int viewDistance = Math.min(extendDistance + 1, DISTANCE);
            // List of blocks removed
            List<Long> removeKeys = new ArrayList<>();
            // Add blocks that are no longer in range to the cache.
            int hitDistance = Math.max(serverDistance, viewDistance + 1);
            int pointerX;
            int pointerZ;
            int chunkX;
            int chunkZ;
            for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
                for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                    chunkX = (centerX - pointerX) + CENTER;
                    chunkZ = (centerZ - pointerZ) + CENTER;
                    // Whether it is no longer within the range.
                    if (isSendSafe(pointerX, pointerZ) && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, hitDistance) && !viewShape.isInside(moveX, moveZ, chunkX, chunkZ, hitDistance) && markWaitSafe(pointerX, pointerZ)) {
                        removeKeys.add(getPositionKey(chunkX, chunkZ));
                    }
                }
            }

            /*
               -      +
               X
            +Z |-------|
               | Chunk |
               | Map   |
            -  |-------|

            When the coordinates move
            x:0    -> x:1
            000000    000000
            011110    111100
            011110    111100
            011110    111100
            011110    111100
            000000    000000

            z:0    -> z:1
            000000    000000
            011110    000000
            011110    011110
            011110    011110
            011110    011110
            000000    011110
            */
            int offsetX = centerX - moveX;
            int offsetZ = centerZ - moveZ;
            // Coordinate X, changes occurred.
            if (offsetX != 0) {
                for (pointerZ = 0; pointerZ < LENGTH; pointerZ++) {
                    chunkMap[pointerZ] = offsetX > 0 ? chunkMap[pointerZ] >> offsetX : chunkMap[pointerZ] << Math.abs(offsetX);
                }
            }
            // Coordinate Z, changes occurred.
            if (offsetZ != 0) {
                long[] newChunkMap = new long[LENGTH];
                int pointerToZ;
                for (int pointerFromZ = 0; pointerFromZ < LENGTH; pointerFromZ++) {
                    pointerToZ = pointerFromZ - offsetZ;
                    if (pointerToZ >= 0 && pointerToZ < LENGTH) {
                        newChunkMap[pointerToZ] = chunkMap[pointerFromZ];
                    }
                }
                System.arraycopy(newChunkMap, 0, chunkMap, 0, chunkMap.length);
            }

            // If the coordinates have been changed, update the currently saved coordinates.
            if (offsetX != 0 || offsetZ != 0) {
                // Mark unused area as 0 (leftmost)
                if (offsetX < 0) {
                    for (pointerZ = 0; pointerZ < LENGTH; pointerZ++)
                        chunkMap[pointerZ] &= 0b0111111111111111111111111111111111111111111111111111111111111111L;
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
     * Retrieve the next block that should be processed.
     *
     * @return positionKey, if there is no block that needs to be in there, then return null.
     */
    public Long get() {
        /*
        The search process
        We'll start from the center and work our way out.

        Clockwise, from the top.
         -----      -----      -----      -----      -----      -----      -----      -----      -----      -----      -----

                                           1          11         111        111        111        111        111        111
           +    ->    +    ->   1+    ->   1+    ->   1+    ->   1+    ->   1+1   ->   1+1   ->   1+1   ->   1+1   ->   1+1
                     1          1          1          1          1          1          1 1        111        111       1111
                                                                                                            1          1
         -----      -----      -----      -----      -----      -----      -----      -----      -----      -----      -----


        Math
         Single Edge Length
        1 = 1 + (1 - 1)
        3 = 2 + (2 - 1)
        5 = 3 + (3 - 1)
        7 = 4 + (4 - 1)

         Total Side Length (No repeat steps)
        0  = 1 * 4 - 4
        8  = 3 * 4 - 4
        16 = 5 * 4 - 4
        24 = 7 * 4 - 4

         edgeStepCount = Every move? You have to change directions four times.
        0  / 4 = 0
        8  / 4 = 2
        16 / 4 = 4
        24 / 4 = 6

        Derived formula
        Each Distance +1 Number of moves +2

        distance = 1    //
        1               // Since it cannot be 1.
        + 1             // Passing through the center point


        distance = 2
         3
        |-|
        | | 8
        |-|


        distance = 3
          5
        |---|
        |   |
        |   | 16
        |   |
        |---|


        distance = 4
           7
        |-----|
        |     |
        |     |
        |     | 24
        |     |
        |     |
        |-----|

         */

        int viewDistance = Math.min(extendDistance + 1, DISTANCE);
        int edgeStepCount = 0;  // On each side, move a few times to change directions.
        int readX;
        int readZ;
        int pointerX;
        int pointerZ;
        int stepCount;
        int chunkX;
        int chunkZ;
        boolean notMiss = true;

        for (int distance = 0; distance < DISTANCE && distance < viewDistance; distance++) {
            if (distance > completedDistance.get()) {
                // Total of 4 directions
                readX = distance;
                readZ = distance;
                pointerX = CENTER + distance;
                pointerZ = CENTER + distance;

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


    public boolean isWaitSafe(int pointerX, int pointerZ) {
        return !isSendSafe(pointerX, pointerZ);
    }

    public boolean isSendSafe(int pointerX, int pointerZ) {
        return ((chunkMap[pointerZ] >> pointerX) & 0b0000000000000000000000000000000000000000000000000000000000000001L) == 0b0000000000000000000000000000000000000000000000000000000000000001L;
    }


    public boolean markWaitSafe(int pointerX, int pointerZ) {
        if (isSendSafe(pointerX, pointerZ)) {
            chunkMap[pointerZ] ^= (0b0000000000000000000000000000000000000000000000000000000000000001L << pointerX);
            return true;
        } else {
            return false;
        }
    }

    public void markSendSafe(int pointerX, int pointerZ) {
        chunkMap[pointerZ] |= (0b0000000000000000000000000000000000000000000000000000000000000001L << pointerX);
    }


    public boolean inPosition(int positionX, int positionZ) {
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX <= CENTER + extendDistance && pointerX >= CENTER - extendDistance && pointerZ <= CENTER + extendDistance && pointerZ >= CENTER - extendDistance;
    }


    public boolean isWaitPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        return isWaitPosition(x, z);
    }

    public boolean isWaitPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH && isWaitSafe(pointerX, pointerZ);
    }

    public boolean isSendPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        return isSendPosition(x, z);
    }

    public boolean isSendPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        return pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH && isSendSafe(pointerX, pointerZ);
    }

    public void markWaitPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        markWaitPosition(x, z);
    }

    public void markWaitPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH)
            markWaitSafe(pointerX, pointerZ);
    }

    public void markSendPosition(long positionKey) {
        int x = getX(positionKey);
        int z = getZ(positionKey);
        markSendPosition(x, z);
    }

    public void markSendPosition(int positionX, int positionZ) {
        // Location of the last recorded block (center)
        int pointerX = CENTER + (centerX - positionX);
        int pointerZ = CENTER + (centerZ - positionZ);
        if (pointerX >= 0 && pointerX < LENGTH && pointerZ >= 0 && pointerZ < LENGTH)
            markSendSafe(pointerX, pointerZ);
    }


    /**
     * @param range Out-of-range blocks are marked as waiting.
     */
    public void markOutsideWait(int range) {
        // Make sure it's only a positive number
        if (range < 0)
            range = Math.abs(range);
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
                if (viewShape.isInside(centerX, centerZ, chunkX, chunkZ, range))
                    markSendSafe(pointerX, pointerZ);
            }
        }
    }


    public void clear() {
        System.arraycopy(new long[LENGTH], 0, chunkMap, 0, chunkMap.length);
        completedDistance.set(-1);
    }


    public long[] getChunkMap() {
        return chunkMap;
    }

    public List<Long> getAll() {
        List<Long> chunkList = new ArrayList<>();
        int pointerX;
        int pointerZ;
        int chunkX;
        int chunkZ;
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
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
        for (pointerX = 0; pointerX < LENGTH; ++pointerX) {
            for (pointerZ = 0; pointerZ < LENGTH; ++pointerZ) {
                chunkX = centerX + pointerX - CENTER;
                chunkZ = centerZ + pointerZ - CENTER;
                if (isSendSafe(pointerX, pointerZ) && !viewShape.isInside(centerX, centerZ, chunkX, chunkZ, serverDistance))
                    chunkList.add(getPositionKey(chunkX, chunkZ));
            }
        }
        return chunkList;
    }


    public static int blockToChunk(double blockLocation) {
        return blockToChunk((int) blockLocation);
    }

    public static int blockToChunk(int blockLocation) {
        return blockLocation >> 4;
    }


    public static String debug(long value) {
        StringBuilder builder = new StringBuilder(LENGTH);
        for (int i = LENGTH; i >= 0; i--) {
            builder.append((value >> i & 1) == 1 ? '■' : '□');
        }
       return builder.toString();
    }

    public void debug(CommandSender sender) {
        StringBuilder builder = new StringBuilder();
        builder.append("LongX31ViewMap:\n");
        for (int index = 0; index < LENGTH; ++index) {
            if (index != 0)
                builder.append('\n');
            builder.append(debug(getChunkMap()[index]));
        }
        sender.sendMessage(builder.toString());
    }
}
