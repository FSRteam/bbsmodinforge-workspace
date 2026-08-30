package mchorse.bbs_together.network;

import mchorse.bbs_together.TogetherLimits;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One owner for the addon broker wire contract and all allocation bounds. */
public final class TogetherWire
{
    private TogetherWire()
    {}

    public static String readText(FriendlyByteBuf buffer, int maximumUtf8Bytes, String name)
    {
        String value = buffer.readUtf(maximumUtf8Bytes);
        validateText(value, maximumUtf8Bytes, name, false);
        return value;
    }

    public static String readOptionalText(FriendlyByteBuf buffer, int maximumUtf8Bytes, String name)
    {
        String value = buffer.readUtf(maximumUtf8Bytes);
        validateText(value, maximumUtf8Bytes, name, true);
        return value;
    }

    public static void writeText(FriendlyByteBuf buffer, String value, int maximumUtf8Bytes, String name)
    {
        validateText(value, maximumUtf8Bytes, name, false);
        buffer.writeUtf(value, maximumUtf8Bytes);
    }

    public static void writeOptionalText(FriendlyByteBuf buffer, String value, int maximumUtf8Bytes, String name)
    {
        validateText(value, maximumUtf8Bytes, name, true);
        buffer.writeUtf(value, maximumUtf8Bytes);
    }

    public static int readCount(FriendlyByteBuf buffer, int maximum, String name)
    {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum)
        {
            throw new IllegalArgumentException(name + " count is outside 0.." + maximum);
        }
        return count;
    }

    public static byte[] readBytes(FriendlyByteBuf buffer, int maximum, String name)
    {
        int length = buffer.readVarInt();
        if (length <= 0 || length > maximum || length > buffer.readableBytes())
        {
            throw new IllegalArgumentException(name + " byte length is outside 1.." + maximum);
        }

        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    public static void writeBytes(FriendlyByteBuf buffer, byte[] bytes, int maximum, String name)
    {
        if (bytes == null || bytes.length == 0 || bytes.length > maximum)
        {
            throw new IllegalArgumentException(name + " byte length is outside 1.." + maximum);
        }
        buffer.writeVarInt(bytes.length);
        buffer.writeBytes(bytes);
    }

    public static List<String> readPath(FriendlyByteBuf buffer)
    {
        int count = readCount(buffer, TogetherLimits.MAX_PATH_SEGMENTS, "path");
        if (count == 0)
        {
            throw new IllegalArgumentException("path is empty");
        }

        List<String> path = new ArrayList<>(count);
        int bytes = 0;
        for (int i = 0; i < count; i++)
        {
            String segment = readText(buffer, TogetherLimits.MAX_PATH_SEGMENT_BYTES, "path segment");
            bytes += segment.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > TogetherLimits.MAX_PATH_BYTES)
            {
                throw new IllegalArgumentException("path exceeds its UTF-8 limit");
            }
            path.add(segment);
        }
        return List.copyOf(path);
    }

    public static void writePath(FriendlyByteBuf buffer, List<String> path)
    {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty() || path.size() > TogetherLimits.MAX_PATH_SEGMENTS)
        {
            throw new IllegalArgumentException("path entry count is invalid");
        }

        buffer.writeVarInt(path.size());
        int bytes = 0;
        for (String segment : path)
        {
            validateText(segment, TogetherLimits.MAX_PATH_SEGMENT_BYTES, "path segment", false);
            bytes += segment.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > TogetherLimits.MAX_PATH_BYTES)
            {
                throw new IllegalArgumentException("path exceeds its UTF-8 limit");
            }
            buffer.writeUtf(segment, TogetherLimits.MAX_PATH_SEGMENT_BYTES);
        }
    }

    public static void requireFullyRead(FriendlyByteBuf buffer)
    {
        if (buffer.readableBytes() != 0)
        {
            throw new IllegalArgumentException("packet contains trailing bytes");
        }
    }

    private static void validateText(String value, int maximumUtf8Bytes, String name, boolean optional)
    {
        Objects.requireNonNull(value, name);
        if ((!optional && value.isBlank())
            || value.getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes
            || value.codePoints().anyMatch(Character::isISOControl))
        {
            throw new IllegalArgumentException(name + " is blank, too large or contains control characters");
        }
    }
}
