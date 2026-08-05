package mchorse.bbs_together.transfer;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class TransferChecksum
{
    public static final int SHA_256_BYTES = 32;

    private TransferChecksum()
    {}

    public static byte[] sha256(byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static boolean matches(byte[] expected, byte[] actual)
    {
        return MessageDigest.isEqual(expected, actual);
    }
}
