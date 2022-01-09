package ca.encodeous.mwx.engines.performance;

public class RealTPS implements Runnable{
    public static RealTPS Instance = new RealTPS();
    public double getTps(){
        double avgDurationNano = 0;
        int criticalTickCount = 0;
        for(int i = 0; i < 10; i++){
            avgDurationNano += tickDurations[i] / 100.0;
            if(tickDurations[i] >= 500000000) criticalTickCount++;
        }
        if(criticalTickCount >= 10){
            return 0;
        }
        return 1000000000 / avgDurationNano;
    }
    private volatile long[] tickDurations = new long[100];
    private long idx = 0;
    public long lastTick = System.nanoTime();
    @Override
    public void run() {
        long curTick = System.nanoTime();
        tickDurations[(int)(idx % 100)] = curTick - lastTick;
        lastTick = curTick;
    }
}
