package xuan.cat.fartherviewdistance.code.data;

/**
 * Cumulative report
 */
public final class CumulativeReport {
    /** High-speed load */
    private volatile int[] loadFast = new int[300];
    /** Slow-speed load */
    private volatile int[] loadSlow = new int[300];
    /** Consumption */
    private volatile int[] consume = new int[300];


    public void next() {
        try {
            // The cumulative amount is moved backward by 1
            int[] loadFastClone = new int[300];
            int[] loadSlowClone = new int[300];
            int[] consumeClone = new int[300];
            System.arraycopy(loadFast, 0, loadFastClone, 1, loadFast.length - 1);
            System.arraycopy(loadSlow, 0, loadSlowClone, 1, loadSlow.length - 1);
            System.arraycopy(consume, 0, consumeClone, 1, consume.length - 1);
            loadFast = loadFastClone;
            loadSlow = loadSlowClone;
            consume = consumeClone;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void increaseLoadFast() {
        loadFast[0]++;
    }
    public void increaseLoadSlow() {
        loadSlow[0]++;
    }
    public void addConsume(int value) {
        consume[0] += value;
    }


    public int reportLoadFast5s() {
        int total = 0;
        for (int i = 0 ; i < 5 ; ++i)
            total += loadFast[i];
        return total;
    }
    public int reportLoadFast1m() {
        int total = 0;
        for (int i = 0 ; i < 60 ; ++i)
            total += loadFast[i];
        return total;
    }
    public int reportLoadFast5m() {
        int total = 0;
        for (int i = 0 ; i < 300 ; ++i)
            total += loadFast[i];
        return total;
    }


    public int reportLoadSlow5s() {
        int total = 0;
        for (int i = 0 ; i < 5 ; ++i)
            total += loadSlow[i];
        return total;
    }
    public int reportLoadSlow1m() {
        int total = 0;
        for (int i = 0 ; i < 60 ; ++i)
            total += loadSlow[i];
        return total;
    }
    public int reportLoadSlow5m() {
        int total = 0;
        for (int i = 0 ; i < 300 ; ++i)
            total += loadSlow[i];
        return total;
    }


    public long reportConsume5s() {
        long total = 0;
        for (int i = 0 ; i < 5 ; ++i)
            total += consume[i];
        return total;
    }
    public long reportConsume1m() {
        long total = 0;
        for (int i = 0 ; i < 60 ; ++i)
            total += consume[i];
        return total;
    }
    public long reportConsume5m() {
        long total = 0;
        for (int i = 0 ; i < 300 ; ++i)
            total += consume[i];
        return total;
    }
}
