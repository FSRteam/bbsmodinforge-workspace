package mchorse.bbs_together.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TogetherAssetStorageTest
{
    @Test
    void writesNestedAssetAtomicallyAndReadsItBack(@TempDir Path root) throws Exception
    {
        byte[] bytes = new byte[] {4, 5, 6};
        Path written = TogetherAssetStorage.writeAtomically(root, "nested/file.bin", bytes);

        assertEquals(root.resolve("nested/file.bin"), written);
        assertArrayEquals(bytes, TogetherAssetStorage.read(root, "nested/file.bin"));
        try (var files = Files.list(written.getParent()))
        {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".part")));
        }
    }

    @Test
    void rejectsTraversal(@TempDir Path root)
    {
        assertThrows(Exception.class, () -> TogetherAssetStorage.writeAtomically(root, "../outside.bin", new byte[] {1}));
    }
}
