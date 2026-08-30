package mchorse.bbs_together;

import net.minecraft.resources.ResourceLocation;

/** Message names inside the BBSFSR-owned addon broker. */
public final class TogetherMessageIds
{
    public static final ResourceLocation HELLO = id("hello");
    public static final ResourceLocation PLAYER_COLOR = id("player_color");
    public static final ResourceLocation SESSION_OPEN = id("session_open");
    public static final ResourceLocation SESSION_CLOSE = id("session_close");
    public static final ResourceLocation MUTATION = id("film_mutation");
    public static final ResourceLocation SNAPSHOT_REQUEST = id("film_snapshot_request");
    public static final ResourceLocation SNAPSHOT = id("film_snapshot");
    public static final ResourceLocation SNAPSHOT_ACK = id("film_snapshot_ack");
    public static final ResourceLocation PRESENCE = id("film_presence");
    public static final ResourceLocation RESYNC = id("film_resync");
    public static final ResourceLocation REPLAY_LOCK = id("replay_lock");
    public static final ResourceLocation RECORDING = id("recording");
    public static final ResourceLocation FILM_SCREENS = id("film_screens");
    public static final ResourceLocation FILM_LIST_CHANGED = id("film_list_changed");
    public static final ResourceLocation FILM_RENAMED = id("film_renamed");
    public static final ResourceLocation FILM_DELETED = id("film_deleted");
    public static final ResourceLocation ASSET_MANIFEST = id("asset_manifest");
    public static final ResourceLocation ASSET_REQUEST = id("asset_request");
    public static final ResourceLocation ASSET_CHUNK = id("asset_chunk");
    public static final ResourceLocation ASSET_UPLOAD = id("asset_upload");
    public static final ResourceLocation ASSET_UPLOAD_ACK = id("asset_upload_ack");
    public static final ResourceLocation ASSET_DOWNLOAD_FAILED = id("asset_download_failed");
    public static final ResourceLocation TRANSFER_BEGIN = id("transfer_begin");
    public static final ResourceLocation TRANSFER_CHUNK = id("transfer_chunk");

    private TogetherMessageIds()
    {}

    private static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(BBSTogetherMod.MOD_ID, path);
    }
}
