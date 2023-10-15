package xuan.cat.fartherviewdistance.code.data;

/**
 * Network traffic monitor
 */
public final class NetworkTraffic {
//    /** Write to array */
//    private volatile int[] writeArray = new int[20];
//    /** Write accumulation */
//    private final AtomicInteger writeTotal = new AtomicInteger(0);
    private volatile int value = 0;


    /**
     * Utilization
     * @param length number of bytes
     */
    public synchronized void use(int length) {
        value += length;
//        synchronized (writeTotal) {
//            writeArray[0] += length;
//            writeTotal.addAndGet(length);
//        }
    }

    /**
     * @return Current status
     */
    public synchronized int get() {
        return value;
//        synchronized (writeTotal) {
//            return writeTotal.get();
//        }
    }

    /**
     * @param length Number of bytes
     * @return Whether it is below the usage level
     */
    public synchronized boolean exceed(int length) {
        return value >= length;
//        synchronized (writeTotal) {
//            return writeTotal.get() >= length;
//        }
    }

    /**
     * Next tick
     */
    public void next() {
        value = 0;
//        synchronized (writeTotal) {
//            writeTotal.addAndGet(-writeArray[writeArray.length - 1]);
//            int[] writeArrayClone = new int[writeArray.length];
//            System.arraycopy(writeArray, 0, writeArrayClone, 1, writeArray.length - 1);
//            writeArray = writeArrayClone;
//        }
    }
}
