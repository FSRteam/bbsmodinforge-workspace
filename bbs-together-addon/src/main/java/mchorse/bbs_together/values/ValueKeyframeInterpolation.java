package mchorse.bbs_together.values;

import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.interps.Interpolations;

public final class ValueKeyframeInterpolation extends ValueInt
{
    public ValueKeyframeInterpolation(String id, int defaultValue)
    {
        super(id, defaultValue, 0, Math.max(0, Interpolations.MAP.size() - 1));
    }
}
