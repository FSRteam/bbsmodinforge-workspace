package mchorse.bbs_together.asset;

import mchorse.bbs_together.TogetherLimits;
import mchorse.bbs_together.TogetherPath;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class TogetherAssetStorage
{
    private TogetherAssetStorage()
    {}

    public static byte[] read(Path root, String relative) throws IOException
    {
        Path path = TogetherPath.resolveContained(root, relative);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("asset is not a regular file");
        }
        long size = Files.size(path);
        if (size < 0 || size > TogetherLimits.MAX_ASSET_BYTES)
        {
            throw new IOException("asset file is too large");
        }
        return Files.readAllBytes(path);
    }

    public static Path writeAtomically(Path root, String relative, byte[] bytes) throws IOException
    {
        if (bytes == null || bytes.length > TogetherLimits.MAX_ASSET_BYTES)
        {
            throw new IOException("asset file is too large");
        }

        Path destination = TogetherPath.resolveContained(root, relative);
        Files.createDirectories(destination.getParent());
        destination = TogetherPath.resolveContained(root, relative);
        Path temporary = Files.createTempFile(destination.getParent(), ".bbs-together-", ".part");

        try
        {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING);
            try
            {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return destination;
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }
}
