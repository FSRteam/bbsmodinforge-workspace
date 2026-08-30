package mchorse.bbs_together;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TogetherReplayLockRegistryTest
{
    @Test
    void rejectsAnotherOwnerAndRequiresAnExactUnlock()
    {
        TogetherReplayLockRegistry locks = new TogetherReplayLockRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(locks.tryLock(first, "film", 2).accepted());
        assertFalse(locks.tryLock(second, "film", 2).accepted());
        assertEquals(Map.of(first, 2), locks.snapshot("film"));

        assertFalse(locks.unlock(first, "film", 3).accepted());
        assertEquals(Map.of(first, 2), locks.snapshot("film"));
        assertTrue(locks.unlock(first, "film", 2).accepted());
        assertTrue(locks.snapshot("film").isEmpty());
    }

    @Test
    void movingAndClearingAnOwnerExposeTheAffectedLock()
    {
        TogetherReplayLockRegistry locks = new TogetherReplayLockRegistry();
        UUID owner = UUID.randomUUID();

        locks.tryLock(owner, "first", 1);
        TogetherReplayLockRegistry.LockChange moved = locks.tryLock(owner, "second", 4);

        assertEquals(new TogetherReplayLockRegistry.ReplayLock("first", 1), moved.previous());
        assertEquals(new TogetherReplayLockRegistry.ReplayLock("second", 4), moved.current());
        assertTrue(locks.snapshot("first").isEmpty());
        assertEquals(Map.of(owner, 4), locks.snapshot("second"));
        assertEquals(moved.current(), locks.clearOwner(owner));
        assertNull(locks.clearOwner(owner));
    }
}
