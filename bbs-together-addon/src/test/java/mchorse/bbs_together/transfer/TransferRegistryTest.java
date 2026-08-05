package mchorse.bbs_together.transfer;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class TransferRegistryTest
{
    @Test
    void reservesGlobalMemoryAndReleasesItOnCompletion()
    {
        AtomicLong clock = new AtomicLong();
        TransferRegistry<String> registry = new TransferRegistry<>(Duration.ofSeconds(1), 6, clock::get);
        UUID owner = UUID.randomUUID();
        byte[] first = new byte[] {1, 2, 3, 4};

        registry.begin(owner, 1, 1, first.length, TransferChecksum.sha256(first), "first");
        assertEquals(first.length, registry.reservedBytes());
        assertThrows(IllegalStateException.class, () ->
            registry.begin(UUID.randomUUID(), 2, 1, 3, TransferChecksum.sha256(new byte[] {1, 2, 3}), "second"));

        assertTrue(registry.accept(owner, 1, 0, first));
        TransferRegistry.CompletedTransfer<String> completed = registry.finish(owner, 1);
        assertEquals("first", completed.metadata());
        assertArrayEquals(first, completed.bytes());
        assertEquals(0, registry.reservedBytes());
    }

    @Test
    void invalidChunkAndTimeoutReleaseReservations()
    {
        AtomicLong clock = new AtomicLong();
        TransferRegistry<Void> registry = new TransferRegistry<>(Duration.ofNanos(10), 32, clock::get);
        UUID owner = UUID.randomUUID();
        byte[] bytes = new byte[] {1, 2};

        registry.begin(owner, 1, 1, bytes.length, TransferChecksum.sha256(bytes), null);
        assertThrows(IllegalArgumentException.class, () -> registry.accept(owner, 1, 1, bytes));
        assertEquals(0, registry.reservedBytes());

        registry.begin(owner, 2, 1, bytes.length, TransferChecksum.sha256(bytes), null);
        clock.set(10);
        assertEquals(1, registry.expireIdle());
        assertEquals(0, registry.reservedBytes());
    }

    @Test
    void completesAnEmptyTransferImmediately()
    {
        TransferRegistry<String> registry = new TransferRegistry<>(Duration.ofSeconds(1));
        UUID owner = UUID.randomUUID();
        byte[] empty = new byte[0];

        registry.begin(owner, 1, 0, 0, TransferChecksum.sha256(empty), "empty");
        TransferRegistry.CompletedTransfer<String> completed = registry.finish(owner, 1);

        assertEquals("empty", completed.metadata());
        assertArrayEquals(empty, completed.bytes());
        assertEquals(0, registry.reservedBytes());
    }
}
