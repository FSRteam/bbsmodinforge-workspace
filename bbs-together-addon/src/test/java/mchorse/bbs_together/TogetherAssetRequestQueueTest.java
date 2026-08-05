package mchorse.bbs_together;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TogetherAssetRequestQueueTest
{
    @Test
    void serializesRequestsAndRetriesTheActivePathFirst()
    {
        TogetherAssetRequestQueue queue = new TogetherAssetRequestQueue();

        queue.enqueue(List.of("first.bin", "second.bin"));
        assertEquals("first.bin", queue.beginNext());
        assertNull(queue.beginNext());
        queue.retryCurrent("first.bin");
        assertEquals("first.bin", queue.beginNext());
        queue.completeCurrent();
        assertEquals("second.bin", queue.beginNext());
        queue.completeCurrent();
        assertTrue(queue.isIdle());
    }

    @Test
    void rejectsMoreThanTheBoundedPendingEntryCount()
    {
        TogetherAssetRequestQueue queue = new TogetherAssetRequestQueue();
        List<String> paths = Collections.nCopies(TogetherLimits.MAX_MANIFEST_ENTRIES + 1, "a");

        assertThrows(IllegalStateException.class, () -> queue.enqueue(paths));
        assertTrue(queue.isIdle());
    }
}
