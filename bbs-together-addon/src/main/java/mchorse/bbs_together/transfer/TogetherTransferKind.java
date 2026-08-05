package mchorse.bbs_together.transfer;

public enum TogetherTransferKind
{
    FILM_SNAPSHOT,
    ASSET_FILE,
    ASSET_MANIFEST;

    public static TogetherTransferKind fromWire(int value)
    {
        TogetherTransferKind[] values = values();
        if (value < 0 || value >= values.length)
        {
            throw new IllegalArgumentException("transfer kind is invalid");
        }
        return values[value];
    }
}
