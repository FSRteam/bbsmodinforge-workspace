package mchorse.bbs_together;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.network.BBSNetworkRegistry;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.PermissionUtils;
import mchorse.bbs_together.asset.TogetherAssetIo;
import mchorse.bbs_together.asset.TogetherAssetManifest;
import mchorse.bbs_together.asset.TogetherAssetStorage;
import mchorse.bbs_together.network.TogetherWire;
import mchorse.bbs_together.network.WireMutation;
import mchorse.bbs_together.transfer.TogetherTransferKind;
import mchorse.bbs_together.transfer.TransferChecksum;
import mchorse.bbs_together.transfer.TransferRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

import java.time.Duration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Authoritative server state and broker routing for BBS Together. */
public final class TogetherServerNetwork
{
    private static final Map<UUID, Integer> COLORS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> DOCUMENTS = new ConcurrentHashMap<>();
    private static final TogetherSequenceTracker SEQUENCES = new TogetherSequenceTracker();
    private static final TogetherReplayLockRegistry REPLAY_LOCKS = new TogetherReplayLockRegistry();
    private static final Set<UUID> RECORDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, ServerLevel> EDITOR_LEVELS = new ConcurrentHashMap<>();
    private static final Set<UUID> ASSET_SESSIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, TogetherAssetRequestQueue> ASSET_REQUESTS = new ConcurrentHashMap<>();
    private static final TransferRegistry<IncomingTransfer> TRANSFERS = new TransferRegistry<>(Duration.ofSeconds(30));
    private static final AtomicLong SERVER_TRANSFER_IDS = new AtomicLong(1);
    private static final UUID SERVER_SOURCE = new UUID(0, 0);
    private static final int ASSET_DOWNLOAD = 0;
    private static final int ASSET_PUSH = 1;
    private static volatile BBSNetworkRegistry network;

    private TogetherServerNetwork()
    {}

    public static synchronized void register(BBSNetworkRegistry registry)
    {
        if (network != null)
        {
            return;
        }

        network = registry;
        register(TogetherMessageIds.HELLO, TogetherServerNetwork::handleHello);
        register(TogetherMessageIds.PLAYER_COLOR, TogetherServerNetwork::handlePlayerColor);
        register(TogetherMessageIds.SESSION_OPEN, TogetherServerNetwork::handleSessionOpen);
        register(TogetherMessageIds.SESSION_CLOSE, TogetherServerNetwork::handleSessionClose);
        register(TogetherMessageIds.MUTATION, TogetherServerNetwork::handleMutation);
        register(TogetherMessageIds.SNAPSHOT_REQUEST, TogetherServerNetwork::handleSnapshotRequest);
        register(TogetherMessageIds.TRANSFER_BEGIN, TogetherServerNetwork::handleTransferBegin);
        register(TogetherMessageIds.TRANSFER_CHUNK, TogetherServerNetwork::handleTransferChunk);
        register(TogetherMessageIds.ASSET_REQUEST, TogetherServerNetwork::handleAssetRequest);
        register(TogetherMessageIds.RECORDING, TogetherServerNetwork::handleRecordingStatus);
        register(TogetherMessageIds.PRESENCE, TogetherServerNetwork::handlePresence);
        register(TogetherMessageIds.REPLAY_LOCK, TogetherServerNetwork::handleReplayLock);
        register(TogetherMessageIds.FILM_LIST_CHANGED, TogetherServerNetwork::handleFilmListChanged);
        register(TogetherMessageIds.FILM_RENAMED, TogetherServerNetwork::handleFilmRenamed);
        register(TogetherMessageIds.FILM_DELETED, TogetherServerNetwork::handleFilmDeleted);
    }

    public static void reset()
    {
        Set<ServerLevel> levels = Collections.newSetFromMap(new IdentityHashMap<>());
        levels.addAll(EDITOR_LEVELS.values());
        levels.forEach(level -> BBSMod.getActions().resetDamage(level));
        COLORS.clear();
        DOCUMENTS.clear();
        EDITOR_LEVELS.clear();
        SEQUENCES.clear();
        REPLAY_LOCKS.clear();
        RECORDING.clear();
        ASSET_SESSIONS.clear();
        ASSET_REQUESTS.clear();
        TRANSFERS.clear();
    }

    public static void clearPlayerState(ServerPlayer player, MinecraftServer server)
    {
        UUID playerId = player.getUUID();
        String document = DOCUMENTS.remove(playerId);
        COLORS.remove(playerId);
        RECORDING.remove(playerId);
        ASSET_SESSIONS.remove(playerId);
        ASSET_REQUESTS.remove(playerId);
        TRANSFERS.clear(playerId);
        stopDamageControl(playerId);
        releaseReplayLock(playerId, server);

        if (document != null)
        {
            long sequence = SEQUENCES.nextPresence(document, playerId);
            broadcastDocument(server, document, null, TogetherMessageIds.SESSION_CLOSE, output ->
            {
                TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
                output.writeUUID(playerId);
                output.writeVarLong(sequence);
            });
            clearDocumentIfUnused(document);
        }
        broadcastFilmScreens(server);
    }

    public static void tick(MinecraftServer server)
    {
        TRANSFERS.expireIdle();
        ASSET_REQUESTS.keySet().forEach(playerId -> drainAssetRequests(server, playerId));
        DOCUMENTS.keySet().forEach(playerId ->
        {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null)
            {
                startDamageControl(player);
            }
        });
    }

    public static boolean isPlayerRecording(ServerPlayer player)
    {
        return RECORDING.contains(player.getUUID());
    }

    public static boolean setRecording(ServerPlayer player, boolean recording)
    {
        MinecraftServer server = player.getServer();
        if (recording && (server == null || !allowed(server, player) || !DOCUMENTS.containsKey(player.getUUID())))
        {
            send(player, TogetherMessageIds.RECORDING, output -> output.writeBoolean(false));
            return false;
        }

        if (recording)
        {
            RECORDING.add(player.getUUID());
        }
        else
        {
            RECORDING.remove(player.getUUID());
        }

        send(player, TogetherMessageIds.RECORDING, output -> output.writeBoolean(recording));
        return true;
    }

    public static void beginRecordingSession(Collection<ServerPlayer> players)
    {
        for (ServerPlayer player : players)
        {
            setRecording(player, !isPlayerRecording(player));
        }
    }

    private static void handleHello(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        TogetherWire.requireFullyRead(input);
        sendColors(server);
        broadcastFilmScreens(server);
        sendAssetManifest(server, player);
    }

    private static void handlePlayerColor(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        int color = input.readInt();
        TogetherWire.requireFullyRead(input);
        COLORS.put(player.getUUID(), color);
        sendColors(server);
    }

    private static void handleSessionOpen(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        if (!allowed(server, player))
        {
            return;
        }

        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        TogetherWire.requireFullyRead(input);
        DOCUMENTS.put(player.getUUID(), document);
        startDamageControl(player);
        long sequence = SEQUENCES.nextPresence(document, player.getUUID());
        broadcastDocument(server, document, null, TogetherMessageIds.SESSION_OPEN, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeUUID(player.getUUID());
            TogetherWire.writeText(output, player.getName().getString(), TogetherLimits.MAX_TEXT_BYTES, "player name");
            output.writeInt(COLORS.getOrDefault(player.getUUID(), 0xFFFFFFFF));
            output.writeVarLong(sequence);
        });
        broadcastFilmScreens(server);
        broadcastReplayLocks(server, document);
    }

    private static void handleSessionClose(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        TogetherWire.requireFullyRead(input);
        if (!document.equals(DOCUMENTS.remove(player.getUUID())))
        {
            return;
        }
        stopDamageControl(player.getUUID());
        releaseReplayLock(player.getUUID(), server);

        long sequence = SEQUENCES.nextPresence(document, player.getUUID());
        broadcastDocument(server, document, player.getUUID(), TogetherMessageIds.SESSION_CLOSE, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeUUID(player.getUUID());
            output.writeVarLong(sequence);
        });
        clearDocumentIfUnused(document);
        broadcastFilmScreens(server);
    }

    private static void handleMutation(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        if (!allowed(server, player))
        {
            return;
        }

        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        long localOpId = input.readVarLong();
        int count = TogetherWire.readCount(input, TogetherLimits.MAX_MUTATIONS, "mutation");
        if (count == 0)
        {
            throw new IllegalArgumentException("mutation batch is empty");
        }

        List<WireMutation> mutations = new ArrayList<>(count);
        long totalBytes = 0;
        for (int i = 0; i < count; i++)
        {
            int kind = input.readUnsignedByte();
            List<String> path = TogetherWire.readPath(input);
            byte[] data = TogetherWire.readBytes(input, 16 * 1024 * 1024, "mutation data");
            int refreshHint = input.readUnsignedByte();
            if (kind > 1 || refreshHint > 4 || (totalBytes += data.length) > 32L * 1024 * 1024)
            {
                throw new IllegalArgumentException("mutation enum or batch size is invalid");
            }
            mutations.add(new WireMutation(kind, path, data, refreshHint));
        }
        TogetherWire.requireFullyRead(input);
        requireActiveDocument(player, document);

        long sequence = SEQUENCES.nextMutation(document);
        broadcastDocument(server, document, null, TogetherMessageIds.MUTATION, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeUUID(player.getUUID());
            output.writeVarLong(sequence);
            output.writeVarLong(localOpId);
            output.writeVarInt(mutations.size());
            for (WireMutation mutation : mutations)
            {
                output.writeByte(mutation.kind());
                TogetherWire.writePath(output, mutation.path());
                TogetherWire.writeBytes(output, mutation.encodedData(), 16 * 1024 * 1024, "mutation data");
                output.writeByte(mutation.refreshHint());
            }
        });
    }

    private static void handleSnapshotRequest(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        TogetherWire.requireFullyRead(input);
        requireActiveDocument(player, document);
        broadcastDocument(server, document, player.getUUID(), TogetherMessageIds.SNAPSHOT_REQUEST, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeUUID(player.getUUID());
        });
    }

    private static void handleTransferBegin(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        if (!allowed(server, player))
        {
            return;
        }

        long transferId = input.readVarLong();
        TogetherTransferKind kind = TogetherTransferKind.fromWire(input.readUnsignedByte());
        int flags = input.readUnsignedByte();
        String scope = TogetherWire.readText(input, TogetherLimits.MAX_PATH_BYTES, "transfer scope");
        int totalBytes = input.readVarInt();
        int chunkCount = input.readVarInt();
        byte[] checksum = TogetherWire.readBytes(input, 32, "transfer checksum");
        TogetherWire.requireFullyRead(input);

        if (checksum.length != TransferChecksum.SHA_256_BYTES || flags != 0
            || kind == TogetherTransferKind.ASSET_MANIFEST)
        {
            throw new IllegalArgumentException("unsupported transfer or checksum");
        }

        if (kind == TogetherTransferKind.FILM_SNAPSHOT)
        {
            requireActiveDocument(player, scope);
        }
        else
        {
            requireAssetSession(player);
            if (totalBytes > TogetherLimits.MAX_ASSET_BYTES)
            {
                throw new IllegalArgumentException("asset upload is too large");
            }
            requireAssetPath(scope);
        }
        TRANSFERS.begin(player.getUUID(), transferId, chunkCount, totalBytes, checksum,
            new IncomingTransfer(kind, scope, flags, checksum));

        if (chunkCount == 0)
        {
            completeIncomingTransfer(server, player, transferId);
        }
    }

    private static void handleTransferChunk(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        long transferId = input.readVarLong();
        int index = input.readVarInt();
        byte[] chunk = TogetherWire.readBytes(input, TogetherLimits.CHUNK_BYTES, "transfer chunk");
        TogetherWire.requireFullyRead(input);

        if (!allowed(server, player))
        {
            TRANSFERS.cancel(player.getUUID(), transferId);
            return;
        }
        if (!TRANSFERS.accept(player.getUUID(), transferId, index, chunk))
        {
            return;
        }

        completeIncomingTransfer(server, player, transferId);
    }

    private static void completeIncomingTransfer(MinecraftServer server, ServerPlayer player, long transferId)
    {
        TransferRegistry.CompletedTransfer<IncomingTransfer> completed = TRANSFERS.finish(player.getUUID(), transferId);
        IncomingTransfer metadata = completed.metadata();

        if (metadata.kind() == TogetherTransferKind.FILM_SNAPSHOT)
        {
            requireActiveDocument(player, metadata.scope());
            publishSnapshot(server, player, transferId, metadata, completed.bytes());
        }
        else if (metadata.kind() == TogetherTransferKind.ASSET_FILE)
        {
            requireAssetSession(player);
            publishAssetUpload(server, player.getUUID(), transferId, metadata, completed.bytes());
        }
    }

    private static void publishSnapshot(
        MinecraftServer server,
        ServerPlayer source,
        long transferId,
        IncomingTransfer metadata,
        byte[] bytes
    )
    {
        long sequence = SEQUENCES.nextMutation(metadata.scope());
        send(source, TogetherMessageIds.SNAPSHOT_ACK, output ->
        {
            TogetherWire.writeText(output, metadata.scope(), TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeVarLong(sequence);
        });

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (!source.getUUID().equals(player.getUUID()) && metadata.scope().equals(DOCUMENTS.get(player.getUUID())))
            {
                sendTransfer(player, source.getUUID(), transferId, metadata, sequence, bytes);
            }
        }
    }

    private static void sendTransfer(
        ServerPlayer player,
        UUID source,
        long transferId,
        IncomingTransfer metadata,
        long sequence,
        byte[] bytes
    )
    {
        int chunkCount = (bytes.length + TogetherLimits.CHUNK_BYTES - 1) / TogetherLimits.CHUNK_BYTES;
        send(player, TogetherMessageIds.TRANSFER_BEGIN, output ->
        {
            output.writeUUID(source);
            output.writeVarLong(transferId);
            output.writeByte(metadata.kind().ordinal());
            output.writeByte(metadata.flags());
            TogetherWire.writeText(output, metadata.scope(), TogetherLimits.MAX_PATH_BYTES, "transfer scope");
            output.writeVarLong(sequence);
            output.writeVarInt(bytes.length);
            output.writeVarInt(chunkCount);
            TogetherWire.writeBytes(output, metadata.checksum(), 32, "transfer checksum");
        });

        for (int index = 0; index < chunkCount; index++)
        {
            int offset = index * TogetherLimits.CHUNK_BYTES;
            int length = Math.min(TogetherLimits.CHUNK_BYTES, bytes.length - offset);
            byte[] chunk = java.util.Arrays.copyOfRange(bytes, offset, offset + length);
            int chunkIndex = index;
            send(player, TogetherMessageIds.TRANSFER_CHUNK, output ->
            {
                output.writeUUID(source);
                output.writeVarLong(transferId);
                output.writeVarInt(chunkIndex);
                TogetherWire.writeBytes(output, chunk, TogetherLimits.CHUNK_BYTES, "transfer chunk");
            });
        }
    }

    public static void sendAssetManifest(MinecraftServer server, ServerPlayer player)
    {
        if (!allowed(server, player))
        {
            return;
        }

        UUID playerId = player.getUUID();
        ASSET_SESSIONS.add(playerId);
        if (!TogetherAssetIo.submit(() ->
        {
            try
            {
                byte[] bytes = TogetherAssetManifest.encode(TogetherAssetManifest.scan(assetsRoot()));
                byte[] checksum = TransferChecksum.sha256(bytes);
                server.execute(() ->
                {
                    ServerPlayer current = server.getPlayerList().getPlayer(playerId);
                    if (current != null && ASSET_SESSIONS.contains(playerId))
                    {
                        sendTransfer(current, SERVER_SOURCE, nextServerTransferId(),
                            new IncomingTransfer(TogetherTransferKind.ASSET_MANIFEST, "manifest", 0, checksum), 0, bytes);
                    }
                });
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to build asset manifest for {}: {}", player.getScoreboardName(), exception.getMessage());
            }
        }))
        {
            BBSTogetherMod.LOGGER.warn("Asset manifest queue is full for {}", player.getScoreboardName());
        }
    }

    public static int requestAssetSync(MinecraftServer server)
    {
        int requested = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (allowed(server, player))
            {
                sendAssetManifest(server, player);
                requested++;
            }
        }
        return requested;
    }

    private static void handleAssetRequest(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        requireAssetSession(player);
        if (!allowed(server, player))
        {
            throw new IllegalStateException("asset request is not permitted");
        }

        int count = TogetherWire.readCount(input, TogetherLimits.MAX_ASSET_REQUESTS, "asset request");
        if (count == 0)
        {
            throw new IllegalArgumentException("asset request is empty");
        }
        List<String> paths = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            String path = TogetherWire.readText(input, TogetherLimits.MAX_PATH_BYTES, "asset path");
            requireAssetPath(path);
            paths.add(path);
        }
        TogetherWire.requireFullyRead(input);
        UUID playerId = player.getUUID();
        TogetherAssetRequestQueue state = ASSET_REQUESTS.computeIfAbsent(playerId, ignored -> new TogetherAssetRequestQueue());
        state.enqueue(paths);
        drainAssetRequests(server, playerId);
    }

    private static void drainAssetRequests(MinecraftServer server, UUID playerId)
    {
        TogetherAssetRequestQueue state = ASSET_REQUESTS.get(playerId);
        if (state == null)
        {
            return;
        }

        String relative = state.beginNext();
        if (relative == null)
        {
            if (state.isIdle())
            {
                ASSET_REQUESTS.remove(playerId, state);
            }
            return;
        }

        if (!TogetherAssetIo.submit(() ->
        {
            byte[] bytes = null;
            try
            {
                bytes = TogetherAssetStorage.read(assetsRoot(), relative);
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to read requested asset {}: {}", relative, exception.getMessage());
            }

            byte[] payload = bytes;
            server.execute(() ->
            {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (payload != null && player != null && ASSET_SESSIONS.contains(playerId))
                {
                    byte[] checksum = TransferChecksum.sha256(payload);
                    sendTransfer(player, SERVER_SOURCE, nextServerTransferId(),
                        new IncomingTransfer(TogetherTransferKind.ASSET_FILE, relative, ASSET_DOWNLOAD, checksum), 0, payload);
                }
                else if (player != null)
                {
                    send(player, TogetherMessageIds.ASSET_DOWNLOAD_FAILED, output ->
                        TogetherWire.writeText(output, relative, TogetherLimits.MAX_PATH_BYTES, "asset path"));
                }
                state.completeCurrent();
                drainAssetRequests(server, playerId);
            });
        }))
        {
            state.retryCurrent(relative);
        }
    }

    private static void publishAssetUpload(
        MinecraftServer server,
        UUID uploaderId,
        long transferId,
        IncomingTransfer metadata,
        byte[] bytes
    )
    {
        if (!TogetherAssetIo.submit(() ->
        {
            try
            {
                TogetherAssetStorage.writeAtomically(assetsRoot(), metadata.scope(), bytes);
                server.execute(() -> finishAssetUpload(server, uploaderId, transferId, metadata, bytes, true));
            }
            catch (IOException exception)
            {
                BBSTogetherMod.LOGGER.warn("Unable to store uploaded asset {}: {}", metadata.scope(), exception.getMessage());
                server.execute(() -> finishAssetUpload(server, uploaderId, transferId, metadata, bytes, false));
            }
        }))
        {
            finishAssetUpload(server, uploaderId, transferId, metadata, bytes, false);
        }
    }

    private static void finishAssetUpload(
        MinecraftServer server,
        UUID uploaderId,
        long transferId,
        IncomingTransfer metadata,
        byte[] bytes,
        boolean successful
    )
    {
        ServerPlayer uploader = server.getPlayerList().getPlayer(uploaderId);
        if (uploader != null)
        {
            send(uploader, TogetherMessageIds.ASSET_UPLOAD_ACK, output ->
            {
                output.writeVarLong(transferId);
                TogetherWire.writeText(output, metadata.scope(), TogetherLimits.MAX_PATH_BYTES, "asset path");
                output.writeBoolean(successful);
            });
        }
        if (!successful)
        {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (!uploaderId.equals(player.getUUID()) && ASSET_SESSIONS.contains(player.getUUID()))
            {
                sendTransfer(player, uploaderId, transferId,
                    new IncomingTransfer(TogetherTransferKind.ASSET_FILE, metadata.scope(), ASSET_PUSH, metadata.checksum()),
                    0,
                    bytes);
            }
        }
    }

    private static Path assetsRoot() throws IOException
    {
        if (BBSMod.getAssetsFolder() == null)
        {
            throw new IOException("BBS assets directory is unavailable");
        }
        Path root = BBSMod.getAssetsFolder().toPath().toAbsolutePath().normalize();
        java.nio.file.Files.createDirectories(root);
        return root;
    }

    private static void requireAssetSession(ServerPlayer player)
    {
        if (!ASSET_SESSIONS.contains(player.getUUID()))
        {
            throw new IllegalStateException("player has no active asset sync session");
        }
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

    private static long nextServerTransferId()
    {
        long id = SERVER_TRANSFER_IDS.getAndIncrement();
        if (id <= 0)
        {
            SERVER_TRANSFER_IDS.set(2);
            return 1;
        }
        return id;
    }

    private static void handlePresence(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        byte[] presence = TogetherWire.readBytes(input, TogetherLimits.BROKER_C2S_BYTES - 1024, "presence");
        TogetherWire.requireFullyRead(input);
        requireActiveDocument(player, document);
        long sequence = SEQUENCES.nextPresence(document, player.getUUID());
        broadcastDocument(server, document, player.getUUID(), TogetherMessageIds.PRESENCE, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeUUID(player.getUUID());
            TogetherWire.writeText(output, player.getName().getString(), TogetherLimits.MAX_TEXT_BYTES, "player name");
            output.writeInt(COLORS.getOrDefault(player.getUUID(), 0xFFFFFFFF));
            output.writeVarLong(sequence);
            TogetherWire.writeBytes(output, presence, TogetherLimits.BROKER_C2S_BYTES - 1024, "presence");
        });
    }

    private static void handleRecordingStatus(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        boolean recording = input.readBoolean();
        TogetherWire.requireFullyRead(input);
        if (!allowed(server, player) || !DOCUMENTS.containsKey(player.getUUID()))
        {
            RECORDING.remove(player.getUUID());
            return;
        }
        if (recording)
        {
            RECORDING.add(player.getUUID());
        }
        else
        {
            RECORDING.remove(player.getUUID());
        }
    }

    private static void handleReplayLock(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
        int replayIndex = input.readVarInt();
        boolean locked = input.readBoolean();
        TogetherWire.requireFullyRead(input);

        if (!allowed(server, player))
        {
            return;
        }
        requireActiveDocument(player, document);
        if (replayIndex < 0 || replayIndex >= TogetherLimits.MAX_REPLAYS)
        {
            throw new IllegalArgumentException("replay index is outside its limit");
        }

        TogetherReplayLockRegistry.LockChange change;
        if (locked)
        {
            MapType film = BBSMod.getFilms().loadRaw(document);
            if (film == null || replayIndex >= film.getList("replays").size())
            {
                throw new IllegalArgumentException("replay index does not exist in the active Film");
            }
            change = REPLAY_LOCKS.tryLock(player.getUUID(), document, replayIndex);
        }
        else
        {
            change = REPLAY_LOCKS.unlock(player.getUUID(), document, replayIndex);
        }

        broadcastReplayLockChange(server, change, document);
    }

    private static void handleFilmListChanged(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        TogetherWire.requireFullyRead(input);
        if (allowed(server, player))
        {
            broadcastAll(server, player.getUUID(), TogetherMessageIds.FILM_LIST_CHANGED, output -> {});
        }
    }

    private static void handleFilmRenamed(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String from = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "old Film id");
        String to = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "new Film id");
        TogetherWire.requireFullyRead(input);
        validateFilmId(from);
        validateFilmId(to);

        if (!allowed(server, player) || BBSMod.getFilms().exists(from) || !BBSMod.getFilms().exists(to))
        {
            return;
        }

        broadcastAll(server, player.getUUID(), TogetherMessageIds.FILM_RENAMED, output ->
        {
            TogetherWire.writeText(output, from, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "old Film id");
            TogetherWire.writeText(output, to, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "new Film id");
        });
    }

    private static void handleFilmDeleted(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input)
    {
        String document = TogetherWire.readText(input, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "Film id");
        TogetherWire.requireFullyRead(input);
        validateFilmId(document);

        if (!allowed(server, player) || BBSMod.getFilms().exists(document))
        {
            return;
        }

        broadcastAll(server, player.getUUID(), TogetherMessageIds.FILM_DELETED, output ->
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "Film id"));
    }

    private static void startDamageControl(ServerPlayer player)
    {
        UUID playerId = player.getUUID();
        ServerLevel next = player.serverLevel();
        ServerLevel previous = EDITOR_LEVELS.get(playerId);
        if (previous == next)
        {
            return;
        }

        boolean firstInNext = EDITOR_LEVELS.values().stream().noneMatch(level -> level == next);
        EDITOR_LEVELS.put(playerId, next);
        if (previous != null && EDITOR_LEVELS.values().stream().noneMatch(level -> level == previous))
        {
            BBSMod.getActions().resetDamage(previous);
        }
        if (firstInNext)
        {
            BBSMod.getActions().trackDamage(next);
        }
    }

    private static void stopDamageControl(UUID playerId)
    {
        ServerLevel previous = EDITOR_LEVELS.remove(playerId);
        if (previous != null && EDITOR_LEVELS.values().stream().noneMatch(level -> level == previous))
        {
            BBSMod.getActions().resetDamage(previous);
        }
    }

    private static void broadcastFilmScreens(MinecraftServer server)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            send(player, TogetherMessageIds.FILM_SCREENS, output ->
            {
                output.writeVarInt(DOCUMENTS.size());
                DOCUMENTS.forEach((id, document) ->
                {
                    output.writeUUID(id);
                    TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
                });
            });
        }
    }

    private static void releaseReplayLock(UUID playerId, MinecraftServer server)
    {
        TogetherReplayLockRegistry.ReplayLock released = REPLAY_LOCKS.clearOwner(playerId);
        if (released != null)
        {
            broadcastReplayLocks(server, released.document());
        }
    }

    private static void broadcastReplayLockChange(
        MinecraftServer server,
        TogetherReplayLockRegistry.LockChange change,
        String requestedDocument
    )
    {
        Set<String> documents = new java.util.LinkedHashSet<>();
        if (change.previous() != null)
        {
            documents.add(change.previous().document());
        }
        if (change.current() != null)
        {
            documents.add(change.current().document());
        }
        if (documents.isEmpty() || !change.accepted())
        {
            documents.add(requestedDocument);
        }
        documents.forEach(document -> broadcastReplayLocks(server, document));
    }

    private static void broadcastReplayLocks(MinecraftServer server, String document)
    {
        Map<UUID, Integer> locks = REPLAY_LOCKS.snapshot(document);
        if (locks.size() > TogetherLimits.MAX_REPLAY_LOCKS)
        {
            throw new IllegalStateException("replay lock count exceeds its limit");
        }

        broadcastAll(server, null, TogetherMessageIds.REPLAY_LOCK, output ->
        {
            TogetherWire.writeText(output, document, TogetherLimits.MAX_DOCUMENT_ID_BYTES, "document id");
            output.writeVarInt(locks.size());
            locks.forEach((owner, replayIndex) ->
            {
                output.writeUUID(owner);
                output.writeVarInt(replayIndex);
            });
        });
    }

    private static void validateFilmId(String document)
    {
        if (BBSMod.getFilms().getFile(document) == null)
        {
            throw new IllegalArgumentException("Film id is outside the repository");
        }
    }

    private static boolean allowed(MinecraftServer server, ServerPlayer player)
    {
        return PermissionUtils.arePanelsAllowed(server, player);
    }

    private static void requireActiveDocument(ServerPlayer player, String document)
    {
        if (!document.equals(DOCUMENTS.get(player.getUUID())))
        {
            throw new IllegalStateException("player is not editing the declared Film");
        }
    }

    private static void clearDocumentIfUnused(String document)
    {
        if (!DOCUMENTS.containsValue(document))
        {
            SEQUENCES.clearDocument(document);
        }
    }

    private static void sendColors(MinecraftServer server)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            send(player, TogetherMessageIds.PLAYER_COLOR, output ->
            {
                output.writeVarInt(COLORS.size());
                COLORS.forEach((id, color) ->
                {
                    output.writeUUID(id);
                    output.writeInt(color);
                });
            });
        }
    }

    private static void broadcastDocument(
        MinecraftServer server,
        String document,
        UUID excluded,
        ResourceLocation message,
        Consumer<FriendlyByteBuf> encoder
    )
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (document.equals(DOCUMENTS.get(player.getUUID())) && (excluded == null || !excluded.equals(player.getUUID())))
            {
                send(player, message, encoder);
            }
        }
    }

    private static void broadcastAll(
        MinecraftServer server,
        UUID excluded,
        ResourceLocation message,
        Consumer<FriendlyByteBuf> encoder
    )
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (excluded == null || !excluded.equals(player.getUUID()))
            {
                send(player, message, encoder);
            }
        }
    }

    private static void send(ServerPlayer player, ResourceLocation message, Consumer<FriendlyByteBuf> encoder)
    {
        BBSNetworkRegistry current = network;
        if (current == null)
        {
            return;
        }
        FriendlyByteBuf output = current.createBuffer();
        encoder.accept(output);
        current.sendToPlayer(player, message, output);
    }

    private static void register(ResourceLocation message, ServerHandler handler)
    {
        network.registerServerReceiver(message, (server, player, ignored, input) ->
        {
            try
            {
                handler.receive(server, player, input);
            }
            catch (RuntimeException exception)
            {
                BBSTogetherMod.LOGGER.warn("Rejected {} packet from {}: {}", message, player.getScoreboardName(), exception.getMessage());
            }
        });
    }

    @FunctionalInterface
    private interface ServerHandler
    {
        void receive(MinecraftServer server, ServerPlayer player, FriendlyByteBuf input);
    }

    private record IncomingTransfer(TogetherTransferKind kind, String scope, int flags, byte[] checksum)
    {
        private IncomingTransfer
        {
            checksum = checksum.clone();
        }

        @Override
        public byte[] checksum()
        {
            return this.checksum.clone();
        }
    }

}
