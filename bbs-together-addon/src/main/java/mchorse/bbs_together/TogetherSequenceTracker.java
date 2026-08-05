package mchorse.bbs_together;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps strict Film ordering independent from per-participant presence watermarks. */
final class TogetherSequenceTracker
{
    private final Map<String, Long> mutationSequences = new HashMap<>();
    private final Map<PresenceKey, Long> presenceSequences = new HashMap<>();

    public synchronized long nextMutation(String document)
    {
        return this.mutationSequences.merge(document, 0L, (previous, ignored) -> previous + 1L);
    }

    public synchronized long nextPresence(String document, UUID participant)
    {
        return this.presenceSequences.merge(new PresenceKey(document, participant), 0L,
            (previous, ignored) -> previous + 1L);
    }

    public synchronized void clearDocument(String document)
    {
        this.mutationSequences.remove(document);
        this.presenceSequences.keySet().removeIf(key -> key.document.equals(document));
    }

    public synchronized void clear()
    {
        this.mutationSequences.clear();
        this.presenceSequences.clear();
    }

    private record PresenceKey(String document, UUID participant)
    {}
}
