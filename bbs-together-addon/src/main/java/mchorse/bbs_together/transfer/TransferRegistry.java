package mchorse.bbs_together.transfer;

import mchorse.bbs_together.TogetherLimits;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Transfer registry with per-owner concurrency, global memory reservations and idle expiry. */
public final class TransferRegistry<T>
{
    private static final int MAX_PER_OWNER = 4;

    private final Map<UUID, Map<Long, Entry<T>>> entries = new HashMap<>();
    private final long idleNanos;
    private final int maxReservedBytes;
    private final LongSupplier nanoTime;
    private long reservedBytes;

    public TransferRegistry(Duration idleTimeout)
    {
        this(idleTimeout, TogetherLimits.MAX_GLOBAL_TRANSFER_BYTES, System::nanoTime);
    }

    TransferRegistry(Duration idleTimeout, int maxReservedBytes, LongSupplier nanoTime)
    {
        if (idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()
            || maxReservedBytes <= 0 || nanoTime == null)
        {
            throw new IllegalArgumentException("transfer registry limits must be positive");
        }

        this.idleNanos = idleTimeout.toNanos();
        this.maxReservedBytes = maxReservedBytes;
        this.nanoTime = nanoTime;
    }

    public synchronized void begin(
        UUID owner,
        long transferId,
        int chunkCount,
        int totalBytes,
        byte[] checksum,
        T metadata
    )
    {
        if (owner == null || transferId <= 0)
        {
            throw new IllegalArgumentException("invalid transfer owner or id");
        }

        TransferAssembler assembler = new TransferAssembler(chunkCount, totalBytes, checksum);
        Map<Long, Entry<T>> owned = this.entries.get(owner);
        if (owned != null)
        {
            expire(owned, this.nanoTime.getAsLong());
        }

        if (owned != null && (owned.size() >= MAX_PER_OWNER || owned.containsKey(transferId)))
        {
            throw new IllegalStateException("transfer concurrency limit reached");
        }
        if (this.reservedBytes + totalBytes > this.maxReservedBytes)
        {
            throw new IllegalStateException("global transfer memory limit reached");
        }

        if (owned == null)
        {
            owned = new HashMap<>();
            this.entries.put(owner, owned);
        }
        owned.put(transferId, new Entry<>(assembler, metadata, this.nanoTime.getAsLong()));
        this.reservedBytes += totalBytes;
    }

    public synchronized boolean accept(UUID owner, long transferId, int index, byte[] chunk)
    {
        Entry<T> entry = require(owner, transferId);
        try
        {
            entry.assembler.accept(index, chunk);
            entry.touchedNanos = this.nanoTime.getAsLong();
            return entry.assembler.complete();
        }
        catch (RuntimeException exception)
        {
            remove(owner, transferId);
            throw exception;
        }
    }

    public synchronized CompletedTransfer<T> finish(UUID owner, long transferId)
    {
        Entry<T> entry = require(owner, transferId);
        try
        {
            return new CompletedTransfer<>(entry.metadata, entry.assembler.finish());
        }
        finally
        {
            remove(owner, transferId);
        }
    }

    public synchronized void cancel(UUID owner, long transferId)
    {
        remove(owner, transferId);
    }

    public synchronized void clear(UUID owner)
    {
        Map<Long, Entry<T>> owned = this.entries.remove(owner);
        if (owned != null)
        {
            owned.values().forEach(this::release);
        }
    }

    public synchronized void clear()
    {
        this.entries.clear();
        this.reservedBytes = 0;
    }

    public synchronized int expireIdle()
    {
        int expired = 0;
        long now = this.nanoTime.getAsLong();
        Iterator<Map.Entry<UUID, Map<Long, Entry<T>>>> owners = this.entries.entrySet().iterator();

        while (owners.hasNext())
        {
            Map<Long, Entry<T>> owned = owners.next().getValue();
            expired += expire(owned, now);
            if (owned.isEmpty())
            {
                owners.remove();
            }
        }

        return expired;
    }

    synchronized long reservedBytes()
    {
        return this.reservedBytes;
    }

    private Entry<T> require(UUID owner, long transferId)
    {
        Map<Long, Entry<T>> owned = this.entries.get(owner);
        Entry<T> entry = owned == null ? null : owned.get(transferId);
        if (entry == null)
        {
            throw new IllegalStateException("unknown or expired transfer");
        }
        return entry;
    }

    private void remove(UUID owner, long transferId)
    {
        Map<Long, Entry<T>> owned = this.entries.get(owner);
        Entry<T> removed = owned == null ? null : owned.remove(transferId);
        if (removed != null)
        {
            release(removed);
        }
        if (owned != null && owned.isEmpty())
        {
            this.entries.remove(owner);
        }
    }

    private int expire(Map<Long, Entry<T>> owned, long now)
    {
        int count = 0;
        Iterator<Entry<T>> iterator = owned.values().iterator();
        while (iterator.hasNext())
        {
            Entry<T> entry = iterator.next();
            if (now - entry.touchedNanos >= this.idleNanos)
            {
                iterator.remove();
                release(entry);
                count++;
            }
        }
        return count;
    }

    private void release(Entry<T> entry)
    {
        this.reservedBytes -= entry.assembler.totalBytes();
    }

    public record CompletedTransfer<T>(T metadata, byte[] bytes)
    {}

    private static final class Entry<T>
    {
        private final TransferAssembler assembler;
        private final T metadata;
        private long touchedNanos;

        private Entry(TransferAssembler assembler, T metadata, long touchedNanos)
        {
            this.assembler = assembler;
            this.metadata = metadata;
            this.touchedNanos = touchedNanos;
        }
    }
}
