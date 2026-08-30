package mchorse.bbs_together;

/** Hard limits shared by the broker codec, server handlers and tests. */
public final class TogetherLimits
{
    public static final int MAX_TEXT_BYTES = 256;
    public static final int MAX_DOCUMENT_ID_BYTES = 512;
    public static final int MAX_PATH_SEGMENTS = 64;
    public static final int MAX_PATH_SEGMENT_BYTES = 1024;
    public static final int MAX_PATH_BYTES = 16 * 1024;
    public static final int BROKER_C2S_BYTES = 28 * 1024;
    public static final int BROKER_S2C_BYTES = 1024 * 1024;
    public static final int CHUNK_BYTES = 24 * 1024;
    public static final int MAX_CHUNKS = 4096;
    public static final int MAX_TRANSFER_BYTES = 64 * 1024 * 1024;
    public static final int MAX_GLOBAL_TRANSFER_BYTES = 96 * 1024 * 1024;
    public static final int MAX_ASSET_BYTES = 32 * 1024 * 1024;
    public static final int MAX_MANIFEST_ENTRIES = 10_000;
    public static final int MAX_ASSET_REQUESTS = 128;
    public static final int MAX_SELECTIONS = 256;
    public static final int MAX_MUTATIONS = 256;
    public static final int MAX_REPLAYS = 4096;
    public static final int MAX_REPLAY_LOCKS = 1024;

    private TogetherLimits()
    {}
}
