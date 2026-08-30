package mchorse.bbs_together;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned replay form locks, independent from client UI lifetime. */
final class TogetherReplayLockRegistry
{
    private final Map<UUID, ReplayLock> locks = new HashMap<>();

    public synchronized LockChange tryLock(UUID owner, String document, int replayIndex)
    {
        ReplayLock requested = new ReplayLock(document, replayIndex);

        for (Map.Entry<UUID, ReplayLock> entry : this.locks.entrySet())
        {
            if (!entry.getKey().equals(owner) && entry.getValue().equals(requested))
            {
                return new LockChange(false, this.locks.get(owner), this.locks.get(owner));
            }
        }

        ReplayLock previous = this.locks.put(owner, requested);

        return new LockChange(true, previous, requested);
    }

    public synchronized LockChange unlock(UUID owner, String document, int replayIndex)
    {
        ReplayLock expected = new ReplayLock(document, replayIndex);
        ReplayLock current = this.locks.get(owner);

        if (!expected.equals(current))
        {
            return new LockChange(false, current, current);
        }

        this.locks.remove(owner);

        return new LockChange(true, current, null);
    }

    public synchronized ReplayLock clearOwner(UUID owner)
    {
        return this.locks.remove(owner);
    }

    public synchronized Map<UUID, Integer> snapshot(String document)
    {
        Map<UUID, Integer> snapshot = new LinkedHashMap<>();

        this.locks.forEach((owner, lock) ->
        {
            if (lock.document().equals(document))
            {
                snapshot.put(owner, lock.replayIndex());
            }
        });

        return Map.copyOf(snapshot);
    }

    public synchronized void clear()
    {
        this.locks.clear();
    }

    record ReplayLock(String document, int replayIndex)
    {}

    record LockChange(boolean accepted, ReplayLock previous, ReplayLock current)
    {}
}
