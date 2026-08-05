package mchorse.bbs_together.asset;

import mchorse.bbs_together.TogetherLimits;
import mchorse.bbs_together.transfer.TransferChecksum;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic, bounded asset manifest codec. */
public final class TogetherAssetManifest
{
    private static final int VERSION = 1;

    private TogetherAssetManifest()
    {}

    public static List<Entry> scan(Path root) throws IOException
    {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Files.createDirectories(normalizedRoot);
        List<Entry> entries = new ArrayList<>();

        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException
            {
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file))
                {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.size() > TogetherLimits.MAX_ASSET_BYTES)
                {
                    return FileVisitResult.CONTINUE;
                }
                if (entries.size() >= TogetherLimits.MAX_MANIFEST_ENTRIES)
                {
                    throw new IOException("asset manifest entry limit exceeded");
                }

                String relative = normalizedRoot.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
                validatePath(relative);
                entries.add(new Entry(relative, attributes.size(), sha256(file)));
                return FileVisitResult.CONTINUE;
            }
        });

        entries.sort(Comparator.comparing(Entry::path));
        return List.copyOf(entries);
    }

    public static byte[] encode(List<Entry> entries) throws IOException
    {
        if (entries == null || entries.size() > TogetherLimits.MAX_MANIFEST_ENTRIES)
        {
            throw new IOException("asset manifest entry limit exceeded");
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes))
        {
            output.writeInt(VERSION);
            output.writeInt(entries.size());
            Set<String> paths = new HashSet<>();
            for (Entry entry : entries)
            {
                validateEntry(entry, paths);
                byte[] path = entry.path().getBytes(StandardCharsets.UTF_8);
                output.writeInt(path.length);
                output.write(path);
                output.writeLong(entry.size());
                output.write(entry.checksum());
                if (bytes.size() > TogetherLimits.MAX_TRANSFER_BYTES)
                {
                    throw new IOException("encoded asset manifest is too large");
                }
            }
        }
        return bytes.toByteArray();
    }

    public static List<Entry> decode(byte[] bytes) throws IOException
    {
        if (bytes == null || bytes.length == 0 || bytes.length > TogetherLimits.MAX_TRANSFER_BYTES)
        {
            throw new IOException("encoded asset manifest size is invalid");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes)))
        {
            if (input.readInt() != VERSION)
            {
                throw new IOException("unsupported asset manifest version");
            }
            int count = input.readInt();
            if (count < 0 || count > TogetherLimits.MAX_MANIFEST_ENTRIES)
            {
                throw new IOException("asset manifest entry count is invalid");
            }

            List<Entry> entries = new ArrayList<>(count);
            Set<String> paths = new HashSet<>();
            for (int i = 0; i < count; i++)
            {
                int pathBytes = input.readInt();
                if (pathBytes <= 0 || pathBytes > TogetherLimits.MAX_PATH_BYTES || pathBytes > input.available())
                {
                    throw new IOException("asset manifest path size is invalid");
                }
                byte[] encodedPath = input.readNBytes(pathBytes);
                if (encodedPath.length != pathBytes)
                {
                    throw new EOFException("asset manifest path is truncated");
                }
                long size = input.readLong();
                byte[] checksum = input.readNBytes(TransferChecksum.SHA_256_BYTES);
                if (checksum.length != TransferChecksum.SHA_256_BYTES)
                {
                    throw new EOFException("asset manifest checksum is truncated");
                }
                Entry entry = new Entry(decodeUtf8(encodedPath), size, checksum);
                validateEntry(entry, paths);
                entries.add(entry);
            }
            if (input.available() != 0)
            {
                throw new IOException("asset manifest contains trailing bytes");
            }
            return List.copyOf(entries);
        }
        catch (RuntimeException exception)
        {
            throw new IOException("asset manifest is invalid", exception);
        }
    }

    private static byte[] sha256(Path path) throws IOException
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path))
            {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    if (read > 0)
                    {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return digest.digest();
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void validateEntry(Entry entry, Set<String> paths) throws IOException
    {
        if (entry == null || entry.size() < 0 || entry.size() > TogetherLimits.MAX_ASSET_BYTES
            || entry.checksum() == null || entry.checksum().length != TransferChecksum.SHA_256_BYTES)
        {
            throw new IOException("asset manifest entry is invalid");
        }
        validatePath(entry.path());
        if (!paths.add(entry.path()))
        {
            throw new IOException("asset manifest contains a duplicate path");
        }
    }

    private static void validatePath(String path) throws IOException
    {
        if (path == null || path.isBlank() || path.getBytes(StandardCharsets.UTF_8).length > TogetherLimits.MAX_PATH_BYTES
            || path.indexOf('\\') >= 0 || path.startsWith("/")
            || path.codePoints().anyMatch(Character::isISOControl))
        {
            throw new IOException("asset manifest path is invalid");
        }
        Path parsed;
        try
        {
            parsed = Path.of(path);
        }
        catch (RuntimeException exception)
        {
            throw new IOException("asset manifest path is invalid", exception);
        }
        String normalized = parsed.normalize().toString().replace('\\', '/');
        if (parsed.isAbsolute() || !normalized.equals(path) || normalized.equals("."))
        {
            throw new IOException("asset manifest path is not canonical");
        }
    }

    private static String decodeUtf8(byte[] bytes) throws IOException
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString();
        }
        catch (CharacterCodingException exception)
        {
            throw new IOException("asset manifest path is not valid UTF-8", exception);
        }
    }

    public record Entry(String path, long size, byte[] checksum)
    {
        public Entry
        {
            checksum = checksum == null ? null : checksum.clone();
        }

        @Override
        public byte[] checksum()
        {
            return this.checksum == null ? null : this.checksum.clone();
        }
    }
}
