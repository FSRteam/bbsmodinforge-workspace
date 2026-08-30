package mchorse.bbs_together.transfer;

import mchorse.bbs_together.TogetherLimits;

import java.util.BitSet;

/** Bounded, duplicate-rejecting chunk assembler used for assets and snapshots. */
public final class TransferAssembler
{
    private final int chunkCount;
    private final int totalBytes;
    private final BitSet received;
    private final byte[][] chunks;
    private final byte[] expectedChecksum;
    private int receivedBytes;

    public TransferAssembler(int chunkCount, int totalBytes)
    {
        this(chunkCount, totalBytes, null);
    }

    public TransferAssembler(int chunkCount, int totalBytes, byte[] expectedChecksum)
    {
        int expectedChunks = totalBytes <= 0 ? 0 : (totalBytes + TogetherLimits.CHUNK_BYTES - 1) / TogetherLimits.CHUNK_BYTES;
        if (chunkCount < 0 || chunkCount > TogetherLimits.MAX_CHUNKS
            || totalBytes < 0 || totalBytes > TogetherLimits.MAX_TRANSFER_BYTES
            || chunkCount != expectedChunks
            || expectedChecksum != null && expectedChecksum.length != TransferChecksum.SHA_256_BYTES)
        {
            throw new IllegalArgumentException("invalid transfer dimensions");
        }

        this.chunkCount = chunkCount;
        this.totalBytes = totalBytes;
        this.received = new BitSet(chunkCount);
        this.chunks = new byte[chunkCount][];
        this.expectedChecksum = expectedChecksum == null ? null : expectedChecksum.clone();
    }

    public synchronized void accept(int index, byte[] bytes)
    {
        int expectedBytes = index == this.chunkCount - 1
            ? this.totalBytes - TogetherLimits.CHUNK_BYTES * (this.chunkCount - 1)
            : TogetherLimits.CHUNK_BYTES;
        if (index < 0 || index >= this.chunkCount || bytes == null || bytes.length == 0
            || bytes.length != expectedBytes || this.received.get(index))
        {
            throw new IllegalArgumentException("invalid or duplicate transfer chunk");
        }

        if (this.receivedBytes + bytes.length > this.totalBytes)
        {
            throw new IllegalArgumentException("transfer exceeds declared size");
        }

        this.received.set(index);
        this.receivedBytes += bytes.length;
        this.chunks[index] = bytes.clone();
    }

    public synchronized boolean complete()
    {
        return this.received.cardinality() == this.chunkCount && this.receivedBytes == this.totalBytes;
    }

    public synchronized byte[] finish()
    {
        if (!complete())
        {
            throw new IllegalStateException("transfer is incomplete");
        }

        if (this.expectedChecksum != null)
        {
            java.security.MessageDigest digest;
            try
            {
                digest = java.security.MessageDigest.getInstance("SHA-256");
            }
            catch (java.security.NoSuchAlgorithmException exception)
            {
                throw new IllegalStateException("SHA-256 is unavailable", exception);
            }
            for (byte[] chunk : this.chunks)
            {
                digest.update(chunk);
            }
            if (!TransferChecksum.matches(this.expectedChecksum, digest.digest()))
            {
                throw new IllegalStateException("transfer checksum mismatch");
            }
        }

        byte[] result = new byte[this.totalBytes];
        int offset = 0;

        for (byte[] chunk : this.chunks)
        {
            System.arraycopy(chunk, 0, result, offset, chunk.length);
            offset += chunk.length;
        }

        return result;
    }

    public int chunkCount()
    {
        return this.chunkCount;
    }

    public int totalBytes()
    {
        return this.totalBytes;
    }
}
