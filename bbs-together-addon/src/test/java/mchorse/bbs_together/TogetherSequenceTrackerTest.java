package mchorse.bbs_together;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TogetherSequenceTrackerTest
{
    @Test
    void presenceDoesNotCreateMutationGaps()
    {
        TogetherSequenceTracker tracker = new TogetherSequenceTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(0, tracker.nextMutation("film"));
        assertEquals(0, tracker.nextPresence("film", first));
        assertEquals(1, tracker.nextPresence("film", first));
        assertEquals(0, tracker.nextPresence("film", second));
        assertEquals(1, tracker.nextMutation("film"));
    }

    @Test
    void clearingDocumentResetsBothSequenceDomains()
    {
        TogetherSequenceTracker tracker = new TogetherSequenceTracker();
        UUID participant = UUID.randomUUID();

        tracker.nextMutation("film");
        tracker.nextPresence("film", participant);
        tracker.clearDocument("film");

        assertEquals(0, tracker.nextMutation("film"));
        assertEquals(0, tracker.nextPresence("film", participant));
    }
}
