package xuan.cat.fartherviewdistance.code.data;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Network Speed Monitor
 */
public final class NetworkSpeed {
    /** Time Stamp for Speed Measurement */
    public volatile long speedTimestamp = 0;
    /** Speed measurement volume */
    public volatile int speedConsume = 0;
    /** Speed ID */
    public volatile Long speedID = null;

    /** Ping timestamp */
    public volatile long pingTimestamp = 0;
    /** Ping ID */
    public volatile Long pingID = null;
    /** Last ping of player */
    public volatile int lastPing = 0;

    /** Write to array */
    private volatile int[] writeArray = new int[50];
    /** Latency log */
    private volatile int[] consumeArray = new int[50];
    /** Write total */
    private final AtomicInteger writeTotal = new AtomicInteger(0);
    /** Consume total */
    private final AtomicInteger consumeTotal = new AtomicInteger(0);


    /**
     * Add a record
     */
    public void add(int ping, int length) {
        synchronized (writeTotal) {
            writeTotal.addAndGet(length);
            consumeTotal.addAndGet(ping);
            writeArray[0] += length;
            consumeArray[0] += ping;
        }
    }


    /**
     * @return average ping
     */
    public int avg() {
        synchronized (writeTotal) {
            int writeGet = writeTotal.get();
            int consumeGet = Math.max(1, consumeTotal.get());
            if (writeGet == 0) {
                return 0;
            } else {
                return writeGet / consumeGet;
            }
        }
    }


    /**
     * Next tick
     */
    public void next() {
        synchronized (writeTotal) {
            writeTotal.addAndGet(-writeArray[writeArray.length - 1]);
            consumeTotal.addAndGet(-consumeArray[consumeArray.length - 1]);
            int[] writeArrayClone = new int[writeArray.length];
            int[] consumeArrayClone = new int[consumeArray.length];
            System.arraycopy(writeArray, 0, writeArrayClone, 1, writeArray.length - 1);
            System.arraycopy(consumeArray, 0, consumeArrayClone, 1, consumeArray.length - 1);
            writeArray = writeArrayClone;
            consumeArray = consumeArrayClone;
        }
    }
}
