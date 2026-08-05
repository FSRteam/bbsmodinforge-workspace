package mchorse.bbs_together;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.api.client.BBSClientApi;
import mchorse.bbs_mod.api.client.film.BBSFilmApplyResult;
import mchorse.bbs_mod.api.client.film.BBSFilmCheckpointRequired;
import mchorse.bbs_mod.api.client.film.BBSFilmCollaborationListener;
import mchorse.bbs_mod.api.client.film.BBSFilmCollaborationSubscription;
import mchorse.bbs_mod.api.client.film.BBSFilmEditorKind;
import mchorse.bbs_mod.api.client.film.BBSFilmEditorView;
import mchorse.bbs_mod.api.client.film.BBSFilmKeyframeSelection;
import mchorse.bbs_mod.api.client.film.BBSFilmMutation;
import mchorse.bbs_mod.api.client.film.BBSFilmMutationBatch;
import mchorse.bbs_mod.api.client.film.BBSFilmMutationKind;
import mchorse.bbs_mod.api.client.film.BBSFilmPresence;
import mchorse.bbs_mod.api.client.film.BBSFilmPresenceClearRequest;
import mchorse.bbs_mod.api.client.film.BBSFilmRefreshHint;
import mchorse.bbs_mod.api.client.film.BBSFilmRemotePresence;
import mchorse.bbs_mod.api.client.film.BBSFilmServerSequenceObserveRequest;
import mchorse.bbs_mod.api.client.film.BBSFilmSession;
import mchorse.bbs_mod.api.client.film.BBSFilmSnapshotApplyRequest;
import mchorse.bbs_mod.api.client.film.BBSFilmSnapshotResult;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_together.asset.TogetherAssetIo;
import mchorse.bbs_together.asset.TogetherAssetManifest;
import mchorse.bbs_together.asset.TogetherAssetStorage;
import mchorse.bbs_together.network.TogetherWire;
import mchorse.bbs_together.transfer.TogetherTransferKind;
import mchorse.bbs_together.transfer.TransferChecksum;
import mchorse.bbs_together.transfer.TransferRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.time.Duration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Client broker adapter and native BBSFSR Film collaboration bridge. */
public final class TogetherClientNetwork
{
    public static final Map<UUID, Integer> PLAYER_COLORS = new ConcurrentHashMap<>();
    public static final Map<UUID, String> PLAYERS_IN_FILM_SCREEN = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, UUID>> REPLAY_FORM_LOCKS = new ConcurrentHashMap<>();
    public static volatile boolean damageControlActive;
    public static volatile int assetDownloadTotal;
    public static volatile int assetDownloadDone;
    public static volatile int assetDownloadErrors;
    public static volatile int assetUploadTotal;
    public static volatile int assetUploadDone;
    public static volatile int assetUploadErrors;
    public static volatile long assetDownloadFinishedAt;
    public static volatile long assetUploadFinishedAt;
    private static final TransferRegistry<IncomingTransfer> INCOMING_TRANSFERS = new TransferRegistry<>(Duration.ofSeconds(30));
    private static final AtomicLong NEXT_TRANSFER_ID = new AtomicLong(1);
    private static BBSFilmCollaborationSubscription collaboration;
    private static LocalSession session;
    private static boolean connected;
    private static List<String> pendingUploads = List.of();
    private static int pendingUploadIndex;
    private static String pendingUploadPath;
    private static long pendingUploadTransferId = -1;
    private static long uploadGeneration;

    private TogetherClientNetwork()
    {}

    public static synchronized void register()
    {
        if (collaboration != null)
        {
            return;
        }

        register(TogetherMessageIds.PLAYER_COLOR, TogetherClientNetwork::handleColors);
        register(TogetherMessageIds.SESSION_CLOSE, TogetherClientNetwork::handleSessionClose);
        register(TogetherMessageIds.MUTATION, TogetherClientNetwork::handleMutation);
        register(TogetherMessageIds.SNAPSHOT_REQUEST, TogetherClientNetwork::handleSnapshotRequest);
        register(TogetherMessageIds.SNAPSHOT_ACK, TogetherClientNetwork::handleSnapshotAck);
        register(TogetherMessageIds.TRANSFER_BEGIN, TogetherClientNetwork::handleTransferBegin);
        register(TogetherMessageIds.TRANSFER_CHUNK, TogetherClientNetwork::handleTransferChunk);
        register(TogetherMessageIds.PRESENCE, TogetherClientNetwork::handlePresence);
        register(TogetherMessageIds.RECORDING, TogetherClientNetwork::handleRecording);
        register(TogetherMessageIds.ASSET_UPLOAD_ACK, TogetherClientNetwork::handleAssetUploadAck);
        register(TogetherMessageIds.ASSET_DOWNLOAD_FAILED, TogetherClientNetwork::handleAssetDownloadFailed);
        register(TogetherMessageIds.FILM_SCREENS, TogetherClientNetwork::handleFilmScreens);
        register(TogetherMessageIds.REPLAY_LOCK, TogetherClientNetwork::handleReplayLocks);
        register(TogetherMessageIds.FILM_LIST_CHANGED, TogetherClientNetwork::handleFilmListChanged);
        register(TogetherMessageIds.FILM_RENAMED, TogetherClientNetwork::handleFilmRenamed);
        register(TogetherMessageIds.FILM_DELETED, TogetherClientNetwork::handleFilmDeleted);
        collaboration = BBSClientApi.registerFilmCollaboration(BBSTogetherMod.DESCRIPTOR, new FilmListener());
    }

    public static void onConnected()
    {
        send(TogetherMessageIds.HELLO, output -> {});
        sendPlayerColor(BBSSettings.primaryColor == null ? 0xFFFFFF : BBSSettings.primaryColor.get());
    }

    public static void reset()
    {
        connected = false;
        PLAYER_COLORS.clear();
        PLAYERS_IN_FILM_SCREEN.clear();
        REPLAY_FORM_LOCKS.clear();
        damageControlActive = false;
        INCOMING_TRANSFERS.clear();
        assetDownloadTotal = 0;
        assetDownloadDone = 0;
        assetDownloadErrors = 0;
        assetUploadTotal = 0;
        assetUploadDone = 0;
        assetUploadErrors = 0;
        assetDownloadFinishedAt = 0;
        assetUploadFinishedAt = 0;
        pendingUploads = List.of();
        pendingUploadIndex = 0;
        pendingUploadPath = null;
        pendingUploadTransferId = -1;
        uploadGeneration++;
        session = null;
    }

    public static boolean isConnected()
    {
        return connected;
    }

    public static void sendPlayerColor(int color)
    {
        send(TogetherMessageIds.PLAYER_COLOR, output -> output.writeInt(color));
    }

    public static void sendReplayFormLock(String document, int replayIndex, boolean locked)
    {
        if (!connected || replayIndex < 0 || replayIndex >= TogetherLimits.MAX_REPLAYS)
        {
            return;
        }
        send(TogetherMessageIds.REPLAY_LOCK, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeVarInt(replayIndex);
            output.writeBoolean(locked);
        });
    }

    public static boolean isReplayFormLocked(String document, int replayIndex)
    {
        UUID owner = REPLAY_FORM_LOCKS.getOrDefault(document, Map.of()).get(replayIndex);

        return owner != null && !owner.equals(localPlayerId());
    }

    public static void sendFilmListChanged()
    {
        if (connected)
        {
            send(TogetherMessageIds.FILM_LIST_CHANGED, output -> {});
        }
    }

    public static void sendFilmRenamed(String from, String to)
    {
        if (connected)
        {
            send(TogetherMessageIds.FILM_RENAMED, output ->
            {
                TogetherWire.writeText(output, from, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "old Film id");
                TogetherWire.writeText(output, to, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "new Film id");
            });
        }
    }

    public static void sendFilmDeleted(String document)
    {
        if (connected)
        {
            send(TogetherMessageIds.FILM_DELETED, output ->
                TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "Film id"));
        }
    }

    private static void handleColors(FriendlyByteBuf input)
    {
        int count = TogetherWire.readCount(input, 1024, "player color");
        Map<UUID, Integer> next = new ConcurrentHashMap<>();
        for (int i = 0; i < count; i++)
        {
            next.put(input.readUUID(), input.readInt());
        }
        TogetherWire.requireFullyRead(input);
        PLAYER_COLORS.clear();
        PLAYER_COLORS.putAll(next);
        connected = true;
    }

    private static void handleSessionClose(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        UUID participant = input.readUUID();
        long sequence = input.readVarLong();
        TogetherWire.requireFullyRead(input);
        LocalSession current = matching(document);
        if (current == null)
        {
            return;
        }
        BBSClientApi.clearRemoteFilmPresence(BBSTogetherMod.DESCRIPTOR, new BBSFilmPresenceClearRequest(
            current.sessionId, current.revision, sequence, participant.toString()
        ));
    }

    private static void handleMutation(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        UUID source = input.readUUID();
        long sequence = input.readVarLong();
        long localOpId = input.readVarLong();
        int count = TogetherWire.readCount(input, TogetherLimits.MAX_MUTATIONS, "mutation");
        List<BBSFilmMutation> mutations = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            int kind = input.readUnsignedByte();
            List<String> path = TogetherWire.readPath(input);
            byte[] data = TogetherWire.readBytes(input, 16 * 1024 * 1024, "mutation data");
            int refresh = input.readUnsignedByte();
            mutations.add(new BBSFilmMutation(
                enumValue(BBSFilmMutationKind.values(), kind, "mutation kind"),
                path,
                data,
                enumValue(BBSFilmRefreshHint.values(), refresh, "refresh hint")
            ));
        }
        TogetherWire.requireFullyRead(input);

        LocalSession current = matching(document);
        if (current == null)
        {
            return;
        }

        UUID localPlayer = localPlayerId();
        if (source.equals(localPlayer))
        {
            BBSClientApi.observeFilmServerSequence(BBSTogetherMod.DESCRIPTOR,
                new BBSFilmServerSequenceObserveRequest(current.sessionId, current.revision, sequence));
            current.serverSequence = sequence;
            return;
        }

        BBSFilmMutationBatch batch = new BBSFilmMutationBatch(
            current.sessionId,
            current.revision,
            localOpId,
            sequence,
            mutations
        );
        BBSClientApi.applyRemoteFilmMutations(BBSTogetherMod.DESCRIPTOR, batch).thenAccept(result -> updateAfterApply(current, result));
    }

    private static void handleSnapshotRequest(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        input.readUUID();
        TogetherWire.requireFullyRead(input);
        LocalSession current = matching(document);
        if (current != null)
        {
            sendSnapshot(current);
        }
    }

    private static void handleSnapshotAck(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        long sequence = input.readVarLong();
        TogetherWire.requireFullyRead(input);
        LocalSession current = matching(document);
        if (current == null)
        {
            return;
        }
        BBSClientApi.observeFilmServerSequence(BBSTogetherMod.DESCRIPTOR,
            new BBSFilmServerSequenceObserveRequest(current.sessionId, current.revision, sequence));
        current.serverSequence = sequence;
    }

    private static void handleTransferBegin(FriendlyByteBuf input)
    {
        UUID source = input.readUUID();
        long transferId = input.readVarLong();
        TogetherTransferKind kind = TogetherTransferKind.fromWire(input.readUnsignedByte());
        int flags = input.readUnsignedByte();
        String scope = TogetherWire.readText(input, TogetherLimits.MAX_PATH_BYTES, "transfer scope");
        long sequence = input.readVarLong();
        int totalBytes = input.readVarInt();
        int chunkCount = input.readVarInt();
        byte[] checksum = TogetherWire.readBytes(input, 32, "transfer checksum");
        TogetherWire.requireFullyRead(input);

        if (checksum.length != TransferChecksum.SHA_256_BYTES || flags < 0 || flags > 1)
        {
            throw new IllegalArgumentException("unsupported transfer or checksum");
        }
        if (kind == TogetherTransferKind.FILM_SNAPSHOT && (flags != 0 || matching(scope) == null))
        {
            throw new IllegalArgumentException("snapshot targets an inactive Film");
        }
        if (kind == TogetherTransferKind.ASSET_MANIFEST && (flags != 0 || !"manifest".equals(scope)))
        {
            throw new IllegalArgumentException("asset manifest metadata is invalid");
        }
        if (kind == TogetherTransferKind.ASSET_FILE)
        {
            if (totalBytes > TogetherLimits.MAX_ASSET_BYTES)
            {
                throw new IllegalArgumentException("asset file is too large");
            }
            requireAssetPath(scope);
        }
        INCOMING_TRANSFERS.begin(source, transferId, chunkCount, totalBytes, checksum,
            new IncomingTransfer(kind, scope, flags, sequence));
        if (chunkCount == 0)
        {
            completeIncomingTransfer(source, transferId);
        }
    }

    private static void handleTransferChunk(FriendlyByteBuf input)
    {
        UUID source = input.readUUID();
        long transferId = input.readVarLong();
        int index = input.readVarInt();
        byte[] chunk = TogetherWire.readBytes(input, TogetherLimits.CHUNK_BYTES, "transfer chunk");
        TogetherWire.requireFullyRead(input);

        if (!INCOMING_TRANSFERS.accept(source, transferId, index, chunk))
        {
            return;
        }
        completeIncomingTransfer(source, transferId);
    }

    private static void completeIncomingTransfer(UUID source, long transferId)
    {
        TransferRegistry.CompletedTransfer<IncomingTransfer> completed = INCOMING_TRANSFERS.finish(source, transferId);
        IncomingTransfer metadata = completed.metadata();
        if (metadata.kind() == TogetherTransferKind.FILM_SNAPSHOT)
        {
            LocalSession current = matching(metadata.scope());
            if (current != null)
            {
                BBSClientApi.applyRemoteFilmSnapshot(BBSTogetherMod.DESCRIPTOR, new BBSFilmSnapshotApplyRequest(
                    current.sessionId, current.revision, metadata.sequence(), completed.bytes()
                )).thenAccept(result -> updateAfterApply(current, result));
            }
        }
        else if (metadata.kind() == TogetherTransferKind.ASSET_MANIFEST)
        {
            handleAssetManifest(completed.bytes());
        }
        else if (metadata.kind() == TogetherTransferKind.ASSET_FILE)
        {
            handleAssetFile(metadata, completed.bytes());
        }
    }

    private static void handlePresence(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        UUID source = input.readUUID();
        String displayName = TogetherWire.readText(input, TogetherLimits.MAX_TEXT_BYTES, "player name");
        int color = input.readInt();
        long sequence = input.readVarLong();
        byte[] payload = TogetherWire.readBytes(input, TogetherLimits.BROKER_C2S_BYTES - 1024, "presence");
        TogetherWire.requireFullyRead(input);
        LocalSession current = matching(document);
        if (current == null || source.equals(localPlayerId()))
        {
            return;
        }

        FriendlyByteBuf presenceBuffer = wrap(payload);
        BBSFilmPresence presence = decodePresence(presenceBuffer, current);
        TogetherWire.requireFullyRead(presenceBuffer);
        BBSClientApi.applyRemoteFilmPresence(BBSTogetherMod.DESCRIPTOR,
            new BBSFilmRemotePresence(source.toString(), displayName, color, sequence, presence));
    }

    private static void handleRecording(FriendlyByteBuf input)
    {
        boolean start = input.readBoolean();
        TogetherWire.requireFullyRead(input);
        Minecraft.getInstance().execute(() -> applyRecordingRequest(start));
    }

    private static void applyRecordingRequest(boolean start)
    {
        boolean active = false;
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
        LocalSession current = session;

        if (panel != null && panel.getData() != null && current != null
            && current.documentId.equals(panel.getData().getId()))
        {
            UIFilmController controller = panel.getController();
            if (start && !controller.isRecording() && panel.replayEditor.getReplay() != null)
            {
                controller.startRecording(null);
            }
            else if (!start && controller.isRecording())
            {
                controller.stopRecording();
            }
            active = controller.isRecording();
        }

        boolean reported = active;
        send(TogetherMessageIds.RECORDING, output -> output.writeBoolean(reported));
        BBSTogetherMod.LOGGER.info("Synchronized recording {}", active ? "started" : "stopped");
    }

    private static void handleFilmScreens(FriendlyByteBuf input)
    {
        int count = TogetherWire.readCount(input, 1024, "Film screen");
        Map<UUID, String> screens = new HashMap<>();
        for (int i = 0; i < count; i++)
        {
            screens.put(input.readUUID(), TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id"));
        }
        TogetherWire.requireFullyRead(input);
        PLAYERS_IN_FILM_SCREEN.clear();
        PLAYERS_IN_FILM_SCREEN.putAll(screens);
        damageControlActive = !screens.isEmpty();
    }

    private static void handleReplayLocks(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        int count = TogetherWire.readCount(input, TogetherLimits.MAX_REPLAY_LOCKS, "replay lock");
        Map<Integer, UUID> locks = new HashMap<>();

        for (int i = 0; i < count; i++)
        {
            UUID owner = input.readUUID();
            int replayIndex = input.readVarInt();
            if (replayIndex < 0 || replayIndex >= TogetherLimits.MAX_REPLAYS)
            {
                throw new IllegalArgumentException("replay lock index is outside its limit");
            }
            locks.putIfAbsent(replayIndex, owner);
        }
        TogetherWire.requireFullyRead(input);

        if (locks.isEmpty())
        {
            REPLAY_FORM_LOCKS.remove(document);
        }
        else
        {
            REPLAY_FORM_LOCKS.put(document, Map.copyOf(locks));
        }
    }

    private static void handleFilmListChanged(FriendlyByteBuf input)
    {
        TogetherWire.requireFullyRead(input);
        Minecraft.getInstance().execute(() ->
        {
            UIFilmPanel panel = filmPanelIfCreated();
            if (panel != null)
            {
                panel.requestNames();
            }
        });
    }

    private static void handleFilmRenamed(FriendlyByteBuf input)
    {
        String from = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "old Film id");
        String to = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "new Film id");
        TogetherWire.requireFullyRead(input);
        Minecraft.getInstance().execute(() ->
        {
            UIFilmPanel panel = filmPanelIfCreated();
            if (panel != null)
            {
                panel.renameFilmId(from, to);
                panel.requestNames();
            }
        });
    }

    private static void handleFilmDeleted(FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "Film id");
        TogetherWire.requireFullyRead(input);
        Minecraft.getInstance().execute(() ->
        {
            UIFilmPanel panel = filmPanelIfCreated();
            if (panel != null)
            {
                panel.deleteFilmIds(Set.of(document));
                panel.requestNames();
            }
        });
    }

    private static UIFilmPanel filmPanelIfCreated()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

        return dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
    }

    private static void sendSnapshot(LocalSession current)
    {
        BBSClientApi.requestFilmSnapshot(BBSTogetherMod.DESCRIPTOR, current.sessionId).thenAccept(result ->
        {
            if (!result.successful())
            {
                BBSTogetherMod.LOGGER.warn("Unable to capture Film snapshot: {}", result.message());
                return;
            }

            sendTransfer(TogetherTransferKind.FILM_SNAPSHOT, current.documentId, 0, result.snapshot().encodedBbsData());
        });
    }

    public static void expireTransfers()
    {
        INCOMING_TRANSFERS.expireIdle();
    }

    private static long sendTransfer(TogetherTransferKind kind, String scope, int flags, byte[] bytes)
    {
        int maximum = kind == TogetherTransferKind.ASSET_FILE
            ? TogetherLimits.MAX_ASSET_BYTES
            : TogetherLimits.MAX_TRANSFER_BYTES;
        if (bytes == null || bytes.length > maximum || kind == TogetherTransferKind.FILM_SNAPSHOT && bytes.length == 0)
        {
            BBSTogetherMod.LOGGER.warn("Rejected outgoing {} transfer with invalid size", kind);
            return -1;
        }

        long transferId = NEXT_TRANSFER_ID.getAndIncrement();
        int chunkCount = (bytes.length + TogetherLimits.CHUNK_BYTES - 1) / TogetherLimits.CHUNK_BYTES;
        byte[] checksum = TransferChecksum.sha256(bytes);
        if (!send(TogetherMessageIds.TRANSFER_BEGIN, output ->
        {
            output.writeVarLong(transferId);
            output.writeByte(kind.ordinal());
            output.writeByte(flags);
            TogetherWire.writeText(output, scope, TogetherLimits.MAX_PATH_BYTES, "transfer scope");
            output.writeVarInt(bytes.length);
            output.writeVarInt(chunkCount);
            TogetherWire.writeBytes(output, checksum, 32, "transfer checksum");
        }))
        {
            return -1;
        }

        for (int index = 0; index < chunkCount; index++)
        {
            int offset = index * TogetherLimits.CHUNK_BYTES;
            byte[] chunk = Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + TogetherLimits.CHUNK_BYTES));
            int chunkIndex = index;
            if (!send(TogetherMessageIds.TRANSFER_CHUNK, output ->
            {
                output.writeVarLong(transferId);
                output.writeVarInt(chunkIndex);
                TogetherWire.writeBytes(output, chunk, TogetherLimits.CHUNK_BYTES, "transfer chunk");
            }))
            {
                BBSTogetherMod.LOGGER.warn("Stopped outgoing {} transfer {} at chunk {}", kind, transferId, chunkIndex);
                return -1;
            }
        }
        return transferId;
    }

    private static void handleAssetManifest(byte[] bytes)
    {
        if (!downloadsEnabled() && !uploadsEnabled())
        {
            return;
        }
        if (assetDownloadTotal > assetDownloadDone + assetDownloadErrors
            || assetUploadTotal > assetUploadDone + assetUploadErrors)
        {
            BBSTogetherMod.LOGGER.info("Ignored a new asset manifest while synchronization is active");
            return;
        }
        if (!TogetherAssetIo.submit(() ->
        {
            try
            {
                List<TogetherAssetManifest.Entry> serverEntries = TogetherAssetManifest.decode(bytes);
                List<TogetherAssetManifest.Entry> localEntries = TogetherAssetManifest.scan(assetsRoot());
                Map<String, TogetherAssetManifest.Entry> server = indexManifest(serverEntries);
                Map<String, TogetherAssetManifest.Entry> local = indexManifest(localEntries);
                List<String> downloads = new ArrayList<>();
                List<String> uploads = new ArrayList<>();

                for (TogetherAssetManifest.Entry entry : serverEntries)
                {
                    TogetherPath.resolveContained(assetsRoot(), entry.path());
                    TogetherAssetManifest.Entry present = local.get(entry.path());
                    if (downloadsEnabled() && (present == null || !Arrays.equals(present.checksum(), entry.checksum())))
                    {
                        downloads.add(entry.path());
                    }
                }
                if (uploadsEnabled())
                {
                    for (TogetherAssetManifest.Entry entry : localEntries)
                    {
                        TogetherAssetManifest.Entry present = server.get(entry.path());
                        if (present == null || !Arrays.equals(present.checksum(), entry.checksum()))
                        {
                            uploads.add(entry.path());
                        }
                    }
                }

                Minecraft.getInstance().execute(() ->
                {
                    assetDownloadTotal = downloads.size();
                    assetDownloadDone = 0;
                    assetDownloadErrors = 0;
                    assetDownloadFinishedAt = downloads.isEmpty() ? System.currentTimeMillis() : 0;
                    assetUploadTotal = uploads.size();
                    assetUploadDone = 0;
                    assetUploadErrors = 0;
                    assetUploadFinishedAt = uploads.isEmpty() ? System.currentTimeMillis() : 0;
                    sendAssetRequests(downloads);
                    startUploads(List.copyOf(uploads));
                });
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to process server asset manifest: {}", exception.getMessage());
            }
        }))
        {
            BBSTogetherMod.LOGGER.warn("Asset manifest processing queue is full");
        }
    }

    private static Map<String, TogetherAssetManifest.Entry> indexManifest(List<TogetherAssetManifest.Entry> entries)
    {
        Map<String, TogetherAssetManifest.Entry> indexed = new HashMap<>();
        for (TogetherAssetManifest.Entry entry : entries)
        {
            indexed.put(entry.path(), entry);
        }
        return indexed;
    }

    private static void sendAssetRequests(List<String> paths)
    {
        int index = 0;
        while (index < paths.size())
        {
            List<String> batch = new ArrayList<>();
            int encodedBytes = 5;
            while (index < paths.size() && batch.size() < TogetherLimits.MAX_ASSET_REQUESTS)
            {
                String path = paths.get(index);
                int nextBytes = path.getBytes(StandardCharsets.UTF_8).length + 5;
                if (!batch.isEmpty() && encodedBytes + nextBytes > TogetherLimits.BROKER_C2S_BYTES - 1024)
                {
                    break;
                }
                batch.add(path);
                encodedBytes += nextBytes;
                index++;
            }
            send(TogetherMessageIds.ASSET_REQUEST, output ->
            {
                output.writeVarInt(batch.size());
                batch.forEach(path -> TogetherWire.writeText(output, path, TogetherLimits.MAX_PATH_BYTES, "asset path"));
            });
        }
    }

    private static void startUploads(List<String> paths)
    {
        pendingUploads = paths;
        pendingUploadIndex = 0;
        pendingUploadPath = null;
        pendingUploadTransferId = -1;
        uploadGeneration++;
        sendNextUpload();
    }

    private static void sendNextUpload()
    {
        if (pendingUploadPath != null || pendingUploadIndex >= pendingUploads.size())
        {
            return;
        }

        String relative = pendingUploads.get(pendingUploadIndex);
        long generation = uploadGeneration;
        pendingUploadPath = relative;
        if (!TogetherAssetIo.submit(() ->
        {
            byte[] bytes = null;
            try
            {
                bytes = TogetherAssetStorage.read(assetsRoot(), relative);
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to read local asset {}: {}", relative, exception.getMessage());
            }
            byte[] payload = bytes;
            Minecraft.getInstance().execute(() ->
            {
                if (generation != uploadGeneration || !relative.equals(pendingUploadPath))
                {
                    return;
                }
                long transferId = payload == null ? -1 : sendTransfer(TogetherTransferKind.ASSET_FILE, relative, 0, payload);
                if (transferId < 0)
                {
                    finishPendingUpload(false);
                }
                else
                {
                    pendingUploadTransferId = transferId;
                }
            });
        }))
        {
            finishPendingUpload(false);
        }
    }

    private static void handleAssetFile(IncomingTransfer metadata, byte[] bytes)
    {
        boolean push = metadata.flags() == 1;
        if (push && !pushesEnabled() || !push && !downloadsEnabled())
        {
            return;
        }
        if (!TogetherAssetIo.submit(() ->
        {
            try
            {
                Path written = TogetherAssetStorage.writeAtomically(assetsRoot(), metadata.scope(), bytes);
                BBSTogetherMod.LOGGER.info("{} asset {}", push ? "Received pushed" : "Downloaded", written);
                if (!push)
                {
                    assetDownloadDone++;
                    finishDownloadProgressIfComplete();
                }
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to store asset {}: {}", metadata.scope(), exception.getMessage());
                if (!push)
                {
                    assetDownloadErrors++;
                    finishDownloadProgressIfComplete();
                }
            }
        }))
        {
            if (!push)
            {
                assetDownloadErrors++;
                finishDownloadProgressIfComplete();
            }
        }
    }

    private static void handleAssetUploadAck(FriendlyByteBuf input)
    {
        long transferId = input.readVarLong();
        String path = TogetherWire.readText(input, TogetherLimits.MAX_PATH_BYTES, "asset path");
        boolean successful = input.readBoolean();
        TogetherWire.requireFullyRead(input);
        if (transferId != pendingUploadTransferId || !path.equals(pendingUploadPath))
        {
            BBSTogetherMod.LOGGER.warn("Ignored stale asset upload acknowledgement for {}", path);
            return;
        }
        finishPendingUpload(successful);
    }

    private static void finishPendingUpload(boolean successful)
    {
        if (pendingUploadPath == null)
        {
            return;
        }
        if (successful)
        {
            assetUploadDone++;
        }
        else
        {
            assetUploadErrors++;
        }
        pendingUploadIndex++;
        pendingUploadPath = null;
        pendingUploadTransferId = -1;
        finishUploadProgressIfComplete();
        sendNextUpload();
    }

    private static void handleAssetDownloadFailed(FriendlyByteBuf input)
    {
        TogetherWire.readText(input, TogetherLimits.MAX_PATH_BYTES, "asset path");
        TogetherWire.requireFullyRead(input);
        assetDownloadErrors++;
        finishDownloadProgressIfComplete();
    }

    private static void finishDownloadProgressIfComplete()
    {
        if (assetDownloadTotal > 0 && assetDownloadDone + assetDownloadErrors >= assetDownloadTotal)
        {
            assetDownloadFinishedAt = System.currentTimeMillis();
        }
    }

    private static void finishUploadProgressIfComplete()
    {
        if (assetUploadTotal > 0 && assetUploadDone + assetUploadErrors >= assetUploadTotal)
        {
            assetUploadFinishedAt = System.currentTimeMillis();
        }
    }

    private static boolean downloadsEnabled()
    {
        return TogetherSettings.assetSyncDownload == null || TogetherSettings.assetSyncDownload.get();
    }

    private static boolean uploadsEnabled()
    {
        return TogetherSettings.assetSyncUpload == null || TogetherSettings.assetSyncUpload.get();
    }

    private static boolean pushesEnabled()
    {
        return TogetherSettings.assetSyncReceive != null && TogetherSettings.assetSyncReceive.get();
    }

    private static Path assetsRoot() throws IOException
    {
        if (BBSMod.getAssetsFolder() == null)
        {
            throw new IOException("BBS assets directory is unavailable");
        }
        Path root = BBSMod.getAssetsFolder().toPath().toAbsolutePath().normalize();
        Files.createDirectories(root);
        return root;
    }

    private static Path requireAssetPath(String relative)
    {
        try
        {
            return TogetherPath.resolveContained(assetsRoot(), relative);
        }
        catch (IOException exception)
        {
            throw new IllegalArgumentException("asset path is invalid", exception);
        }
    }

    private static BBSFilmPresence decodePresence(FriendlyByteBuf input, LocalSession current)
    {
        BBSFilmEditorKind kind = enumValue(BBSFilmEditorKind.values(), input.readUnsignedByte(), "editor kind");
        BBSFilmEditorView view = enumValue(BBSFilmEditorView.values(), input.readUnsignedByte(), "editor view");
        int replayIndex = input.readVarInt() - 1;
        int playhead = input.readVarInt();
        OptionalInt cursorTick = input.readBoolean() ? OptionalInt.of(input.readVarInt()) : OptionalInt.empty();
        OptionalInt cursorRow = input.readBoolean() ? OptionalInt.of(input.readVarInt()) : OptionalInt.empty();
        String sheetId = TogetherWire.readOptionalText(input, 256, "cursor sheet id");
        int replayCount = TogetherWire.readCount(input, TogetherLimits.MAX_SELECTIONS, "replay selection");
        List<Integer> replays = new ArrayList<>(replayCount);
        for (int i = 0; i < replayCount; i++)
        {
            replays.add(input.readVarInt());
        }
        int keyframeCount = TogetherWire.readCount(input, TogetherLimits.MAX_SELECTIONS, "keyframe selection");
        List<BBSFilmKeyframeSelection> keyframes = new ArrayList<>(keyframeCount);
        for (int i = 0; i < keyframeCount; i++)
        {
            keyframes.add(new BBSFilmKeyframeSelection(
                TogetherWire.readText(input, 256, "keyframe sheet id"),
                input.readVarInt()
            ));
        }
        return new BBSFilmPresence(
            current.sessionId,
            current.revision,
            kind,
            view,
            replayIndex,
            playhead,
            cursorTick,
            cursorRow,
            sheetId,
            replays,
            keyframes
        );
    }

    private static byte[] encodePresence(BBSFilmPresence presence)
    {
        FriendlyByteBuf output = BBSClientApi.createNetworkBuffer();
        output.writeByte(presence.editorKind().ordinal());
        output.writeByte(presence.editorView().ordinal());
        output.writeVarInt(presence.replayIndex() + 1);
        output.writeVarInt(presence.playheadTick());
        output.writeBoolean(presence.semanticCursorTick().isPresent());
        presence.semanticCursorTick().ifPresent(output::writeVarInt);
        output.writeBoolean(presence.semanticCursorRow().isPresent());
        presence.semanticCursorRow().ifPresent(output::writeVarInt);
        TogetherWire.writeOptionalText(output, presence.semanticCursorSheetId(), 256, "cursor sheet id");
        output.writeVarInt(presence.selectedReplayIndices().size());
        presence.selectedReplayIndices().forEach(output::writeVarInt);
        output.writeVarInt(presence.selectedKeyframes().size());
        for (BBSFilmKeyframeSelection keyframe : presence.selectedKeyframes())
        {
            TogetherWire.writeText(output, keyframe.sheetId(), 256, "keyframe sheet id");
            output.writeVarInt(keyframe.keyframeIndex());
        }
        byte[] bytes = new byte[output.readableBytes()];
        output.getBytes(output.readerIndex(), bytes);
        return bytes;
    }

    private static void updateAfterApply(LocalSession expected, BBSFilmApplyResult result)
    {
        if (session != expected)
        {
            return;
        }
        if (result.applied())
        {
            expected.revision = result.revision();
            expected.serverSequence = result.serverSeq();
        }
        else
        {
            BBSTogetherMod.LOGGER.warn("Remote Film apply requires recovery: {}", result.message());
            send(TogetherMessageIds.SNAPSHOT_REQUEST, output ->
                TogetherWire.writeText(output, expected.documentId, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id"));
        }
    }

    private static LocalSession matching(String document)
    {
        LocalSession current = session;
        return current != null && current.documentId.equals(document) ? current : null;
    }

    private static UUID localPlayerId()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? new UUID(0, 0) : minecraft.player.getUUID();
    }

    private static <T> T enumValue(T[] values, int index, String name)
    {
        if (index < 0 || index >= values.length)
        {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return values[index];
    }

    private static FriendlyByteBuf wrap(byte[] bytes)
    {
        FriendlyByteBuf buffer = BBSClientApi.createNetworkBuffer();
        buffer.writeBytes(bytes);
        return buffer;
    }

    private static boolean send(ResourceLocation message, Consumer<FriendlyByteBuf> encoder)
    {
        if (Minecraft.getInstance().getConnection() == null)
        {
            return false;
        }

        FriendlyByteBuf output = BBSClientApi.createNetworkBuffer();
        encoder.accept(output);
        return BBSClientApi.sendNetworkToServer(BBSTogetherMod.DESCRIPTOR, message, output);
    }

    private static void register(ResourceLocation message, Consumer<FriendlyByteBuf> receiver)
    {
        BBSClientApi.registerNetworkReceiver(BBSTogetherMod.DESCRIPTOR, message, (ignored, input) ->
        {
            try
            {
                receiver.accept(input);
            }
            catch (RuntimeException exception)
            {
                BBSTogetherMod.LOGGER.warn("Rejected server {} packet: {}", message, exception.getMessage());
            }
        });
    }

    private static final class FilmListener implements BBSFilmCollaborationListener
    {
        @Override
        public void onSessionOpened(BBSFilmSession opened)
        {
            session = new LocalSession(opened.sessionId(), opened.documentId(), opened.revision());
            send(TogetherMessageIds.SESSION_OPEN, output ->
                TogetherWire.writeText(output, opened.documentId(), TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id"));
        }

        @Override
        public void onLocalMutations(BBSFilmMutationBatch batch)
        {
            LocalSession current = session;
            if (current == null || current.sessionId != batch.sessionId())
            {
                return;
            }
            current.revision = batch.baseRevision() + 1;
            send(TogetherMessageIds.MUTATION, output ->
            {
                TogetherWire.writeText(output, current.documentId, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
                output.writeVarLong(batch.localOpId());
                output.writeVarInt(batch.mutations().size());
                for (BBSFilmMutation mutation : batch.mutations())
                {
                    output.writeByte(mutation.kind().ordinal());
                    TogetherWire.writePath(output, mutation.pathSegments());
                    TogetherWire.writeBytes(output, mutation.encodedBbsData(), 16 * 1024 * 1024, "mutation data");
                    output.writeByte(mutation.refreshHint().ordinal());
                }
            });
        }

        @Override
        public void onCheckpointRequired(BBSFilmCheckpointRequired checkpoint)
        {
            LocalSession current = session;
            if (current != null && current.sessionId == checkpoint.sessionId())
            {
                current.revision = checkpoint.revision();
                sendSnapshot(current);
            }
        }

        @Override
        public void onPresence(BBSFilmPresence presence)
        {
            LocalSession current = session;
            if (current == null || current.sessionId != presence.sessionId())
            {
                return;
            }
            current.revision = presence.revision();
            byte[] bytes = encodePresence(presence);
            send(TogetherMessageIds.PRESENCE, output ->
            {
                TogetherWire.writeText(output, current.documentId, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
                TogetherWire.writeBytes(output, bytes, TogetherLimits.BROKER_C2S_BYTES - 1024, "presence");
            });
        }

        @Override
        public void onSessionClosing(BBSFilmSession closing)
        {
            LocalSession current = session;
            if (current != null && current.sessionId == closing.sessionId())
            {
                sendSnapshot(current);
            }
        }

        @Override
        public void onSessionClosed(long sessionId)
        {
            LocalSession current = session;
            if (current != null && current.sessionId == sessionId)
            {
                send(TogetherMessageIds.SESSION_CLOSE, output ->
                    TogetherWire.writeText(output, current.documentId, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id"));
                session = null;
            }
        }
    }

    private static final class LocalSession
    {
        private final long sessionId;
        private final String documentId;
        private volatile long revision;
        private volatile long serverSequence = -1;

        private LocalSession(long sessionId, String documentId, long revision)
        {
            this.sessionId = sessionId;
            this.documentId = documentId;
            this.revision = revision;
        }
    }

    private record IncomingTransfer(TogetherTransferKind kind, String scope, int flags, long sequence)
    {}
}
