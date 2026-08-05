package mchorse.bbs_together.asset;

import mchorse.bbs_together.transfer.TransferChecksum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class TogetherAssetManifestTest
{
    @Test
    void scansAndRoundTripsDeterministically(@TempDir Path root) throws Exception
    {
        Files.createDirectories(root.resolve("nested"));
        Files.write(root.resolve("nested/file.bin"), new byte[] {1, 2, 3});

        var scanned = TogetherAssetManifest.scan(root);
        assertEquals(1, scanned.size());
        assertEquals("nested/file.bin", scanned.getFirst().path());
        assertArrayEquals(TransferChecksum.sha256(new byte[] {1, 2, 3}), scanned.getFirst().checksum());

        var decoded = TogetherAssetManifest.decode(TogetherAssetManifest.encode(scanned));
        assertEquals(scanned.getFirst().path(), decoded.getFirst().path());
        assertEquals(scanned.getFirst().size(), decoded.getFirst().size());
        assertArrayEquals(scanned.getFirst().checksum(), decoded.getFirst().checksum());
    }

    @Test
    void rejectsDuplicateManifestPaths() throws Exception
    {
        byte[] checksum = TransferChecksum.sha256(new byte[] {1});
        var entries = java.util.List.of(
            new TogetherAssetManifest.Entry("same.bin", 1, checksum),
            new TogetherAssetManifest.Entry("same.bin", 1, checksum)
        );
        assertThrows(Exception.class, () -> TogetherAssetManifest.encode(entries));
    }

    @Test
    void rejectsNonCanonicalPaths()
    {
        byte[] checksum = TransferChecksum.sha256(new byte[] {1});
        assertThrows(Exception.class, () -> TogetherAssetManifest.encode(java.util.List.of(
            new TogetherAssetManifest.Entry("folder/../file.bin", 1, checksum)
        )));
    }

    @Test
    void rejectsTruncatedAndTrailingManifestData() throws Exception
    {
        byte[] encoded = TogetherAssetManifest.encode(java.util.List.of(
            new TogetherAssetManifest.Entry("file.bin", 0, TransferChecksum.sha256(new byte[0]))
        ));

        assertThrows(Exception.class, () -> TogetherAssetManifest.decode(java.util.Arrays.copyOf(encoded, encoded.length - 1)));
        byte[] trailing = java.util.Arrays.copyOf(encoded, encoded.length + 1);
        assertThrows(Exception.class, () -> TogetherAssetManifest.decode(trailing));
    }

    @Test
    void rejectsMalformedUtf8Paths()
    {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 4 + 1 + 8 + TransferChecksum.SHA_256_BYTES);
        bytes.putInt(1);
        bytes.putInt(1);
        bytes.putInt(1);
        bytes.put((byte) 0xFF);
        bytes.putLong(0);
        bytes.put(TransferChecksum.sha256(new byte[0]));

        assertThrows(Exception.class, () -> TogetherAssetManifest.decode(bytes.array()));
    }
}
