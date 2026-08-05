package mchorse.bbs_together;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;

/** Bounded per-player queue that serializes asset reads through the shared I/O lane. */
final class TogetherAssetRequestQueue
{
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private int pathBytes;
    private boolean active;

    public synchronized void enqueue(List<String> paths)
    {
        int addedBytes = paths.stream().mapToInt(path -> path.getBytes(StandardCharsets.UTF_8).length).sum();
        if (this.pending.size() + paths.size() > TogetherLimits.MAX_MANIFEST_ENTRIES
            || (long) this.pathBytes + addedBytes > TogetherLimits.MAX_TRANSFER_BYTES)
        {
            throw new IllegalStateException("asset request queue limit reached");
        }
        this.pending.addAll(paths);
        this.pathBytes += addedBytes;
    }

    public synchronized String beginNext()
    {
        if (this.active)
        {
            return null;
        }
        String next = this.pending.pollFirst();
        if (next != null)
        {
            this.pathBytes -= next.getBytes(StandardCharsets.UTF_8).length;
            this.active = true;
        }
        return next;
    }

    public synchronized void completeCurrent()
    {
        this.active = false;
    }

    public synchronized void retryCurrent(String path)
    {
        this.pending.addFirst(path);
        this.pathBytes += path.getBytes(StandardCharsets.UTF_8).length;
        this.active = false;
    }

    public synchronized boolean isIdle()
    {
        return !this.active && this.pending.isEmpty();
    }
}
