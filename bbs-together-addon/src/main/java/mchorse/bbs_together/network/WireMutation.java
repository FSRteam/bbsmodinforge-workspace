package mchorse.bbs_together.network;

import java.util.List;

public record WireMutation(int kind, List<String> path, byte[] encodedData, int refreshHint)
{
    public WireMutation
    {
        path = List.copyOf(path);
        encodedData = encodedData.clone();
    }

    @Override
    public byte[] encodedData()
    {
        return this.encodedData.clone();
    }
}
