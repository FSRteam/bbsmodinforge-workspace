package mchorse.bbs_together.transfer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferAssemblerTest
{
    @Test
    void rejectsDuplicatesAndRequiresAllChunks()
    {
        byte[] first = new byte[mchorse.bbs_together.TogetherLimits.CHUNK_BYTES];
        byte[] last = new byte[] {4, 5, 6};
        TransferAssembler assembler = new TransferAssembler(2, first.length + last.length);
        assembler.accept(1, last);
        assertFalse(assembler.complete());
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(1, last));
        assembler.accept(0, first);
        byte[] expected = new byte[first.length + last.length];
        System.arraycopy(last, 0, expected, first.length, last.length);
        assertArrayEquals(expected, assembler.finish());
    }

    @Test
    void rejectsDeclaredSizeOverflow()
    {
        TransferAssembler assembler = new TransferAssembler(1, 2);
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(0, new byte[] {1, 2, 3}));
    }

    @Test
    void verifiesChecksumBeforePublishingBytes()
    {
        byte[] payload = new byte[] {1, 2, 3, 4};
        TransferAssembler valid = new TransferAssembler(1, payload.length, TransferChecksum.sha256(payload));
        valid.accept(0, payload);
        assertArrayEquals(payload, valid.finish());

        byte[] wrong = TransferChecksum.sha256(new byte[] {9});
        TransferAssembler invalid = new TransferAssembler(1, payload.length, wrong);
        invalid.accept(0, payload);
        assertThrows(IllegalStateException.class, invalid::finish);
    }

    @Test
    void verifiesAnEmptyTransferWithoutChunks()
    {
        byte[] empty = new byte[0];
        TransferAssembler assembler = new TransferAssembler(0, 0, TransferChecksum.sha256(empty));

        assertTrue(assembler.complete());
        assertArrayEquals(empty, assembler.finish());
    }
}
