package mchorse.bbs_together.asset;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded background lane for hashing and asset file I/O. */
public final class TogetherAssetIo
{
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(2),
        runnable ->
        {
            Thread thread = new Thread(runnable, "BBSTogether-AssetIO");
            thread.setDaemon(true);
            return thread;
        }
    );

    private TogetherAssetIo()
    {}

    public static boolean submit(Runnable task)
    {
        try
        {
            EXECUTOR.execute(task);
            return true;
        }
        catch (RejectedExecutionException exception)
        {
            return false;
        }
    }
}
