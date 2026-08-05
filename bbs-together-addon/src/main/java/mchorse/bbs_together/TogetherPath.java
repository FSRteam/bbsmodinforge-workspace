package mchorse.bbs_together;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Normalizes and confines a remote asset path to the BBS assets root. */
public final class TogetherPath
{
    private TogetherPath()
    {}

    public static Path resolveContained(Path root, String relative) throws IOException
    {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(relative, "relative");

        if (relative.isBlank() || relative.indexOf('\0') >= 0 || relative.codePoints().anyMatch(Character::isISOControl))
        {
            throw new IOException("asset path is blank or contains control characters");
        }

        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(relative.replace('\\', '/')).normalize();

        if (!candidate.startsWith(normalizedRoot) || candidate.equals(normalizedRoot))
        {
            throw new IOException("asset path escapes the BBS assets directory");
        }

        if (!Files.exists(normalizedRoot, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("BBS assets directory does not exist");
        }

        Path existingRoot = normalizedRoot.toRealPath();
        Path existingCandidate = candidate;
        while (existingCandidate != null && !Files.exists(existingCandidate, LinkOption.NOFOLLOW_LINKS))
        {
            existingCandidate = existingCandidate.getParent();
        }

        if (existingCandidate == null || !existingCandidate.toRealPath().startsWith(existingRoot))
        {
            throw new IOException("asset path resolves outside the BBS assets directory");
        }

        return candidate;
    }
}
