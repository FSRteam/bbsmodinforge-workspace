package mchorse.bbs_together;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TogetherPathTest
{
    @Test
    void rejectsTraversalAndAcceptsNestedFiles() throws Exception
    {
        var root = Files.createTempDirectory("bbs-together-assets");
        assertEquals(root.resolve("nested/file.bin"), TogetherPath.resolveContained(root, "nested/file.bin"));
        assertThrows(Exception.class, () -> TogetherPath.resolveContained(root, "../outside.bin"));
        assertThrows(Exception.class, () -> TogetherPath.resolveContained(root, root.toString()));
    }

    @Test
    void rejectsMissingTargetBelowSymlink(@TempDir Path temporary) throws Exception
    {
        Path root = Files.createDirectory(temporary.resolve("assets"));
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Path link = root.resolve("linked");

        try
        {
            Files.createSymbolicLink(link, outside);
        }
        catch (IOException | UnsupportedOperationException exception)
        {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are unavailable: " + exception.getMessage());
        }

        assertThrows(IOException.class, () -> TogetherPath.resolveContained(root, "linked/new/file.bin"));
    }
}
