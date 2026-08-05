package mchorse.bbs_together;

import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

/** Applies the addon's configured style to keyframes explicitly created by the user. */
public final class TogetherKeyframeStyle
{
    private TogetherKeyframeStyle()
    {}

    public static void apply(Keyframe<?> keyframe)
    {
        if (keyframe == null)
        {
            return;
        }

        keyframe.setShape(TogetherSettings.defaultShape());

        if (TogetherSettings.shouldApplyInterpolation())
        {
            keyframe.getInterpolation().setInterp(TogetherSettings.defaultInterpolation());
        }

        if (TogetherSettings.shouldApplyColor())
        {
            keyframe.setColor(Color.rgb(TogetherSettings.defaultColor()));
        }
    }
}
