package mchorse.bbs_together.values;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

public final class ValueKeyframeShape extends ValueInt
{
    public ValueKeyframeShape(String id, KeyframeShape defaultValue)
    {
        super(id, defaultValue.ordinal(), 0, KeyframeShape.values().length - 1);
    }
}
