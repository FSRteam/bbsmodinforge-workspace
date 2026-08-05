package mchorse.bbs_together;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_together.values.ValueKeyframeInterpolation;
import mchorse.bbs_together.values.ValueKeyframeShape;

import java.util.ArrayList;

/** Addon-owned settings. CDN fields from the upstream jar were unused and are omitted. */
public final class TogetherSettings
{
    public static ValueBoolean assetSyncDownload;
    public static ValueBoolean assetSyncUpload;
    public static ValueBoolean assetSyncReceive;
    public static ValueKeyframeShape defaultKeyframeShape;
    public static ValueBoolean defaultKeyframeInterpolationEnabled;
    public static ValueKeyframeInterpolation defaultKeyframeInterpolation;
    public static ValueBoolean defaultKeyframeColorEnabled;
    public static ValueInt defaultKeyframeColor;

    private TogetherSettings()
    {}

    public static void register(SettingsBuilder builder)
    {
        builder.category("sync");
        assetSyncDownload = builder.getBoolean("download", true);
        assetSyncUpload = builder.getBoolean("upload", true);
        assetSyncReceive = builder.getBoolean("receive", false);

        builder.category("keyframes");
        defaultKeyframeShape = new ValueKeyframeShape("default_shape", KeyframeShape.SQUARE);
        builder.register(defaultKeyframeShape);
        defaultKeyframeInterpolationEnabled = builder.getBoolean("default_interpolation_enabled", false);
        defaultKeyframeInterpolation = new ValueKeyframeInterpolation("default_interpolation", 0);
        builder.register(defaultKeyframeInterpolation);
        defaultKeyframeColorEnabled = builder.getBoolean("default_color_enabled", false);
        defaultKeyframeColor = builder.getInt("default_color", -1).color();
    }

    public static KeyframeShape defaultShape()
    {
        int value = defaultKeyframeShape == null ? 0 : defaultKeyframeShape.get();
        KeyframeShape[] shapes = KeyframeShape.values();
        return value >= 0 && value < shapes.length ? shapes[value] : KeyframeShape.SQUARE;
    }

    public static IInterp defaultInterpolation()
    {
        int value = defaultKeyframeInterpolation == null ? 0 : defaultKeyframeInterpolation.get();
        ArrayList<IInterp> values = new ArrayList<>(Interpolations.MAP.values());
        return value >= 0 && value < values.size() ? values.get(value) : Interpolations.LINEAR;
    }

    public static boolean shouldApplyInterpolation()
    {
        return defaultKeyframeInterpolationEnabled != null && defaultKeyframeInterpolationEnabled.get();
    }

    public static boolean shouldApplyColor()
    {
        return defaultKeyframeColorEnabled != null && defaultKeyframeColorEnabled.get();
    }

    public static int defaultColor()
    {
        return defaultKeyframeColor == null ? -1 : defaultKeyframeColor.get();
    }
}
